package com.myboo.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tiny persisted session (token + cached profile + chosen circle). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("myboo", Context.MODE_PRIVATE)
    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()
    var userId: String?
        get() = sp.getString("userId", null)
        set(v) = sp.edit().putString("userId", v).apply()
    var name: String?
        get() = sp.getString("name", null)
        set(v) = sp.edit().putString("name", v).apply()
    var email: String?
        get() = sp.getString("email", null)
        set(v) = sp.edit().putString("email", v).apply()
    var circleId: String?
        get() = sp.getString("circleId", null)
        set(v) = sp.edit().putString("circleId", v).apply()
    var lastAlertId: Long
        get() = sp.getLong("lastAlertId", 0)
        set(v) = sp.edit().putLong("lastAlertId", v).apply()
    var sharing: Boolean
        get() = sp.getBoolean("sharing", false)
        set(v) = sp.edit().putBoolean("sharing", v).apply()

    /** Forget everything tied to the signed-in account. */
    fun clearSession() {
        sp.edit().clear().apply()
    }
}

data class Circle(val id: String, val name: String, val inviteCode: String)
data class Member(
    val id: String, val name: String, val lat: Double?, val lng: Double?,
    val speed: Double?, val ts: Long?, val driving: Boolean, val insidePlaceIds: List<String>,
)
data class PlaceInfo(val id: String, val name: String, val lat: Double, val lng: Double, val radiusM: Double)
data class CircleDetail(val circle: Circle, val members: List<Member>, val places: List<PlaceInfo>)

data class Account(val id: String, val name: String, val email: String, val twofa: Boolean)
data class SessionInfo(val id: String, val device: String, val lastUsedAt: Long, val current: Boolean)

/** Result of an auth step: either we're signed in, or an emailed code is needed. */
sealed interface AuthResult {
    data object SignedIn : AuthResult
    data class NeedsCode(val challengeId: String, val step: String, val email: String) : AuthResult
}

class ApiException(val code: Int, message: String) : Exception(message)

class Api(private val prefs: Prefs) {

    companion object {
        /** Called on the main thread when the server rejects our token (revoked / expired). */
        @Volatile var onSignedOut: (() -> Unit)? = null
        private val main = Handler(Looper.getMainLooper())
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                val token = prefs.token
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code == 401 && token != null) {
                    // Session revoked or expired elsewhere: drop it and bounce to sign-in.
                    prefs.clearSession()
                    main.post { onSignedOut?.invoke() }
                }
                if (code !in 200..299) throw ApiException(code, json.optString("error", "Something went wrong ($code)"))
                json
            } catch (e: java.io.IOException) {
                throw ApiException(0, "Can't reach MyBoo. Check your connection.")
            } finally {
                conn.disconnect()
            }
        }

    private val device get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()

    // ---------- accounts ----------

    private fun accept(r: JSONObject): AuthResult {
        if (r.has("token")) {
            val u = r.getJSONObject("user")
            prefs.token = r.getString("token")
            prefs.userId = u.getString("id")
            prefs.name = u.getString("name")
            prefs.email = u.getString("email")
            return AuthResult.SignedIn
        }
        return AuthResult.NeedsCode(r.getString("challenge_id"), r.getString("step"), r.optString("email"))
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResult =
        accept(call("POST", "/v1/auth/signup", JSONObject().put("name", name).put("email", email).put("password", password).put("device", device)))

    suspend fun signIn(email: String, password: String): AuthResult =
        accept(call("POST", "/v1/auth/login", JSONObject().put("email", email).put("password", password).put("device", device)))

    suspend fun verifyEmail(challengeId: String, code: String): AuthResult =
        accept(call("POST", "/v1/auth/verify-email", JSONObject().put("challenge_id", challengeId).put("code", code).put("device", device)))

    suspend fun twoFactor(challengeId: String, code: String): AuthResult =
        accept(call("POST", "/v1/auth/2fa", JSONObject().put("challenge_id", challengeId).put("code", code).put("device", device)))

    suspend fun resend(challengeId: String) {
        call("POST", "/v1/auth/resend", JSONObject().put("challenge_id", challengeId))
    }

    suspend fun forgot(email: String): AuthResult.NeedsCode =
        accept(call("POST", "/v1/auth/forgot", JSONObject().put("email", email))) as AuthResult.NeedsCode

    suspend fun reset(challengeId: String, code: String, newPassword: String) {
        call("POST", "/v1/auth/reset", JSONObject().put("challenge_id", challengeId).put("code", code).put("new_password", newPassword))
    }

    suspend fun logout() {
        runCatching { call("POST", "/v1/auth/logout") }
        prefs.clearSession()
    }

    suspend fun logoutAll() {
        call("POST", "/v1/me/logout-all")
        prefs.clearSession()
    }

    suspend fun me(): Account = parseAccount(call("GET", "/v1/me").getJSONObject("user")).also {
        prefs.name = it.name; prefs.email = it.email
    }

    suspend fun updateName(name: String): Account =
        parseAccount(call("PATCH", "/v1/me", JSONObject().put("name", name)).getJSONObject("user")).also { prefs.name = it.name }

    suspend fun setTwoFactor(enabled: Boolean, password: String): Account =
        parseAccount(call("PATCH", "/v1/me", JSONObject().put("twofa_enabled", enabled).put("password", password)).getJSONObject("user"))

    suspend fun changePassword(current: String, new: String) {
        call("POST", "/v1/me/password", JSONObject().put("current_password", current).put("new_password", new))
    }

    suspend fun sessions(): List<SessionInfo> {
        val a = call("GET", "/v1/me/sessions").getJSONArray("sessions")
        return (0 until a.length()).map {
            val s = a.getJSONObject(it)
            SessionInfo(s.getString("id"), s.getString("device"), s.getLong("last_used_at"), s.getBoolean("current"))
        }
    }

    suspend fun revokeSession(id: String) {
        call("DELETE", "/v1/me/sessions/$id")
    }

    suspend fun deleteAccount(password: String) {
        call("DELETE", "/v1/me", JSONObject().put("password", password))
        prefs.clearSession()
    }

    private fun parseAccount(u: JSONObject) = Account(u.getString("id"), u.getString("name"), u.getString("email"), u.getBoolean("twofa_enabled"))

    // ---------- circles ----------

    suspend fun createCircle(name: String): Circle =
        parseCircle(call("POST", "/v1/circles", JSONObject().put("name", name)))

    suspend fun joinCircle(code: String): Circle =
        parseCircle(call("POST", "/v1/circles/join", JSONObject().put("code", code)))

    suspend fun myCircles(): List<Circle> {
        val arr = call("GET", "/v1/me/circles").getJSONArray("circles")
        return (0 until arr.length()).map { parseCircle(arr.getJSONObject(it)) }
    }

    suspend fun circleDetail(id: String): CircleDetail {
        val r = call("GET", "/v1/circles/$id")
        val members = r.getJSONArray("members").let { a ->
            (0 until a.length()).map { i ->
                val m = a.getJSONObject(i)
                Member(
                    id = m.getString("id"), name = m.getString("name"),
                    lat = m.optDoubleOrNull("lat"), lng = m.optDoubleOrNull("lng"),
                    speed = m.optDoubleOrNull("speed"),
                    ts = if (m.isNull("ts")) null else m.getLong("ts"),
                    driving = m.optBoolean("driving"),
                    insidePlaceIds = m.getJSONArray("inside").toStrings(),
                )
            }
        }
        val places = r.getJSONArray("places").let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                PlaceInfo(p.getString("id"), p.getString("name"), p.getDouble("lat"), p.getDouble("lng"), p.getDouble("radius_m"))
            }
        }
        return CircleDetail(parseCircle(r.getJSONObject("circle")), members, places)
    }

    suspend fun addPlace(circleId: String, name: String, lat: Double, lng: Double, radiusM: Double) {
        call("POST", "/v1/circles/$circleId/places",
            JSONObject().put("name", name).put("lat", lat).put("lng", lng).put("radius_m", radiusM))
    }

    suspend fun deletePlace(circleId: String, placeId: String) {
        call("DELETE", "/v1/circles/$circleId/places/$placeId")
    }

    /** Alerts queued for me. Passing the last shown id acknowledges (deletes) everything up to it. */
    suspend fun alerts(after: Long): List<Alert> {
        val a = call("GET", "/v1/alerts?after=$after").getJSONArray("alerts")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Alert(o.getLong("id"), o.getString("title"), o.getString("body"))
        }
    }

    suspend fun postLocation(lat: Double, lng: Double, speed: Float?, accuracy: Float?, ts: Long) {
        val b = JSONObject().put("lat", lat).put("lng", lng).put("ts", ts)
        if (speed != null) b.put("speed", speed.toDouble())
        if (accuracy != null) b.put("accuracy", accuracy.toDouble())
        call("POST", "/v1/location", b)
    }

    private fun parseCircle(o: JSONObject) = Circle(o.getString("id"), o.getString("name"), o.getString("invite_code"))
}

private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
private fun JSONArray.toStrings(): List<String> = (0 until length()).map { getString(it) }

data class Alert(val id: Long, val title: String, val body: String)
