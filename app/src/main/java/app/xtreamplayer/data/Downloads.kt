package app.xtreamplayer.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import app.xtreamplayer.DownloadService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request

enum class DlStatus { QUEUED, DOWNLOADING, PAUSED, DONE, ERROR }

/** Una película o capítulo descargado (o en proceso de descarga). */
data class DownloadItem(
    /** Misma clave que usa el reproductor para recordar la posición. */
    val key: String,
    val accountId: String,
    /** "movie" | "episode" */
    val kind: String,
    /** Nombre de la película o de la serie. */
    val title: String,
    /** Por ejemplo "T1E3" (vacío en películas). */
    val subtitle: String,
    val image: String?,
    val url: String,
    val ext: String,
    val contentId: String,
    val seriesId: Int,
    /** Nombre del archivo dentro de la carpeta de descargas. */
    val file: String,
    val status: DlStatus,
    val bytes: Long,
    val total: Long,
    val error: String? = null,
)

/**
 * Cola de descargas: baja de a un archivo, con reanudación (cabecera Range).
 * Todo el estado se modifica desde el hilo principal; la transferencia corre en IO.
 */
object DownloadCenter {
    val items = mutableStateListOf<DownloadItem>()

    private var appCtx: Context? = null
    private var storage: Storage? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())

    /** Verdadero mientras el trabajador de la cola está activo (lo lee el servicio). */
    @Volatile
    var running = false
        private set

    private var currentKey: String? = null
    private var currentJob: Deferred<String?>? = null

    @Volatile
    private var currentCall: Call? = null

    fun init(context: Context) {
        if (appCtx != null) return
        val ctx = context.applicationContext
        appCtx = ctx
        val st = Storage(ctx)
        storage = st
        // Tras cerrar la app lo que estaba bajando queda en pausa; se reanuda a mano.
        items.addAll(st.loadDownloads().map {
            if (it.status == DlStatus.QUEUED || it.status == DlStatus.DOWNLOADING) {
                it.copy(status = DlStatus.PAUSED)
            } else {
                it
            }
        })
    }

    private fun dir(): File {
        val ctx = appCtx!!
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "downloads").apply { mkdirs() }
    }

    fun fileNameFor(key: String, ext: String): String =
        key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + "." + ext.ifEmpty { "mp4" }

    fun find(key: String): DownloadItem? = items.firstOrNull { it.key == key }

    /** Archivo listo para reproducir sin conexión, o null si no está descargado. */
    fun localFile(key: String): File? {
        val d = find(key) ?: return null
        if (d.status != DlStatus.DONE) return null
        val f = File(dir(), d.file)
        return if (f.exists() && f.length() > 0) f else null
    }

    private fun persist() {
        storage?.saveDownloads(items.toList())
    }

    private fun update(key: String, change: (DownloadItem) -> DownloadItem) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) items[i] = change(items[i])
    }

    fun enqueue(item: DownloadItem) {
        val existing = find(item.key)
        if (existing != null) {
            if (existing.status == DlStatus.DONE || existing.status == DlStatus.QUEUED ||
                existing.status == DlStatus.DOWNLOADING
            ) return
            update(item.key) { it.copy(status = DlStatus.QUEUED, error = null) }
        } else {
            items.add(item.copy(status = DlStatus.QUEUED))
        }
        persist()
        startWorker()
    }

    fun pause(key: String) {
        val d = find(key) ?: return
        if (d.status != DlStatus.QUEUED && d.status != DlStatus.DOWNLOADING) return
        update(key) { it.copy(status = DlStatus.PAUSED) }
        persist()
        if (currentKey == key) abortCurrent()
    }

    fun resume(key: String) {
        val d = find(key) ?: return
        if (d.status != DlStatus.PAUSED && d.status != DlStatus.ERROR) return
        update(key) { it.copy(status = DlStatus.QUEUED, error = null) }
        persist()
        startWorker()
    }

    fun remove(key: String) {
        val d = find(key) ?: return
        items.removeAll { it.key == key }
        persist()
        if (currentKey == key) abortCurrent()
        try {
            File(dir(), d.file).delete()
        } catch (e: Exception) {
        }
    }

    private fun abortCurrent() {
        currentCall?.cancel()
        currentJob?.cancel()
    }

    private fun startWorker() {
        if (running) return
        val ctx = appCtx ?: return
        running = true
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        } catch (e: Exception) {
            // Sin servicio la descarga igual corre mientras la app esté abierta.
        }
        scope.launch {
            try {
                while (true) {
                    val next = items.firstOrNull { it.status == DlStatus.QUEUED } ?: break
                    runOne(next)
                }
            } finally {
                running = false
            }
        }
    }

    private suspend fun runOne(item: DownloadItem) {
        update(item.key) { it.copy(status = DlStatus.DOWNLOADING, error = null) }
        persist()
        val file = File(dir(), item.file)
        val job = scope.async(Dispatchers.IO) { transfer(item, file) }
        currentKey = item.key
        currentJob = job
        val outcome: Result<String?> = runCatching { job.await() }
        currentKey = null
        currentJob = null
        currentCall = null
        val now = find(item.key) ?: return // se borró mientras bajaba
        if (now.status != DlStatus.DOWNLOADING) return // se pausó
        val error = outcome.getOrElse { "Descarga interrumpida" }
        if (error == null) {
            update(item.key) { it.copy(status = DlStatus.DONE, bytes = file.length(), total = file.length(), error = null) }
        } else {
            update(item.key) { it.copy(status = DlStatus.ERROR, error = error) }
        }
        persist()
    }

    private fun progress(key: String, bytes: Long, total: Long) {
        main.post {
            val d = find(key)
            if (d != null && d.status == DlStatus.DOWNLOADING) {
                update(key) { it.copy(bytes = bytes, total = total) }
            }
        }
    }

    /** Baja el archivo (retomando si ya hay una parte). Devuelve null si salió bien, o el mensaje de error. */
    private fun transfer(item: DownloadItem, file: File): String? {
        return try {
            var have = if (file.exists()) file.length() else 0L
            val req = Request.Builder()
                .url(item.url)
                .header("User-Agent", Http.UA)
                .apply { if (have > 0) header("Range", "bytes=$have-") }
                .build()
            val call = Http.client.newCall(req)
            currentCall = call
            call.execute().use { resp ->
                if (resp.code == 416 && have > 0) {
                    val total = Regex("^bytes \\*/(\\d+)$")
                        .matchEntire(resp.header("Content-Range").orEmpty())
                        ?.groupValues?.get(1)?.toLongOrNull()
                    if (total == have) return null
                    file.delete()
                    return "El servidor rechazó la reanudación; se descartó el fragmento. Reanuda la descarga."
                }
                if (!resp.isSuccessful) return "El servidor respondió ${resp.code}"

                val range = if (resp.code == 206) {
                    val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$")
                        .matchEntire(resp.header("Content-Range").orEmpty())
                        ?: return "El servidor envió un rango inválido"
                    val start = match.groupValues[1].toLongOrNull()
                        ?: return "El servidor envió un rango inválido"
                    val end = match.groupValues[2].toLongOrNull()
                        ?: return "El servidor envió un rango inválido"
                    val total = match.groupValues[3].toLongOrNull()
                    if (start != have || end < start || (total != null && end >= total)) {
                        return "El rango de descarga no coincide"
                    }
                    Triple(start, end, total)
                } else {
                    null
                }

                val type = resp.header("Content-Type") ?: ""
                if (type.startsWith("text/") || type.contains("json")) {
                    return "El servidor no entregó el video (¿límite de conexiones de tu lista?)"
                }
                val body = resp.body ?: return "Respuesta vacía del servidor"
                val append = resp.code == 206 && have > 0
                if (!append) have = 0L
                val len = body.contentLength()
                if (range != null && len > 0 && len != range.second - range.first + 1) {
                    return "La respuesta del servidor llegó incompleta"
                }
                val total = range?.third ?: if (len > 0) have + len else 0L
                val requiredBytes = if (total > have) total - have else len
                val usableSpace = file.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (requiredBytes > 0 && usableSpace < requiredBytes + MIN_FREE_SPACE_BYTES) {
                    return "No hay espacio suficiente. Libera espacio y reanuda."
                }

                var written = have
                var lastReport = 0L
                FileOutputStream(file, append).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            val available = file.parentFile?.usableSpace ?: Long.MAX_VALUE
                            if (available < n.toLong() + MIN_FREE_SPACE_BYTES) {
                                throw IOException("No hay espacio suficiente. Libera espacio y reanuda.")
                            }
                            out.write(buf, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 500) {
                                lastReport = now
                                progress(item.key, written, total)
                            }
                        }
                    }
                }
                if (total > 0 && written != total) return "La descarga se cortó. Reanúdala."
                if (written == 0L) return "El archivo llegó vacío"
            }
            null
        } catch (e: IOException) {
            if (e.message?.contains("No hay espacio suficiente") == true) {
                "No hay espacio suficiente. Libera espacio y reanuda."
            } else {
                "Se cortó la conexión. Comprueba la red y reanuda la descarga."
            }
        }
    }

    private const val MIN_FREE_SPACE_BYTES = 10L * 1024 * 1024

}
