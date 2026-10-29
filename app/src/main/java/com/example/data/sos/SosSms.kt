package com.example.data.sos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.model.TrustedContact
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The numbers that get an SMS from this phone when an SOS starts. Stored only on the device. */
object TrustedSmsContacts {
    private const val KEY = "trusted_contacts"

    // Same file and format the older trusted-contacts screen used, so saved numbers carry over.
    private fun prefs(context: Context, uid: String) =
        context.getSharedPreferences("rokkhon_guardian_state_${uid.take(32)}", Context.MODE_PRIVATE)

    fun load(context: Context, uid: String): List<TrustedContact> =
        (prefs(context, uid).getString(KEY, "") ?: "").split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size == 2 && parts[1].isNotBlank()) TrustedContact(parts[0], parts[1]) else null
        }

    fun save(context: Context, uid: String, contacts: List<TrustedContact>) {
        prefs(context, uid).edit().putString(KEY, contacts.joinToString("\n") { "${it.name}\t${it.phone}" }).apply()
    }
}

/** What the SOS texts say. Pure, so it can be tested. */
object SosSmsText {

    /** "01712345678", "8801712345678" and "+8801712345678" all become "+8801712345678". */
    fun destination(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        val local = if (digits.startsWith("880")) "0" + digits.drop(3) else digits
        return "+88$local"
    }

    /** The first SMS, or a newer position while the SOS is still running ([update]). */
    fun alert(ownerName: String, liveUrl: String, mapsUrl: String?, update: Boolean): String = buildString {
        appendLine(if (update) "আমার নতুন অবস্থান — SOS এখনও চলছে।" else "জরুরি! আমি বিপদে আছি, সাহায্য করুন।")
        appendLine("লাইভ লোকেশন (Rokkhon): $liveUrl")
        if (mapsUrl != null) appendLine("এখনকার অবস্থান: $mapsUrl")
        append("— ${ownerName.ifBlank { "Rokkhon" }}")
    }

    fun safe(ownerName: String): String =
        "আমি এখন নিরাপদ, SOS শেষ করেছি। চিন্তা করবেন না।\n— ${ownerName.ifBlank { "Rokkhon" }}"
}

/** The phone's SMS sender; replaced in tests, which have no SIM. */
internal interface SmsTransport {
    fun divide(body: String): ArrayList<String>
    fun send(destination: String, parts: ArrayList<String>, sentIntents: ArrayList<PendingIntent?>)
}

private class SmsManagerTransport(private val sms: SmsManager) : SmsTransport {
    override fun divide(body: String): ArrayList<String> = sms.divideMessage(body)
    override fun send(destination: String, parts: ArrayList<String>, sentIntents: ArrayList<PendingIntent?>) =
        sms.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
}

/**
 * [ok] numbers whose text left the phone, [failed] the rest ([failedNumbers], as dialled).
 * This is not a delivery receipt.
 */
data class SmsOutcome(val ok: Int, val failed: Int, val failedNumbers: Set<String> = emptySet())

/**
 * Sends the SOS text itself (no SMS app to open), through the phone's own SIM. Needs the SMS
 * permission; without it the caller falls back to opening the SMS app with the text filled in.
 */
object SosSms {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasTelephony(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    @Suppress("DEPRECATION")
    private fun manager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    /** A recent position the phone already knows, so the first SMS can carry it. */
    @SuppressLint("MissingPermission")
    fun lastKnownPosition(context: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < 15 * 60_000L }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }

    /**
     * Texts [body] to every contact and waits (up to [timeoutMs]) for the phone to report whether
     * each message was handed to the network.
     */
    suspend fun send(context: Context, contacts: List<TrustedContact>, body: String, timeoutMs: Long = 40_000L): SmsOutcome =
        send(context, contacts, body, timeoutMs, transport = null)

    internal suspend fun send(
        context: Context,
        contacts: List<TrustedContact>,
        body: String,
        timeoutMs: Long,
        transport: SmsTransport?
    ): SmsOutcome =
        withContext(Dispatchers.IO) {
            if (contacts.isEmpty()) return@withContext SmsOutcome(0, 0)
            val app = context.applicationContext
            val allFailed = SmsOutcome(0, contacts.size, contacts.map { SosSmsText.destination(it.phone) }.toSet())
            val sms = transport ?: manager(app)?.let { SmsManagerTransport(it) } ?: return@withContext allFailed
            val parts = runCatching { sms.divide(body) }.getOrNull() ?: return@withContext allFailed
            val perContact = parts.size

            val action = "${app.packageName}.SOS_SMS_SENT.${System.nanoTime()}"
            val partOk = ConcurrentHashMap<Int, Boolean>()
            val pending = AtomicInteger(contacts.size * perContact)
            val done = CompletableDeferred<Unit>()

            fun record(key: Int, ok: Boolean) {
                if (partOk.putIfAbsent(key, ok) == null && pending.decrementAndGet() == 0) done.complete(Unit)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    record(intent.getIntExtra(EXTRA_KEY, -1), resultCode == Activity.RESULT_OK)
                }
            }
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            try {
                contacts.forEachIndexed { index, contact ->
                    val sentIntents = ArrayList<PendingIntent?>(perContact)
                    for (part in 0 until perContact) {
                        val key = index * perContact + part
                        sentIntents += PendingIntent.getBroadcast(
                            app, key,
                            Intent(action).setPackage(app.packageName).putExtra(EXTRA_KEY, key),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }
                    try {
                        sms.send(SosSmsText.destination(contact.phone), parts, sentIntents)
                    } catch (e: Exception) {
                        // Bad number, no permission, no SIM: this contact's parts all failed.
                        for (part in 0 until perContact) record(index * perContact + part, false)
                    }
                }
                withTimeoutOrNull(timeoutMs) { done.await() }
                val failed = contacts.filterIndexed { index, _ ->
                    !(0 until perContact).all { part -> partOk[index * perContact + part] == true }
                }.map { SosSmsText.destination(it.phone) }.toSet()
                SmsOutcome(contacts.size - failed.size, failed.size, failed)
            } finally {
                runCatching { app.unregisterReceiver(receiver) }
            }
        }

    private const val EXTRA_KEY = "k"
}
