package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.data.DlStatus
import app.xtreamplayer.data.Fav
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.MovieInfo
import coil.compose.AsyncImage

/** Ficha de una película: sinopsis y botones para reproducir, guardar en favoritos o descargar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieScreen(vm: AppViewModel, movie: Movie) {
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    LaunchedEffect(movie.id) {
        vm.loadMovieInfo(movie.id).onSuccess { info = it }
    }

    val isFav = vm.isFav("movie", movie.id)
    val key = vm.currentAccount?.let { "${it.id}:movie:${movie.id}" }
    val dl = vm.downloads.firstOrNull { it.key == key }
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(movie.name)?.value
    val category = vm.movies.cats.firstOrNull { it.id == movie.categoryId }?.name
    val meta = listOfNotNull(year, category, info?.duration?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val plot = (info?.plot ?: movie.plot)
        ?.replace("\\r", " ")?.replace("\\n", " ")?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.width(130.dp).height(195.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = movie.icon,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "PELÍCULA",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(movie.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    info?.genre?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                plot ?: "Sin descripción disponible.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { vm.playMovie(movie.id, movie.name, movie.ext, movie.icon) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reproducir película")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.toggleFav(Fav("movie", movie.id, movie.name, movie.icon, movie.ext)) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (isFav) "★ En favoritos" else "☆ Añadir a favoritos") }
                OutlinedButton(
                    onClick = {
                        if (dl == null) {
                            vm.downloadMovie(movie.id, movie.name, movie.ext, movie.icon)
                        } else if (key != null) {
                            vm.toggleDownload(key)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (dl?.status) {
                            null -> "⬇ Descargar"
                            DlStatus.DONE -> "✓ Descargada"
                            DlStatus.DOWNLOADING ->
                                if (dl.total > 0) "Descargando ${(dl.bytes * 100 / dl.total).coerceIn(0, 100)}%" else "Descargando…"
                            DlStatus.QUEUED -> "En cola"
                            DlStatus.PAUSED -> "Reanudar descarga"
                            DlStatus.ERROR -> "Reintentar descarga"
                        },
                    )
                }
            }
        }
    }
}
