package app.xtreamplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas del guardado en disco del catálogo: los campos deben volver igual al leerlos. */
class CatalogCodecTest {

    /** Fallback de nombre sin dependencia de Android (el de la app usa recursos i18n). */
    private val fallback = { id: Int -> "N$id" }

    @Test
    fun `canal de ida y vuelta`() {
        val canal = Channel(id = 12, name = "Canal 12", icon = "http://img/12.png", categoryId = "deportes")
        val fields = CatalogCodec.channelFields(canal).filter { it.second != null }.associate { it.first to it.second!! }
        assertEquals(canal, CatalogCodec.channelFrom(fields, fallback))
    }

    @Test
    fun `pelicula de ida y vuelta`() {
        val pelicula = Movie(
            id = 7,
            name = "Película",
            icon = null,
            categoryId = "cine",
            ext = "mkv",
            rating = "7.5",
            plot = "Sinopsis",
            added = 1_700_000_000L,
        )
        val fields = CatalogCodec.movieFields(pelicula).filter { it.second != null }.associate { it.first to it.second!! }
        assertEquals(pelicula, CatalogCodec.movieFrom(fields, fallback))
    }

    @Test
    fun `serie de ida y vuelta`() {
        val serie = Series(
            id = 3,
            name = "Serie",
            cover = "http://img/3.jpg",
            categoryId = "series",
            plot = "Sinopsis",
            rating = "8",
            added = 1_700_000_000L,
        )
        val fields = CatalogCodec.seriesFields(serie).filter { it.second != null }.associate { it.first to it.second!! }
        assertEquals(serie, CatalogCodec.seriesFrom(fields, fallback))
    }

    @Test
    fun `sin id no se reconstruye el elemento`() {
        assertNull(CatalogCodec.channelFrom(emptyMap(), fallback))
        assertNull(CatalogCodec.movieFrom(mapOf("name" to "x"), fallback))
        assertNull(CatalogCodec.seriesFrom(mapOf("id" to "no-numero"), fallback))
    }

    @Test
    fun `los campos opcionales usan valores por defecto`() {
        // Sin nombre en el caché, se inventa uno con el fallback inyectado.
        val canal = CatalogCodec.channelFrom(mapOf("id" to "5"), fallback)!!
        assertEquals("N5", canal.name)
        assertEquals("", canal.categoryId)
        assertNull(canal.icon)

        val pelicula = CatalogCodec.movieFrom(mapOf("id" to "6"), fallback)!!
        assertEquals("mp4", pelicula.ext)
        assertEquals(0L, pelicula.added)
    }
}
