package com.example.data.sos

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/** Notifications for the private SOS: the sender's ongoing one, alerts to trusted people, listening. */
object SosNotifier {
    const val CHANNEL_ALERT = "rokkhon_private_sos_alert"
    const val CHANNEL_ONGOING = "rokkhon_private_sos_ongoing"
    const val CHANNEL_UPDATES = "rokkhon_updates"
    const val EXTRA_OPEN_TAB = "rokkhon_open_tab"
    const val EXTRA_SESSION = "rokkhon_sos_session"
    const val EXTRA_OPEN_OWNER = "rokkhon_sos_open_owner"
    const val ID_OWNER = 7101
    const val ID_LISTEN = 7102
    private const val ID_ALERT_BASE = 7200

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "SOS from people who trust you", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Someone who added you to their SOS needs help"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Rokkhon updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Blood camps, urgent appeals and news from the Rokkhon team"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Active SOS", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while your SOS shares location or while you listen to one"
            }
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts if the user allows notifications (Android 13+ asks); silently skips otherwise. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun openSessionIntent(context: Context, sessionId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SESSION, sessionId)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openOwnerIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_OWNER, true)
        }
        return PendingIntent.getActivity(context, ID_OWNER, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ownerOngoing(context: Context, text: String): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SOS চালু আছে")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openOwnerIntent(context))
            .build()
    }

    fun listenOngoing(context: Context, sessionId: String, text: String): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SOS শুনছেন")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openSessionIntent(context, sessionId, ID_LISTEN))
            .build()
    }

    /** "X needs help" — tapping opens the monitoring screen for that session. */
    fun alert(context: Context, sessionId: String, ownerName: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val id = ID_ALERT_BASE + (sessionId.hashCode() and 0x3ff)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$ownerName SOS চালু করেছেন")
            .setContentText("লাইভ লোকেশন দেখতে ট্যাপ করুন। জরুরি হলে ৯৯৯-এ কল করুন।")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
            .setContentIntent(openSessionIntent(context, sessionId, id))
            .build()
        post(context, id, n)
    }

    fun ended(context: Context, sessionId: String, ownerName: String) {
        if (!canPost(context)) return
        val id = ID_ALERT_BASE + (sessionId.hashCode() and 0x3ff)
        val n = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$ownerName SOS বন্ধ করেছেন")
            .setAutoCancel(true)
            .setContentIntent(openSessionIntent(context, sessionId, id))
            .build()
        post(context, id, n)
    }

    fun resumeNeeded(context: Context) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("আপনার SOS এখনও চালু")
            .setContentText("লোকেশন শেয়ার আবার চালু করতে ট্যাপ করুন।")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openOwnerIntent(context))
            .build()
        post(context, ID_OWNER + 50, n)
    }

    /** A push the Rokkhon team sent from the admin panel; tapping opens the app (on [tab]). */
    fun broadcast(context: Context, title: String, body: String, tab: String?) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            tab?.let { putExtra(EXTRA_OPEN_TAB, it) }
        }
        val id = 7300 + (title.hashCode() and 0xff)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        post(context, id, n)
    }
}
