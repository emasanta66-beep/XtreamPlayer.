package app.xtreamplayer.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Tracks
import app.xtreamplayer.R
import kotlin.math.cos
import kotlin.math.sin

/** Iconos del reproductor, dibujados a mano (el set "core" de Material no trae pausa, volumen, etc.). */
enum class Glyph { PAUSE, PLAY, BACK10, FWD10, NEXT, PREV, VOLUME, MUTE, SUBS, PIP, SCREEN }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = Color.White, size: Int = 26) {
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val s = this.size.minDimension / 24f
            when (glyph) {
                Glyph.PAUSE -> {
                    drawRoundRect(tint, Offset(5 * s, 4 * s), Size(5 * s, 16 * s), CornerRadius(1.2f * s))
                    drawRoundRect(tint, Offset(14 * s, 4 * s), Size(5 * s, 16 * s), CornerRadius(1.2f * s))
                }
                Glyph.PLAY -> drawTriangle(tint, s, 6f, 4f, 20f, 12f, 6f, 20f)
                Glyph.NEXT -> {
                    drawTriangle(tint, s, 5f, 5f, 16f, 12f, 5f, 19f)
                    drawRect(tint, Offset(17.5f * s, 5 * s), Size(2.2f * s, 14 * s))
                }
                Glyph.PREV -> {
                    drawTriangle(tint, s, 19f, 5f, 8f, 12f, 19f, 19f)
                    drawRect(tint, Offset(4.3f * s, 5 * s), Size(2.2f * s, 14 * s))
                }
                Glyph.BACK10 -> drawCircularArrow(tint, s, forward = false)
                Glyph.FWD10 -> drawCircularArrow(tint, s, forward = true)
                Glyph.VOLUME, Glyph.MUTE -> {
                    val p = Path().apply {
                        moveTo(3 * s, 9 * s); lineTo(7 * s, 9 * s); lineTo(12 * s, 5 * s)
                        lineTo(12 * s, 19 * s); lineTo(7 * s, 15 * s); lineTo(3 * s, 15 * s); close()
                    }
                    drawPath(p, tint)
                    if (glyph == Glyph.VOLUME) {
                        val st = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
                        drawArc(tint, -50f, 100f, false, Offset(8 * s, 8 * s), Size(8 * s, 8 * s), style = st)
                        drawArc(tint, -50f, 100f, false, Offset(5 * s, 5 * s), Size(14 * s, 14 * s), style = st)
                    } else {
                        val st = 1.8f * s
                        drawLine(tint, Offset(15 * s, 9 * s), Offset(21 * s, 15 * s), st, StrokeCap.Round)
                        drawLine(tint, Offset(21 * s, 9 * s), Offset(15 * s, 15 * s), st, StrokeCap.Round)
                    }
                }
                Glyph.SUBS -> {
                    drawRoundRect(tint, Offset(2 * s, 5 * s), Size(20 * s, 14 * s), CornerRadius(2.5f * s), Stroke(1.8f * s))
                    val w = 1.8f * s
                    drawLine(tint, Offset(5.5f * s, 10 * s), Offset(10 * s, 10 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(12.5f * s, 10 * s), Offset(18.5f * s, 10 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(5.5f * s, 14 * s), Offset(14 * s, 14 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(16.5f * s, 14 * s), Offset(18.5f * s, 14 * s), w, StrokeCap.Round)
                }
                Glyph.PIP -> {
                    drawRoundRect(tint, Offset(2 * s, 4 * s), Size(20 * s, 16 * s), CornerRadius(2.5f * s), Stroke(1.8f * s))
                    drawRoundRect(tint, Offset(12 * s, 11 * s), Size(8 * s, 6 * s), CornerRadius(1.2f * s))
                }
                Glyph.SCREEN -> {
                    val w = 2f * s
                    // Cuatro esquinas (pantalla completa / girar).
                    drawLine(tint, Offset(4 * s, 9 * s), Offset(4 * s, 4 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(4 * s, 4 * s), Offset(9 * s, 4 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(15 * s, 4 * s), Offset(20 * s, 4 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(20 * s, 4 * s), Offset(20 * s, 9 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(4 * s, 15 * s), Offset(4 * s, 20 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(4 * s, 20 * s), Offset(9 * s, 20 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(20 * s, 15 * s), Offset(20 * s, 20 * s), w, StrokeCap.Round)
                    drawLine(tint, Offset(15 * s, 20 * s), Offset(20 * s, 20 * s), w, StrokeCap.Round)
                }
            }
        }
        if (glyph == Glyph.BACK10 || glyph == Glyph.FWD10) {
            Text("10", color = tint, fontSize = (size * 0.34f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = (size * 0.12f).dp))
        }
    }
}

private fun DrawScope.drawTriangle(
    color: Color, s: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
) {
    val p = Path().apply {
        moveTo(x1 * s, y1 * s); lineTo(x2 * s, y2 * s); lineTo(x3 * s, y3 * s); close()
    }
    drawPath(p, color)
}

/** Flecha circular (como "retroceder/avanzar 10 s"); `forward` = sentido de las agujas del reloj. */
private fun DrawScope.drawCircularArrow(color: Color, s: Float, forward: Boolean) {
    val cx = 12f
    val cy = 13f
    val r = 8f
    val end = if (forward) -75f else -105f // donde termina el arco y va la punta
    val start = if (forward) -15f else -165f
    val sweep = if (forward) 300f else -300f
    drawArc(
        color, start, sweep, false,
        Offset((cx - r) * s, (cy - r) * s), Size(2 * r * s, 2 * r * s),
        style = Stroke(width = 1.8f * s, cap = StrokeCap.Round),
    )
    val a = Math.toRadians(end.toDouble())
    val px = cx + r * cos(a).toFloat()
    val py = cy + r * sin(a).toFloat()
    val sign = if (forward) 1f else -1f
    val dx = sign * (-sin(a).toFloat())
    val dy = sign * cos(a).toFloat()
    val nx = -dy
    val ny = dx
    val p = Path().apply {
        moveTo(px + dx * 3f, py + dy * 3f)
        lineTo(px - dx * 1f + nx * 3f, py - dy * 1f + ny * 3f)
        lineTo(px - dx * 1f - nx * 3f, py - dy * 1f - ny * 3f)
        close()
    }
    drawPath(p, color)
}

/** Una calidad de video elegible (por ejemplo una variante 720p de un canal HLS). */
class QualityOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

/** Variantes de video que el dispositivo puede reproducir, de mayor a menor resolución. */
fun Tracks.qualityOptions(context: Context): List<QualityOption> {
    val out = ArrayList<QualityOption>()
    for (g in groups) {
        if (g.type != androidx.media3.common.C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val h = f.height
            val name = when {
                h > 0 -> "${h}p"
                else -> context.getString(R.string.player_quality_variant, out.size + 1)
            }
            val rate = if (f.bitrate > 0) " · %.1f Mbps".format(f.bitrate / 1_000_000f) else ""
            out.add(QualityOption(name + rate, g, i, g.isTrackSelected(i)))
        }
    }
    return out.sortedByDescending { it.group.getTrackFormat(it.index).height }
}

@Composable
fun QualityDialog(
    info: String,
    options: List<QualityOption>,
    auto: Boolean,
    onAuto: () -> Unit,
    onPick: (QualityOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_quality)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.player_playing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(info, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.player_choose_quality),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (options.size < 2) {
                    Text(
                        stringResource(R.string.player_single_quality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    QChoice(stringResource(R.string.player_auto), auto, onAuto)
                    options.forEach { o -> QChoice(o.label, !auto && o.selected) { onPick(o) } }
                }
            }
        },
    )
}

@Composable
private fun QChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón circular de la barra de controles del reproductor.
 *
 *  Los iconos se dibujan a mano (Canvas + Text), así que TalkBack no sabe qué hacen:
 *  [desc] lleva la etiqueta de accesibilidad ("Pausar", "Retroceder 10 segundos", ...). */
@Composable
internal fun CtlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .then(if (desc != null) Modifier.semantics { contentDescription = desc } else Modifier)
            .tvFocus(CircleShape, scale = 1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
