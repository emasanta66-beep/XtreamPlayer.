package app.xtreamplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de la limpieza de servidores y de la lectura de URLs de lista (lógica pura, sin red). */
class XtreamApiTest {

    @Test
    fun `normalize deja solo esquema host y puerto`() {
        assertEquals("http://host.com:8080", XtreamApi.normalize("http://host.com:8080/"))
        assertEquals("http://host.com:8080", XtreamApi.normalize("host.com:8080"))
        assertEquals("https://host.com", XtreamApi.normalize("https://host.com"))
    }

    @Test
    fun `normalize quita player_api y get php`() {
        assertEquals("http://host.com:8080", XtreamApi.normalize("http://host.com:8080/player_api.php"))
        assertEquals(
            "http://host.com:8080",
            XtreamApi.normalize("http://host.com:8080/get.php?username=a&password=b&type=m3u_plus"),
        )
    }

    @Test
    fun `normalize conserva la ruta del servidor`() {
        assertEquals("http://host.com/iptv", XtreamApi.normalize("http://host.com/iptv/"))
        assertEquals("https://host.com/iptv", XtreamApi.normalize("https://host.com/iptv/player_api.php"))
    }

    @Test
    fun `normalize recorta espacios`() {
        assertEquals("http://host.com:8080", XtreamApi.normalize("  http://host.com:8080/  "))
    }

    @Test
    fun `parseListUrl extrae servidor usuario y contrasena`() {
        val parsed = XtreamApi.parseListUrl(
            "http://host.com:8080/get.php?username=usuario&password=secreto&type=m3u_plus",
        )
        assertEquals(Triple("http://host.com:8080", "usuario", "secreto"), parsed)
    }

    @Test
    fun `parseListUrl omite el puerto por defecto`() {
        assertEquals(
            Triple("http://host.com", "u", "p"),
            XtreamApi.parseListUrl("http://host.com/get.php?username=u&password=p"),
        )
        assertEquals(
            Triple("https://host.com", "u", "p"),
            XtreamApi.parseListUrl("https://host.com/get.php?username=u&password=p"),
        )
    }

    @Test
    fun `parseListUrl acepta una url sin esquema`() {
        assertEquals(
            Triple("http://host.com:8080", "u", "p"),
            XtreamApi.parseListUrl("host.com:8080/get.php?username=u&password=p"),
        )
    }

    @Test
    fun `parseListUrl devuelve null si faltan datos o no es una url`() {
        assertNull(XtreamApi.parseListUrl("http://host.com/get.php?password=p"))
        assertNull(XtreamApi.parseListUrl("http://host.com/get.php?username=u"))
        assertNull(XtreamApi.parseListUrl("esto no es una url"))
    }
}
