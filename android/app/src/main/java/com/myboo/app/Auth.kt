package com.myboo.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Stage {
    data object Welcome : Stage
    data object SignIn : Stage
    data object SignUp : Stage
    data object Forgot : Stage
    /** `step` is "verify_email" or "2fa". */
    data class Code(val challengeId: String, val email: String, val step: String) : Stage
    data class Reset(val challengeId: String, val email: String) : Stage
}

@Composable
fun AuthFlow(api: Api, onSignedIn: () -> Unit) {
    var stage by remember { mutableStateOf<Stage>(Stage.Welcome) }
    var notice by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = stage != Stage.Welcome) {
        stage = if (stage is Stage.Reset) Stage.Forgot else Stage.Welcome
    }

    fun handle(r: AuthResult) {
        when (r) {
            AuthResult.SignedIn -> onSignedIn()
            is AuthResult.NeedsCode -> stage = Stage.Code(r.challengeId, r.email, r.step)
        }
    }

    when (val s = stage) {
        Stage.Welcome -> Welcome(onSignIn = { notice = null; stage = Stage.SignIn }, onSignUp = { stage = Stage.SignUp })
        Stage.SignIn -> SignIn(api, notice, onBack = { stage = Stage.Welcome }, onForgot = { stage = Stage.Forgot }, onResult = ::handle)
        Stage.SignUp -> SignUp(api, onBack = { stage = Stage.Welcome }, onResult = ::handle)
        Stage.Forgot -> Forgot(api, onBack = { stage = Stage.SignIn }, onSent = { stage = Stage.Reset(it.challengeId, it.email) })
        is Stage.Code -> CodeScreen(api, s, onBack = { stage = Stage.Welcome }, onResult = ::handle)
        is Stage.Reset -> ResetScreen(api, s, onBack = { stage = Stage.Forgot }, onDone = {
            notice = "Password updated. Sign in with your new one."
            stage = Stage.SignIn
        })
    }
}

// ---------- shared layout ----------

@Composable
private fun AuthPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(start = 44.dp, end = 22.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) TextLink("← back", onClick = onBack)
        else Spacer(Modifier.height(24.dp))
        GhostLogo(54.dp)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ---------- screens ----------

@Composable
private fun Welcome(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    val nb = MaterialTheme.notebook
    Column(
        Modifier.fillMaxSize().padding(start = 44.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        GhostLogo(104.dp)
        Spacer(Modifier.height(12.dp))
        Text("MyBoo", style = MaterialTheme.typography.displaySmall)
        Text(
            "Keep your people close.",
            style = MaterialTheme.typography.titleMedium, color = nb.ink.copy(alpha = 0.8f),
        )
        Text(
            "See everyone on a live map and get a little note when they arrive, leave, or hit the road.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 36.dp),
        )
        StickerButton("Create account", onClick = onSignUp)
        Spacer(Modifier.height(10.dp))
        StickerButton("I already have an account", fill = nb.sky, onClick = onSignIn)
    }
}

@Composable
private fun SignIn(api: Api, notice: String?, onBack: () -> Unit, onForgot: () -> Unit, onResult: (AuthResult) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canGo = email.isNotBlank() && password.isNotEmpty() && !busy
    fun go() {
        if (!canGo) return
        busy = true; error = null
        scope.launch {
            runCatching { api.signIn(email.trim(), password) }.onSuccess(onResult).onFailure { error = it.message }
            busy = false
        }
    }
    AuthPage("Welcome back", "Sign in to see your circle.", onBack) {
        notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyLarge) }
        NoteField(email, { email = it }, "Email", keyboard = KeyboardType.Email)
        NoteField(password, { password = it }, "Password", password = true, imeAction = ImeAction.Go, onDone = ::go)
        ErrorNote(error)
        StickerButton(if (busy) "Signing in…" else "Sign in", enabled = canGo, onClick = ::go)
        TextLink("Forgot your password?", onClick = onForgot)
    }
}

@Composable
private fun SignUp(api: Api, onBack: () -> Unit, onResult: (AuthResult) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canGo = name.isNotBlank() && email.contains("@") && password.length >= 8 && !busy
    fun go() {
        if (!canGo) return
        busy = true; error = null
        scope.launch {
            runCatching { api.signUp(name.trim(), email.trim(), password) }.onSuccess(onResult).onFailure { error = it.message }
            busy = false
        }
    }
    AuthPage("Make an account", "We'll email you a code to confirm it's you.", onBack) {
        NoteField(name, { name = it.take(40) }, "What should we call you?")
        NoteField(email, { email = it }, "Email", keyboard = KeyboardType.Email)
        NoteField(password, { password = it }, "Password (8+ characters)", password = true, imeAction = ImeAction.Go, onDone = ::go)
        ErrorNote(error)
        StickerButton(if (busy) "Creating…" else "Create account", enabled = canGo, onClick = ::go)
    }
}

@Composable
private fun CodeScreen(api: Api, stage: Stage.Code, onBack: () -> Unit, onResult: (AuthResult) -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(45) }
    val scope = rememberCoroutineScope()
    val is2fa = stage.step == "2fa"

    LaunchedEffect(cooldown) { if (cooldown > 0) { delay(1000); cooldown-- } }

    fun submit(c: String) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { if (is2fa) api.twoFactor(stage.challengeId, c) else api.verifyEmail(stage.challengeId, c) }
                .onSuccess(onResult)
                .onFailure { error = it.message; code = "" }
            busy = false
        }
    }

    AuthPage(
        if (is2fa) "Check your email" else "Confirm your email",
        "We sent a 6-digit code to ${stage.email}. It expires in 10 minutes.",
        onBack,
    ) {
        CodeField(code, { code = it; if (it.length == 6) submit(it) })
        ErrorNote(error)
        info?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
        StickerButton(if (busy) "Checking…" else "Continue", enabled = code.length == 6 && !busy, onClick = { submit(code) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLink(
                if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code",
                onClick = {
                    if (cooldown > 0) return@TextLink
                    scope.launch {
                        runCatching { api.resend(stage.challengeId) }
                            .onSuccess { info = "New code sent."; error = null; cooldown = 45 }
                            .onFailure { error = it.message }
                    }
                },
            )
        }
    }
}

@Composable
private fun Forgot(api: Api, onBack: () -> Unit, onSent: (AuthResult.NeedsCode) -> Unit) {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() {
        if (busy || !email.contains("@")) return
        busy = true; error = null
        scope.launch {
            runCatching { api.forgot(email.trim()) }.onSuccess(onSent).onFailure { error = it.message }
            busy = false
        }
    }
    AuthPage("Forgot your password?", "Enter your email and we'll send a code to reset it.", onBack) {
        NoteField(email, { email = it }, "Email", keyboard = KeyboardType.Email, imeAction = ImeAction.Go, onDone = ::go)
        ErrorNote(error)
        StickerButton(if (busy) "Sending…" else "Send code", enabled = email.contains("@") && !busy, onClick = ::go)
    }
}

@Composable
private fun ResetScreen(api: Api, stage: Stage.Reset, onBack: () -> Unit, onDone: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canGo = code.length == 6 && password.length >= 8 && !busy
    fun go() {
        if (!canGo) return
        busy = true; error = null
        scope.launch {
            runCatching { api.reset(stage.challengeId, code, password) }.onSuccess { onDone() }.onFailure { error = it.message }
            busy = false
        }
    }
    AuthPage("Choose a new password", "If ${stage.email} has an account, a code is on its way.", onBack) {
        CodeField(code, { code = it })
        NoteField(password, { password = it }, "New password (8+ characters)", password = true, imeAction = ImeAction.Go, onDone = ::go)
        ErrorNote(error)
        StickerButton(if (busy) "Saving…" else "Reset password", enabled = canGo, onClick = ::go)
    }
}
