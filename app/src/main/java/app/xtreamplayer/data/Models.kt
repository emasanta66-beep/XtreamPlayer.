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
)
