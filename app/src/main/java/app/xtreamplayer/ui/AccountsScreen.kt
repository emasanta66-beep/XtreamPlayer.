@file:OptIn(ExperimentalMaterial3Api::class)

package app.xtreamplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xtreamplayer.AppViewModel
import app.xtreamplayer.R
import app.xtreamplayer.data.Account
import app.xtreamplayer.data.XtreamApi

@Composable
fun AccountsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    // Si se llega desde ⋮ → "Añadir lista", abre el diálogo automáticamente.
    LaunchedEffect(vm.pendingAddAccount) {
        if (vm.pendingAddAccount) {
            editing = null
            dialogOpen = true
            vm.consumeAddAccountRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (vm.stack.size > 1) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                dialogOpen = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add))
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.accounts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.accounts_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.accounts_empty_body),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.accounts, key = { it.id }) { acc ->
                        Card(onClick = { vm.openAccount(acc) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        acc.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.accounts_line,
                                            acc.username,
                                            acc.server.removePrefix("http://").removePrefix("https://"),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = {
                                    editing = acc
                                    dialogOpen = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.accounts_edit))
                                }
                                IconButton(onClick = { deleting = acc }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                }
                            }
                        }
                    }
                }
            }
            if (vm.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (dialogOpen) {
        AccountDialog(
            initial = editing,
            busy = vm.busy,
            onDismiss = { dialogOpen = false },
            onSave = { name, server, user, pass ->
                vm.saveAccount(editing?.id, name, server, user, pass) { dialogOpen = false }
            },
        )
    }

    deleting?.let { acc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.accounts_delete_title)) },
            text = { Text(stringResource(R.string.accounts_delete_message, acc.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccount(acc)
                    deleting = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun AccountDialog(
    initial: Account?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, server: String, user: String, pass: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    val usesInsecureHttp = server.trim().let {
        it.startsWith("http://", ignoreCase = true) || (it.isNotEmpty() && !it.contains("://"))
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.accounts_add_title else R.string.accounts_edit_title,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { raw ->
                        // Si pegan la URL completa de la lista (get.php?username=…&password=…),
                        // se rellena el servidor limpio y el usuario/contraseña solos.
                        val parsed = XtreamApi.parseListUrl(raw)
                        if (parsed != null) {
                            server = parsed.first
                            user = parsed.second
                            pass = parsed.third
                        } else {
                            server = raw
                        }
                    },
                    label = { Text(stringResource(R.string.accounts_server)) },
                    placeholder = { Text("http://servidor.com:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (usesInsecureHttp) {
                    Text(
                        stringResource(R.string.accounts_insecure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.accounts_user)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.accounts_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.accounts_url_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSave(name, server, user, pass) }) {
                Text(
                    stringResource(
                        if (busy) R.string.accounts_connecting else R.string.common_save,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
