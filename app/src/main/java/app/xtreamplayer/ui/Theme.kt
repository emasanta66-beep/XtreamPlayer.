package app.xtreamplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF00205B),
    primaryContainer = Color(0xFF1F3C80),
    onPrimaryContainer = Color(0xFFD9E5FF),
    secondary = Color(0xFFB4C5F5),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE4E7EE),
    surfaceVariant = Color(0xFF1C222C),
    onSurfaceVariant = Color(0xFFA9B1C1),
    surfaceContainer = Color(0xFF161B23),
    surfaceContainerHigh = Color(0xFF1C222C),
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
