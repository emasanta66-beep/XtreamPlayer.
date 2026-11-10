package app.xtreamplayer.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Verdadero en Android TV o en un televisor con leanback. */
fun Context.isTelevision(): Boolean {
    val pm = packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    ) {
        return true
    }
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return false
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTelevision() }
}

/**
 * Anillo de foco para el D-pad: resalta el elemento y lo agranda un poco.
 * En el teléfono no cambia nada visible salvo que se use un teclado o un mando.
 */
@Composable
fun Modifier.tvFocus(
    shape: Shape,
    scale: Float = 1.035f,
    color: androidx.compose.ui.graphics.Color = Palette.Accent,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        label = "tvFocusScale",
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scaleAnim
            scaleY = scaleAnim
        }
        .then(
            if (focused) Modifier.border(2.dp, color, shape) else Modifier,
        )
}

/** Igual que [tvFocus] pero sin [focusable]: para elementos que ya lo son (p. ej. botones). */
@Composable
fun Modifier.focusHighlight(
    shape: Shape,
    color: androidx.compose.ui.graphics.Color = Palette.Accent,
): Modifier = tvFocus(shape = shape, scale = 1f, color = color).focusable()
