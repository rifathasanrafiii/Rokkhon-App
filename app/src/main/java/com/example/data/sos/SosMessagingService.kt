package com.example.data.sos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives the high-priority SOS push sent by the trusted server. Data-only messages, so this code
 * decides what to show and the tap always opens the monitoring screen for that session.
 */
class SosMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { saveToken(this@SosMessagingService, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val sid = data["sessionId"]?.takeIf { SosIds.isSessionId(it) } ?: return
        val name = data["ownerName"]?.take(60) ?: "Someone"
        when (data["type"]) {
            "sos_started" -> SosNotifier.alert(this, sid, name)
            "sos_ended" -> SosNotifier.ended(this, sid, name)
        }
    }

    companion object {
        suspend fun saveToken(context: android.content.Context, token: String) {
            if (FirebaseAuth.getInstance().currentUser == null) return
            val db = SosDatabase.get(context) ?: return
            runCatching { SosRepository(db, FirebaseAuth.getInstance()).saveFcmToken(token) }
        }

        /** Called after sign-in so a phone that already had a token still registers it for this account. */
        suspend fun registerCurrentToken(context: android.content.Context) {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
            saveToken(context, token)
        }
    }
}
