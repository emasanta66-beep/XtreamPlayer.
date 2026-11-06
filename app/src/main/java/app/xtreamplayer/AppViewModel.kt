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
import app.xtreamplayer.data.Series
import app.xtreamplayer.data.SeriesDetail
import app.xtreamplayer.data.Storage
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
    }

    fun openAccount(acc: Account) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val a = XtreamApi(acc)
                val info = withContext(Dispatchers.IO) { a.authenticate() }
                api = a
                currentAccount = acc
                authInfo = info
                live = Section()
                movies = Section()
                series = Section()
                favs.clear()
                favs.addAll(storage.loadFavs(acc.id))
                stack.add(Screen.Browse(acc.id))
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

    fun playMovie(id: Int, name: String, ext: String) {
        val a = api ?: return
        val acc = currentAccount ?: return
        val key = "${acc.id}:movie:$id"
        stack.add(
            Screen.Player(
                PlayItem(
                    title = name,
                    url = a.movieUrl(id, ext.ifEmpty { "mp4" }),
                    altUrl = null,
                    isLive = false,
                    streamId = null,
                    resumeKey = key,
                    startPositionMs = storage.getPosition(key),
                )
            )
        )
    }

    fun openSeries(s: Series) {
        stack.add(Screen.SeriesPage(s))
    }

    fun playEpisode(seriesName: String, ep: Episode) {
        val a = api ?: return
        val acc = currentAccount ?: return
        val key = "${acc.id}:ep:${ep.id}"
        stack.add(
            Screen.Player(
                PlayItem(
                    title = "$seriesName · T${ep.season}E${ep.number}",
                    url = a.episodeUrl(ep.id, ep.ext),
                    altUrl = null,
                    isLive = false,
                    streamId = null,
                    resumeKey = key,
                    startPositionMs = storage.getPosition(key),
                )
            )
        )
    }

    fun savePosition(key: String, ms: Long) = storage.setPosition(key, ms)

    // ---------- Utilidades ----------

    private fun hostOf(server: String) =
        server.removePrefix("http://").removePrefix("https://").substringBefore("/")

    private fun errorText(e: Exception): String = when (e) {
        is UnknownHostException -> "servidor no encontrado (revisa la dirección)"
        is SocketTimeoutException -> "tiempo de espera agotado"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication<Application>(), msg, Toast.LENGTH_LONG).show()
    }
}
