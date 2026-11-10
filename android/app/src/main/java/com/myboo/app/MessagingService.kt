package com.myboo.app

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        if (prefs.token == null) return // uploaded after registration instead
        CoroutineScope(Dispatchers.IO).launch { runCatching { Api(prefs).setFcmToken(token) } }
    }

    // Notification-payload messages are shown by the system when the app is in the background.
    // This handles the foreground case so alerts are never silently dropped.
    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        LocationService.ensureChannels(this)
        val notif = NotificationCompat.Builder(this, LocationService.CHANNEL_EVENTS)
            .setContentTitle(n.title ?: "MyBoo")
            .setContentText(n.body)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}
