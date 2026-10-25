package com.example.data.notification

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

object RokkhonNotificationHelper {
    private const val CHANNEL_ID_EMERGENCY = "rokkhon_emergency_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "Rokkhon Emergency & Blood Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for nearby urgent blood requests and SOS beacons."
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private const val NOTIFIED_PREFS = "rokkhon_notified_keys"
    private const val NOTIFIED_TTL_MS = 24L * 60L * 60L * 1000L

    /**
     * Posts an alert only the first time [key] is seen in 24 h. The screen and the background
     * guard service can both hear the same SOS (Firestore + Bluetooth); this keeps it to one alert.
     */
    @Synchronized
    fun notifyOnce(context: Context, key: String, title: String, body: String): Boolean {
        val prefs = context.getSharedPreferences(NOTIFIED_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0L) < NOTIFIED_TTL_MS) return false
        val editor = prefs.edit()
        prefs.all.forEach { (k, v) -> if (v is Long && now - v > NOTIFIED_TTL_MS) editor.remove(k) }
        editor.putLong(key, now).apply()
        dispatchEmergencyAlert(context, title, body, notificationId = key.hashCode())
        return true
    }

    @SuppressLint("MissingPermission")
    fun dispatchEmergencyAlert(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        if (!canPostNotifications(context)) return
        try {
            ensureChannel(context)
            // Tapping the notification opens the app instead of doing nothing.
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }
}
