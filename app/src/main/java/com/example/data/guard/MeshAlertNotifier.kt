package com.example.data.guard

import android.content.Context
import com.example.data.ble.BleSosMeshManager
import com.example.data.model.AppLanguage
import com.example.data.model.BleSosBeacon
import com.example.data.model.emergencyTypeLabel
import com.example.data.notification.RokkhonNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns alerts heard over the Bluetooth mesh into phone notifications. Started once per process,
 * by whichever comes first: the app screen or the background guard service.
 */
object MeshAlertNotifier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val appContext = context.applicationContext
        val mesh = BleSosMeshManager.get(appContext)
        GuardPrefs.lastLocation(appContext)?.let { (lat, lng) -> mesh.updateOwnLocation(lat, lng) }
        scope.launch {
            mesh.incomingAlerts.collect { beacon -> handle(appContext, beacon) }
        }
    }

    private fun handle(context: Context, beacon: BleSosBeacon) {
        val bn = GuardPrefs.isBengali(context)
        if (beacon.packetKind == BleSosMeshManager.PACKET_KIND_SOS_1KM) {
            val (title, body) = sosText(beacon, bn)
            RokkhonNotificationHelper.notifyOnce(context, "ble_${beacon.packetId}", title, body)
        } else {
            val me = GuardPrefs.donorProfile(context) ?: return
            val sameArea = beacon.districtAreaKey == "areahash_${me.areaHash}"
            if (!me.eligible || !sameArea || !me.bloodGroup.equals(beacon.bloodGroup, ignoreCase = true)) return
            val title = if (bn) "আপনার এলাকায় ${beacon.bloodGroup} রক্ত দরকার" else "${beacon.bloodGroup} blood needed in your area"
            val body = if (bn) {
                "${beacon.unitsNeeded} ব্যাগ ${beacon.bloodGroup} রক্ত দরকার। ব্লুটুথে কাছের এক রক্ষণ ইউজার থেকে এসেছে। বিস্তারিত দেখতে অ্যাপ খুলুন।"
            } else {
                "${beacon.unitsNeeded} bag(s) of ${beacon.bloodGroup} needed. Received over Bluetooth from a nearby Rokkhon user. Open the app for details."
            }
            RokkhonNotificationHelper.notifyOnce(context, "ble_${beacon.packetId}", title, body)
        }
    }

    internal fun sosText(beacon: BleSosBeacon, bn: Boolean): Pair<String, String> {
        val lang = if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH
        val type = emergencyTypeLabel(beacon.emergencyType, lang)
        val distance = beacon.distanceFromOriginKm
        val where = when {
            distance == null -> if (bn) "কাছেই" else "nearby"
            distance < 1.0 -> if (bn) "${"%.0f".format(distance * 1000)} মিটার দূরে" else "${"%.0f".format(distance * 1000)} m away"
            else -> if (bn) "${"%.1f".format(distance)} কি.মি. দূরে" else "${"%.1f".format(distance)} km away"
        }
        val title = if (bn) "জরুরি SOS • $where" else "SOS • $where"
        val via = when (beacon.hops) {
            0 -> if (bn) "সরাসরি ব্লুটুথে এসেছে" else "heard directly over Bluetooth"
            else -> if (bn) "${beacon.hops}টি রক্ষণ ফোন হয়ে ব্লুটুথে এসেছে" else "relayed over Bluetooth by ${beacon.hops} Rokkhon phone(s)"
        }
        val body = if (bn) {
            "$type • কাছের একজন রক্ষণ ইউজার বিপদে আছেন ($via, ইন্টারনেট লাগেনি)। লোকেশন দেখতে অ্যাপ খুলুন।"
        } else {
            "$type • A Rokkhon user near you needs help ($via, no internet needed). Open the app to see where."
        }
        return title to body
    }
}
