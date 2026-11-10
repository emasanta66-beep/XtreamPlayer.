package app.xtreamplayer.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import app.xtreamplayer.R

/** Una TV u otro reproductor DLNA/UPnP encontrado en la red local. */
data class DlnaDevice(
    val name: String,
    val model: String?,
    /** URL del servicio AVTransport, que es el que permite ordenar "reproduce esto". */
    val controlUrl: String,
)

/** Posición y duración que informa la TV mientras reproduce. */
data class DlnaPosition(
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Envía películas y episodios a una TV compatible con DLNA/UPnP (la mayoría de LG lo son).
 * No necesita Chromecast ni instalar nada en la TV: la TV recibe la URL y reproduce el archivo.
 */
class DlnaClient(context: Context) {
    private val appContext = context.applicationContext

    private val http = Http.client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Busca reproductores DLNA en la misma red Wi-Fi. */
    suspend fun discover(timeoutMs: Long = 4500): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val locations = searchLocations(timeoutMs)
        locations.mapNotNull { location ->
            runCatching { describe(location) }.getOrNull()
        }
            .distinctBy { it.controlUrl }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /**
     * Manda [url] a la TV. Primero intenta con metadatos (título y tipo de video) y, si la TV
     * los rechaza, reintenta sin metadatos, que es lo que aceptan algunos modelos antiguos.
     */
    suspend fun cast(device: DlnaDevice, url: String, title: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val metadata = didl(title, url, mimeForUrl(url))
                val withMetadata = runCatching { setAvTransportUri(device, url, metadata) }
                if (withMetadata.isFailure) {
                    setAvTransportUri(device, url, "")
                }
                play(device)
            }
        }

    /** Detiene la reproducción en la TV. */
    suspend fun stop(device: DlnaDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            soap(device, "Stop", "<InstanceID>0</InstanceID>")
            Unit
        }
    }

    /** Pregunta a la TV en qué minuto va y cuánto dura el video (para "Continuar viendo"). */
    suspend fun position(device: DlnaDevice): Result<DlnaPosition> = withContext(Dispatchers.IO) {
        runCatching {
            val text = soap(device, "GetPositionInfo", "<InstanceID>0</InstanceID>")
            val rel = Regex("<RelTime>([^<]+)</RelTime>").find(text)
                ?.groupValues?.get(1) ?: throw IOException(appContext.getString(R.string.tv_no_position))
            val duration = Regex("<TrackDuration>([^<]+)</TrackDuration>").find(text)
                ?.groupValues?.get(1)
            DlnaPosition(parseTime(rel), duration?.let { parseTime(it) } ?: 0L)
        }
    }

    // ---------- Descubrimiento SSDP ----------

    private fun searchLocations(timeoutMs: Long): List<String> {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("xtream-dlna")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val socket = DatagramSocket(0, InetAddress.getByName("0.0.0.0")).apply {
            reuseAddress = true
            soTimeout = 1000
        }

        try {
            val request = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: 239.255.255.250:1900\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
                append("USER-AGENT: Android/UPnP/1.0 XtreamPlayer\r\n")
                append("\r\n")
            }
            val data = request.toByteArray(Charsets.US_ASCII)
            val target = InetAddress.getByName("239.255.255.250")
            socket.send(DatagramPacket(data, data.size, target, 1900))

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            val locations = linkedSetOf<String>()
            val buffer = ByteArray(8192)

            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                socket.soTimeout = remaining.coerceAtMost(1000).toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                text.lineSequence()
                    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { locations.add(it) }
            }
            return locations.toList()
        } finally {
            socket.close()
            if (lock?.isHeld == true) lock.release()
        }
    }

    /** Lee el XML de descripción del dispositivo y localiza su servicio AVTransport. */
    private fun describe(location: String): DlnaDevice? {
        val req = Request.Builder()
            .url(location)
            .header("User-Agent", USER_AGENT)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val doc = parseXml(bytes)
            val friendly = textOf(doc.documentElement, "friendlyName")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
            val model = textOf(doc.documentElement, "modelName")?.trim()?.takeIf { it.isNotEmpty() }

            val services = doc.getElementsByTagName("service")
            for (i in 0 until services.length) {
                val service = services.item(i) as? Element ?: continue
                if (textOf(service, "serviceType")?.trim() != AV_TRANSPORT) continue
                val control = textOf(service, "controlURL")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val controlUrl = runCatching {
                    URI(location).resolve(control).toString()
                }.getOrNull() ?: continue
                return DlnaDevice(friendly, model, controlUrl)
            }
        }
        return null
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // El XML viene de la red local; se bloquean entidades externas por seguridad.
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun textOf(element: Element, tag: String): String? {
        val nodes = element.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent
    }

    // ---------- Órdenes SOAP AVTransport ----------

    private fun setAvTransportUri(device: DlnaDevice, url: String, metadata: String) {
        val inner = buildString {
            append("<InstanceID>0</InstanceID>")
            append("<CurrentURI>")
            append(xmlEscape(url))
            append("</CurrentURI>")
            append("<CurrentURIMetaData>")
            append(xmlEscape(metadata))
            append("</CurrentURIMetaData>")
        }
        soap(device, "SetAVTransportURI", inner)
    }

    private fun play(device: DlnaDevice) {
        soap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    private fun soap(device: DlnaDevice, action: String, inner: String): String {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            append("<s:Body><u:")
            append(action)
            append(" xmlns:u=\"")
            append(AV_TRANSPORT)
            append("\">")
            append(inner)
            append("</u:")
            append(action)
            append("></s:Body></s:Envelope>")
        }

        val request = Request.Builder()
            .url(device.controlUrl)
            .header("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        return http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(appContext.getString(R.string.tv_http_error, resp.code))
            }
            if (text.contains("<UPnPError", ignoreCase = true)) {
                val code = Regex("<errorCode>(\\d+)</errorCode>").find(text)?.groupValues?.get(1)
                throw IOException(
                    code?.let { appContext.getString(R.string.tv_command_rejected_code, it) }
                        ?: appContext.getString(R.string.tv_command_rejected),
                )
            }
            text
        }
    }

    // ---------- Metadatos y utilidades ----------

    private fun didl(title: String, url: String, mime: String): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
        append("<dc:title>")
        append(xmlEscape(title))
        append("</dc:title>")
        append("<upnp:class>object.item.videoItem</upnp:class>")
        append("<res protocolInfo=\"http-get:*:")
        append(mime)
        append(":*\">")
        append(xmlEscape(url))
        append("</res>")
        append("</item></DIDL-Lite>")
    }

    private fun parseTime(value: String): Long {
        val parts = value.trim().split(':')
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: return 0L
        val minutes = parts[1].toLongOrNull() ?: return 0L
        val seconds = parts[2].toDoubleOrNull() ?: return 0L
        return (hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong()
    }

    private fun mimeForUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi", "divx" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "mpg", "mpeg", "vob" -> "video/mpeg"
            "3gp" -> "video/3gpp"
            "ogv" -> "video/ogg"
            "m3u8" -> "application/vnd.apple.mpegurl"
            else -> "video/mp4"
        }
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private companion object {
        const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val USER_AGENT = "Android/UPnP/1.0 XtreamPlayer"
    }
}
