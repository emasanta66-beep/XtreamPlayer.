package app.xtreamplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xtreamplayer.R
import app.xtreamplayer.data.EpgItem
import java.text.SimpleDateFormat

/**
 * Barra superior del reproductor: volver, canales, título con EPG, resolución y menú.
 * Es solo presentación; todo el estado vive en [PlayerScreen].
 */
@Composable
internal fun PlayerTopBar(
    title: String,
    isLive: Boolean,
    hasChannels: Boolean,
    epg: List<EpgItem>,
    timeFormat: SimpleDateFormat,
    videoHeight: Int,
    videoModeLabel: String,
    audioAvailable: Boolean,
    casting: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onChannels: () -> Unit,
    onQuality: () -> Unit,
    onTracks: () -> Unit,
    onCycleVideoMode: () -> Unit,
    onCast: () -> Unit,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .displayCutoutPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
            )
        }
        if (isLive && hasChannels) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp, end = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x66000000))
                    .clickable {
                        onPoke()
                        onChannels()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_channels), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val now = System.currentTimeMillis()
            val current = epg.firstOrNull { now in it.start..it.end } ?: epg.firstOrNull()
            val next = current?.let { c -> epg.firstOrNull { it.start >= c.end } }
            if (current != null) {
                Text(
                    stringResource(
                        R.string.player_epg_now,
                        current.title,
                        timeFormat.format(current.start),
                        timeFormat.format(current.end),
                    ),
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null) {
                Text(
                    stringResource(R.string.player_epg_next, next.title, timeFormat.format(next.start)),
                    color = Color(0x99FFFFFF),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (videoHeight > 0) {
            Text(
                resolutionLabel(videoHeight),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 10.dp, end = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .clickable {
                        onPoke()
                        onQuality()
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        IconButton(onClick = {
            onPoke()
            onQuality()
        }) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.player_quality), tint = Color.White)
        }
        Box {
            IconButton(onClick = {
                onPoke()
                onMenuOpenChange(true)
            }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more), tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                if (audioAvailable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_audio_subs)) },
                        onClick = {
                            onMenuOpenChange(false)
                            onTracks()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_quality)) },
                    onClick = {
                        onMenuOpenChange(false)
                        onQuality()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_image, videoModeLabel)) },
                    onClick = {
                        onMenuOpenChange(false)
                        onCycleVideoMode()
                    },
                )
                if (!isLive) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (casting) R.string.player_cast_short else R.string.player_cast,
                                ),
                            )
                        },
                        enabled = !casting,
                        onClick = {
                            onMenuOpenChange(false)
                            onCast()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Barra inferior: transporte, progreso, volumen, subtítulos, velocidad, PiP.
 * En pantallas anchas se coloca en una sola fila; en teléfono, en tres.
 */
@Composable
internal fun PlayerBottomBar(
    isLive: Boolean,
    isPlaying: Boolean,
    hasPrevChannel: Boolean,
    hasNextChannel: Boolean,
    hasNextEpisode: Boolean,
    hasTracks: Boolean,
    supportsPip: Boolean,
    muted: Boolean,
    positionMs: Long,
    durationMs: Long,
    seeking: Boolean,
    seekValue: Float,
    speedLabel: String,
    onPrevChannel: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onMute: () -> Unit,
    onTracks: () -> Unit,
    onCycleSpeed: () -> Unit,
    onPip: () -> Unit,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val transport: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasPrevChannel) {
                CtlButton({
                    onPoke()
                    onPrevChannel()
                }) { GlyphIcon(Glyph.PREV) }
            }
            CtlButton({
                onPoke()
                onPlayPause()
            }) { GlyphIcon(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, size = 30) }
            if (!isLive) {
                CtlButton({ onSeek(-10_000L) }) { GlyphIcon(Glyph.BACK10) }
                CtlButton({ onSeek(10_000L) }) { GlyphIcon(Glyph.FWD10) }
            }
            if (hasNextChannel || hasNextEpisode) {
                CtlButton({
                    onPoke()
                    onNext()
                }) { GlyphIcon(Glyph.NEXT) }
            }
        }
    }

    val seekArea: @Composable (Modifier) -> Unit = { m ->
        if (isLive) {
            Row(m.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_live), color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(m) {
                val dur = durationMs.coerceAtLeast(1L).toFloat()
                val shown = if (seeking) seekValue else positionMs.toFloat()
                Slider(
                    value = shown.coerceIn(0f, dur),
                    onValueChange = {
                        onSeekChange(it)
                        onPoke()
                    },
                    onValueChangeFinished = onSeekFinished,
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0x55FFFFFF),
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatTime(shown.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(durationMs), color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    val tools: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtlButton({
                onPoke()
                onMute()
            }) { GlyphIcon(if (muted) Glyph.MUTE else Glyph.VOLUME) }
            if (hasTracks) {
                CtlButton({
                    onPoke()
                    onTracks()
                }) { GlyphIcon(Glyph.SUBS) }
            }
            if (!isLive) {
                CtlButton({
                    onPoke()
                    onCycleSpeed()
                }) {
                    Text(speedLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (supportsPip) CtlButton({ onPip() }) { GlyphIcon(Glyph.PIP) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .displayCutoutPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Scrim)
            .pointerInput(Unit) { detectTapGestures { } } // los toques en la barra no la ocultan
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                transport()
                seekArea(Modifier.weight(1f).padding(horizontal = 8.dp))
                tools()
            }
        } else {
            seekArea(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { transport() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { tools() }
        }
    }
}
