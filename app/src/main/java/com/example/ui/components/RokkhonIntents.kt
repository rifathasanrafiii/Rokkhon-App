package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.SosAlert
import com.example.data.model.TrustedContact
import com.example.data.model.mapsLink

/**
 * Small helpers that hand off to the phone's own apps (dialer, SMS, maps, share sheet).
 * Each one is guarded so a device without that app shows a message instead of crashing.
 */
object RokkhonIntents {

    private fun launch(context: Context, intent: Intent, language: AppLanguage) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                if (language == AppLanguage.BENGALI) "এই কাজের জন্য ফোনে কোনো অ্যাপ নেই" else "No app on this phone can do that",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
        }
    }

    fun dial(context: Context, phone: String, language: AppLanguage) {
        if (phone.isBlank()) return
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")), language)
    }

    /** Opens the SMS app to one number with the text filled in (no SMS permission needed). */
    fun sms(context: Context, phone: String, text: String, language: AppLanguage = AppLanguage.BENGALI) {
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).putExtra("sms_body", text)
        launch(context, intent, language)
    }

    /** Opens turn-by-turn directions to a point (Google Maps if installed, else any maps app). */
    fun directions(context: Context, lat: Double, lng: Double, language: AppLanguage) {
        val navUri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
        val nav = Intent(Intent.ACTION_VIEW, navUri)
        if (nav.resolveActivity(context.packageManager) != null) {
            launch(context, nav, language)
        } else {
            launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")), language)
        }
    }

    /** Pre-filled SMS to all trusted contacts; the user only has to press send (no SMS permission needed). */
    fun smsTrustedContacts(
        context: Context,
        contacts: List<TrustedContact>,
        senderName: String,
        lat: Double,
        lng: Double,
        language: AppLanguage
    ) {
        if (contacts.isEmpty()) return
        val body = sosMessage(senderName, lat, lng)
        val recipients = contacts.joinToString(";") { it.phone.trim() }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$recipients")).putExtra("sms_body", body)
        launch(context, intent, language)
    }

    fun shareLocation(context: Context, senderName: String, lat: Double, lng: Double, language: AppLanguage) {
        share(context, sosMessage(senderName, lat, lng), language)
    }

    fun shareBloodRequest(context: Context, req: BloodRequest, language: AppLanguage) {
        val text = buildString {
            appendLine("জরুরি রক্তের প্রয়োজন / Blood needed")
            appendLine("গ্রুপ: ${req.bloodGroup} • ${req.unitsNeeded} ব্যাগ")
            if (req.patientName.isNotBlank()) appendLine("রোগী: ${req.patientName}")
            if (req.patientProblem.isNotBlank() && req.patientProblem != "-") appendLine("সমস্যা: ${req.patientProblem}")
            appendLine("হাসপাতাল: ${req.hospitalName}${if (req.wardCabinNumber.isNotBlank() && req.wardCabinNumber != "-") " (${req.wardCabinNumber})" else ""}")
            appendLine("এলাকা: ${req.district}")
            if (req.neededDateTime.isNotBlank() && req.neededDateTime != "-") appendLine("কখন: ${req.neededDateTime}")
            appendLine("যোগাযোগ: ${req.contactPhone}")
            append("— রক্ষণ (Rokkhon) অ্যাপ থেকে শেয়ার করা")
        }
        share(context, text, language)
    }

    fun shareSos(context: Context, alert: SosAlert, language: AppLanguage) {
        share(context, sosMessage(alert.senderName, alert.latitude, alert.longitude), language)
    }

    private fun share(context: Context, text: String, language: AppLanguage) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, null), language)
    }

    private fun sosMessage(name: String, lat: Double, lng: Double): String =
        "জরুরি! আমি বিপদে আছি / SOS — I need help. ${name.ifBlank { "" }}\n" +
            "আমার লোকেশন / My location: ${mapsLink(lat, lng)}\n" +
            "— Rokkhon"
}
