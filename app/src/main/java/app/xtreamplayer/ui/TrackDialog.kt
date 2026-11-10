package app.xtreamplayer.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import app.xtreamplayer.R
import java.util.Locale

/** Una pista de audio o de subtítulos que el usuario puede elegir. */
data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

/** Lista las pistas de un tipo (C.TRACK_TYPE_AUDIO o C.TRACK_TYPE_TEXT) que el dispositivo puede reproducir. */
fun Tracks.options(type: Int, context: Context): List<TrackOption> {
    val out = ArrayList<TrackOption>()
    var n = 0
    for (g in groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            n++
            out.add(TrackOption(g, i, trackLabel(g.getTrackFormat(i), n, type, context), g.isTrackSelected(i)))
        }
    }
    return out
}

private fun trackLabel(f: Format, n: Int, type: Int, context: Context): String {
    val lang = f.language
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
    val name = f.label?.takeIf { it.isNotBlank() }
    val generic = if (type == C.TRACK_TYPE_AUDIO) {
        context.getString(R.string.track_audio, n)
    } else {
        context.getString(R.string.track_subtitle, n)
    }
    val base = when {
        name != null && lang != null && !name.contains(lang, ignoreCase = true) ->
            context.getString(R.string.track_name, name, lang)
        name != null -> name
        lang != null -> lang
        else -> generic
    }
    val channels = when (f.channelCount) {
        6 -> " · 5.1"
        8 -> " · 7.1"
        else -> ""
    }
    return if (type == C.TRACK_TYPE_AUDIO) base + channels else base
}

@Composable
fun TrackDialog(
    audio: List<TrackOption>,
    text: List<TrackOption>,
    onAudio: (TrackOption) -> Unit,
    onText: (TrackOption?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_audio_subs)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.track_language_audio))
                if (audio.isEmpty()) {
                    Hint(stringResource(R.string.track_single_audio))
                } else {
                    audio.forEach { o -> Choice(o.label, o.selected) { onAudio(o) } }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(stringResource(R.string.track_subtitles))
                if (text.isEmpty()) {
                    Hint(stringResource(R.string.track_no_subtitles))
                } else {
                    Choice(stringResource(R.string.track_off), text.none { it.selected }) { onText(null) }
                    text.forEach { o -> Choice(o.label, o.selected) { onText(o) } }
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
