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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
import app.xtreamplayer.data.DlStatus
import app.xtreamplayer.data.DownloadItem

/**
 * Botón redondo de descarga: flecha para bajar, anillo con avance mientras baja
 * (tocar = pausar), triángulo si está en pausa o falló (tocar = reanudar) y tilde si ya está lista.
 */
@Composable
fun DownloadBadge(
    d: DownloadItem?,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Etiqueta única para TalkBack: uno de estos títulos se lee al enfocar el botón,
    // junto a la acción de tocar (descargar, pausar o reanudar).
    val label = when (d?.status) {
        null -> stringResource(R.string.common_download)
        DlStatus.DONE -> stringResource(R.string.downloads_downloaded)
        DlStatus.DOWNLOADING, DlStatus.QUEUED -> stringResource(R.string.downloads_pause)
        DlStatus.PAUSED, DlStatus.ERROR -> stringResource(R.string.downloads_resume)
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.ScrimSoft)
            .semantics { contentDescription = label }
            .clickable {
                when (d?.status) {
                    null -> onStart()
                    DlStatus.DONE -> {}
                    else -> onToggle()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (d?.status) {
            null -> Text("⬇", color = Color.White, fontSize = 16.sp)
            DlStatus.DONE -> Icon(
                Icons.Default.Check,
                contentDescription = null, // ya lo anuncia la etiqueta del contenedor
                tint = Palette.Success,
                modifier = Modifier.size(20.dp),
            )
            DlStatus.DOWNLOADING -> {
                if (d.total > 0) {
                    CircularProgressIndicator(
                        progress = { (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) },
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                }
            }
            DlStatus.QUEUED -> CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 3.dp,
                color = Color.LightGray,
            )
            DlStatus.PAUSED, DlStatus.ERROR -> Icon(
                Icons.Default.PlayArrow,
                contentDescription = null, // ya lo anuncia la etiqueta del contenedor
                tint = if (d.status == DlStatus.ERROR) Palette.Error else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun statusText(d: DownloadItem): String = when (d.status) {
    DlStatus.QUEUED -> stringResource(R.string.downloads_queued)
    DlStatus.DOWNLOADING ->
        if (d.total > 0) {
            stringResource(
                R.string.downloads_progress,
                (d.bytes * 100 / d.total).coerceIn(0, 100),
                formatMb(d.bytes),
                formatMb(d.total),
            )
        } else {
            stringResource(R.string.downloads_progress_simple, formatMb(d.bytes))
        }
    DlStatus.PAUSED -> stringResource(R.string.downloads_paused, formatMb(d.bytes))
    DlStatus.DONE -> stringResource(R.string.downloads_ready, formatMb(d.bytes))
    DlStatus.ERROR -> stringResource(
        R.string.downloads_error,
        d.error ?: stringResource(R.string.common_unknown_error),
    )
}

@Composable
fun DownloadsTab(vm: AppViewModel, query: String, modifier: Modifier) {
    val q = query.trim()
    val accId = vm.currentAccount?.id
    val all = vm.downloads.filter { it.accountId == accId }
    val list = all.filter {
        q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
    }.sortedWith(compareBy({ it.title.lowercase() }, { it.subtitle }))
    if (list.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (all.isEmpty()) {
                    stringResource(R.string.downloads_empty)
                } else {
                    stringResource(R.string.common_no_results)
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(list, key = { it.key }) { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (d.status == DlStatus.DONE) vm.playDownload(d) else vm.toggleDownload(d.key)
                    }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumb(image = d.image, progress = null, modifier = Modifier.width(130.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (d.subtitle.isNotEmpty()) {
                        Text(
                            d.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        statusText(d),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d.status == DlStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (d.status == DlStatus.DOWNLOADING && d.total > 0) {
                        LinearProgressIndicator(
                            progress = { (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.deleteDownload(d.key) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.downloads_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
