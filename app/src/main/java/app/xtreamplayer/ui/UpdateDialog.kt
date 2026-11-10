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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R

/** Aviso de "versión nueva disponible" con botón para descargar e instalar. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val info = vm.update ?: return
    val progress = vm.updateProgress
    val downloading = progress != null

    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text(stringResource(R.string.update_new_available)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(info.name, style = MaterialTheme.typography.titleSmall)
                if (info.sizeBytes > 0) {
                    Text(
                        stringResource(R.string.update_size, (info.sizeBytes / 1_048_576.0).toFloat()),
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
                        stringResource(R.string.update_downloading, ((progress ?: 0f) * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        stringResource(R.string.update_install_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.installUpdate() }, enabled = !downloading) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdate() }, enabled = !downloading) { Text(stringResource(R.string.update_later)) }
        },
    )
}
