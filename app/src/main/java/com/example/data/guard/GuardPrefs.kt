package com.example.data.guard

import android.content.Context

/**
 * Small, process-independent state the background guard needs when the app screen is closed:
 * whether the guard is on, the user's donor profile (for Bluetooth blood alerts) and the last
 * real GPS fix (for the 1 km SOS check).
 */
object GuardPrefs {
    /** Same file as the app-wide preferences used by the view model. */
    private const val PREFS = "rokkhon_app_prefs"
    private const val KEY_ENABLED = "guard_network_enabled"
    private const val KEY_BLOOD = "guard_profile_blood"
    private const val KEY_AREA_HASH = "guard_profile_area_hash"
    private const val KEY_ELIGIBLE = "guard_profile_eligible"
    private const val KEY_LAT = "guard_last_lat"
    private const val KEY_LNG = "guard_last_lng"
    private const val KEY_FIX_AT = "guard_last_fix_at"
    const val KEY_LANGUAGE = "app_language"

    /** A location older than this is not trusted for the 1 km decision. */
    private const val FIX_MAX_AGE_MS = 2L * 60L * 60L * 1000L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isBengali(context: Context): Boolean = prefs(context).getString(KEY_LANGUAGE, "bn") != "en"

    data class DonorProfile(val bloodGroup: String, val areaHash: Int, val eligible: Boolean)

    fun saveDonorProfile(context: Context, profile: DonorProfile?) {
        val editor = prefs(context).edit()
        if (profile == null) {
            editor.remove(KEY_BLOOD).remove(KEY_AREA_HASH).remove(KEY_ELIGIBLE)
        } else {
            editor.putString(KEY_BLOOD, profile.bloodGroup)
                .putInt(KEY_AREA_HASH, profile.areaHash)
                .putBoolean(KEY_ELIGIBLE, profile.eligible)
        }
        editor.apply()
    }

    fun donorProfile(context: Context): DonorProfile? {
        val p = prefs(context)
        val blood = p.getString(KEY_BLOOD, null) ?: return null
        return DonorProfile(blood, p.getInt(KEY_AREA_HASH, -1), p.getBoolean(KEY_ELIGIBLE, false))
    }

    fun saveLastLocation(context: Context, lat: Double, lng: Double) {
        prefs(context).edit()
            .putLong(KEY_LAT, lat.toRawBits())
            .putLong(KEY_LNG, lng.toRawBits())
            .putLong(KEY_FIX_AT, System.currentTimeMillis())
            .apply()
    }

    /** Last real GPS fix as (lat, lng), or null when unknown or too old. */
    fun lastLocation(context: Context): Pair<Double, Double>? {
        val p = prefs(context)
        val at = p.getLong(KEY_FIX_AT, 0L)
        if (at == 0L || System.currentTimeMillis() - at > FIX_MAX_AGE_MS) return null
        return Double.fromBits(p.getLong(KEY_LAT, 0L)) to Double.fromBits(p.getLong(KEY_LNG, 0L))
    }
}
