package app.xtreamplayer.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class CachedCatalog<T>(val cats: List<Category>, val items: List<T>, val savedAt: Long)

/**
 * Guarda en el celular las listas de canales, películas y series de cada cuenta, para mostrarlas al instante
 * al abrir la app. Se escribe y se lee en streaming (la lista puede tener decenas de miles de títulos).
 */
class CatalogCache(context: Context) {
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }

    private fun file(accountId: String, kind: String) =
        File(dir, accountId.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_" + kind + ".json")

    /** Verdadero si esta cuenta ya se abrió antes y hay algo guardado. */
    fun has(accountId: String): Boolean =
        listOf("live", "movies", "series").any { file(accountId, it).exists() }

    fun clear(accountId: String) {
        listOf("live", "movies", "series").forEach { file(accountId, it).delete() }
    }

    fun <T> save(
        accountId: String,
        kind: String,
        cats: List<Category>,
        items: List<T>,
        toFields: (T) -> List<Pair<String, String?>>,
    ) {
        try {
            val target = file(accountId, kind)
            val tmp = File(dir, target.name + ".tmp")
            JsonWriter(OutputStreamWriter(FileOutputStream(tmp).buffered(), Charsets.UTF_8)).use { w ->
                w.beginObject()
                w.name("cats").beginArray()
                for (c in cats) {
                    w.beginObject()
                    w.name("id").value(c.id)
                    w.name("name").value(c.name)
                    w.endObject()
                }
                w.endArray()
                w.name("items").beginArray()
                for (item in items) {
                    w.beginObject()
                    for ((k, v) in toFields(item)) {
                        if (v != null) w.name(k).value(v)
                    }
                    w.endObject()
                }
                w.endArray()
                w.endObject()
            }
            // Se escribe aparte y se renombra al final: si algo se corta, la copia anterior queda intacta.
            target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            // Guardar es opcional: sin copia, la próxima vez se descarga normal.
        }
    }

    fun <T> load(accountId: String, kind: String, fromFields: (Map<String, String>) -> T?): CachedCatalog<T>? {
        val f = file(accountId, kind)
        if (!f.exists()) return null
        return try {
            JsonReader(InputStreamReader(FileInputStream(f).buffered(), Charsets.UTF_8)).use { r ->
                val cats = ArrayList<Category>()
                val items = ArrayList<T>()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "cats" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                val m = readFields(r)
                                val id = m["id"]
                                if (id != null) cats.add(Category(id, m["name"] ?: id))
                            }
                            r.endArray()
                        }
                        "items" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                val item = fromFields(readFields(r))
                                if (item != null) items.add(item)
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                CachedCatalog(cats, items, f.lastModified())
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun readFields(r: JsonReader): Map<String, String> {
        val m = HashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (r.peek() == JsonToken.NULL) r.skipValue() else m[name] = r.nextString()
        }
        r.endObject()
        return m
    }
}

/** Cómo se guarda y se reconstruye cada tipo de elemento. */
object CatalogCodec {
    fun channelFields(c: Channel): List<Pair<String, String?>> =
        listOf("id" to c.id.toString(), "name" to c.name, "icon" to c.icon, "cat" to c.categoryId)

    fun channelFrom(m: Map<String, String>): Channel? {
        val id = m["id"]?.toIntOrNull() ?: return null
        return Channel(id, m["name"] ?: "Canal $id", m["icon"], m["cat"] ?: "")
    }

    fun movieFields(x: Movie): List<Pair<String, String?>> = listOf(
        "id" to x.id.toString(), "name" to x.name, "icon" to x.icon, "cat" to x.categoryId, "ext" to x.ext,
        "rating" to x.rating, "plot" to x.plot, "added" to x.added.toString(),
    )

    fun movieFrom(m: Map<String, String>): Movie? {
        val id = m["id"]?.toIntOrNull() ?: return null
        return Movie(
            id = id,
            name = m["name"] ?: "Película $id",
            icon = m["icon"],
            categoryId = m["cat"] ?: "",
            ext = m["ext"] ?: "mp4",
            rating = m["rating"],
            plot = m["plot"],
            added = m["added"]?.toLongOrNull() ?: 0L,
        )
    }

    fun seriesFields(x: Series): List<Pair<String, String?>> = listOf(
        "id" to x.id.toString(), "name" to x.name, "cover" to x.cover, "cat" to x.categoryId,
        "plot" to x.plot, "rating" to x.rating, "added" to x.added.toString(),
    )

    fun seriesFrom(m: Map<String, String>): Series? {
        val id = m["id"]?.toIntOrNull() ?: return null
        return Series(
            id = id,
            name = m["name"] ?: "Serie $id",
            cover = m["cover"],
            categoryId = m["cat"] ?: "",
            plot = m["plot"],
            rating = m["rating"],
            added = m["added"]?.toLongOrNull() ?: 0L,
        )
    }
}
