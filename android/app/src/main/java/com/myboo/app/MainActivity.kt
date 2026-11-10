package com.myboo.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocationService.ensureChannels(this)
        setContent {
            MyBooTheme {
                NotebookBackground { App() }
            }
        }
    }
}

private enum class Screen { Home, Settings }

@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val api = remember { Api(prefs) }
    var signedIn by remember { mutableStateOf(prefs.token != null) }
    var circleId by remember { mutableStateOf(prefs.circleId) }
    var screen by remember { mutableStateOf(Screen.Home) }

    fun signedOut() { signedIn = false; circleId = null; screen = Screen.Home }

    // Server says our session is gone (signed out from another device, password reset, ...).
    DisposableEffect(Unit) {
        Api.onSignedOut = { LocationService.stop(ctx); signedOut() }
        onDispose { Api.onSignedOut = null }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        when {
            !signedIn -> AuthFlow(api) { signedIn = true; circleId = prefs.circleId }
            screen == Screen.Settings -> SettingsScreen(api, prefs, onBack = { screen = Screen.Home }, onSignedOut = ::signedOut)
            circleId == null -> CircleSetup(api, prefs, onOpenSettings = { screen = Screen.Settings }) { circleId = it }
            else -> CircleScreen(api, prefs, circleId!!, onSettings = { screen = Screen.Settings }) {
                prefs.circleId = null; circleId = null
            }
        }
        if (signedIn) UpdateBanner(Modifier.align(Alignment.BottomCenter))
    }
}

// ---------- create / join a circle ----------

@Composable
private fun CircleSetup(api: Api, prefs: Prefs, onOpenSettings: () -> Unit, onPicked: (String) -> Unit) {
    val nb = MaterialTheme.notebook
    var circleName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Signed in on a new phone? Reuse the circle you already belong to.
    LaunchedEffect(Unit) {
        runCatching { api.myCircles().firstOrNull() }.getOrNull()?.let { prefs.circleId = it.id; onPicked(it.id) }
        loading = false
    }
    if (loading) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GhostLogo(64.dp)
            Text("Opening your notebook…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.imePadding(),
        contentPadding = PaddingValues(start = 44.dp, end = 22.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostLogo(48.dp)
                Spacer(Modifier.weight(1f))
                TextLink("account", onClick = onOpenSettings)
                Box(Modifier.clickable(onClick = onOpenSettings)) { Avatar(prefs.name ?: "?", prefs.userId ?: "me", 42.dp) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Start your circle", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A circle is the group you share with: family, roommates, best friends.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            NoteCard(color = nb.pink, tilt = -0.7f, tape = true) {
                Text("Create a circle", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                NoteField(circleName, { circleName = it.take(40) }, "Name, e.g. Family")
                Spacer(Modifier.height(12.dp))
                StickerButton("Create circle", fill = nb.butter, enabled = circleName.isNotBlank(), onClick = {
                    scope.launch {
                        runCatching { api.createCircle(circleName.trim()) }
                            .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                            .onFailure { error = it.message }
                    }
                })
            }
        }
        item {
            NoteCard(color = nb.sky, tilt = 0.6f, tape = true) {
                Text("Join with a code", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                NoteField(code, { code = it.uppercase().take(8) }, "Invite code")
                Spacer(Modifier.height(12.dp))
                StickerButton("Join circle", fill = nb.mint, enabled = code.length >= 4, onClick = {
                    scope.launch {
                        runCatching { api.joinCircle(code.trim()) }
                            .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                            .onFailure { error = it.message }
                    }
                })
            }
        }
        error?.let { item { ErrorNote(it) } }
    }
}

// ---------- circle home ----------

@SuppressLint("MissingPermission")
@Composable
private fun CircleScreen(api: Api, prefs: Prefs, circleId: String, onSettings: () -> Unit, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    val nb = MaterialTheme.notebook
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<CircleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(prefs.sharing) }
    var showAddPlace by remember { mutableStateOf(false) }

    fun hasPerm(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun startSharing() { LocationService.start(ctx); prefs.sharing = true; sharing = true }

    // Android 10+: background location is requested separately, after foreground.
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startSharing() }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            error = "Location permission is needed to share."; return@rememberLauncherForActivityResult
        }
        if (Build.VERSION.SDK_INT >= 29 && !hasPerm(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else startSharing()
    }

    fun toggleSharing(on: Boolean) {
        if (!on) { LocationService.stop(ctx); prefs.sharing = false; sharing = false; return }
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        fgLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        runCatching { api.setFcmToken(FirebaseMessaging.getInstance().token.await()) }
        while (true) {
            runCatching { api.circleDetail(circleId) }
                .onSuccess { detail = it; error = null }
                .onFailure { error = it.message }
            delay(15_000)
        }
    }

    val d = detail
    if (d == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GhostLogo(64.dp)
            Text(error ?: "Finding your people…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }
    AvatarPalette.order = d.members.map { it.id }.sorted()

    LazyColumn(
        contentPadding = PaddingValues(start = 44.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostLogo(40.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(d.circle.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${d.members.size} ${if (d.members.size == 1) "person" else "people"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextLink("switch", onClick = onLeave)
                Box(Modifier.clickable(onClick = onSettings)) { Avatar(prefs.name ?: "?", prefs.userId ?: "me", 42.dp) }
            }
        }

        // live map
        item { LiveMap(d.members, d.places, Modifier.fillMaxWidth().height(340.dp).padding(end = 6.dp, bottom = 6.dp)) }

        // sharing
        item {
            NoteCard(color = if (sharing) nb.mint else nb.butter, tilt = 0.4f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Share my location", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (sharing) "Your circle can see where you are." else "Off, so nobody can see you right now.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = sharing, onCheckedChange = ::toggleSharing,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.secondary,
                            checkedThumbColor = nb.ink, checkedBorderColor = nb.ink,
                            uncheckedBorderColor = nb.ink, uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            uncheckedThumbColor = nb.ink,
                        ),
                    )
                }
            }
        }
        error?.let { item { ErrorNote(it) } }

        // people
        item { SectionTitle("People") }
        items(d.members, key = { it.id }) { m ->
            val here = d.places.filter { it.id in m.insidePlaceIds }.joinToString { it.name }
            val (label, tint) = when {
                m.lat == null -> "no location yet" to MaterialTheme.colorScheme.surfaceVariant
                m.driving -> ("driving" + (speedLabel(m.speed)?.let { " · $it" } ?: "")) to nb.peach
                here.isNotEmpty() -> "at $here" to nb.mint
                else -> "out and about" to nb.lavender
            }
            Box(Modifier.padding(end = 4.dp, bottom = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().sticker(MaterialTheme.colorScheme.surface, radius = 18.dp, shadow = 4.dp)
                        .clickable(enabled = m.lat != null) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.name)})")))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m.name, m.id)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(m.name + if (m.id == prefs.userId) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                        Text("updated ${ago(m.ts)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Tag(label, tint)
                }
            }
        }
        item {
            StickerButton("Invite · code ${d.circle.inviteCode}", fill = nb.sky, onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my circle \"${d.circle.name}\" on MyBoo. Invite code: ${d.circle.inviteCode}")
                }
                ctx.startActivity(Intent.createChooser(send, "Invite to ${d.circle.name}"))
            })
        }

        // places
        item { SectionTitle("Places") }
        if (d.places.isEmpty()) {
            item {
                Text(
                    "Add Home, School or Work to get a note when someone arrives or leaves.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(d.places, key = { it.id }) { p ->
            Box(Modifier.padding(end = 4.dp, bottom = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().sticker(nb.mint, radius = 16.dp, shadow = 3.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("${p.radiusM.toInt()} m radius", style = MaterialTheme.typography.bodySmall, color = nb.ink.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = {
                        scope.launch { runCatching { api.deletePlace(circleId, p.id); detail = api.circleDetail(circleId) } }
                    }) { Text("remove", color = nb.ink) }
                }
            }
        }
        item { StickerButton("Add a place here", onClick = { showAddPlace = true }) }
    }

    if (showAddPlace) {
        var placeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPlace = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("New place", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Saved at your current location.", style = MaterialTheme.typography.bodyMedium)
                    NoteField(placeName, { placeName = it.take(40) }, "Home, School, Work…")
                }
            },
            confirmButton = {
                TextButton(enabled = placeName.isNotBlank(), onClick = {
                    showAddPlace = false
                    scope.launch {
                        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            error = "Turn on location sharing first so MyBoo can find where you are."; return@launch
                        }
                        runCatching {
                            val loc = LocationServices.getFusedLocationProviderClient(ctx)
                                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                                ?: throw IllegalStateException("Couldn't get a location fix")
                            api.addPlace(circleId, placeName.trim(), loc.latitude, loc.longitude, 150.0)
                            detail = api.circleDetail(circleId)
                        }.onFailure { error = it.message }
                    }
                }) { Text("Save", color = nb.ink, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { showAddPlace = false }) { Text("Cancel", color = nb.ink) } },
        )
    }
}
