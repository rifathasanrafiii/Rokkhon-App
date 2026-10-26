package com.example.data.sos

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asks the trusted server (web/api/sos-notify.js) to send the FCM alert. The app only proves who it
 * is with its Firebase ID token; the service-account key that can send pushes lives on the server.
 */
object SosNotifyClient {

    suspend fun notifyTrusted(context: Context, sessionId: String, event: String = "started"): PushStatus {
        val endpoint = context.getString(R.string.sos_notify_endpoint).trim()
        if (endpoint.isEmpty()) return PushStatus.Unavailable("not_configured")
        val user = FirebaseAuth.getInstance().currentUser ?: return PushStatus.Unavailable("signed_out")
        val token = runCatching { user.getIdToken(false).await().token }.getOrNull()
            ?: return PushStatus.Unavailable("no_token")
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                conn.outputStream.use { it.write(JSONObject(mapOf("sessionId" to sessionId, "event" to event)).toString().toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) return@withContext PushStatus.Unavailable("http_$code")
                val body = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                PushStatus.Sent(
                    delivered = body.optInt("sent"),
                    failed = body.optInt("failed"),
                    noDevice = body.optInt("noDevice")
                )
            } catch (e: Exception) {
                PushStatus.Unavailable("unreachable")
            } finally {
                conn?.disconnect()
            }
        }
    }
}
