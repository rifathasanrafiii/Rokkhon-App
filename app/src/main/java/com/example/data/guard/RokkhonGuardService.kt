package com.example.data.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ble.BleSosMeshManager
import com.example.data.location.LocationTracker
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.calculateDistanceKm
import com.example.data.model.emergencyTypeLabel
import com.example.data.model.AppLanguage
import com.example.data.notification.RokkhonNotificationHelper
import com.example.data.repository.RokkhonRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * "সুরক্ষা নেটওয়ার্ক" — keeps this phone part of the SOS network while the app is closed:
 *  - listens for Bluetooth SOS packets and relays them (no internet needed),
 *  - keeps a low-power location fix so the 1 km decision is correct,
 *  - for signed-in users, watches new SOS alerts in the cloud and alerts when one is within 1 km.
 * Runs as a foreground service with a quiet ongoing notification, as Android requires.
 */
class RokkhonGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cloudJob: Job? = null
    private lateinit var locationTracker: LocationTracker
    private lateinit var mesh: BleSosMeshManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mesh = BleSosMeshManager.get(this)
        locationTracker = LocationTracker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            GuardPrefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!GuardPrefs.isEnabled(this) || !promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        MeshAlertNotifier.ensureStarted(this)
        mesh.startMeshScanning()
        locationTracker.startLiveUpdates(intervalMillis = LOCATION_INTERVAL_MS, highAccuracy = false) { lat, lng ->
            mesh.updateOwnLocation(lat, lng)
            GuardPrefs.saveLastLocation(this, lat, lng)
        }
        startCloudWatch()
        return START_STICKY
    }

    override fun onDestroy() {
        locationTracker.stopLiveUpdates()
        cloudJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Starts as a foreground service with only the types whose permissions are granted. */
    private fun promoteToForeground(): Boolean {
        val hasBle = BleSosMeshManager.hasBlePermissions(this)
        val hasLocation = LocationTracker(this).hasPermission()
        if (!hasBle && !hasLocation) return false
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return try {
                startForeground(ONGOING_ID, notification)
                true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground failed on API ${Build.VERSION.SDK_INT}", e)
                false
            }
        }
        var types = 0
        if (hasBle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (hasLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return try {
            ServiceCompat.startForeground(this, ONGOING_ID, notification, types)
            true
        } catch (e: Exception) {
            // e.g. started at boot: Android doesn't allow a location service from the background.
            Log.w(TAG, "Foreground start with types $types failed", e)
            if (hasBle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    ServiceCompat.startForeground(this, ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                    true
                } catch (e2: Exception) {
                    Log.w(TAG, "Foreground start failed", e2)
                    false
                }
            } else {
                false
            }
        }
    }

    private fun startCloudWatch() {
        if (cloudJob?.isActive == true) return
        val configured = try { FirebaseApp.getApps(this).isNotEmpty() } catch (_: Exception) { false }
        if (!configured) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val repository = try { RokkhonRepository(this) } catch (_: Exception) { return }
        cloudJob = scope.launch {
            repository.observeSosAlerts()
                .catch { Log.w(TAG, "Cloud SOS watch stopped", it) }
                .collect { alerts ->
                    val here = GuardPrefs.lastLocation(this@RokkhonGuardService) ?: return@collect
                    val now = System.currentTimeMillis()
                    alerts.filter {
                        it.status == "ACTIVE" && it.userId != uid && it.id.isNotBlank() &&
                            now - it.createdAtMillis < RECENT_SOS_WINDOW_MS
                    }.forEach { alert ->
                        val km = calculateDistanceKm(here.first, here.second, alert.latitude, alert.longitude)
                        if (km <= SOS_NOTIFICATION_RADIUS_KM) {
                            val bn = GuardPrefs.isBengali(this@RokkhonGuardService)
                            val type = emergencyTypeLabel(alert.emergencyType, if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH)
                            RokkhonNotificationHelper.notifyOnce(
                                context = this@RokkhonGuardService,
                                key = "sos_${alert.id}",
                                title = if (bn) "জরুরি SOS • ${"%.1f".format(km)} কি.মি. দূরে" else "SOS • ${"%.1f".format(km)} km away",
                                body = if (bn) "$type • ${alert.senderName} বিপদে আছেন (${alert.district})। সাহায্য করতে অ্যাপ খুলুন।"
                                else "$type • ${alert.senderName} needs help near ${alert.district}. Open Rokkhon to respond."
                            )
                        }
                    }
                }
        }
    }

    private fun buildOngoingNotification(): android.app.Notification {
        val bn = GuardPrefs.isBengali(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (bn) "সুরক্ষা নেটওয়ার্ক" else "Safety network",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, RokkhonGuardService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (bn) "রক্ষণ সুরক্ষা নেটওয়ার্ক চালু" else "Rokkhon safety network is on")
            .setContentText(if (bn) "১ কি.মি.-এর মধ্যে কেউ SOS দিলে জানাবে, ব্লুটুথে অন্যদের কাছেও পৌঁছে দেবে" else "Alerts you to SOS within 1 km and relays them over Bluetooth")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, if (bn) "বন্ধ করুন" else "Turn off", stop)
            .build()
    }

    companion object {
        private const val TAG = "RokkhonGuardService"
        private const val CHANNEL_ID = "rokkhon_guard_channel"
        private const val ONGOING_ID = 7001
        private const val ACTION_STOP = "com.example.guard.STOP"
        private const val LOCATION_INTERVAL_MS = 60_000L
        private const val RECENT_SOS_WINDOW_MS = 30L * 60L * 1000L

        /** True when Android would let the guard run (Bluetooth or location permission granted). */
        fun canRun(context: Context): Boolean =
            BleSosMeshManager.hasBlePermissions(context) || LocationTracker(context).hasPermission()

        fun start(context: Context) {
            if (!GuardPrefs.isEnabled(context) || !canRun(context)) return
            try {
                context.startService(Intent(context, RokkhonGuardService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start guard service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RokkhonGuardService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop guard service", e)
            }
        }
    }
}
