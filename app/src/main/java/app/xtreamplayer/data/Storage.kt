package app.xtreamplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistencia local sencilla (SharedPreferences + JSON). */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("xtream_player", Context.MODE_PRIVATE)

    fun loadAccounts(): List<Account> = try {
        val arr = JSONArray(prefs.getString("accounts", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Account(
                id = o.getString("id"),
                name = o.getString("name"),
                server = o.getString("server"),
                username = o.getString("username"),
                password = o.getString("password"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAccounts(list: List<Account>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("server", a.server)
                    .put("username", a.username)
                    .put("password", a.password)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun loadFavs(accountId: String): List<Fav> = try {
        val arr = JSONArray(prefs.getString("favs_$accountId", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Fav(
                type = o.getString("type"),
                id = o.getInt("id"),
                name = o.getString("name"),
                icon = if (o.isNull("icon")) null else o.getString("icon"),
                ext = o.optString("ext", ""),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveFavs(accountId: String, list: List<Fav>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("type", f.type)
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("icon", f.icon ?: JSONObject.NULL)
                    .put("ext", f.ext)
            )
        }
        prefs.edit().putString("favs_$accountId", arr.toString()).apply()
    }

    fun loadRecents(accountId: String): List<Recent> = try {
        val arr = JSONArray(prefs.getString("recent_$accountId", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Recent(
                key = o.getString("key"),
                kind = o.getString("kind"),
                contentId = o.getString("contentId"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle", ""),
                image = if (o.isNull("image")) null else o.getString("image"),
                ext = o.optString("ext", ""),
                seriesId = o.optInt("seriesId", 0),
                positionMs = o.optLong("positionMs", 0L),
                durationMs = o.optLong("durationMs", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveRecents(accountId: String, list: List<Recent>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("key", r.key)
                    .put("kind", r.kind)
                    .put("contentId", r.contentId)
                    .put("title", r.title)
                    .put("subtitle", r.subtitle)
                    .put("image", r.image ?: JSONObject.NULL)
                    .put("ext", r.ext)
                    .put("seriesId", r.seriesId)
                    .put("positionMs", r.positionMs)
                    .put("durationMs", r.durationMs)
                    .put("updatedAt", r.updatedAt)
            )
        }
        prefs.edit().putString("recent_$accountId", arr.toString()).apply()
    }

    /** Orden elegido para las categorías de una sección ("live"), como lista de ids de categoría. */
    fun loadCatOrder(accountId: String, section: String): List<String> = try {
        val arr = JSONArray(prefs.getString("catorder_${section}_$accountId", "[]"))
        List(arr.length()) { i -> arr.getString(i) }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveCatOrder(accountId: String, section: String, ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString("catorder_${section}_$accountId", arr.toString()).apply()
    }

    /** Descargas (películas y capítulos) de todas las listas. */
    fun loadDownloads(): List<DownloadItem> = try {
        val arr = JSONArray(prefs.getString("downloads", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            DownloadItem(
                key = o.getString("key"),
                accountId = o.getString("accountId"),
                kind = o.getString("kind"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle", ""),
                image = o.optString("image", "").ifEmpty { null },
                url = o.getString("url"),
                ext = o.optString("ext", "mp4"),
                contentId = o.getString("contentId"),
                seriesId = o.optInt("seriesId", 0),
                file = o.getString("file"),
                status = try {
                    DlStatus.valueOf(o.getString("status"))
                } catch (e: Exception) {
                    DlStatus.PAUSED
                },
                bytes = o.optLong("bytes", 0L),
                total = o.optLong("total", 0L),
                error = o.optString("error", "").ifEmpty { null },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveDownloads(list: List<DownloadItem>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject()
                    .put("key", d.key)
                    .put("accountId", d.accountId)
                    .put("kind", d.kind)
                    .put("title", d.title)
                    .put("subtitle", d.subtitle)
                    .put("image", d.image ?: "")
                    .put("url", d.url)
                    .put("ext", d.ext)
                    .put("contentId", d.contentId)
                    .put("seriesId", d.seriesId)
                    .put("file", d.file)
                    .put("status", d.status.name)
                    .put("bytes", d.bytes)
                    .put("total", d.total)
                    .put("error", d.error ?: "")
            )
        }
        prefs.edit().putString("downloads", arr.toString()).apply()
    }

    fun getPosition(key: String): Long = prefs.getLong("pos_$key", 0L)

    fun setPosition(key: String, ms: Long) {
        val editor = prefs.edit()
        if (ms > 0) editor.putLong("pos_$key", ms) else editor.remove("pos_$key")
        editor.apply()
    }

    /** Si ya se pidió el permiso de notificaciones (solo se pide una vez). */
    var notifAsked: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(value) {
            prefs.edit().putBoolean("notif_asked", value).apply()
        }

    /** Tamaño de imagen elegido en el reproductor (posición en la lista de modos). */
    var videoMode: Int
        get() = prefs.getInt("video_mode", 0)
        set(value) {
            prefs.edit().putInt("video_mode", value).apply()
        }

    var liveFormat: String
        get() = prefs.getString("live_format", "m3u8") ?: "m3u8"
        set(value) {
            prefs.edit().putString("live_format", value).apply()
        }

    /** Última lista abierta, para entrar directamente en ella al arrancar la app. */
    var lastAccountId: String?
        get() = prefs.getString("last_account", null)
        set(value) {
            val editor = prefs.edit()
            if (value != null) editor.putString("last_account", value) else editor.remove("last_account")
            editor.apply()
        }
}
