package app.xtreamplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel

/** Aviso de "versión nueva disponible" con botón para descargar e instalar. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val info = vm.update ?: return
    val progress = vm.updateProgress
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text("Nueva versión disponible") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(info.name, style = MaterialTheme.typography.titleSmall)
                if (info.sizeBytes > 0) {
                    Text(
                        "%.1f MB".format(info.sizeBytes / 1_048_576.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(info.notes, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Descargando… ${((progress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Android te pedirá confirmar la instalación. Tus cuentas y favoritos se conservan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.installUpdate() }, enabled = !downloading) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdate() }, enabled = !downloading) { Text("Más tarde") }
        },
    )
}
