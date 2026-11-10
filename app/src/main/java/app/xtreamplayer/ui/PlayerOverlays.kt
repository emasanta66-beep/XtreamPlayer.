package app.xtreamplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Capas que se dibujan encima del video: avisos de gesto, buffering, errores y estado de DLNA.
 * No contienen estado propio: reciben todo desde [PlayerScreen].
 */

/** Aviso de −10 s / +10 s tras un doble toque. */
@Composable
internal fun PlayerSeekFlash(deltaMs: Long, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = deltaMs != 0L,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Scrim)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (deltaMs < 0) "−10 s" else "+10 s",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Pastilla central con el nivel de brillo o volumen mientras se arrastra el dedo. */
@Composable
internal fun PlayerGestureHint(
    visible: Boolean,
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Scrim)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.width(150.dp).height(4.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}

@Composable
internal fun PlayerBuffering(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    CircularProgressIndicator(
        modifier = modifier.size(44.dp),
        color = Color.White,
        strokeWidth = 3.dp,
    )
}

/** Tarjeta de error con reintento (prueba el formato alternativo) y salida. */
@Composable
internal fun PlayerErrorCard(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Palette.Scrim, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Reintentar") }
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }
    }
}

/** Aviso mientras se envía el contenido a la TV. */
@Composable
internal fun PlayerCastBusyCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.Scrim, RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Text("Enviando a TV…", color = Color.White)
    }
}

/** Mientras la TV reproduce, el teléfono queda como mando y se oculta el video local. */
@Composable
internal fun PlayerCastingCard(
    deviceName: String,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .displayCutoutPadding()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Reproduciendo en TV",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            deviceName,
            color = Palette.AccentSoft,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Usa el mando de la TV para pausar o cambiar el volumen. Si vuelves atrás, la TV seguirá reproduciendo.",
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStop) { Text("Detener TV") }
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }
    }
}
