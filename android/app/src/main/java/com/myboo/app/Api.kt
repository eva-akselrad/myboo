package com.myboo.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tiny persisted session (user id + bearer token + chosen circle). */
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
    var circleId: String?
        get() = sp.getString("circleId", null)
        set(v) = sp.edit().putString("circleId", v).apply()
    var sharing: Boolean
        get() = sp.getBoolean("sharing", false)
        set(v) = sp.edit().putBoolean("sharing", v).apply()
}

data class Circle(val id: String, val name: String, val inviteCode: String)
data class Member(
    val id: String, val name: String, val lat: Double?, val lng: Double?,
    val speed: Double?, val ts: Long?, val driving: Boolean, val insidePlaceIds: List<String>,
)
data class PlaceInfo(val id: String, val name: String, val lat: Double, val lng: Double, val radiusM: Double)
data class CircleDetail(val circle: Circle, val members: List<Member>, val places: List<PlaceInfo>)

class ApiException(val code: Int, message: String) : Exception(message)

class Api(private val prefs: Prefs) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                prefs.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code !in 200..299) throw ApiException(code, json.optString("error", "HTTP $code"))
                json
            } finally {
                conn.disconnect()
            }
        }

    suspend fun register(name: String) {
        val r = call("POST", "/v1/users", JSONObject().put("name", name))
        prefs.token = r.getString("token")
        prefs.userId = r.getString("id")
        prefs.name = r.getString("name")
    }

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

    suspend fun setFcmToken(token: String) {
        call("PUT", "/v1/me/fcm-token", JSONObject().put("token", token))
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
