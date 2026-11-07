package app.xtreamplayer.data

data class Account(
    val id: String,
    val name: String,
    val server: String,
    val username: String,
    val password: String,
)

data class Category(val id: String, val name: String)

data class Channel(
    val id: Int,
    val name: String,
    val icon: String?,
    val categoryId: String,
)

data class Movie(
    val id: Int,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val ext: String,
    val rating: String?,
)

data class Series(
    val id: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
    val rating: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val plot: String?,
    /** Imagen de vista previa del capítulo (si el servidor la entrega). */
    val image: String? = null,
    val duration: String? = null,
)

data class SeriesDetail(
    val plot: String?,
    val seasons: Map<Int, List<Episode>>,
)

data class EpgItem(
    val title: String,
    val start: Long,
    val end: Long,
    val description: String,
)

data class AuthInfo(
    val status: String,
    val expires: String?,
    val maxConnections: String?,
)

/** type: "live" | "movie" | "series" */
data class Fav(
    val type: String,
    val id: Int,
    val name: String,
    val icon: String?,
    val ext: String,
)

data class PlayItem(
    val title: String,
    val url: String,
    /** URL alternativa (por ejemplo .ts si .m3u8 falla) solo para canales en vivo. */
    val altUrl: String?,
    val isLive: Boolean,
    val streamId: Int?,
    /** Clave para recordar la posición de reproducción (películas y episodios). */
    val resumeKey: String?,
    val startPositionMs: Long,
    /** Datos para listar este contenido en "Continuar viendo" (películas y episodios). */
    val recent: Recent? = null,
    /** Dirección en línea cuando `url` es un archivo descargado (para enviar a la TV). */
    val remoteUrl: String? = null,
)

/** Elemento de "Continuar viendo". kind: "movie" | "episode". */
data class Recent(
    /** Misma clave que usa el reproductor para recordar la posición. */
    val key: String,
    val kind: String,
    /** Id del stream (película) o del episodio. */
    val contentId: String,
    /** Nombre de la película o de la serie. */
    val title: String,
    /** Por ejemplo "T1E3" (vacío en películas). */
    val subtitle: String,
    val image: String?,
    val ext: String,
    /** Id de la serie (0 en películas). */
    val seriesId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
