@file:OptIn(ExperimentalMaterial3Api::class)

package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.Section
import app.xtreamplayer.data.Category
import app.xtreamplayer.data.Channel
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.Recent
import app.xtreamplayer.data.Series
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun BrowseScreen(vm: AppViewModel) {
    // Pestaña inicial: "Inicio".
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val titles = listOf("Inicio", "En vivo", "Películas", "Series", "Favoritos", "Continuar", "Descargas")

    // La búsqueda espera a que el usuario deje de escribir: con catálogos de decenas de miles
    // de elementos, filtrar en cada tecla se nota.
    val debouncedQuery by produceState(initialValue = query, query) {
        if (query.isNotEmpty()) delay(220)
        value = query
    }

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
        containerColor = Palette.Background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Palette.Background,
                        titleContentColor = Palette.TextPrimary,
                    ),
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Buscar en tu lista…", color = Palette.TextTertiary) },
                                singleLine = true,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Palette.SurfaceHigh,
                                    unfocusedContainerColor = Palette.SurfaceHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Palette.TextPrimary,
                                    unfocusedTextColor = Palette.TextPrimary,
                                    cursorColor = Palette.Accent,
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AccountAvatar(vm.currentAccount?.name)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        vm.currentAccount?.name ?: "Xtream Player",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
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
                                            color = Palette.TextSecondary,
                                        )
                                    }
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
                                contentDescription = if (searching) "Cerrar búsqueda" else "Buscar",
                                tint = Palette.TextPrimary,
                            )
                        }
                        IconButton(onClick = {
                            when (tab) {
                                TAB_LIVE -> vm.ensureLive(true)
                                TAB_MOVIES -> vm.ensureMovies(true)
                                TAB_SERIES -> vm.ensureSeries(true)
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Palette.TextPrimary) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Palette.TextPrimary)
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
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 12.dp,
                    containerColor = Palette.Background,
                    contentColor = Palette.Accent,
                    divider = {},
                ) {
                    titles.forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                Text(
                                    t,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            },
                            selectedContentColor = Palette.Accent,
                            unselectedContentColor = Palette.TextSecondary,
                        )
                    }
                }
            }
        },
    ) { pad ->
        val modifier = Modifier.padding(pad).fillMaxSize()
        when (tab) {
            TAB_HOME -> HomeTab(
                vm,
                onContinue = { tab = TAB_CONTINUE },
                onMovies = { tab = TAB_MOVIES },
                onLive = { tab = TAB_LIVE },
                onSeries = { tab = TAB_SERIES },
                onDownloads = { tab = TAB_DOWNLOADS },
                modifier = modifier,
            )
            TAB_LIVE -> LiveTab(vm, debouncedQuery, modifier)
            TAB_MOVIES -> MoviesTab(vm, debouncedQuery, modifier)
            TAB_SERIES -> SeriesTab(vm, debouncedQuery, modifier)
            TAB_FAVS -> FavTab(vm, debouncedQuery, modifier)
            TAB_CONTINUE -> ContinueTab(vm, debouncedQuery, modifier)
            else -> DownloadsTab(vm, debouncedQuery, modifier)
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
private const val TAB_DOWNLOADS = 6

@Composable
private fun AccountAvatar(name: String?) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar() ?: 'X'
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.Accent.copy(alpha = 0.16f))
            .border(1.dp, Palette.Accent.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

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
    val progress = if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else null
    val remainingMin = ((r.durationMs - r.positionMs) / 60_000L).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumb(image = r.image, progress = progress, modifier = Modifier.width(148.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                r.title,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    r.subtitle.ifEmpty { "Película" },
                    "quedan $remainingMin min",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar de Continuar viendo",
                tint = Palette.TextTertiary,
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
        val list = filteredItems(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.id }) { ch ->
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
        val list = filteredItems(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            val ctx = LocalContext.current
            LaunchedEffect(list) { prefetchImages(ctx, list.take(24).map { it.icon }) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { m ->
                    val key = vm.currentAccount?.let { "${it.id}:movie:${m.id}" }
                    Poster(
                        title = m.name,
                        image = m.icon,
                        metadata = movieMetadata(m.name, m.rating),
                        progress = vm.movieProgress(m.id),
                        watched = vm.isMovieWatched(m.id),
                        modifier = Modifier.fillMaxWidth(),
                        fav = vm.isFav("movie", m.id),
                        onClick = { vm.openMovie(m) },
                        onFav = { vm.toggleFav(Fav("movie", m.id, m.name, m.icon, m.ext)) },
                        download = {
                            DownloadBadge(
                                d = vm.downloadFor(key),
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

@Composable
private fun SeriesTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val s = vm.series
    SectionHost(s, modifier, onRetry = { vm.ensureSeries(true) }) {
        CategoryRow(s.cats, s.selectedCat) {
            s.catChosen = true
            s.selectedCat = it
        }
        val list = filteredItems(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            val ctx = LocalContext.current
            LaunchedEffect(list) { prefetchImages(ctx, list.take(24).map { it.cover }) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { sr ->
                    Poster(
                        title = sr.name,
                        image = sr.cover,
                        metadata = seriesMetadata(sr.name, sr.rating),
                        modifier = Modifier.fillMaxWidth(),
                        fav = vm.isFav("series", sr.id),
                        onClick = { vm.openSeries(sr) },
                        onFav = { vm.toggleFav(Fav("series", sr.id, sr.name, sr.cover, "")) },
                    )
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
            items(list, key = { "${it.type}:${it.id}" }) { f ->
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
                            "movie" -> vm.openMovie(Movie(f.id, f.name, f.icon, "", f.ext.ifEmpty { "mp4" }, null))
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
                CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp)
            }

            s.error != null && !s.loaded -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No se pudo cargar",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s.error ?: "",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onRetry) { Text("Reintentar") }
            }

            else -> {
                if (s.loading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(2.dp),
                        color = Palette.Accent,
                        trackColor = Color.Transparent,
                    )
                }
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
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        if (onReorder != null) {
            item {
                IconButton(onClick = onReorder, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = "Ordenar categorías",
                        tint = Palette.TextSecondary,
                    )
                }
            }
        }
        item {
            CategoryChip("Todos", selected == null) { onSelect(null) }
        }
        items(cats, key = { it.id }) { c ->
            CategoryChip(c.name, selected == c.id) { onSelect(c.id) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.SurfaceHigh,
            labelColor = Palette.TextSecondary,
            selectedContainerColor = Palette.Accent.copy(alpha = 0.16f),
            selectedLabelColor = Palette.Accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Palette.OutlineSoft,
            selectedBorderColor = Palette.Accent.copy(alpha = 0.45f),
        ),
    )
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
                    color = Palette.TextSecondary,
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
            .padding(start = 16.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.OutlineSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                )
            }
        }
        IconButton(onClick = onFav) {
            Icon(
                if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (fav) Palette.Favorite else Palette.TextTertiary,
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
    progress: Float? = null,
    watched: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.OutlineSoft, shape),
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
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Palette.ScrimSoft),
            ) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (fav) Palette.Favorite else Color.White,
                    modifier = Modifier.clickable(onClick = onFav).padding(5.dp).size(18.dp),
                )
            }
            if (download != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) { download() }
            }
            if (watched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Palette.ScrimSoft)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Vista",
                        tint = Palette.Success,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    color = Palette.Accent,
                    trackColor = Color(0x55000000),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!metadata.isNullOrBlank()) {
            Text(
                metadata,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun movieMetadata(title: String, rating: String?): String {
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(title)?.value
    val score = rating?.toFloatOrNull()?.takeIf { it > 0f }?.let { "★ $it" }
    return listOfNotNull(year, score).joinToString(" · ")
}

private fun seriesMetadata(title: String, rating: String?): String = movieMetadata(title, rating)

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = Palette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
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
    // Al buscar, se busca en todas las categorías (no solo en la elegida).
    val cat = if (query.isEmpty()) cat else null
    if (cat == null && query.isEmpty()) return items
    return items.filter {
        (cat == null || catOf(it) == cat) && (query.isEmpty() || nameOf(it).contains(query, ignoreCase = true))
    }
}

/**
 * Filtra una categoría o un texto de búsqueda sin bloquear el hilo principal.
 * Con catálogos de decenas de miles de elementos, filtrar en cada recomposición causaba tirones;
 * aquí el trabajo se hace en [Dispatchers.Default] y se mantiene la lista anterior mientras llega la nueva.
 */
@Composable
private fun <T> filteredItems(
    items: List<T>,
    cat: String?,
    query: String,
    catOf: (T) -> String,
    nameOf: (T) -> String,
): List<T> {
    val q = query.trim()
    val result by produceState(initialValue = items, items, cat, q) {
        value = withContext(Dispatchers.Default) { filterBy(items, cat, q, catOf, nameOf) }
    }
    return result
}
