@file:OptIn(ExperimentalMaterial3Api::class)

package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Series
import app.xtreamplayer.data.SeriesDetail
import coil.compose.AsyncImage

@Composable
fun SeriesScreen(vm: AppViewModel, series: Series) {
    var detail by remember { mutableStateOf<SeriesDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(series.id, reload) {
        error = null
        vm.loadSeriesInfo(series.id)
            .onSuccess {
                detail = it
                if (season == null) season = it.seasons.keys.firstOrNull()
            }
            .onFailure { error = it.message ?: "Error desconocido" }
    }

    val isFav = vm.isFav("series", series.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.toggleFav(Fav("series", series.id, series.name, series.cover, ""))
                    }) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFav) Color(0xFFFF6B81) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { pad ->
        val d = detail
        val modifier = Modifier.padding(pad).fillMaxSize()
        when {
            d != null -> {
                val selected = season
                val episodes = if (selected != null) d.seasons[selected].orEmpty() else emptyList()
                val plot = d.plot ?: series.plot
                LazyColumn(modifier) {
                    item {
                        Row(Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = series.cover,
                                    contentDescription = series.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                plot ?: "Sin descripción disponible.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val resume = vm.recents.firstOrNull { it.seriesId == series.id }
                    if (resume != null) {
                        item {
                            Button(
                                onClick = { vm.playRecent(resume) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Continuar ${resume.subtitle}")
                            }
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(d.seasons.keys.toList()) { n ->
                                FilterChip(
                                    selected = n == selected,
                                    onClick = { season = n },
                                    label = { Text("Temporada $n") },
                                )
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        item {
                            OutlinedButton(
                                onClick = { vm.downloadEpisodes(series, episodes) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            ) { Text("⬇  Descargar temporada $selected") }
                        }
                    }
                    items(episodes) { ep ->
                        val saved = vm.recents.firstOrNull { it.key == vm.episodeKey(ep) }
                        val progress = saved?.takeIf { it.durationMs > 0 }
                            ?.let { it.positionMs.toFloat() / it.durationMs }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.playEpisode(series, ep) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            VideoThumb(
                                image = ep.image ?: series.cover,
                                progress = progress,
                                modifier = Modifier.width(150.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "E${ep.number} · ${ep.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!ep.duration.isNullOrBlank()) {
                                    Text(
                                        ep.duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!ep.plot.isNullOrBlank()) {
                                    Text(
                                        ep.plot,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val epKey = vm.episodeKey(ep)
                            DownloadBadge(
                                d = vm.downloads.firstOrNull { it.key == epKey },
                                onStart = { vm.downloadEpisodes(series, listOf(ep)) },
                                onToggle = { epKey?.let { vm.toggleDownload(it) } },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (d.seasons.isEmpty()) {
                        item {
                            Text(
                                "Esta serie no tiene episodios disponibles.",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            error != null -> Column(
                modifier = modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo cargar la serie: $error", textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { reload++ }) { Text("Reintentar") }
            }

            else -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
