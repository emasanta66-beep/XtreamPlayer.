package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
import app.xtreamplayer.data.DlStatus
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.MovieInfo
import coil.compose.AsyncImage

/** Ficha de una película: portada, sinopsis y botones para reproducir, guardar o descargar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieScreen(vm: AppViewModel, movie: Movie) {
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    LaunchedEffect(movie.id) {
        vm.loadMovieInfo(movie.id).onSuccess { info = it }
    }

    val isFav = vm.isFav("movie", movie.id)
    val key = vm.currentAccount?.let { "${it.id}:movie:${movie.id}" }
    val dl = vm.downloadFor(key)
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(movie.name)?.value
    val category = vm.movies.cats.firstOrNull { it.id == movie.categoryId }?.name
    val meta = listOfNotNull(year, category, info?.duration?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val rating = movie.rating?.toFloatOrNull()?.takeIf { it > 0f }
    val plot = (info?.plot ?: movie.plot)
        ?.replace("\\r", " ")?.replace("\\n", " ")?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = Palette.Background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Palette.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // Cabecera: portada sobre un degradado que se funde con el fondo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Palette.HeroTop.copy(alpha = 0.55f), Palette.Background)))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .width(134.dp)
                        .height(201.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.SurfaceHigh)
                        .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp)),
                ) {
                    AsyncImage(
                        model = movie.icon,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    Modifier.weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        stringResource(R.string.movie_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        movie.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.TextPrimary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★", color = Palette.Star, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                String.format(java.util.Locale.ROOT, "%.1f", rating),
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.TextPrimary,
                            )
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                    }
                    info?.genre?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, maxLines = 2)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                plot ?: stringResource(R.string.movie_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.playMovie(movie.id, movie.name, movie.ext, movie.icon) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 18.dp)
                    .tvFocus(CircleShape, scale = 1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Accent,
                    contentColor = Palette.OnAccent,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.movie_play), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) {
                OutlinedButton(
                    onClick = { vm.toggleFav(Fav("movie", movie.id, movie.name, movie.icon, movie.ext)) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        stringResource(
                            if (isFav) R.string.movie_in_favorites else R.string.movie_favorites,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (dl == null) {
                            vm.downloadMovie(movie.id, movie.name, movie.ext, movie.icon)
                        } else if (key != null) {
                            vm.toggleDownload(key)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        when (dl?.status) {
                            null -> stringResource(R.string.common_download)
                            DlStatus.DONE -> stringResource(R.string.common_downloaded)
                            DlStatus.DOWNLOADING ->
                                if (dl.total > 0) "${(dl.bytes * 100 / dl.total).coerceIn(0, 100)}%"
                                else stringResource(R.string.common_downloading)
                            DlStatus.QUEUED -> stringResource(R.string.common_queued)
                            DlStatus.PAUSED -> stringResource(R.string.common_resume)
                            DlStatus.ERROR -> stringResource(R.string.common_retry)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
