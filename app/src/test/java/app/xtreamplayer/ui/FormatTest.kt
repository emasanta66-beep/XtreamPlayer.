package app.xtreamplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas de los textos que se muestran en el reproductor y en las descargas. */
class FormatTest {

    @Test
    fun `tiempo en minutos y segundos`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:00", formatTime(-5_000))
        assertEquals("0:59", formatTime(59_999))
        assertEquals("1:01", formatTime(61_000))
        assertEquals("59:59", formatTime(3_599_000))
    }

    @Test
    fun `tiempo con horas`() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1:01:01", formatTime(3_661_000))
        assertEquals("10:00:00", formatTime(36_000_000))
    }

    @Test
    fun `etiqueta de resolucion`() {
        assertEquals("4K", resolutionLabel(2160))
        assertEquals("2K", resolutionLabel(1440))
        assertEquals("1080p", resolutionLabel(1080))
        assertEquals("720p", resolutionLabel(720))
        assertEquals("576p", resolutionLabel(576))
        assertEquals("480p", resolutionLabel(480))
        assertEquals("0p", resolutionLabel(0))
    }

    @Test
    fun `tamano en megabytes enteros`() {
        assertEquals("0 MB", formatMb(0))
        assertEquals("1 MB", formatMb(1024L * 1024L))
        assertEquals("1 MB", formatMb(1024L * 1024L + 500_000L))
        assertEquals("512 MB", formatMb(512L * 1024L * 1024L))
    }
}
