@file:OptIn(ExperimentalMaterial3Api::class)

package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.Section
import app.xtreamplayer.data.Category
import app.xtreamplayer.data.Channel
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Recent
import app.xtreamplayer.data.Series
import coil.compose.AsyncImage

@Composable
fun BrowseScreen(vm: AppViewModel) {
    // Pestaña inicial: "Películas". "Continuar" queda al final, después de "Favoritos".
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val titles = listOf("Inicio", "En vivo", "Películas", "Series", "Favoritos", "Continuar", "Descargas")

    LaunchedEffect(tab) {
        when (tab) {
            TAB_LIVE -> vm.ensureLive()
            TAB_MOVIES -> vm.ensureMovies()
            TAB_SERIES -> vm.ensureSeries()
        }
    }
    LaunchedEffect(searching) {
        if (searching) runCatching { focus.requestFocus() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Buscar…") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        } else {
                            Column {
                                Text(
                                    vm.currentAccount?.name ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val info = vm.authInfo
                                val sub = listOfNotNull(
                                    info?.status?.takeIf { it.isNotBlank() },
                                    info?.expires?.let { "vence $it" },
                                ).joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar",
                            )
                        }
                        IconButton(onClick = {
                            when (tab) {
                                TAB_LIVE -> vm.ensureLive(true)
                                TAB_MOVIES -> vm.ensureMovies(true)
                                TAB_SERIES -> vm.ensureSeries(true)
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Cambiar de lista") },
                                    onClick = {
                                        vm.manageAccounts()
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Añadir lista") },
                                    onClick = {
                                        vm.manageAccounts(add = true)
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("En vivo: HLS (.m3u8)" + if (vm.liveFormat == "m3u8") "  ✓" else "") },
                                    onClick = {
                                        vm.setLiveFormat("m3u8")
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("En vivo: MPEG-TS (.ts)" + if (vm.liveFormat == "ts") "  ✓" else "") },
                                    onClick = {
                                        vm.setLiveFormat("ts")
                                        menuOpen = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Buscar actualización") },
                                    onClick = {
                                        vm.checkUpdate(manual = true)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    titles.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                    }
                }
            }
        },
    ) { pad ->
        val modifier = Modifier.padding(pad).fillMaxSize()
        when (tab) {
            TAB_HOME -> HomeTab(vm, onContinue = { tab = TAB_CONTINUE }, onMovies = { tab = TAB_MOVIES }, onLive = { tab = TAB_LIVE }, modifier = modifier)
            TAB_LIVE -> LiveTab(vm, query, modifier)
            TAB_MOVIES -> MoviesTab(vm, query, modifier)
            TAB_SERIES -> SeriesTab(vm, query, modifier)
            TAB_FAVS -> FavTab(vm, query, modifier)
            TAB_CONTINUE -> ContinueTab(vm, query, modifier)
            else -> DownloadsTab(vm, query, modifier)
        }
    }
}

// Posición de cada pestaña (mismo orden que `titles`).
private const val TAB_HOME = 0
private const val TAB_LIVE = 1
private const val TAB_MOVIES = 2
private const val TAB_SERIES = 3
private const val TAB_FAVS = 4
private const val TAB_CONTINUE = 5

// ---------------------------------------------------------------- Pestañas

@Composable
private fun ContinueTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val q = query.trim()
    val list = vm.recents.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    if (list.isEmpty()) {
        Box(modifier) {
            EmptyHint(
                if (vm.recents.isEmpty()) "Lo que empieces a ver aparecerá aquí para que lo continúes donde lo dejaste."
                else "Sin resultados",
            )
        }
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.key }) { r ->
                ContinueRow(r, onClick = { vm.playRecent(r) }, onRemove = { vm.removeRecent(r) })
            }
        }
    }
}

@Composable
private fun ContinueRow(r: Recent, onClick: () -> Unit, onRemove: () -> Unit) {
    val progress = if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else null
    val remainingMin = ((r.durationMs - r.positionMs) / 60_000L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumb(image = r.image, progress = progress, modifier = Modifier.width(150.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    r.subtitle.ifEmpty { "Película" },
                    "quedan $remainingMin min",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar de Continuar viendo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val s = vm.live
    var ordering by remember { mutableStateOf(false) }
    if (ordering) {
        CategoryOrderDialog(
            cats = s.cats,
            onMove = { from, to -> vm.moveLiveCategory(from, to) },
            onDismiss = { ordering = false },
        )
    }
    SectionHost(s, modifier, onRetry = { vm.ensureLive(true) }) {
        CategoryRow(s.cats, s.selectedCat, onReorder = { ordering = true }) {
            s.catChosen = true
            s.selectedCat = it
        }
        val list = remember(s.items, s.selectedCat, query) {
            filterBy(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        }
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { ch ->
                    ListRow(
                        title = ch.name,
                        image = ch.icon,
                        fav = vm.isFav("live", ch.id),
                        onClick = { vm.playChannel(ch) },
                        onFav = { vm.toggleFav(Fav("live", ch.id, ch.name, ch.icon, "")) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoviesTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val s = vm.movies
    SectionHost(s, modifier, onRetry = { vm.ensureMovies(true) }) {
        CategoryRow(s.cats, s.selectedCat) {
            s.catChosen = true
            s.selectedCat = it
        }
        val list = remember(s.items, s.selectedCat, query) {
            filterBy(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        }
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            val categoryNames = remember(s.cats) { s.cats.associate { it.id to it.name } }
            val shelves = remember(list) { list.groupBy { it.categoryId } }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                shelves.forEach { (categoryId, movies) ->
                    item(key = "heading-$categoryId") {
                        Text(
                            categoryNames[categoryId] ?: "Películas",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "shelf-$categoryId") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(movies, key = { it.id }) { m ->
                                Poster(
                                    title = m.name,
                                    image = m.icon,
                                    metadata = movieMetadata(m.name, m.rating, m.plot),
                                    modifier = Modifier.width(146.dp),
                                    fav = vm.isFav("movie", m.id),
                                    onClick = { vm.playMovie(m.id, m.name, m.ext, m.icon) },
                                    onFav = { vm.toggleFav(Fav("movie", m.id, m.name, m.icon, m.ext)) },
                                    download = {
                                        val key = vm.currentAccount?.let { "${it.id}:movie:${m.id}" }
                                        DownloadBadge(
                                            d = vm.downloads.firstOrNull { it.key == key },
                                            onStart = { vm.downloadMovie(m.id, m.name, m.ext, m.icon) },
                                            onToggle = { key?.let { vm.toggleDownload(it) } },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val s = vm.series
    SectionHost(s, modifier, onRetry = { vm.ensureSeries(true) }) {
        CategoryRow(s.cats, s.selectedCat) {
            s.catChosen = true
            s.selectedCat = it
        }
        val list = remember(s.items, s.selectedCat, query) {
            filterBy(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        }
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            val categoryNames = remember(s.cats) { s.cats.associate { it.id to it.name } }
            val shelves = remember(list) { list.groupBy { it.categoryId } }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                shelves.forEach { (categoryId, seriesList) ->
                    item(key = "heading-$categoryId") {
                        Text(
                            categoryNames[categoryId] ?: "Series",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "shelf-$categoryId") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(seriesList, key = { it.id }) { sr ->
                                Poster(
                                    title = sr.name,
                                    image = sr.cover,
                                    metadata = seriesMetadata(sr.rating, sr.plot),
                                    modifier = Modifier.width(146.dp),
                                    fav = vm.isFav("series", sr.id),
                                    onClick = { vm.openSeries(sr) },
                                    onFav = { vm.toggleFav(Fav("series", sr.id, sr.name, sr.cover, "")) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val q = query.trim()
    val list = vm.favs.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    if (list.isEmpty()) {
        Box(modifier) {
            EmptyHint(
                if (vm.favs.isEmpty()) "Toca el corazón en canales, películas o series para verlos aquí."
                else "Sin resultados",
            )
        }
    } else {
        LazyColumn(modifier) {
            items(list) { f ->
                ListRow(
                    title = f.name,
                    subtitle = when (f.type) {
                        "live" -> "En vivo"
                        "movie" -> "Película"
                        else -> "Serie"
                    },
                    image = f.icon,
                    fav = true,
                    onClick = {
                        when (f.type) {
                            "live" -> vm.playChannel(Channel(f.id, f.name, f.icon, ""))
                            "movie" -> vm.playMovie(f.id, f.name, f.ext, f.icon)
                            else -> vm.openSeries(Series(f.id, f.name, f.icon, "", null, null))
                        }
                    },
                    onFav = { vm.toggleFav(f) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Componentes

@Composable
private fun <T> SectionHost(
    s: Section<T>,
    modifier: Modifier,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        when {
            s.loading && !s.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            s.error != null && !s.loaded -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo cargar: ${s.error}", textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Reintentar") }
            }

            else -> {
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                content()
            }
        }
    }
}

@Composable
private fun CategoryRow(
    cats: List<Category>,
    selected: String?,
    onReorder: (() -> Unit)? = null,
    onSelect: (String?) -> Unit,
) {
    if (cats.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        if (onReorder != null) {
            item {
                IconButton(onClick = onReorder, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ordenar categorías")
                }
            }
        }
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Todos") })
        }
        items(cats) { c ->
            FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(c.id) },
                label = { Text(c.name, maxLines = 1) },
            )
        }
    }
}

/** Diálogo para cambiar el orden de las categorías: sube, baja o manda una al principio. */
@Composable
private fun CategoryOrderDialog(
    cats: List<Category>,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordenar categorías") },
        text = {
            Column {
                Text(
                    "Pon tus categorías favoritas primero. El orden se guarda para esta lista.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onMove(i, 0) }, enabled = i > 0) { Text("Primero") }
                            IconButton(onClick = { onMove(i, i - 1) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir")
                            }
                            IconButton(onClick = { onMove(i, i + 1) }, enabled = i < cats.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun ListRow(
    title: String,
    image: String?,
    fav: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onFav) {
            Icon(
                if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (fav) Color(0xFFFF6B81) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Poster(
    title: String,
    image: String?,
    fav: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit,
    download: (@Composable () -> Unit)? = null,
    metadata: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
            ) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (fav) Color(0xFFFF6B81) else Color.White,
                    modifier = Modifier.clickable(onClick = onFav).padding(6.dp).size(20.dp),
                )
            }
            if (download != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) { download() }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!metadata.isNullOrBlank()) {
            Text(
                metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun movieMetadata(title: String, rating: String?, plot: String?): String {
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(title)?.value
    val score = rating?.takeIf { it.toFloatOrNull()?.let { n -> n > 0f } == true }?.let { "★ $it" }
    val details = listOfNotNull(year, score).joinToString(" · ")
    val description = plot?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }?.take(76)
    return listOfNotNull(details.takeIf { it.isNotBlank() }, description).joinToString("\\n")
}

private fun seriesMetadata(rating: String?, plot: String?): String {
    val score = rating?.takeIf { it.toFloatOrNull()?.let { n -> n > 0f } == true }?.let { "★ $it" }
    val description = plot?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }?.take(72)
    return listOfNotNull(score, description).joinToString(" · ")
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

private fun <T> filterBy(
    items: List<T>,
    cat: String?,
    q: String,
    catOf: (T) -> String,
    nameOf: (T) -> String,
): List<T> {
    val query = q.trim()
    if (cat == null && query.isEmpty()) return items
    return items.filter {
        (cat == null || catOf(it) == cat) && (query.isEmpty() || nameOf(it).contains(query, ignoreCase = true))
    }
}
