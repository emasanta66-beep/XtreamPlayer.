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

    fun getPosition(key: String): Long = prefs.getLong("pos_$key", 0L)

    fun setPosition(key: String, ms: Long) {
        val editor = prefs.edit()
        if (ms > 0) editor.putLong("pos_$key", ms) else editor.remove("pos_$key")
        editor.apply()
    }

    var liveFormat: String
        get() = prefs.getString("live_format", "m3u8") ?: "m3u8"
        set(value) {
            prefs.edit().putString("live_format", value).apply()
        }
}
