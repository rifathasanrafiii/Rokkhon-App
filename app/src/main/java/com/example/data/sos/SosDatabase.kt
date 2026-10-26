package com.example.data.sos

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * The Realtime Database used for the private SOS. It is separate from the Firestore database the
 * blood-donor features use: live GPS and audio signalling change every few seconds, and the access
 * rules that protect them have to live in the same database (database.rules.json).
 */
object SosDatabase {
    private const val TAG = "SosDatabase"

    @Volatile
    private var cached: FirebaseDatabase? = null

    @Volatile
    private var failed = false

    /** Null when Firebase or Realtime Database is not set up; callers show that instead of pretending. */
    fun get(context: Context): FirebaseDatabase? {
        cached?.let { return it }
        if (failed) return null
        synchronized(this) {
            cached?.let { return it }
            return try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    failed = true
                    return null
                }
                val url = context.applicationContext.getString(R.string.sos_rtdb_url).trim()
                val db = if (url.isNotEmpty()) FirebaseDatabase.getInstance(url) else FirebaseDatabase.getInstance()
                // No disk persistence on purpose: someone else's location must not stay cached on a
                // phone after access is revoked. Writes still queue in memory while the network drops.
                cached = db
                db
            } catch (e: Exception) {
                // No databaseURL in google-services.json and none configured: Realtime Database not created yet.
                Log.w(TAG, "Realtime Database not configured", e)
                failed = true
                null
            }
        }
    }

    fun isConfigured(context: Context): Boolean = get(context) != null
}
