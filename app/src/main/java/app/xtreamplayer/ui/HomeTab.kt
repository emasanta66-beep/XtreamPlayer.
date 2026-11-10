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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.Recent
import app.xtreamplayer.data.Series
import coil.compose.AsyncImage

@Composable
fun HomeTab(
    vm: AppViewModel,
    onContinue: () -> Unit,
    onMovies: () -> Unit,
    onLive: () -> Unit,
    onSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = vm.recents.firstOrNull()
    // Las "recién agregadas" salen de las listas completas (categoría "Todos"), así que se cargan aquí.
    LaunchedEffect(Unit) {
        vm.ensureMovies()
        vm.ensureSeries()
    }
    val newMovies = remember(vm.movies.items) {
        vm.movies.items.sortedWith(compareByDescending<Movie> { it.added }.thenByDescending { it.id }).take(20)
    }
    val newSeries = remember(vm.series.items) {
        vm.series.items.sortedWith(compareByDescending<Series> { it.added }.thenByDescending { it.id }).take(20)
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("¿Qué vemos hoy?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    vm.currentAccount?.name ?: "Tu entretenimiento, en un solo lugar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF143C38), Color(0xFF17202A), Color(0xFF10151D)))),
            ) {
                if (recent?.image?.isNotBlank() == true) {
                    AsyncImage(
                        model = recent.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 0.48f,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xF510151D))),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (recent == null) "TU ESPACIO DE ENTRETENIMIENTO" else "VUELVE A TU HISTORIA",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF72E0BE),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        recent?.title ?: "Encuentra algo para ver",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = { if (recent != null) vm.playRecent(recent) else onMovies() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39C6A3), contentColor = Color(0xFF06241D)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (recent == null) "Explorar catálogo" else "Continuar viendo")
                    }
                }
            }
        }
        if (vm.recents.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onContinue),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continuar viendo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Ver todo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vm.recents.take(10), key = { it.key }) { item ->
                            RecentCard(item, onClick = { vm.playRecent(item) })
                        }
                    }
                }
            }
        }
        if (newMovies.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfHeader("Películas recién agregadas", onMovies)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(newMovies, key = { it.id }) { m ->
                            ShelfCard(m.name, m.icon, onClick = { vm.openMovie(m) })
                        }
                    }
                }
            }
        }
        if (newSeries.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfHeader("Series recién agregadas", onSeries)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(newSeries, key = { it.id }) { sr ->
                            ShelfCard(sr.name, sr.cover, onClick = { vm.openSeries(sr) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSeeAll), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("Ver todo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ShelfCard(title: String, image: String?, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            if (!image.isNullOrBlank()) {
                AsyncImage(image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentCard(item: Recent, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            if (item.image?.isNotBlank() == true) {
                AsyncImage(item.image, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle.ifBlank { "Continuar viendo" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
