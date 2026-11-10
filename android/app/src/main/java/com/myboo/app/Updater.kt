package com.myboo.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release that is newer than the running app. */
data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-updater backed by GitHub Releases (the app is distributed outside any store).
 * Flow: check latest release -> download the .apk asset -> hand it to PackageInstaller.
 * Android verifies the new APK is signed with the same key as the installed one.
 */
object Updater {
    private const val REPO = "eva-akselrad/myboo"

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    /** Hide the banner for the rest of this session without forgetting the update. */
    var snoozed by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Compare dotted versions numerically ("0.10.0" > "0.9.3"). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size, 3)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Auto check once per app launch (GitHub's anonymous API limit is plenty for this). */
    fun checkOnLaunch(ctx: Context) {
        if (state is UpdateState.Idle || state is UpdateState.Failed) check(ctx, auto = true)
    }

    fun check(ctx: Context, auto: Boolean = false) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        val app = ctx.applicationContext
        state = UpdateState.Checking
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            result.onSuccess { rel ->
                if (rel != null && isNewer(rel.version, BuildConfig.VERSION_NAME)) {
                    snoozed = false
                    state = UpdateState.Available(rel)
                    download(app, rel) // grab it in the background so installing is one tap
                } else state = UpdateState.UpToDate
            }.onFailure {
                // A failed background check shouldn't nag; a manual one should say so.
                state = if (auto) UpdateState.Idle else UpdateState.Failed("Couldn't reach GitHub. Try again in a bit.")
            }
        }
    }

    private fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "MyBoo-Android/${BuildConfig.VERSION_NAME}")
        try {
            if (conn.responseCode == 404) return null // no release yet
            if (conn.responseCode != 200) error("GitHub ${conn.responseCode}")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = j.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Release(
                        version = j.getString("tag_name").removePrefix("v"),
                        notes = tidyNotes(j.optString("body")),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size"),
                    )
                }
            }
            return null
        } finally { conn.disconnect() }
    }

    private fun tidyNotes(body: String) = body.lines()
        .map { it.trim().trimStart('#', '-', '*', ' ').replace("**", "") }
        .filter { it.isNotBlank() }.take(4).joinToString("\n")

    private fun download(app: Context, rel: Release) {
        state = UpdateState.Downloading(rel, 0)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                    val out = File(dir, "myboo-${rel.version}.apk")
                    val conn = URL(rel.apkUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000; conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", "MyBoo-Android/${BuildConfig.VERSION_NAME}")
                    try {
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.size
                        var done = 0L; var lastPct = -1
                        conn.inputStream.use { input ->
                            out.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf); if (n < 0) break
                                    o.write(buf, 0, n); done += n
                                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                                    if (pct != lastPct) { lastPct = pct; withContext(Dispatchers.Main) { state = UpdateState.Downloading(rel, pct) } }
                                }
                            }
                        }
                        if (total > 0 && done != total) error("incomplete download")
                    } finally { conn.disconnect() }
                    out
                }
            }.onSuccess { state = UpdateState.Ready(rel, it) }
                .onFailure { state = UpdateState.Failed("Download failed. Check your connection and try again.") }
        }
    }

    /** Retry from a failed download. */
    fun retry(ctx: Context) {
        val s = state
        if (s is UpdateState.Available) download(ctx.applicationContext, s.release) else check(ctx)
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hand the downloaded APK to the system installer. The app restarts once it's replaced. */
    fun install(ctx: Context, file: File) {
        runCatching {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("myboo.apk", 0, file.length()).use { o -> input.copyTo(o); session.fsync(o) }
                }
                val pi = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pi.intentSender)
            }
        }.onFailure { state = UpdateState.Failed("Couldn't start the install: ${it.message}") }
    }

    internal fun installFailed(msg: String?) { state = UpdateState.Failed(msg ?: "The install didn't go through.") }
}

/** Receives PackageInstaller progress: forwards the system confirmation screen when one is needed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.installFailed("Install cancelled.")
            else -> Updater.installFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}

/** After an update replaces the app, resume location sharing if it was on. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Prefs(context).sharing) runCatching { LocationService.start(context) }
    }
}

// ---------- UI ----------

@Composable
private fun ProgressSticker(percent: Int, modifier: Modifier = Modifier) {
    val nb = MaterialTheme.notebook
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().height(16.dp).clip(shape).background(nb.paper)
        .drawBehind { drawRoundRect(nb.ink, style = Stroke(3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)) }) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(16.dp).background(nb.mint))
    }
}

/** The pastel "new version" note that floats at the bottom of the screen. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val nb = MaterialTheme.notebook
    LaunchedEffect(Unit) { Updater.checkOnLaunch(ctx) }

    val s = Updater.state
    val release = when (s) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        else -> null
    }
    AnimatedVisibility(
        visible = release != null && !Updater.snoozed,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        if (release == null) return@AnimatedVisibility
        NoteCard(Modifier.padding(start = 40.dp, end = 17.dp, bottom = 12.dp), color = nb.butter, tilt = -0.7f, tape = true) {
            Text("A new MyBoo is here!", style = MaterialTheme.typography.titleMedium)
            Text("Version ${release.version}", style = MaterialTheme.typography.bodySmall, color = nb.ink.copy(alpha = 0.7f))
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(release.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            }
            Spacer(Modifier.height(12.dp))
            when (s) {
                is UpdateState.Downloading -> {
                    Text("Fetching it… ${s.percent}%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    ProgressSticker(s.percent)
                }
                is UpdateState.Ready -> Column {
                    StickerButton("Update now", fill = nb.mint, onClick = {
                        if (Updater.canInstall(ctx)) Updater.install(ctx, s.file) else Updater.askInstallPermission(ctx)
                    })
                    TextLink("later", onClick = { Updater.snoozed = true })
                }
                else -> TextLink("later", onClick = { Updater.snoozed = true })
            }
            if (s is UpdateState.Ready && !Updater.canInstall(ctx)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "First time: Android will ask you to allow installs from MyBoo. Flip it on, come back, and tap Update now again.",
                    style = MaterialTheme.typography.bodySmall, color = nb.ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Version row for Settings, with a manual "Check for updates". */
@Composable
fun VersionCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val nb = MaterialTheme.notebook
    val s = Updater.state
    NoteCard(modifier, color = nb.lavender, tilt = 0.4f) {
        Text("MyBoo ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text(
            when (s) {
                UpdateState.Checking -> "Checking for a new version…"
                UpdateState.UpToDate -> "You're on the latest version."
                is UpdateState.Available, is UpdateState.Downloading -> "A new version is downloading."
                is UpdateState.Ready -> "Version ${s.release.version} is ready to install."
                is UpdateState.Failed -> s.message
                UpdateState.Idle -> "Updates come straight from GitHub."
            },
            style = MaterialTheme.typography.bodyMedium, color = nb.ink.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(12.dp))
        if (s is UpdateState.Ready) {
            StickerButton("Update to ${s.release.version}", fill = nb.mint, onClick = {
                if (Updater.canInstall(ctx)) Updater.install(ctx, s.file) else Updater.askInstallPermission(ctx)
            })
        } else {
            StickerButton("Check for updates", fill = nb.butter, enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading, onClick = {
                Updater.snoozed = false
                if (s is UpdateState.Failed && s.message.startsWith("Download")) Updater.retry(ctx) else Updater.check(ctx)
            })
        }
    }
}
