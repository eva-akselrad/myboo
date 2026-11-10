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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocationService.ensureChannels(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App() }
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

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("MyBoo", style = MaterialTheme.typography.headlineMedium)
        when {
            !registered -> Onboarding(api) { registered = true }
            circleId == null -> CircleSetup(api, prefs) { circleId = it }
            else -> CircleScreen(api, prefs, circleId!!) { prefs.circleId = null; circleId = null }
        }
    }
}

@Composable
private fun Onboarding(api: Api, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
        Text("What should your family call you?")
        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = name.isNotBlank() && !busy, onClick = {
            busy = true; error = null
            scope.launch {
                runCatching { api.register(name.trim()) }
                    .onSuccess { onDone() }
                    .onFailure { error = it.message }
                busy = false
            }
        }) { Text("Continue") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CircleSetup(api: Api, prefs: Prefs, onPicked: (String) -> Unit) {
    var circleName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Returning user on a fresh install of the same account: reuse an existing circle.
    LaunchedEffect(Unit) {
        runCatching { api.myCircles().firstOrNull() }.getOrNull()?.let { prefs.circleId = it.id; onPicked(it.id) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
        Text("Create a circle", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(circleName, { circleName = it }, label = { Text("Circle name (e.g. Family)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = circleName.isNotBlank(), onClick = {
            scope.launch {
                runCatching { api.createCircle(circleName.trim()) }
                    .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                    .onFailure { error = it.message }
            }
        }) { Text("Create") }

        Text("…or join one", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = code.length >= 4, onClick = {
            scope.launch {
                runCatching { api.joinCircle(code.trim()) }
                    .onSuccess { prefs.circleId = it.id; onPicked(it.id) }
                    .onFailure { error = it.message }
            }
        }) { Text("Join") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

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

    fun startSharing() {
        LocationService.start(ctx); prefs.sharing = true; sharing = true
    }

    // Step 2 (Android 10+): background location must be requested separately, after foreground.
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startSharing() }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!fine) { error = "Location permission is required to share."; return@rememberLauncherForActivityResult }
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

    // Register push token once per launch, and poll the circle while visible.
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
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (d == null) { Text(error ?: "Loading…"); return@Column }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(d.circle.name, style = MaterialTheme.typography.titleLarge)
                Text("Invite code: ${d.circle.inviteCode}", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onLeave) { Text("Switch") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Share my location", modifier = Modifier.weight(1f))
            Switch(checked = sharing, onCheckedChange = ::toggleSharing)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item { Text("People", style = MaterialTheme.typography.titleMedium) }
            items(d.members, key = { it.id }) { m ->
                val placeNames = d.places.filter { it.id in m.insidePlaceIds }.joinToString { it.name }
                Card(Modifier.fillMaxWidth().clickable(enabled = m.lat != null) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.name)})")))
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                m.lat == null -> "No location yet"
                                m.driving -> "Driving"
                                placeNames.isNotEmpty() -> "At $placeNames"
                                else -> "Out and about"
                            },
                        )
                        m.ts?.let { Text("Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Text("Places", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(d.places, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.name} · ${p.radiusM.toInt()} m", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { runCatching { api.deletePlace(circleId, p.id); detail = api.circleDetail(circleId) } }
                    }) { Text("Remove") }
                }
            }
            item {
                OutlinedButton(onClick = { showAddPlace = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Add a place at my current location")
                }
            }
        }
    }

    if (showAddPlace) {
        var placeName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddPlace = false },
            title = { Text("New place") },
            text = {
                OutlinedTextField(placeName, { placeName = it }, label = { Text("Name (Home, School…)") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = placeName.isNotBlank(), onClick = {
                    showAddPlace = false
                    scope.launch {
                        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) { error = "Grant location permission first (toggle sharing)."; return@launch }
                        runCatching {
                            val loc = LocationServices.getFusedLocationProviderClient(ctx)
                                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                                ?: throw IllegalStateException("Couldn't get a location fix")
                            api.addPlace(circleId, placeName.trim(), loc.latitude, loc.longitude, 150.0)
                            detail = api.circleDetail(circleId)
                        }.onFailure { error = it.message }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddPlace = false }) { Text("Cancel") } },
        )
    }
}
