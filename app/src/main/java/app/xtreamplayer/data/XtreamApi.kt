package app.xtreamplayer.data

import android.content.Context
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import app.xtreamplayer.R
import java.io.EOFException
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Http {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

/** Cliente de la API "player_api.php" de Xtream Codes. Todas las llamadas son bloqueantes: usar en IO. */
class XtreamApi(private val acc: Account, private val context: Context) {
    val base: String = normalize(acc.server)

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    // ---------- Peticiones ----------

    private fun url(action: String? = null, vararg extra: Pair<String, String>): HttpUrl {
        val b = "$base/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", acc.username)
            .addQueryParameter("password", acc.password)
        if (action != null) b.addQueryParameter("action", action)
        extra.forEach { b.addQueryParameter(it.first, it.second) }
        return b.build()
    }

    private fun get(u: HttpUrl): String {
        val req = Request.Builder().url(u).header("User-Agent", Http.UA).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException(s(R.string.error_http, r.code))
            return r.body?.string() ?: throw IOException(s(R.string.error_empty_response))
        }
    }

    // ---------- API ----------

    fun authenticate(): AuthInfo {
        val o = JSONObject(get(url()))
        val ui = o.optJSONObject("user_info") ?: throw IOException(s(R.string.error_invalid_response))
        if (ui.opt("auth")?.toString() != "1") throw IOException(s(R.string.error_bad_credentials))
        val exp = ui.str("exp_date")?.toLongOrNull()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it * 1000))
        }
        return AuthInfo(
            status = ui.str("status") ?: "",
            expires = exp,
            maxConnections = ui.str("max_connections"),
        )
    }

    fun liveCategories(): List<Category> = categories("get_live_categories")
    fun vodCategories(): List<Category> = categories("get_vod_categories")
    fun seriesCategories(): List<Category> = categories("get_series_categories")

    private fun categories(action: String): List<Category> {
        val arr = parseArray(get(url(action)))
        val out = ArrayList<Category>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("category_id") ?: continue
            out.add(Category(id, o.str("category_name") ?: id))
        }
        return out
    }

    // Las listas completas pueden tener decenas de miles de elementos (y varios MB de JSON). Se leen
    // en streaming: nunca se carga todo el texto en memoria y solo se guardan los campos que la app usa.

    fun liveStreams(): List<Channel> =
        streamList("get_live_streams", setOf("stream_id", "name", "stream_icon", "category_id")) { f ->
            val id = f.intOf("stream_id") ?: return@streamList null
            Channel(
                id = id,
                name = f["name"] ?: s(R.string.fallback_channel, id),
                icon = f["stream_icon"],
                categoryId = f["category_id"] ?: "",
            )
        }

    fun vodStreams(): List<Movie> =
        streamList(
            "get_vod_streams",
            setOf("stream_id", "name", "stream_icon", "category_id", "container_extension", "rating", "plot", "added"),
        ) { f ->
            val id = f.intOf("stream_id") ?: return@streamList null
            Movie(
                id = id,
                name = f["name"] ?: s(R.string.fallback_movie, id),
                icon = f["stream_icon"],
                categoryId = f["category_id"] ?: "",
                ext = f["container_extension"] ?: "mp4",
                rating = f["rating"],
                plot = f["plot"]?.take(PLOT_PREVIEW_CHARS),
                added = f["added"]?.toLongOrNull() ?: 0L,
            )
        }

    fun vodInfo(id: Int): MovieInfo {
        val info = JSONObject(get(url("get_vod_info", "vod_id" to id.toString()))).optJSONObject("info")
        return MovieInfo(
            plot = info?.str("plot") ?: info?.str("description"),
            genre = info?.str("genre"),
            duration = info?.str("duration"),
        )
    }

    fun seriesList(): List<Series> =
        streamList("get_series", setOf("series_id", "name", "cover", "category_id", "plot", "rating", "last_modified")) { f ->
            val id = f.intOf("series_id") ?: return@streamList null
            Series(
                id = id,
                name = f["name"] ?: s(R.string.fallback_series, id),
                cover = f["cover"],
                categoryId = f["category_id"] ?: "",
                // La sinopsis completa se pide al abrir la serie; aquí basta un resumen (ahorra mucha memoria).
                plot = f["plot"]?.take(PLOT_PREVIEW_CHARS),
                rating = f["rating"],
                added = f["last_modified"]?.toLongOrNull() ?: 0L,
            )
        }

    /**
     * Descarga `action` y lo lee como un array JSON de objetos, uno a uno. De cada objeto solo se conservan
     * los campos de [keys]; el resto se salta sin crear textos. [build] devuelve null para descartar un elemento.
     */
    private fun <T> streamList(
        action: String,
        keys: Set<String>,
        build: (Map<String, String>) -> T?,
    ): List<T> {
        val req = Request.Builder().url(url(action)).header("User-Agent", Http.UA).build()
        return Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException(s(R.string.error_http, r.code))
            val body = r.body ?: throw IOException(s(R.string.error_empty_response))
            JsonReader(body.charStream().buffered(64 * 1024)).use { reader ->
                reader.isLenient = true
                // Si el servidor no devuelve un array (vacío, error, HTML...), la lista queda vacía.
                val first = try {
                    reader.peek()
                } catch (e: EOFException) {
                    return emptyList()
                }
                if (first != JsonToken.BEGIN_ARRAY) return emptyList()

                // Los ids de categoría y las extensiones se repiten miles de veces: se comparte un solo String.
                val shared = HashMap<String, String>()
                val out = ArrayList<T>()
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                        reader.skipValue()
                        continue
                    }
                    val fields = HashMap<String, String>(8)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (name !in keys) {
                            reader.skipValue()
                            continue
                        }
                        val value = when (reader.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                            JsonToken.NULL -> {
                                reader.nextNull()
                                null
                            }
                            else -> {
                                reader.skipValue()
                                null
                            }
                        }
                        if (value.isNullOrBlank()) continue
                        fields[name] = if (name in SHARED_KEYS) shared.getOrPut(value) { value } else value
                    }
                    reader.endObject()
                    build(fields)?.let { out.add(it) }
                }
                reader.endArray()
                out.trimToSize()
                out
            }
        }
    }

    /** Entero de un campo (acepta "123" o "123.0"); null si falta o no es numérico. */
    private fun Map<String, String>.intOf(key: String): Int? {
        val v = this[key] ?: return null
        val n = v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt() ?: return null
        return if (n >= 0) n else null
    }

    fun seriesInfo(seriesId: Int): SeriesDetail {
        val o = JSONObject(get(url("get_series_info", "series_id" to seriesId.toString())))
        val plot = o.optJSONObject("info")?.str("plot")
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun addEpisode(e: JSONObject) {
            val id = e.str("id") ?: return
            val season = e.optInt("season", 1)
            val number = e.optInt("episode_num", 0)
            val info = e.optJSONObject("info")
            seasons.getOrPut(season) { mutableListOf() }.add(
                Episode(
                    id = id,
                    title = e.str("title") ?: s(R.string.fallback_episode, number),
                    season = season,
                    number = number,
                    ext = e.str("container_extension") ?: "mp4",
                    plot = info?.str("plot"),
                    image = info?.str("movie_image") ?: info?.str("cover_big"),
                    duration = info?.str("duration"),
                )
            )
        }

        // "episodes" puede venir como objeto {"1":[...],"2":[...]} o como array (de arrays).
        fun collect(v: Any?) {
            when (v) {
                is JSONArray -> for (i in 0 until v.length()) collect(v.opt(i))
                is JSONObject -> addEpisode(v)
            }
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key -> collect(eps.opt(key)) }
            is JSONArray -> collect(eps)
        }

        return SeriesDetail(
            plot = plot,
            seasons = seasons.mapValues { (_, list) -> list.sortedBy { it.number } },
        )
    }

    fun shortEpg(streamId: Int): List<EpgItem> {
        val o = JSONObject(get(url("get_short_epg", "stream_id" to streamId.toString(), "limit" to "4")))
        val arr = o.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgItem>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            out.add(
                EpgItem(
                    title = decode(e.str("title")),
                    start = e.optLong("start_timestamp") * 1000,
                    end = e.optLong("stop_timestamp") * 1000,
                    description = decode(e.str("description")),
                )
            )
        }
        return out
    }

    // ---------- URLs de reproducción ----------

    fun liveUrl(id: Int, format: String) = "$base/live/${enc(acc.username)}/${enc(acc.password)}/$id.$format"
    fun movieUrl(id: Int, ext: String) = "$base/movie/${enc(acc.username)}/${enc(acc.password)}/$id.$ext"
    fun episodeUrl(id: String, ext: String) = "$base/series/${enc(acc.username)}/${enc(acc.password)}/$id.$ext"

    // ---------- Utilidades ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun decode(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            s
        }
    }

    private fun parseArray(raw: String): JSONArray {
        val t = raw.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    companion object {
        private const val PLOT_PREVIEW_CHARS = 400
        private val SHARED_KEYS = setOf("category_id", "container_extension")

        /** Deja solo "http(s)://host:puerto[/ruta]" sin barra final ni get.php/player_api.php. */
        fun normalize(raw: String): String {
            var s = raw.trim()
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("?")
            return s.trimEnd('/')
        }

        /**
         * Si el usuario pega una URL tipo "http://host:8080/get.php?username=U&password=P&type=m3u_plus",
         * devuelve Triple(servidor, usuario, contraseña). Si no, null.
         */
        fun parseListUrl(raw: String): Triple<String, String, String>? {
            val t = raw.trim()
            val withScheme = if (t.startsWith("http", ignoreCase = true)) t else "http://$t"
            val u = withScheme.toHttpUrlOrNull() ?: return null
            val user = u.queryParameter("username") ?: return null
            val pass = u.queryParameter("password") ?: return null
            val port = if (u.port != HttpUrl.defaultPort(u.scheme)) ":${u.port}" else ""
            return Triple("${u.scheme}://${u.host}$port", user, pass)
        }
    }
}

/** String no vacío o null (cubre claves ausentes y JSON null, que org.json convierte en "null"). */
private fun JSONObject.str(key: String): String? {
    if (isNull(key)) return null
    return optString(key, "").ifBlank { null }
}
