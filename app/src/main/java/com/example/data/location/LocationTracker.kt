package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Thin wrapper around the fused location provider.
 * Used for the one-shot "where am I" lookup and for real continuous updates while an SOS is live
 * (the old code moved the pin with fake random jitter instead of reading GPS).
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private var liveCallback: LocationCallback? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(onLocation: (lat: Double, lng: Double) -> Unit) {
        if (!hasPermission()) return
        try {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        onLocation(loc.latitude, loc.longitude)
                    } else {
                        client.lastLocation.addOnSuccessListener { last ->
                            if (last != null) onLocation(last.latitude, last.longitude)
                        }
                    }
                }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveUpdates(onLocation: (lat: Double, lng: Double) -> Unit): Boolean {
        if (!hasPermission()) return false
        stopLiveUpdates()
        return try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L)
                .setMinUpdateDistanceMeters(5f)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocation(it.latitude, it.longitude) }
                }
            }
            LocationServices.getFusedLocationProviderClient(appContext)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
            liveCallback = callback
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopLiveUpdates() {
        val callback = liveCallback ?: return
        try {
            LocationServices.getFusedLocationProviderClient(appContext).removeLocationUpdates(callback)
        } catch (_: Exception) {
        }
        liveCallback = null
    }
}
