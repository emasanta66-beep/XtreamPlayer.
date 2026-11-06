package app.xtreamplayer

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xtreamplayer.data.Account
import app.xtreamplayer.data.AuthInfo
import app.xtreamplayer.data.Category
import app.xtreamplayer.data.Channel
import app.xtreamplayer.data.Episode
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

    data class SeriesPage(val series: Series) : Screen {
        override val key: String get() = "series_${series.id}"
    }

    data class Player(val item: PlayItem) : Screen {
        override val key: String = "player"
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
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private var api: XtreamApi? = null

    val accounts = mutableStateListOf<Account>().apply { addAll(storage.loadAccounts()) }
    val stack = mutableStateListOf<Screen>(Screen.Accounts)
    val favs = mutableStateListOf<Fav>()
    /** "Continuar viendo": lo más reciente primero. */
    val recents = mutableStateListOf<Recent>()

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

    init {
        // Si ya hay listas guardadas, entra directamente en la última usada (o en la primera).
        val last = storage.lastAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
            ?: accounts.firstOrNull()
        if (last != null) openAccount(last, fromLaunch = true)
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
                val info = withContext(Dispatchers.IO) { a.authenticate() }
                api = a
                currentAccount = acc
                authInfo = info
                storage.lastAccountId = acc.id
                live = Section()
                movies = Section()
                series = Section()
                favs.clear()
                favs.addAll(storage.loadFavs(acc.id))
                recents.clear()
                recents.addAll(storage.loadRecents(acc.id))
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
            } catch (e: Exception) {
                toast("No se pudo abrir la lista: ${errorText(e)}")
            }
            busy = false
        }
    }

    // ---------- Contenido ----------

    fun ensureLive(force: Boolean = false) =
        load(live, force, { it.liveCategories() }, { it.liveStreams() })

    fun ensureMovies(force: Boolean = false) =
        load(movies, force, { it.vodCategories() }, { it.vodStreams() })

    fun ensureSeries(force: Boolean = false) =
        load(series, force, { it.seriesCategories() }, { it.seriesList() })

    private fun <T> load(
        section: Section<T>,
        force: Boolean,
        fetchCats: (XtreamApi) -> List<Category>,
        fetchItems: (XtreamApi) -> List<T>,
    ) {
        val a = api ?: return
        if (section.loading) return
        if (section.loaded && !force) return
        section.loading = true
        section.error = null
        viewModelScope.launch {
            try {
                val (cats, items) = withContext(Dispatchers.IO) { fetchCats(a) to fetchItems(a) }
                section.cats = cats
                section.items = items
                section.loaded = true
            } catch (e: OutOfMemoryError) {
                section.error = "La lista es demasiado grande para la memoria del teléfono"
            } catch (e: Exception) {
                section.error = errorText(e)
            }
            section.loading = false
        }
    }

    suspend fun loadSeriesInfo(id: Int): Result<SeriesDetail> = withContext(Dispatchers.IO) {
        runCatching { api!!.seriesInfo(id) }
    }

    suspend fun epgFor(streamId: Int): List<EpgItem> = withContext(Dispatchers.IO) {
        try {
            api?.shortEpg(streamId) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Favoritos ----------

    fun isFav(type: String, id: Int): Boolean = favs.any { it.type == type && it.id == id }

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
        val a = api ?: return
        val main = liveFormat
        val other = if (main == "m3u8") "ts" else "m3u8"
        stack.add(
            Screen.Player(
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
        )
    }

    fun playMovie(id: Int, name: String, ext: String, image: String? = null) {
        val a = api ?: return
        val acc = currentAccount ?: return
        val key = "${acc.id}:movie:$id"
        val extension = ext.ifEmpty { "mp4" }
        stack.add(
            Screen.Player(
                PlayItem(
                    title = name,
                    url = a.movieUrl(id, extension),
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

    fun openSeries(s: Series) {
        stack.add(Screen.SeriesPage(s))
    }

    /** Clave con la que se recuerda la posición de un episodio (la misma que usa el reproductor). */
    fun episodeKey(ep: Episode): String? = currentAccount?.let { "${it.id}:ep:${ep.id}" }

    fun playEpisode(series: Series, ep: Episode) {
        val a = api ?: return
        val key = episodeKey(ep) ?: return
        val label = "T${ep.season}E${ep.number}"
        stack.add(
            Screen.Player(
                PlayItem(
                    title = "${series.name} · $label",
                    url = a.episodeUrl(ep.id, ep.ext),
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
        )
    }

    /** Retoma un elemento de "Continuar viendo" justo donde se dejó. */
    fun playRecent(r: Recent) {
        val a = api ?: return
        val ext = r.ext.ifEmpty { "mp4" }
        val url = if (r.kind == "movie") {
            a.movieUrl(r.contentId.toIntOrNull() ?: return, ext)
        } else {
            a.episodeUrl(r.contentId, ext)
        }
        stack.add(
            Screen.Player(
                PlayItem(
                    title = if (r.subtitle.isEmpty()) r.title else "${r.title} · ${r.subtitle}",
                    url = url,
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
        } else {
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

    private companion object {
        const val MAX_RECENTS = 30
    }
}
