package com.example.data.sos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs an active SOS on the sender's phone: publishes GPS to the Realtime Database every 5–10 s and
 * streams the microphone to authorised listeners when the sender turned audio on. A foreground
 * service with a visible notification, so it keeps going with the screen locked; Android's own
 * location and microphone indicators stay visible. Ends only when the sender ends the SOS.
 */
class PrivateSosService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var repo: SosRepository? = null
    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var requestedInterval = 0L
    private val adaptive = AdaptiveInterval()
    private var broadcaster: SosAudioBroadcaster? = null
    private var listenJob: Job? = null
    private var loopJob: Job? = null
    private var connectedJob: Job? = null
    private var sessionWatch: Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>? = null
    private var offsetWatch: Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>? = null
    private var serverOffsetMs = 0L
    private var sessionId: String? = null
    private var audioOn = false
    private var lastLocation: Location? = null
    private var lastTrailMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        repo = SosDatabase.get(this)?.let { SosRepository(it, FirebaseAuth.getInstance()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (intent?.action) {
            ACTION_START -> {
                val sid = intent.getStringExtra(EXTRA_SID)
                if (sid == null || !SosIds.isSessionId(sid)) return stopNow()
                begin(sid, intent.getBooleanExtra(EXTRA_AUDIO, false), freshStart = !intent.getBooleanExtra(EXTRA_RESUME, false))
            }
            ACTION_AUDIO -> {
                if (sessionId == null) return stopNow()
                setAudio(intent.getBooleanExtra(EXTRA_AUDIO, false))
            }
            ACTION_LOCATION -> {
                if (sessionId == null) return stopNow()
                setLocationSharing(intent.getBooleanExtra(EXTRA_LOCATION, true))
            }
            ACTION_STOP -> {
                finish()
                return START_NOT_STICKY
            }
            else -> {
                // Restarted by Android after the process was killed: carry on with the saved session.
                val sid = prefs.getString(KEY_SID, null) ?: return stopNow()
                if (sessionId == null) begin(sid, prefs.getBoolean(KEY_AUDIO, false), freshStart = false)
            }
        }
        return START_STICKY
    }

    private fun stopNow(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun begin(sid: String, audio: Boolean, freshStart: Boolean) {
        val r = repo
        if (r == null || r.myUid == null) {
            stopSelf(); return
        }
        sessionId = sid
        audioOn = audio && hasMic()
        if (!promote()) {
            // Android refused a background start (e.g. restart while the app is not visible).
            SosNotifier.resumeNeeded(this)
            sessionId = null
            stopSelf()
            return
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SID, sid).putBoolean(KEY_AUDIO, audio).apply()
        SosRuntime.update {
            it.copy(
                sessionId = sid,
                startedAtMs = if (it.sessionId == sid) it.startedAtMs else System.currentTimeMillis(),
                mic = micStatus(audio),
                locationOn = true
            )
        }

        r.armOnlinePresence(sid)
        connectedJob = scope.launch {
            r.observeConnected().collect { online ->
                SosRuntime.update { it.copy(online = online) }
                if (online) {
                    // onDisconnect handlers fire once; arm again after every reconnect.
                    r.armOnlinePresence(sid)
                    r.heartbeat(sid)
                }
            }
        }
        offsetWatch = SosDatabase.get(this)?.getReference(".info/serverTimeOffset")?.let { ref ->
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    serverOffsetMs = snapshot.getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(l)
            ref to l
        }
        // If the session is ended anywhere else (another device, the web page), stop here too.
        sessionWatch = r.ref("sosSessions/$sid/status").let { ref ->
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java)
                    if (status == "ended" || (status == null && snapshot.exists().not() && !freshStart)) finish()
                }
                override fun onCancelled(error: DatabaseError) { finish() }
            }
            ref.addValueEventListener(l)
            ref to l
        }

        startLocation()
        if (audioOn) startAudio()

        loopJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(15_000)
                tick++
                if (tick % 2 == 0) r.heartbeat(sid)
                checkGpsHealth()
                updateNotification()
            }
        }

        updateNotification()
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun micStatus(wanted: Boolean): MicStatus = when {
        !wanted -> MicStatus.OFF
        !hasMic() -> MicStatus.NO_PERMISSION
        SosRuntime.owner.value.listening.isNotEmpty() -> MicStatus.LIVE
        else -> MicStatus.READY
    }

    private fun promote(): Boolean {
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocation()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && audioOn) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        val notification = SosNotifier.ownerOngoing(this, notificationText())
        return try {
            if (types == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Nothing this service may do without location or microphone permission.
                false
            } else {
                ServiceCompat.startForeground(this, SosNotifier.ID_OWNER, notification, types)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            false
        }
    }

    // ------------------------------------------------------------------ location

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!hasLocation()) {
            SosRuntime.update { it.copy(gpsProblem = GpsProblem.NO_PERMISSION) }
            return
        }
        // Show something immediately: the last position Android knows, clearly marked as such.
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && lastLocation == null) publish(loc, fromLastKnown = true)
        }
        requestUpdates(AdaptiveInterval.FAST_MS)
        checkGpsHealth()
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalMs: Long) {
        if (!hasLocation()) return
        callback?.let { fused.removeLocationUpdates(it) }
        requestedInterval = intervalMs
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) checkGpsHealth()
            }
        }
        callback = cb
        fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    private fun onLocation(loc: Location) {
        val moved = lastLocation?.distanceTo(loc)
        val battery = batteryState()
        val next = adaptive.onFix(if (loc.hasSpeed()) loc.speed else null, moved, battery.first, battery.second)
        val sinceLast = System.currentTimeMillis() - SosRuntime.owner.value.lastPublishMs
        if (sinceLast >= requestedInterval - 1_000 || SosRuntime.owner.value.lastFix?.fromLastKnown != false) {
            publish(loc, fromLastKnown = false)
        }
        if (next != requestedInterval) requestUpdates(next)
    }

    private fun publish(loc: Location, fromLastKnown: Boolean) {
        val sid = sessionId ?: return
        val r = repo ?: return
        if (!SosRuntime.owner.value.locationOn) return
        // Age of the fix on the phone's monotonic clock, stamped in server time (phone clocks drift).
        val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        val ts = System.currentTimeMillis() + serverOffsetMs - ageMs.coerceAtLeast(0)
        val fix = LiveFix(
            lat = loc.latitude,
            lng = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0,
            ts = ts,
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            fromLastKnown = fromLastKnown
        )
        r.publishFix(sid, fix)
        if (!fromLastKnown) {
            lastLocation = loc
            if (System.currentTimeMillis() - lastTrailMs >= 60_000) {
                lastTrailMs = System.currentTimeMillis()
                r.appendTrail(sid, fix)
            }
        }
        SosRuntime.update {
            it.copy(lastFix = fix, lastPublishMs = System.currentTimeMillis(), intervalMs = requestedInterval, gpsProblem = GpsProblem.NONE)
        }
    }

    private fun checkGpsHealth() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val problem = when {
            !hasLocation() -> GpsProblem.NO_PERMISSION
            lm != null && !LocationManagerCompat.isLocationEnabled(lm) -> GpsProblem.LOCATION_OFF
            System.currentTimeMillis() - SosRuntime.owner.value.lastPublishMs > 45_000 -> GpsProblem.WAITING_FOR_FIX
            else -> GpsProblem.NONE
        }
        SosRuntime.update { it.copy(gpsProblem = problem) }
    }

    private fun batteryState(): Pair<Int?, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null to false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        return pct to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun setLocationSharing(on: Boolean) {
        val sid = sessionId ?: return
        SosRuntime.update { it.copy(locationOn = on) }
        if (on) {
            startLocation()
        } else {
            // Stops immediately: no more updates leave the phone.
            callback?.let { fused.removeLocationUpdates(it) }
            callback = null
        }
        scope.launch { runCatching { repo?.setLocationOn(sid, on) } }
        updateNotification()
    }

    // ------------------------------------------------------------------ audio

    private fun startAudio() {
        val sid = sessionId ?: return
        val r = repo ?: return
        val uid = r.myUid ?: return
        if (broadcaster != null) return
        val b = SosAudioBroadcaster(this, r, sid, uid)
        broadcaster = b
        b.start()
        listenJob = scope.launch {
            b.connected.collect { set ->
                SosRuntime.update { it.copy(listening = set, mic = if (set.isEmpty()) MicStatus.READY else MicStatus.LIVE) }
                updateNotification()
            }
        }
    }

    private fun stopAudio() {
        listenJob?.cancel()
        listenJob = null
        broadcaster?.stop()
        broadcaster = null
        SosRuntime.update { it.copy(listening = emptySet(), mic = MicStatus.OFF) }
    }

    private fun setAudio(on: Boolean) {
        audioOn = on && hasMic()
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUDIO, on).apply()
        if (audioOn) {
            promote()
            startAudio()
            SosRuntime.update { it.copy(mic = micStatus(true)) }
        } else {
            stopAudio()
            SosRuntime.update { it.copy(mic = micStatus(on)) }
            // Drop the microphone type so Android's mic indicator goes away.
            promote()
        }
        updateNotification()
    }

    // ------------------------------------------------------------------ notification + teardown

    private fun notificationText(): String {
        val s = SosRuntime.owner.value
        val loc = if (s.locationOn) "লোকেশন শেয়ার হচ্ছে" else "লোকেশন শেয়ার বন্ধ"
        val mic = when (s.mic) {
            MicStatus.LIVE -> " · মাইক্রোফোন চালু (${s.listening.size} জন শুনছেন)"
            MicStatus.READY -> " · মাইক্রোফোন প্রস্তুত"
            else -> ""
        }
        return "$loc$mic। বন্ধ করতে অ্যাপ খুলুন।"
    }

    private fun updateNotification() {
        if (sessionId == null) return
        SosNotifier.post(this, SosNotifier.ID_OWNER, SosNotifier.ownerOngoing(this, notificationText()))
    }

    private fun finish() {
        val sid = sessionId
        sessionId = null
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        stopAudio()
        loopJob?.cancel()
        connectedJob?.cancel()
        sessionWatch?.let { (ref, l) -> ref.removeEventListener(l) }
        offsetWatch?.let { (ref, l) -> ref.removeEventListener(l) }
        sessionWatch = null
        offsetWatch = null
        if (sid != null) repo?.cancelOnlinePresence(sid)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SosRuntime.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (sessionId != null) {
            // Killed without an end: keep the saved session so a restart (START_STICKY) resumes it.
            callback?.let { fused.removeLocationUpdates(it) }
            broadcaster?.stop()
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PrivateSosService"
        private const val ACTION_START = "com.example.sos.START"
        private const val ACTION_STOP = "com.example.sos.STOP"
        private const val ACTION_AUDIO = "com.example.sos.AUDIO"
        private const val ACTION_LOCATION = "com.example.sos.LOCATION"
        private const val EXTRA_SID = "sid"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_RESUME = "resume"
        const val PREFS = "rokkhon_private_sos"
        private const val KEY_SID = "active_sid"
        private const val KEY_AUDIO = "audio"

        fun savedSessionId(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SID, null)

        /** [resume]: carry on with a session that is already running (no second alert to trusted people). */
        fun start(context: Context, sessionId: String, audio: Boolean, resume: Boolean = false): Boolean {
            val i = Intent(context, PrivateSosService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_SID, sessionId).putExtra(EXTRA_AUDIO, audio).putExtra(EXTRA_RESUME, resume)
            return runCatching { ContextCompat.startForegroundService(context, i) }.isSuccess
        }

        fun setAudio(context: Context, on: Boolean) {
            runCatching { context.startService(Intent(context, PrivateSosService::class.java).setAction(ACTION_AUDIO).putExtra(EXTRA_AUDIO, on)) }
        }

        fun setLocation(context: Context, on: Boolean) {
            runCatching { context.startService(Intent(context, PrivateSosService::class.java).setAction(ACTION_LOCATION).putExtra(EXTRA_LOCATION, on)) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, PrivateSosService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
