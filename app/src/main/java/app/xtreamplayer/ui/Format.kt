package app.xtreamplayer.ui

import java.util.Locale

/** "1:05" o "1:02:03" a partir de milisegundos. Los negativos se muestran como "0:00". */
internal fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0L)
    val h = t / 3600
    val m = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}

/** Etiqueta corta de calidad a partir de la altura del vídeo. */
internal fun resolutionLabel(height: Int): String = when {
    height >= 2000 -> "4K"
    height >= 1400 -> "2K"
    height >= 1000 -> "1080p"
    height >= 700 -> "720p"
    height >= 500 -> "576p"
    else -> "${height}p"
}

/** Tamaño en megabytes enteros (para descargas). */
internal fun formatMb(bytes: Long): String = "${bytes / (1024 * 1024)} MB"
