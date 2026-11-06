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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
    // Pestaña inicial: "Continuar" si hay algo empezado; si no, "En vivo".
    var tab by rememberSaveable { mutableIntStateOf(if (vm.recents.isEmpty()) 1 else 0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val titles = listOf("Continuar", "En vivo", "Películas", "Series", "Favoritos")

    LaunchedEffect(tab) {
        when (tab) {
            1 -> vm.ensureLive()
            2 -> vm.ensureMovies()
            3 -> vm.ensureSeries()
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
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                                1 -> vm.ensureLive(true)
                                2 -> vm.ensureMovies(true)
                                3 -> vm.ensureSeries(true)
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            0 -> ContinueTab(vm, query, modifier)
            1 -> LiveTab(vm, query, modifier)
            2 -> MoviesTab(vm, query, modifier)
            3 -> SeriesTab(vm, query, modifier)
            else -> FavTab(vm, query, modifier)
        }
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
    SectionHost(s, modifier, onRetry = { vm.ensureLive(true) }) {
        CategoryRow(s.cats, s.selectedCat) { s.selectedCat = it }
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
        CategoryRow(s.cats, s.selectedCat) { s.selectedCat = it }
        val list = remember(s.items, s.selectedCat, query) {
            filterBy(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        }
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { m ->
                    Poster(
                        title = m.name,
                        image = m.icon,
                        fav = vm.isFav("movie", m.id),
                        onClick = { vm.playMovie(m.id, m.name, m.ext, m.icon) },
                        onFav = { vm.toggleFav(Fav("movie", m.id, m.name, m.icon, m.ext)) },
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
        CategoryRow(s.cats, s.selectedCat) { s.selectedCat = it }
        val list = remember(s.items, s.selectedCat, query) {
            filterBy(s.items, s.selectedCat, query, { it.categoryId }, { it.name })
        }
        if (list.isEmpty()) {
            EmptyHint("Sin resultados")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { sr ->
                    Poster(
                        title = sr.name,
                        image = sr.cover,
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
private fun CategoryRow(cats: List<Category>, selected: String?, onSelect: (String?) -> Unit) {
    if (cats.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
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
private fun Poster(title: String, image: String?, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
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
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
