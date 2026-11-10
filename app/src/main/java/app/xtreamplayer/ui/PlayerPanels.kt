package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.data.Channel
import coil.compose.AsyncImage

/** Panel lateral con los canales de la misma categoría; tocar uno lo reproduce. */
@Composable
internal fun ChannelPanel(
    title: String,
    channels: List<Channel>,
    currentId: Int?,
    onPick: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = channels.indexOfFirst { it.id == currentId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Palette.Surface)
            .displayCutoutPadding()
            .pointerInput(Unit) {} // los toques dentro del panel no lo cierran
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.id }) { ch ->
                val current = ch.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) Palette.Accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { if (current) onClose() else onPick(ch) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ch.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        ch.name,
                        color = if (current) Palette.Accent else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (current) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproduciendo", tint = Palette.Accent)
                    }
                }
            }
        }
    }
}
