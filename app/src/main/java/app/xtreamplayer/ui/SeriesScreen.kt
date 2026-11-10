@file:OptIn(ExperimentalMaterial3Api::class)

package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
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

    val unknownError = stringResource(R.string.common_unknown_error)
    LaunchedEffect(series.id, reload) {
        error = null
        vm.loadSeriesInfo(series.id)
            .onSuccess {
                detail = it
                if (season == null) season = it.seasons.keys.firstOrNull()
            }
            .onFailure { error = it.message ?: unknownError }
    }

    val isFav = vm.isFav("series", series.id)

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = { Text(series.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Palette.TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.toggleFav(Fav("series", series.id, series.name, series.cover, ""))
                    }) {
                        Icon(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.common_favorite),
                            tint = if (isFav) Palette.Favorite else Palette.TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Palette.Background,
                    titleContentColor = Palette.TextPrimary,
                ),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Palette.HeroTop.copy(alpha = 0.55f), Palette.Background),
                                    ),
                                )
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(168.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Palette.SurfaceHigh)
                                    .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp)),
                            ) {
                                AsyncImage(
                                    model = series.cover,
                                    contentDescription = series.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column(
                                Modifier.weight(1f).padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Text(
                                    stringResource(R.string.series_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.Accent,
                                    fontWeight = FontWeight.Bold,
                                )
                                series.rating?.toFloatOrNull()?.takeIf { it > 0f }?.let { score ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("★", color = Palette.Star, style = MaterialTheme.typography.labelLarge)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            String.format(java.util.Locale.ROOT, "%.1f", score),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Palette.TextPrimary,
                                        )
                                    }
                                }
                                Text(
                                    plot?.replace(Regex("\\s+"), " ")?.trim()
                                        ?: stringResource(R.string.series_no_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextSecondary,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    val resume = vm.recents.firstOrNull { it.seriesId == series.id }
                    if (resume != null) {
                        item {
                            Button(
                                onClick = { vm.playRecent(resume) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                                    .height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Palette.Accent,
                                    contentColor = Palette.OnAccent,
                                ),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.series_continue_from, resume.subtitle),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            items(d.seasons.keys.toList(), key = { it }) { n ->
                                FilterChip(
                                    selected = n == selected,
                                    onClick = { season = n },
                                    label = { Text(stringResource(R.string.series_season, n), maxLines = 1) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Palette.SurfaceHigh,
                                        labelColor = Palette.TextSecondary,
                                        selectedContainerColor = Palette.Accent.copy(alpha = 0.16f),
                                        selectedLabelColor = Palette.Accent,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = n == selected,
                                        borderColor = Palette.OutlineSoft,
                                        selectedBorderColor = Palette.Accent.copy(alpha = 0.45f),
                                    ),
                                )
                            }
                        }
                    }

                    if (episodes.isNotEmpty()) {
                        item {
                            OutlinedButton(
                                onClick = { vm.downloadEpisodes(series, episodes) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                                    .height(46.dp),
                                shape = CircleShape,
                            ) {
                                Text(
                                    stringResource(R.string.series_download_season, selected ?: 0),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    items(episodes, key = { it.id }) { ep ->
                        val epKey = vm.episodeKey(ep)
                        val saved = vm.recents.firstOrNull { it.key == epKey }
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
                                modifier = Modifier.width(146.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    (if (epKey in vm.watched) "✓ " else "") +
                                        stringResource(R.string.series_episode, ep.number, ep.title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Palette.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!ep.duration.isNullOrBlank()) {
                                    Text(
                                        ep.duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Palette.TextSecondary,
                                    )
                                }
                                if (!ep.plot.isNullOrBlank()) {
                                    Text(
                                        ep.plot,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Palette.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            DownloadBadge(
                                d = vm.downloadFor(epKey),
                                onStart = { vm.downloadEpisodes(series, listOf(ep)) },
                                onToggle = { epKey?.let { vm.toggleDownload(it) } },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    if (d.seasons.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.series_no_episodes),
                                modifier = Modifier.padding(24.dp),
                                color = Palette.TextSecondary,
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
                Text(
                    stringResource(R.string.series_load_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(error ?: "", textAlign = TextAlign.Center, color = Palette.TextSecondary)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { reload++ }) { Text(stringResource(R.string.common_retry)) }
            }

            else -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent, strokeWidth = 3.dp)
            }
        }
    }
}
