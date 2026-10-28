package com.example.data.push

import android.content.Context
import android.util.Log
import com.example.data.model.BangladeshDistrict
import com.example.data.model.areaPath
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM topics this phone listens to, so the admin panel can send a push to everyone, to one
 * blood group, or to one district. Topic names must match web/admin and web/api/admin-push.js.
 */
object PushTopics {
    private const val PREFS = "rokkhon_push_topics"
    private const val KEY = "subscribed"
    const val ALL = "all"

    /** "O+" → "bg_O_POS", "AB-" → "bg_AB_NEG". */
    fun bloodGroupTopic(group: String): String? {
        val g = group.trim().uppercase()
        if (g !in listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")) return null
        return "bg_" + g.dropLast(1) + if (g.endsWith("+")) "_POS" else "_NEG"
    }

    /** The district an area belongs to, as "dist_<districtKey>" (upazilas map to their district). */
    fun districtTopic(area: BangladeshDistrict): String {
        val key = areaPath(area)?.second?.main?.areaKey ?: area.areaKey
        return "dist_" + key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun wanted(bloodGroup: String?, area: BangladeshDistrict?): Set<String> = buildSet {
        add(ALL)
        bloodGroup?.let { bloodGroupTopic(it) }?.let(::add)
        area?.let { add(districtTopic(it)) }
    }

    /** Subscribes to the wanted topics and leaves old ones (blood group or area changed). */
    fun sync(context: Context, bloodGroup: String?, area: BangladeshDistrict?) {
        if (runCatching { FirebaseApp.getApps(context).isEmpty() }.getOrDefault(true)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val now = wanted(bloodGroup, area)
        if (old == now) return
        val fcm = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        (now - old).forEach { t -> fcm.subscribeToTopic(t).addOnFailureListener { Log.w("PushTopics", "subscribe $t", it) } }
        (old - now).forEach { t -> fcm.unsubscribeFromTopic(t) }
        prefs.edit().putStringSet(KEY, now).apply()
    }
}
