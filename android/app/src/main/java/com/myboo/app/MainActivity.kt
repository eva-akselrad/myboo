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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                val bg = MaterialTheme.colorScheme.background
                Surface(Modifier.fillMaxSize(), color = bg) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.radialGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), Color.Transparent),
                                radius = 900f,
                            ),
                        ),
                    ) { App() }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val api = remember { Api(prefs) }
    var registered by remember { mutableStateOf(prefs.token != null) }
    var circleId by remember { mutableStateOf(prefs.circleId) }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        when {
            !registered -> Onboarding(api) { registered = true }
            circleId == null -> CircleSetup(api, prefs) { circleId = it }
            else -> CircleScreen(api, prefs, circleId!!) { prefs.circleId = null; circleId = null }
        }
    }
}

// ---------- onboarding ----------

@Composable
private fun Onboarding(api: Api, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        GhostLogo(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("MyBoo", style = MaterialTheme.typography.displaySmall)
        Text(
            "Know your people got home safe.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 36.dp),
        )
        OutlinedTextField(
            name, { name = it.take(40) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            label = { Text("What should your circle call you?") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (busy) "Setting up…" else "Get started", enabled = name.isNotBlank() && !busy) {
            busy = true; error = null
            scope.launch {
                runCatching { api.register(name.trim()) }.onSuccess { onDone() }.onFailure { error = it.message }
                busy = false
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    }
}

// ---------- create / join a circle ----------

@Composable
private fun CircleSetup(api: Api, prefs: Prefs, onPicked: (String) -> Unit) {
    var circleName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { api.myCircles().firstOrNull() }.getOrNull()?.let { prefs.circleId = it.id; onPicked(it.id) }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GhostLogo(48.dp)
            Spacer(Modifier.height(16.dp))
            Text("Find your circle", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Start a new one for your family or friends, or join with an invite code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
        item {
            Card {
                Text("Create a circle", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    circleName, { circleName = it.take(40) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    label = { Text("Name, e.g. Family") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                PrimaryButton("Create circle", enabled = circleName.isNotBlank()) {
                    scope.launch {
                        runCatching { api.createCircle(circleName.trim()) }
                            .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                            .onFailure { error = it.message }
                    }
                }
            }
        }
        item {
            Card {
                Text("Join with a code", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    code, { code = it.uppercase().take(8) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                GhostButton("Join circle", enabled = code.length >= 4) {
                    scope.launch {
                        runCatching { api.joinCircle(code.trim()) }
                            .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                            .onFailure { error = it.message }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface).padding(20.dp),
        content = content,
    )
}

// ---------- circle home ----------

@SuppressLint("MissingPermission")
@Composable
private fun CircleScreen(api: Api, prefs: Prefs, circleId: String, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<CircleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(prefs.sharing) }
    var showAddPlace by remember { mutableStateOf(false) }

    fun hasPerm(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun startSharing() { LocationService.start(ctx); prefs.sharing = true; sharing = true }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startSharing() }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            error = "Location permission is required to share."; return@rememberLauncherForActivityResult
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
            GhostLogo(56.dp)
            Text(error ?: "Finding your circle…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
        return
    }

    AvatarPalette.order = d.members.map { it.id }.sorted()

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostLogo(34.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(d.circle.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${d.members.size} ${if (d.members.size == 1) "person" else "people"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onLeave) { Text("Switch", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        // radar
        item { Radar(d.members, d.places, Modifier.fillMaxWidth().height(300.dp)) }

        // sharing
        item {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Share my location", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (sharing) "Your circle can see you" else "Off — nobody can see you",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sharing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sharing, onCheckedChange = ::toggleSharing,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.secondary,
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                )
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        // people
        item { SectionTitle("People") }
        items(d.members, key = { it.id }) { m ->
            val here = d.places.filter { it.id in m.insidePlaceIds }.joinToString { it.name }
            val (label, color) = when {
                m.lat == null -> "No location yet" to MaterialTheme.colorScheme.onSurfaceVariant
                m.driving -> ("Driving" + (speedLabel(m.speed)?.let { " · $it" } ?: "")) to MaterialTheme.colorScheme.primary
                here.isNotEmpty() -> "At $here" to MaterialTheme.colorScheme.secondary
                else -> "Away" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = m.lat != null) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.name)})")))
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(m.name, m.id)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(m.name + if (m.id == prefs.userId) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Updated ${ago(m.ts)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(label, color)
            }
        }
        item {
            GhostButton("Invite with code ${d.circle.inviteCode}") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my circle \"${d.circle.name}\" on MyBoo. Invite code: ${d.circle.inviteCode}")
                }
                ctx.startActivity(Intent.createChooser(send, "Invite to ${d.circle.name}"))
            }
        }

        // places
        item { SectionTitle("Places") }
        if (d.places.isEmpty()) {
            item {
                Text(
                    "Add Home, School or Work to get an alert when someone arrives or leaves.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(d.places, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${p.radiusM.toInt()} m radius", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    scope.launch { runCatching { api.deletePlace(circleId, p.id); detail = api.circleDetail(circleId) } }
                }) { Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { PrimaryButton("Add a place here") { showAddPlace = true } }
    }

    if (showAddPlace) {
        var placeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPlace = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("New place") },
            text = {
                Column {
                    Text("Saved at your current location.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        placeName, { placeName = it.take(40) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                        label = { Text("Home, School, Work…") }, modifier = Modifier.padding(top = 12.dp),
                    )
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
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddPlace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}
