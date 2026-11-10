package com.myboo.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class FieldSpec(val label: String, val password: Boolean = true)

private sealed interface Dialog {
    data object None : Dialog
    data class TwoFa(val enable: Boolean) : Dialog
    data object ChangePassword : Dialog
    data object Delete : Dialog
}

@Composable
fun SettingsScreen(api: Api, prefs: Prefs, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val nb = MaterialTheme.notebook
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<Account?>(null) }
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var name by remember { mutableStateOf(prefs.name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<Dialog>(Dialog.None) }

    BackHandler(onBack = onBack)

    suspend fun reload() {
        runCatching { api.me() }.onSuccess { account = it; name = it.name }.onFailure { error = it.message }
        runCatching { api.sessions() }.onSuccess { sessions = it }
    }
    LaunchedEffect(Unit) { reload() }

    fun finishSignOut() {
        LocationService.stop(ctx)
        onSignedOut()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 44.dp, end = 22.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                TextLink("← back", onClick = onBack)
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { ErrorNote(error); info?.let { Text(it, color = MaterialTheme.colorScheme.secondary) } }

        // profile
        item {
            NoteCard(color = nb.pink, tilt = -0.6f, tape = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name.ifBlank { "?" }, prefs.userId ?: "me", size = 56.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(account?.name ?: name, style = MaterialTheme.typography.titleMedium)
                        Text(account?.email ?: prefs.email ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                NoteField(name, { name = it.take(40) }, "Display name")
                Spacer(Modifier.height(10.dp))
                StickerButton("Save name", fill = nb.butter, enabled = name.isNotBlank() && name.trim() != account?.name, onClick = {
                    scope.launch {
                        runCatching { api.updateName(name.trim()) }
                            .onSuccess { account = it; info = "Saved." ; error = null }
                            .onFailure { error = it.message }
                    }
                })
            }
        }

        // security
        item {
            NoteCard(color = nb.sky, tilt = 0.5f) {
                Text("Sign-in security", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Two-step sign-in", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "We'll email a code whenever you sign in on a new device.",
                            style = MaterialTheme.typography.bodySmall, color = nb.ink.copy(alpha = 0.75f),
                        )
                    }
                    Switch(
                        checked = account?.twofa == true,
                        enabled = account != null,
                        onCheckedChange = { dialog = Dialog.TwoFa(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.secondary,
                            checkedThumbColor = nb.ink,
                            checkedBorderColor = nb.ink,
                            uncheckedBorderColor = nb.ink,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            uncheckedThumbColor = nb.ink,
                        ),
                    )
                }
                Spacer(Modifier.height(14.dp))
                StickerButton("Change password", fill = nb.lavender, onClick = { dialog = Dialog.ChangePassword })
            }
        }

        // devices
        item {
            NoteCard(color = nb.mint, tilt = -0.4f) {
                Text("Signed-in devices", style = MaterialTheme.typography.titleMedium)
                sessions.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.device, style = MaterialTheme.typography.titleSmall)
                            Text("Active ${ago(s.lastUsedAt)}", style = MaterialTheme.typography.bodySmall, color = nb.ink.copy(alpha = 0.7f))
                        }
                        if (s.current) Tag("this device", nb.butter)
                        else TextButton(onClick = {
                            scope.launch { runCatching { api.revokeSession(s.id); sessions = api.sessions() } }
                        }) { Text("sign out", color = nb.ink) }
                    }
                }
            }
        }

        item { VersionCard() }

        // account actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StickerButton("Sign out", fill = nb.butter, onClick = {
                    scope.launch { api.logout(); finishSignOut() }
                })
                StickerButton("Sign out everywhere", fill = nb.peach, onClick = {
                    scope.launch {
                        runCatching { api.logoutAll() }.onSuccess { finishSignOut() }.onFailure { error = it.message }
                    }
                })
                TextButton(onClick = { dialog = Dialog.Delete }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete my account", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    when (val d = dialog) {
        Dialog.None -> Unit
        is Dialog.TwoFa -> FormDialog(
            title = if (d.enable) "Turn on two-step sign-in" else "Turn off two-step sign-in",
            body = "Enter your password to confirm.",
            confirm = if (d.enable) "Turn on" else "Turn off",
            fields = listOf(FieldSpec("Password")),
            onDismiss = { dialog = Dialog.None },
        ) { v ->
            account = api.setTwoFactor(d.enable, v[0])
            info = if (d.enable) "Two-step sign-in is on." else "Two-step sign-in is off."
        }
        Dialog.ChangePassword -> FormDialog(
            title = "Change password",
            body = "Your other devices will be signed out.",
            confirm = "Change",
            fields = listOf(FieldSpec("Current password"), FieldSpec("New password (8+ characters)")),
            onDismiss = { dialog = Dialog.None },
        ) { v ->
            api.changePassword(v[0], v[1])
            sessions = api.sessions()
            info = "Password changed."
        }
        Dialog.Delete -> FormDialog(
            title = "Delete your account?",
            body = "This permanently removes your profile, location, and circle memberships. It can't be undone.",
            confirm = "Delete forever",
            danger = true,
            fields = listOf(FieldSpec("Password")),
            onDismiss = { dialog = Dialog.None },
        ) { v ->
            api.deleteAccount(v[0])
            finishSignOut()
        }
    }
}

@Composable
private fun FormDialog(
    title: String,
    body: String,
    confirm: String,
    fields: List<FieldSpec>,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    onSubmit: suspend (List<String>) -> Unit,
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(fields.size) { add("") } } }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                fields.forEachIndexed { i, f -> NoteField(values[i], { values[i] = it }, f.label, password = f.password) }
                ErrorNote(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && values.all { it.isNotEmpty() }, onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { onSubmit(values.toList()) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message }
                    busy = false
                }
            }) {
                Text(
                    if (busy) "…" else confirm,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.notebook.ink,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = MaterialTheme.notebook.ink) } },
    )
}
