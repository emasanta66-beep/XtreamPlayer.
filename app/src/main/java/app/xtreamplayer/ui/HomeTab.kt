package app.xtreamplayer.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
import app.xtreamplayer.data.Movie
import app.xtreamplayer.data.Recent
import app.xtreamplayer.data.Series
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inicio: un héroe cinematográfico con lo que estabas viendo, accesos rápidos y
 * carruseles de "Continuar viendo", películas y series recién agregadas.
 */
@Composable
fun HomeTab(
    vm: AppViewModel,
    onContinue: () -> Unit,
    onMovies: () -> Unit,
    onLive: () -> Unit,
    onSeries: () -> Unit,
    onDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = vm.recents.firstOrNull()
    // Las "recién agregadas" salen de las listas completas (categoría "Todos"), así que se cargan aquí.
    LaunchedEffect(Unit) {
        vm.ensureMovies()
        vm.ensureSeries()
    }

    // El orden de decenas de miles de elementos se calcula fuera del hilo principal.
    val newMovies by produceState(emptyList<Movie>(), vm.movies.items) {
        value = withContext(Dispatchers.Default) { newestMovies(vm.movies.items) }
    }
    val newSeries by produceState(emptyList<Series>(), vm.series.items) {
        value = withContext(Dispatchers.Default) { newestSeries(vm.series.items) }
    }

    val context = LocalContext.current
    LaunchedEffect(newMovies, newSeries, vm.recents.size) {
        prefetchImages(
            context,
            vm.recents.take(8).map { it.image } +
                newMovies.take(12).map { it.icon } +
                newSeries.take(12).map { it.cover },
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "¿Qué vemos hoy?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.TextPrimary,
                )
                Text(
                    vm.currentAccount?.name ?: "Tu entretenimiento, en un solo lugar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            HeroCard(
                recent = recent,
                fallbackImage = newMovies.firstOrNull()?.icon ?: newSeries.firstOrNull()?.cover,
                onPlay = { if (recent != null) vm.playRecent(recent) else onMovies() },
                onExplore = onMovies,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item {
            QuickAccess(
                onLive = onLive,
                onMovies = onMovies,
                onSeries = onSeries,
                onDownloads = onDownloads,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        if (vm.recents.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Continuar viendo", onContinue)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(vm.recents.take(10), key = { it.key }) { item ->
                            RecentCard(item, onClick = { vm.playRecent(item) })
                        }
                    }
                }
            }
        }

        // Las filas reservan su lugar desde el principio (con esqueletos) para que nada se mueva al cargar.
        if (newMovies.isNotEmpty() || (!vm.movies.loaded && vm.movies.error == null)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Películas recién agregadas", onMovies)
                    if (newMovies.isEmpty()) {
                        ShelfSkeleton()
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(newMovies, key = { it.id }) { m ->
                                HomePoster(
                                    title = m.name,
                                    image = m.icon,
                                    rating = m.rating,
                                    isNew = isRecent(m.added),
                                    onClick = { vm.openMovie(m) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (newSeries.isNotEmpty() || (!vm.series.loaded && vm.series.error == null)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Series recién agregadas", onSeries)
                    if (newSeries.isEmpty()) {
                        ShelfSkeleton()
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(newSeries, key = { it.id }) { sr ->
                                HomePoster(
                                    title = sr.name,
                                    image = sr.cover,
                                    rating = sr.rating,
                                    isNew = isRecent(sr.added),
                                    onClick = { vm.openSeries(sr) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Héroe

@Composable
private fun HeroCard(
    recent: Recent?,
    fallbackImage: String?,
    onPlay: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val image = recent?.image?.takeIf { it.isNotBlank() } ?: fallbackImage
    val progress = recent?.let { r ->
        if (r.durationMs > 0) (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Palette.HeroTop, Palette.HeroMid, Palette.HeroBottom),
                ),
            )
            .border(1.dp, Palette.OutlineSoft, shape),
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.62f,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color(0x66080A0E),
                    1f to Palette.HeroBottom,
                ),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (recent != null) Palette.Accent else Palette.AccentSoft)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (recent == null) "DESTACADO" else "CONTINUAR VIENDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.OnAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (recent?.subtitle?.isNotBlank() == true) {
                    Text(
                        recent.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
            }
            Text(
                recent?.title ?: "Encuentra algo para ver",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null && progress > 0f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = Palette.Accent,
                        trackColor = Color(0x55FFFFFF),
                    )
                    val remaining = ((recent.durationMs - recent.positionMs) / 60_000L).coerceAtLeast(1L)
                    Text(
                        "Quedan $remaining min",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlay,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.Accent,
                        contentColor = Palette.OnAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (recent == null) "Explorar catálogo" else "Continuar",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    if (recent == null) "Películas y series" else "Ver más",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xE6FFFFFF),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable(onClick = onExplore)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Accesos rápidos

@Composable
private fun QuickAccess(
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickCard("En vivo", R.drawable.ic_live, Palette.Live, onLive, Modifier.weight(1f))
        QuickCard("Películas", R.drawable.ic_movies, Palette.Accent, onMovies, Modifier.weight(1f))
        QuickCard("Series", R.drawable.ic_series, Palette.AccentSoft, onSeries, Modifier.weight(1f))
        QuickCard("Descargas", R.drawable.ic_download, Palette.Star, onDownloads, Modifier.weight(1f))
    }
}

@Composable
private fun QuickCard(
    label: String,
    @DrawableRes icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.OutlineSoft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextSecondary,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Secciones

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeeAll)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Palette.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Ver todo",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Accent,
        )
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Palette.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HomePoster(
    title: String,
    image: String?,
    rating: String?,
    isNew: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.width(132.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(198.dp)
                .clip(shape)
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.OutlineSoft, shape),
        ) {
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val score = rating?.toFloatOrNull()?.takeIf { it > 0f }
            if (score != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Palette.ScrimSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("★", style = MaterialTheme.typography.labelSmall, color = Palette.Star)
                    Text(
                        String.format(java.util.Locale.ROOT, "%.1f", score),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            if (isNew) {
                Text(
                    "NUEVO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.OnAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentCard(item: Recent, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else {
        null
    }
    Column(
        modifier = Modifier.width(216.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .clip(shape)
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.OutlineSoft, shape),
        ) {
            if (item.image?.isNotBlank() == true) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Palette.Accent,
                    trackColor = Color(0x55000000),
                )
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.subtitle.ifBlank { "Continuar viendo" },
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShelfSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(198.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.SurfaceHigh.copy(alpha = alpha)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.SurfaceHigh.copy(alpha = alpha)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Utilidades

private fun newestMovies(items: List<Movie>): List<Movie> =
    items.sortedWith(compareByDescending<Movie> { it.added }.thenByDescending { it.id }).take(20)

private fun newestSeries(items: List<Series>): List<Series> =
    items.sortedWith(compareByDescending<Series> { it.added }.thenByDescending { it.id }).take(20)

/** Un elemento es "nuevo" si el proveedor dio fecha y es de los últimos 10 días. */
private fun isRecent(addedSeconds: Long): Boolean {
    if (addedSeconds <= 0L) return false
    val now = System.currentTimeMillis() / 1000
    return now - addedSeconds in 0..(10L * 24 * 60 * 60)
}
