package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time device location & directional movement tracker:
 * 1. FusedLocationProviderClient at 1s interval and 0m threshold.
 * 2. Native Android LocationManager (GPS_PROVIDER + NETWORK_PROVIDER) at 0m threshold.
 * 3. Compass heading (ROTATION_VECTOR / ACCELEROMETER + MAGNETIC_FIELD) + Pedestrian Dead-Reckoning
 *    (STEP_DETECTOR + accelerometer stride detection) so moving in any direction immediately
 *    moves the live coordinate in that exact direction in real time.
 */
class LocationTracker(private val context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var activeCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null
    private var sensorListener: SensorEventListener? = null

    @Volatile
    var currentBearingDegrees: Float = 0f
        private set

    @Volatile
    private var currentLat: Double = Double.NaN

    @Volatile
    private var currentLng: Double = Double.NaN

    @Volatile
    private var lastGpsAnchorMs: Long = 0L

    private var liveLocationCallback: ((Double, Double) -> Unit)? = null
    private var liveBearingCallback: ((Float) -> Unit)? = null

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(onResult: (lat: Double, lng: Double) -> Unit) {
        if (!hasPermission()) return

        // 1. Immediately emit best last-known location from native providers if available
        try {
            val gpsLast = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLast = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                gpsLast != null && netLast != null -> if (gpsLast.time >= netLast.time - 60_000L) gpsLast else netLast
                gpsLast != null -> gpsLast
                else -> netLast
            }
            if (bestLast != null && bestLast.latitude != 0.0 && bestLast.longitude != 0.0) {
                anchorFromGpsLocation(bestLast)
                onResult(currentLat, currentLng)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native lastKnownLocation read failed", e)
        }

        // 2. Request fresh high-accuracy fix from FusedLocationProviderClient
        try {
            val tokenSource = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                        anchorFromGpsLocation(loc)
                        onResult(currentLat, currentLng)
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { last ->
                            if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
                                anchorFromGpsLocation(last)
                                onResult(currentLat, currentLng)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getCurrentLocation failed", e)
                }
        } catch (e: Throwable) {
            Log.w(TAG, "requestCurrentLocation error", e)
        }
    }

    /**
     * Starts continuous real-time GPS + Compass + Step Dead-Reckoning updates.
     * Every physical movement or GPS tick invokes [onLocation] in the exact direction of motion.
     */
    @SuppressLint("MissingPermission")
    fun startLiveUpdates(
        intervalMillis: Long = 1_000L,
        highAccuracy: Boolean = true,
        minDistanceMeters: Float = 0f,
        onBearing: ((bearingDegrees: Float) -> Unit)? = null,
        onLocation: (lat: Double, lng: Double) -> Unit
    ) {
        if (!hasPermission()) return
        stopLiveUpdates()

        liveLocationCallback = onLocation
        liveBearingCallback = onBearing

        // 1. Seed initial coordinates if not yet set
        requestCurrentLocation { lat, lng ->
            onLocation(lat, lng)
        }

        // 2. FusedLocationProviderClient continuous stream (0m minimum distance for real-time responsiveness)
        try {
            val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, intervalMillis)
                .setMinUpdateIntervalMillis((intervalMillis / 2).coerceAtLeast(350L))
                .setMaxUpdateDelayMillis(intervalMillis)
                .setMinUpdateDistanceMeters(minDistanceMeters)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    if (loc.latitude != 0.0 && loc.longitude != 0.0) {
                        handleIncomingHardwareLocation(loc)
                    }
                }
            }
            activeCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Throwable) {
            Log.w(TAG, "Fused requestLocationUpdates failed", e)
        }

        // 3. Native LocationManager (GPS + Network) for zero-lag hardware updates
        // Explicitly override onStatusChanged, onProviderEnabled, and onProviderDisabled so
        // Android 10 (API 29) and below never throw java.lang.AbstractMethodError.
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (loc.latitude != 0.0 && loc.longitude != 0.0) {
                        handleIncomingHardwareLocation(loc)
                    }
                }

                @Deprecated("Deprecated in API 29, required for Android 9 and below")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
            nativeLocationListener = listener
            val minTime = intervalMillis.coerceAtLeast(500L)
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTime,
                    minDistanceMeters,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTime,
                    minDistanceMeters,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Native LocationManager registration failed", e)
        }

        // 4. Real-Time Compass + Step Dead-Reckoning when highAccuracy is enabled
        if (highAccuracy) {
            startCompassAndDeadReckoningSensors()
        }
    }

    private fun anchorFromGpsLocation(loc: Location) {
        if (currentLat.isNaN() || currentLng.isNaN()) {
            currentLat = loc.latitude
            currentLng = loc.longitude
            lastGpsAnchorMs = System.currentTimeMillis()
            return
        }
        val distMeters = distanceMeters(currentLat, currentLng, loc.latitude, loc.longitude)
        val accuracy = if (loc.hasAccuracy()) loc.accuracy else 15f
        // If GPS jumped significantly or accuracy is sharp, blend smoothly toward the new hardware fix
        if (distMeters > accuracy * 0.8f || distMeters > 8.0) {
            val prevLat = currentLat
            val prevLng = currentLng
            currentLat = loc.latitude
            currentLng = loc.longitude
            if (loc.hasBearing() && loc.speed >= 0.8f) {
                updateSmoothedBearing(loc.bearing)
            } else if (distMeters >= 1.5) {
                val computedBearing = bearingBetween(prevLat, prevLng, currentLat, currentLng)
                updateSmoothedBearing(computedBearing)
            }
        } else if (distMeters >= 0.6 && accuracy <= 12f) {
            // Smooth 50% blend for fine GPS movement
            val prevLat = currentLat
            val prevLng = currentLng
            currentLat = prevLat * 0.45 + loc.latitude * 0.55
            currentLng = prevLng * 0.45 + loc.longitude * 0.55
            if (loc.hasBearing() && loc.speed >= 0.8f) {
                updateSmoothedBearing(loc.bearing)
            }
        }
        lastGpsAnchorMs = System.currentTimeMillis()
    }

    private fun handleIncomingHardwareLocation(loc: Location) {
        anchorFromGpsLocation(loc)
        if (!currentLat.isNaN() && !currentLng.isNaN()) {
            liveLocationCallback?.invoke(currentLat, currentLng)
        }
    }

    private fun startCompassAndDeadReckoningSensors() {
        val sm = sensorManager ?: return
        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val linearAccel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMag = false
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        var lastStepMs = 0L
        var filteredDynamicMag = 0f
        var wasAboveThreshold = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        try {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            val azimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()).toFloat()) + 360f) % 360f
                            updateSmoothedBearing(azimuthDeg)
                        } catch (_: Exception) {
                        }
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val alpha = 0.85f
                        gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                        hasGravity = true

                        if (rotationSensor == null && hasMag) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                                val azimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()).toFloat()) + 360f) % 360f
                                updateSmoothedBearing(azimuthDeg)
                            }
                        }

                        // Fallback stride detector when walking if hardware TYPE_STEP_DETECTOR hasn't fired
                        val dx = event.values[0] - gravity[0]
                        val dy = event.values[1] - gravity[1]
                        val dz = event.values[2] - gravity[2]
                        val dynamicMag = sqrt(dx * dx + dy * dy + dz * dz)
                        filteredDynamicMag = filteredDynamicMag * 0.7f + dynamicMag * 0.3f

                        val now = System.currentTimeMillis()
                        if (filteredDynamicMag > 1.85f && !wasAboveThreshold && now - lastStepMs > 360L) {
                            wasAboveThreshold = true
                            lastStepMs = now
                            advancePositionByStep(stepMeters = 0.72)
                        } else if (filteredDynamicMag < 1.05f) {
                            wasAboveThreshold = false
                        }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val alpha = 0.85f
                        geomagnetic[0] = alpha * geomagnetic[0] + (1f - alpha) * event.values[0]
                        geomagnetic[1] = alpha * geomagnetic[1] + (1f - alpha) * event.values[1]
                        geomagnetic[2] = alpha * geomagnetic[2] + (1f - alpha) * event.values[2]
                        hasMag = true
                    }

                    Sensor.TYPE_STEP_DETECTOR -> {
                        val now = System.currentTimeMillis()
                        if (now - lastStepMs > 280L) {
                            lastStepMs = now
                            advancePositionByStep(stepMeters = 0.75)
                        }
                    }

                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        val mag = sqrt(ax * ax + ay * ay + az * az)
                        val now = System.currentTimeMillis()
                        if (mag > 2.1f && now - lastStepMs > 360L) {
                            lastStepMs = now
                            advancePositionByStep(stepMeters = 0.70)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorListener = listener
        try {
            if (rotationSensor != null) {
                sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (accelSensor != null) {
                sm.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (rotationSensor == null && magSensor != null) {
                sm.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (stepDetector != null) {
                sm.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_UI)
            }
            if (linearAccel != null) {
                sm.registerListener(listener, linearAccel, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sensor registration failed", e)
        }
    }

    private fun updateSmoothedBearing(newDeg: Float) {
        val normalized = ((newDeg % 360f) + 360f) % 360f
        var diff = normalized - currentBearingDegrees
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        if (kotlin.math.abs(diff) < 1.2f) return
        currentBearingDegrees = ((currentBearingDegrees + diff * 0.28f) + 360f) % 360f
        liveBearingCallback?.invoke(currentBearingDegrees)
    }

    /**
     * Advances the live coordinate in the exact direction [currentBearingDegrees] when the user moves.
     */
    fun advancePositionByStep(stepMeters: Double = 0.75, overrideBearingDegrees: Float? = null) {
        val lat = currentLat
        val lng = currentLng
        if (lat.isNaN() || lng.isNaN()) return

        val bearing = overrideBearingDegrees ?: currentBearingDegrees
        if (overrideBearingDegrees != null) {
            currentBearingDegrees = ((overrideBearingDegrees % 360f) + 360f) % 360f
            liveBearingCallback?.invoke(currentBearingDegrees)
        }

        val bearingRad = Math.toRadians(bearing.toDouble())
        val latRad = Math.toRadians(lat)
        val metersPerDegLat = 111_132.0
        val metersPerDegLng = (111_320.0 * cos(latRad)).coerceAtLeast(10_000.0)

        val dLat = (stepMeters * cos(bearingRad)) / metersPerDegLat
        val dLng = (stepMeters * sin(bearingRad)) / metersPerDegLng

        currentLat = lat + dLat
        currentLng = lng + dLng
        liveLocationCallback?.invoke(currentLat, currentLng)
    }

    fun seedInitialCoordinatesIfUnset(lat: Double, lng: Double) {
        if (currentLat.isNaN() || currentLng.isNaN()) {
            currentLat = lat
            currentLng = lng
        }
    }

    fun stopLiveUpdates() {
        activeCallback?.let { cb ->
            try {
                fusedClient.removeLocationUpdates(cb)
            } catch (e: Exception) {
                Log.w(TAG, "removeLocationUpdates failed", e)
            }
        }
        activeCallback = null

        nativeLocationListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Native removeUpdates failed", e)
            }
        }
        nativeLocationListener = null

        sensorListener?.let { listener ->
            try {
                sensorManager?.unregisterListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Sensor unregister failed", e)
            }
        }
        sensorListener = null
        liveLocationCallback = null
        liveBearingCallback = null
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

    private fun bearingBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lng2 - lng1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = Math.toDegrees(atan2(y, x)).toFloat()
        return (theta + 360f) % 360f
    }

    companion object {
        private const val TAG = "LocationTracker"
    }
}
