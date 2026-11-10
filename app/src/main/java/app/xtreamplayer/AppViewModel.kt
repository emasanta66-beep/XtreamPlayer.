package app.xtreamplayer

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xtreamplayer.data.Account
import app.xtreamplayer.data.AuthInfo
import app.xtreamplayer.data.CatalogCache
import app.xtreamplayer.data.CatalogCodec
import app.xtreamplayer.data.Category
import app.xtreamplayer.data.Channel
import app.xtreamplayer.data.DlStatus
import app.xtreamplayer.data.DownloadCenter
import app.xtreamplayer.data.DownloadItem
import app.xtreamplayer.data.Episode
import app.xtreamplayer.data.MovieInfo
import app.xtreamplayer.data.EpgItem
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.PlayItem
import app.xtreamplayer.data.Recent
import app.xtreamplayer.data.Series
import app.xtreamplayer.data.SeriesDetail
import app.xtreamplayer.data.Storage
import app.xtreamplayer.data.UpdateInfo
import app.xtreamplayer.data.Updater
import app.xtreamplayer.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

sealed interface Screen {
    val key: String

    data object Accounts : Screen {
        override val key: String = "accounts"
    }

    data class Browse(val accountId: String) : Screen {
        override val key: String get() = "browse_$accountId"
    }

    data class MoviePage(val movie: Movie) : Screen {
        override val key: String get() = "movie_${movie.id}"
    }

    data class SeriesPage(val series: Series) : Screen {
        override val key: String get() = "series_${series.id}"
    }

    data class Player(val item: PlayItem) : Screen {
        // Cada contenido tiene su propia clave: al pasar al siguiente capítulo el reproductor se recrea limpio.
        override val key: String get() = "player_${item.resumeKey ?: item.url}"
    }
}

/** Estado de una pestaña de contenido (en vivo, películas o series). */
class Section<T> {
    var cats by mutableStateOf<List<Category>>(emptyList())
    var items by mutableStateOf<List<T>>(emptyList())
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedCat by mutableStateOf<String?>(null)

    /** Verdadero cuando ya se eligió categoría (o "Todos") a mano: no se vuelve a forzar la primera. */
    var catChosen = false
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val catalog = CatalogCache(app)
    private var api: XtreamApi? = null

    val accounts = mutableStateListOf<Account>().apply { addAll(storage.loadAccounts()) }
    val stack = mutableStateListOf<Screen>(Screen.Accounts)
    val favs = mutableStateListOf<Fav>()
    /** "Continuar viendo": lo más reciente primero. */
    val recents = mutableStateListOf<Recent>()

    /** Películas y capítulos que ya se vieron completos (para marcarlos con ✓). */
    val watched = mutableStateListOf<String>()

    /** Avance (0..1) de una película empezada, o null. */
    fun movieProgress(id: Int): Float? {
        val key = currentAccount?.let { "${it.id}:movie:$id" } ?: return null
        val r = recents.firstOrNull { it.key == key } ?: return null
        return if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else null
    }

    fun isMovieWatched(id: Int): Boolean = currentAccount?.let { "${it.id}:movie:$id" in watched } ?: false

    /** Idioma de subtítulos que se activa solo al abrir un video (null = el predeterminado). */
    var subtitleLang: String?
        get() = storage.subtitleLang
        set(value) {
            storage.subtitleLang = value
        }

    var busy by mutableStateOf(false)
        private set
    var currentAccount by mutableStateOf<Account?>(null)
        private set
    var authInfo by mutableStateOf<AuthInfo?>(null)
        private set
    var live by mutableStateOf(Section<Channel>())
        private set
    var movies by mutableStateOf(Section<Movie>())
        private set
    var series by mutableStateOf(Section<Series>())
        private set
    var liveFormat by mutableStateOf(storage.liveFormat)
        private set
    /** Petición pendiente de abrir el diálogo "Añadir lista" al entrar en gestión de cuentas. */
    var pendingAddAccount by mutableStateOf(false)
        private set

    /** Descargas (películas y capítulos), compartidas por todas las listas. */
    val downloads get() = DownloadCenter.items

    /** Descargas indexadas por clave, para no recorrer la cola en cada tarjeta. */
    private val downloadIndex by derivedStateOf { downloads.associateBy { it.key } }

    fun downloadFor(key: String?): DownloadItem? = key?.let { downloadIndex[it] }

    /** La interfaz debe pedir el permiso de notificaciones (Android 13+). */
    var askNotifPermission by mutableStateOf(false)
        private set

    init {
        DownloadCenter.init(app)
    }

    // ---------- Navegación ----------

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // ---------- Cuentas ----------

    fun saveAccount(
        existingId: String?,
        name: String,
        server: String,
        username: String,
        password: String,
        onDone: () -> Unit,
    ) {
        if (busy) return
        var srv = server.trim()
        var user = username.trim()
        var pass = password.trim()
        XtreamApi.parseListUrl(srv)?.let { (s, u, p) ->
            srv = s
            if (user.isEmpty()) user = u
            if (pass.isEmpty()) pass = p
        }
        if (srv.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            toast("Completa servidor, usuario y contraseña")
            return
        }
        val normalized = XtreamApi.normalize(srv)
        val acc = Account(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { hostOf(normalized) },
            server = normalized,
            username = user,
            password = pass,
        )
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { XtreamApi(acc).authenticate() }
                val idx = accounts.indexOfFirst { it.id == acc.id }
                // Si cambió el servidor o el usuario, lo guardado ya no corresponde a esta lista.
                if (idx >= 0 && (accounts[idx].server != acc.server || accounts[idx].username != acc.username)) {
                    catalog.clear(acc.id)
                }
                if (idx >= 0) accounts[idx] = acc else accounts.add(acc)
                storage.saveAccounts(accounts.toList())
                onDone()
            } catch (e: Exception) {
                toast("No se pudo conectar: ${errorText(e)}")
            }
            busy = false
        }
    }

    fun deleteAccount(acc: Account) {
        accounts.removeAll { it.id == acc.id }
        catalog.clear(acc.id)
        storage.saveAccounts(accounts.toList())
        if (storage.lastAccountId == acc.id) storage.lastAccountId = accounts.firstOrNull()?.id
    }

    /** Abre la pantalla de listas; con [add] abre directamente el diálogo para añadir una nueva. */
    fun manageAccounts(add: Boolean = false) {
        pendingAddAccount = add
        if (stack.lastOrNull() !is Screen.Accounts) stack.add(Screen.Accounts)
    }

    fun consumeAddAccountRequest() {
        pendingAddAccount = false
    }

    fun openAccount(acc: Account, fromLaunch: Boolean = false) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val a = XtreamApi(acc)
                if (catalog.has(acc.id)) {
                    // Ya se abrió antes: entra al instante con lo guardado y verifica la cuenta por detrás.
                    enterAccount(acc, a, null, fromLaunch)
                    launch {
                        try {
                            val info = withContext(Dispatchers.IO) { a.authenticate() }
                            if (currentAccount?.id == acc.id) authInfo = info
                        } catch (e: Exception) {
                            toast("No se pudo verificar la lista: ${errorText(e)}")
                        }
                    }
                } else {
                    val info = withContext(Dispatchers.IO) { a.authenticate() }
                    enterAccount(acc, a, info, fromLaunch)
                }
            } catch (e: Exception) {
                toast("No se pudo abrir la lista: ${errorText(e)}")
            }
            busy = false
        }
    }

    private fun enterAccount(acc: Account, a: XtreamApi, info: AuthInfo?, fromLaunch: Boolean) {
        api = a
        currentAccount = acc
        authInfo = info
        storage.lastAccountId = acc.id
        live = Section()
        movies = Section()
        series = Section()
        seriesCache.clear()
        favs.clear()
        favs.addAll(storage.loadFavs(acc.id))
        recents.clear()
        recents.addAll(storage.loadRecents(acc.id))
        watched.clear()
        watched.addAll(storage.loadWatched(acc.id))
        when {
            // Arranque automático: la app queda directamente en la lista, sin cuentas detrás.
            fromLaunch -> {
                stack.clear()
                stack.add(Screen.Browse(acc.id))
            }
            // Primera lista añadida desde la pantalla de cuentas.
            stack.size == 1 -> stack.add(Screen.Browse(acc.id))
            // Cambio de lista: igual que al arrancar, queda solo la pantalla de contenido.
            else -> {
                stack.clear()
                stack.add(Screen.Browse(acc.id))
            }
        }
    }

    // ---------- Contenido ----------

    fun ensureLive(force: Boolean = false) = load(
        live, force, "live", { it.liveCategories() }, { it.liveStreams() },
        CatalogCodec::channelFields, CatalogCodec::channelFrom, orderKey = LIVE_ORDER,
    )

    /** Mueve la categoría de En vivo en la posición [from] a la posición [to] y guarda el orden. */
    fun moveLiveCategory(from: Int, to: Int) {
        val list = live.cats.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        live.cats = list
        currentAccount?.let { storage.saveCatOrder(it.id, LIVE_ORDER, list.map { c -> c.id }) }
    }

    fun ensureMovies(force: Boolean = false) = load(
        movies, force, "movies", { it.vodCategories() }, { it.vodStreams() },
        CatalogCodec::movieFields, CatalogCodec::movieFrom,
    )

    fun ensureSeries(force: Boolean = false) = load(
        series, force, "series", { it.seriesCategories() }, { it.seriesList() },
        CatalogCodec::seriesFields, CatalogCodec::seriesFrom,
    )

    /** Pone en la sección lo que se va a mostrar y deja elegida la primera categoría si no se eligió otra. */
    private fun <T> applySection(
        section: Section<T>,
        cats: List<Category>,
        items: List<T>,
        accountId: String,
        orderKey: String?,
    ) {
        section.cats = if (orderKey != null) applyCatOrder(cats, storage.loadCatOrder(accountId, orderKey)) else cats
        section.items = items
        // Abre en la primera categoría (no en "Todos") para que cargue rápido.
        if (section.selectedCat == null && !section.catChosen) {
            section.selectedCat = section.cats.firstOrNull()?.id
        }
        section.loaded = true
    }

    /**
     * Carga una sección. Si hay una copia guardada la muestra al instante y, cuando ya tiene varias horas
     * (o se pide [force]), la actualiza por detrás con lo que entrega el proveedor.
     */
    private fun <T> load(
        section: Section<T>,
        force: Boolean,
        kind: String,
        fetchCats: (XtreamApi) -> List<Category>,
        fetchItems: (XtreamApi) -> List<T>,
        toFields: (T) -> List<Pair<String, String?>>,
        fromFields: (Map<String, String>) -> T?,
        orderKey: String? = null,
    ) {
        val a = api ?: return
        if (section.loading) return
        if (section.loaded && !force) return
        val accountId = currentAccount?.id ?: return
        section.loading = true
        section.error = null
        viewModelScope.launch {
            var shown = false
            try {
                if (!force) {
                    val cached = withContext(Dispatchers.IO) { catalog.load(accountId, kind, fromFields) }
                    if (cached != null && cached.items.isNotEmpty()) {
                        applySection(section, cached.cats, cached.items, accountId, orderKey)
                        shown = true
                        section.loading = false
                        if (System.currentTimeMillis() - cached.savedAt < CATALOG_FRESH_MS) return@launch
                    }
                }
                val (cats, items) = withContext(Dispatchers.IO) { fetchCats(a) to fetchItems(a) }
                if (currentAccount?.id != accountId) return@launch
                applySection(section, cats, items, accountId, orderKey)
                withContext(Dispatchers.IO) { catalog.save(accountId, kind, cats, items, toFields) }
            } catch (e: OutOfMemoryError) {
                if (!shown) section.error = "La lista es demasiado grande para la memoria del teléfono"
            } catch (e: Exception) {
                // Si ya se está mostrando la copia guardada, un fallo de red al actualizar no se muestra.
                if (!shown) section.error = errorText(e)
            }
            section.loading = false
        }
    }

    /** Detalle de series ya cargado (temporadas y episodios), para saber cuál es el capítulo siguiente. */
    private val seriesCache = HashMap<Int, SeriesDetail>()

    /** Pone primero las categorías con orden guardado; las que no lo tienen (nuevas) van al final. */
    private fun applyCatOrder(cats: List<Category>, order: List<String>): List<Category> {
        if (order.isEmpty()) return cats
        val rank = order.withIndex().associate { (i, id) -> id to i }
        return cats.sortedBy { rank[it.id] ?: Int.MAX_VALUE } // sortedBy es estable: lo nuevo conserva su orden
    }

    suspend fun loadSeriesInfo(id: Int): Result<SeriesDetail> {
        val a = api ?: return Result.failure(IOException("No hay una lista abierta"))
        val result = withContext(Dispatchers.IO) { runCatching { a.seriesInfo(id) } }
        result.onSuccess { seriesCache[id] = it }
        return result
    }

    suspend fun epgFor(streamId: Int): List<EpgItem> = withContext(Dispatchers.IO) {
        try {
            api?.shortEpg(streamId) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Favoritos ----------

    /** Índice "tipo:id" para consultar favoritos en O(1) desde cada tarjeta de la lista. */
    private val favIndex by derivedStateOf {
        favs.mapTo(HashSet(favs.size)) { "${it.type}:${it.id}" }
    }

    fun isFav(type: String, id: Int): Boolean = "$type:$id" in favIndex

    fun toggleFav(f: Fav) {
        val idx = favs.indexOfFirst { it.type == f.type && it.id == f.id }
        if (idx >= 0) favs.removeAt(idx) else favs.add(f)
        currentAccount?.let { storage.saveFavs(it.id, favs.toList()) }
    }

    // ---------- Reproducción ----------

       @JvmName("applyLiveFormat")
    fun setLiveFormat(format: String) {
        liveFormat = format
        storage.liveFormat = format
    }

    fun playChannel(ch: Channel) {
        stack.add(channelScreen(ch) ?: return)
    }

    private fun channelScreen(ch: Channel): Screen.Player? {
        val a = api ?: return null
        val main = liveFormat
        val other = if (main == "m3u8") "ts" else "m3u8"
        return Screen.Player(
            PlayItem(
                title = ch.name,
                url = a.liveUrl(ch.id, main),
                altUrl = a.liveUrl(ch.id, other),
                isLive = true,
                streamId = ch.id,
                resumeKey = null,
                startPositionMs = 0L,
            )
        )
    }

    /** Cambia de canal sin salir del reproductor (reemplaza el reproductor actual). */
    fun switchChannel(ch: Channel) {
        val screen = channelScreen(ch) ?: return
        if (stack.lastOrNull() is Screen.Player) stack[stack.lastIndex] = screen else stack.add(screen)
    }

    /** Canales de la misma categoría que el canal [streamId], en el orden de la lista. Vacío si aún no cargó En vivo. */
    fun channelsInCategoryOf(streamId: Int?): List<Channel> {
        if (streamId == null) return emptyList()
        val items = live.items
        val current = items.firstOrNull { it.id == streamId } ?: return emptyList()
        return items.filter { it.categoryId == current.categoryId }
    }

    /** Nombre de la categoría de En vivo con ese id (null si no se conoce). */
    fun liveCategoryName(categoryId: String): String? = live.cats.firstOrNull { it.id == categoryId }?.name

    // ---------- Ajustes del reproductor ----------

    /** Tamaño de imagen del reproductor (se recuerda entre canales y entre sesiones). */
    var videoMode by mutableStateOf(storage.videoMode)
        private set

    // Se llama distinto a "setVideoMode" para no chocar con el setter privado de la propiedad (misma firma en JVM).
    fun chooseVideoMode(mode: Int) {
        videoMode = mode
        storage.videoMode = mode
    }

    /** Orientación del reproductor; se conserva al cambiar de canal o de capítulo. */
    var playerLandscape by mutableStateOf(true)

    fun playMovie(id: Int, name: String, ext: String, image: String? = null) {
        val a = api ?: return
        val acc = currentAccount ?: return
        val key = "${acc.id}:movie:$id"
        val extension = ext.ifEmpty { "mp4" }
        val remote = a.movieUrl(id, extension)
        val local = DownloadCenter.localFile(key)
        stack.add(
            Screen.Player(
                PlayItem(
                    title = name,
                    url = local?.let { Uri.fromFile(it).toString() } ?: remote,
                    remoteUrl = if (local != null) remote else null,
                    altUrl = null,
                    isLive = false,
                    streamId = null,
                    resumeKey = key,
                    startPositionMs = storage.getPosition(key),
                    recent = Recent(
                        key = key,
                        kind = "movie",
                        contentId = id.toString(),
                        title = name,
                        subtitle = "",
                        image = image,
                        ext = extension,
                        seriesId = 0,
                        positionMs = 0L,
                        durationMs = 0L,
                        updatedAt = 0L,
                    ),
                )
            )
        )
    }

    // ---------- Descargas ----------
    private fun requestNotifPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            getApplication<Application>(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !storage.notifAsked) {
            storage.notifAsked = true
            askNotifPermission = true
        }
    }

    fun consumeNotifRequest() {
        askNotifPermission = false
    }

    fun downloadMovie(id: Int, name: String, ext: String, image: String?) {
        val a = api ?: return
        val acc = currentAccount ?: return
        val extension = ext.ifEmpty { "mp4" }
        val key = "${acc.id}:movie:$id"
        enqueueDownload(
            DownloadItem(
                key = key, accountId = acc.id, kind = "movie", title = name, subtitle = "",
                image = image, url = a.movieUrl(id, extension), ext = extension,
                contentId = id.toString(), seriesId = 0,
                file = DownloadCenter.fileNameFor(key, extension),
                status = DlStatus.QUEUED, bytes = 0L, total = 0L,
            )
        )
    }

    private fun episodeDownload(series: Series, ep: Episode): DownloadItem? {
        val a = api ?: return null
        val acc = currentAccount ?: return null
        val key = episodeKey(ep) ?: return null
        val ext = ep.ext.ifEmpty { "mp4" }
        return DownloadItem(
            key = key, accountId = acc.id, kind = "episode", title = series.name,
            subtitle = "T${ep.season}E${ep.number}", image = ep.image ?: series.cover,
            url = a.episodeUrl(ep.id, ext), ext = ext, contentId = ep.id, seriesId = series.id,
            file = DownloadCenter.fileNameFor(key, ext),
            status = DlStatus.QUEUED, bytes = 0L, total = 0L,
        )
    }

    fun downloadEpisodes(series: Series, eps: List<Episode>) {
        val list = eps.mapNotNull { episodeDownload(series, it) }
        if (list.isEmpty()) return
        requestNotifPermissionOnce()
        list.forEach { DownloadCenter.enqueue(it) }
        toast(if (list.size == 1) "Descargando…" else "Descargando ${list.size} capítulos")
    }

    private fun enqueueDownload(item: DownloadItem) {
        requestNotifPermissionOnce()
        DownloadCenter.enqueue(item)
        toast("Descargando…")
    }

    /** Toca el botón de descarga de algo que ya está en la cola: pausa o reanuda. */
    fun toggleDownload(key: String) {
        val d = DownloadCenter.find(key) ?: return
        when (d.status) {
            DlStatus.QUEUED, DlStatus.DOWNLOADING -> DownloadCenter.pause(key)
            DlStatus.PAUSED, DlStatus.ERROR -> DownloadCenter.resume(key)
            DlStatus.DONE -> {}
        }
    }

    fun deleteDownload(key: String) = DownloadCenter.remove(key)

    fun playDownload(d: DownloadItem) {
        playRecent(
            Recent(
                key = d.key, kind = d.kind, contentId = d.contentId, title = d.title,
                subtitle = d.subtitle, image = d.image, ext = d.ext, seriesId = d.seriesId,
                positionMs = 0L, durationMs = 0L, updatedAt = 0L,
            )
        )
    }

    /** Abre la ficha de la película; si ya la venías viendo, la reproduce directamente desde donde quedaste. */
    fun openMovie(m: Movie) {
        if (movieProgress(m.id) != null) {
            playMovie(m.id, m.name, m.ext, m.icon)
        } else {
            stack.add(Screen.MoviePage(m))
        }
    }

    suspend fun loadMovieInfo(id: Int): Result<MovieInfo> {
        val a = api ?: return Result.failure(IOException("No hay una lista abierta"))
        return withContext(Dispatchers.IO) { runCatching { a.vodInfo(id) } }
    }

    fun openSeries(s: Series) {
        stack.add(Screen.SeriesPage(s))
    }

    /** Clave con la que se recuerda la posición de un episodio (la misma que usa el reproductor). */
    fun episodeKey(ep: Episode): String? = currentAccount?.let { "${it.id}:ep:${ep.id}" }

    fun playEpisode(series: Series, ep: Episode) {
        stack.add(episodeScreen(series, ep) ?: return)
    }

    private fun episodeScreen(series: Series, ep: Episode): Screen.Player? {
        val a = api ?: return null
        val key = episodeKey(ep) ?: return null
        val label = "T${ep.season}E${ep.number}"
        val remote = a.episodeUrl(ep.id, ep.ext)
        val local = DownloadCenter.localFile(key)
        return Screen.Player(
            PlayItem(
                title = "${series.name} · $label",
                url = local?.let { Uri.fromFile(it).toString() } ?: remote,
                remoteUrl = if (local != null) remote else null,
                altUrl = null,
                isLive = false,
                streamId = null,
                resumeKey = key,
                startPositionMs = storage.getPosition(key),
                recent = Recent(
                    key = key,
                    kind = "episode",
                    contentId = ep.id,
                    title = series.name,
                    subtitle = label,
                    image = ep.image ?: series.cover,
                    ext = ep.ext,
                    seriesId = series.id,
                    positionMs = 0L,
                    durationMs = 0L,
                    updatedAt = 0L,
                ),
            )
        )
    }

    /**
     * Capítulo que sigue al que se está reproduciendo (pasa a la temporada siguiente si hace falta).
     * Devuelve null si no es un episodio o si es el último disponible.
     */
    suspend fun nextEpisode(item: PlayItem): Episode? {
        val r = item.recent ?: return null
        if (r.kind != "episode" || r.seriesId == 0) return null
        val detail = seriesCache[r.seriesId] ?: loadSeriesInfo(r.seriesId).getOrNull() ?: return null
        // Las temporadas vienen ordenadas y los episodios de cada una, por número.
        val all = detail.seasons.values.flatten()
        val i = all.indexOfFirst { it.id == r.contentId }
        return if (i >= 0) all.getOrNull(i + 1) else null
    }

    /** Reemplaza el reproductor actual por el siguiente capítulo. Devuelve true si se pudo abrir. */
    fun playNextEpisode(item: PlayItem, next: Episode): Boolean {
        val r = item.recent ?: return false
        val series = Series(r.seriesId, r.title, r.image, "", null, null)
        val screen = episodeScreen(series, next) ?: return false
        if (stack.lastOrNull() is Screen.Player) stack[stack.lastIndex] = screen else stack.add(screen)
        return true
    }

    /** Retoma un elemento de "Continuar viendo" justo donde se dejó. */
    fun playRecent(r: Recent) {
        val a = api ?: return
        val ext = r.ext.ifEmpty { "mp4" }
        val remote = if (r.kind == "movie") {
            a.movieUrl(r.contentId.toIntOrNull() ?: return, ext)
        } else {
            a.episodeUrl(r.contentId, ext)
        }
        val local = DownloadCenter.localFile(r.key)
        val url = local?.let { Uri.fromFile(it).toString() } ?: remote
        stack.add(
            Screen.Player(
                PlayItem(
                    title = if (r.subtitle.isEmpty()) r.title else "${r.title} · ${r.subtitle}",
                    url = url,
                    remoteUrl = if (local != null) remote else null,
                    altUrl = null,
                    isLive = false,
                    streamId = null,
                    resumeKey = r.key,
                    startPositionMs = storage.getPosition(r.key).takeIf { it > 0 } ?: r.positionMs,
                    recent = r,
                )
            )
        )
    }

    fun removeRecent(r: Recent) {
        recents.removeAll { it.key == r.key }
        persistRecents()
    }

    /**
     * Se llama al salir del reproductor: recuerda la posición y actualiza "Continuar viendo".
     * Si ya casi terminó, se marca como visto y sale de la lista.
     */
    fun saveProgress(item: PlayItem, positionMs: Long, durationMs: Long) {
        val key = item.resumeKey ?: return
        if (durationMs <= 0) return
        val finished = positionMs > durationMs - 15_000
        storage.setPosition(key, if (finished) 0L else positionMs)

        val idx = recents.indexOfFirst { it.key == key }
        if (finished) {
            if (idx >= 0) recents.removeAt(idx)
            if (key !in watched && watched.add(key)) currentAccount?.let { storage.saveWatched(it.id, watched.toSet()) }
        } else {
            if (positionMs >= 10_000 && watched.remove(key)) {
                currentAccount?.let { storage.saveWatched(it.id, watched.toSet()) }
            }
            val base = item.recent ?: return
            // No se agrega lo que se abrió por menos de 10 segundos (salvo que ya estuviera en la lista).
            if (positionMs < 10_000 && idx < 0) return
            if (idx >= 0) recents.removeAt(idx)
            recents.add(
                0,
                base.copy(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            while (recents.size > MAX_RECENTS) recents.removeAt(recents.lastIndex)
        }
        persistRecents()
    }

    private fun persistRecents() {
        currentAccount?.let { storage.saveRecents(it.id, recents.toList()) }
    }

    // ---------- Utilidades ----------

    private fun hostOf(server: String) =
        server.removePrefix("http://").removePrefix("https://").substringBefore("/")

    private fun errorText(e: Exception): String = when (e) {
        is UnknownHostException -> "servidor no encontrado (revisa la dirección)"
        is SocketTimeoutException -> "tiempo de espera agotado"
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---------------------------------------------------------------- Actualizaciones

    private val updater = Updater(app)

    /** Versión nueva disponible (null = nada que mostrar). */
    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    /** Avance de la descarga de 0 a 1 (null = no se está descargando). */
    var updateProgress by mutableStateOf<Float?>(null)
        private set

    /** Busca una versión nueva. Sin `manual`, solo lo hace si pasaron 3 días desde la última vez. */
    fun checkUpdate(manual: Boolean = false) {
        if (!manual && !updater.isCheckDue()) return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { updater.fetchLatest() }
                updater.markChecked()
                if (info != null) {
                    update = info
                } else if (manual) {
                    toast("Ya tienes la última versión (${updater.currentName()})")
                }
            } catch (e: Exception) {
                if (manual) toast("No se pudo buscar actualizaciones. Revisa tu conexión.")
            }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        if (updateProgress != null) return
        // Android exige que el usuario active "Instalar apps desconocidas" para esta app (una sola vez).
        if (!updater.canInstall()) {
            toast("Activa el permiso para instalar y vuelve a tocar Actualizar")
            updater.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            updateProgress = 0f
            try {
                val file = withContext(Dispatchers.IO) {
                    updater.download(info) { p -> updateProgress = p }
                }
                updater.install(file)
                update = null
            } catch (e: Exception) {
                toast("No se pudo descargar la actualización. Inténtalo de nuevo.")
            } finally {
                updateProgress = null
            }
        }
    }

    fun dismissUpdate() {
        if (updateProgress == null) update = null
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication<Application>(), msg, Toast.LENGTH_LONG).show()
    }

    // Debe ir al final de la clase: abrir la cuenta usa propiedades declaradas más arriba y más abajo, y Kotlin
    // las inicializa en orden. Con la copia guardada entra al instante (sin esperar red), así que si esto estuviera
    // antes, esas propiedades todavía serían null y la lista no abriría.
    init {
        // Si ya hay listas guardadas, entra directamente en la última usada (o en la primera).
        val last = storage.lastAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: accounts.firstOrNull()
        if (last != null) openAccount(last, fromLaunch = true)
    }

    private companion object {
        const val MAX_RECENTS = 30
        const val CATALOG_FRESH_MS = 6L * 60 * 60 * 1000
        const val LIVE_ORDER = "live"
    }
}
