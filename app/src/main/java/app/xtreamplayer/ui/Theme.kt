package app.xtreamplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF39C6A3),
    onPrimary = Color(0xFF06241D),
    primaryContainer = Color(0xFF164B40),
    onPrimaryContainer = Color(0xFFB7F2DF),
    secondary = Color(0xFF72E0BE),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1E222B),
    onSurfaceVariant = Color(0xFF9BA3B4),
    surfaceContainer = Color(0xFF171A21),
    surfaceContainerHigh = Color(0xFF1E222B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
