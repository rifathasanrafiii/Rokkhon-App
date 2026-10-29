package com.example.data.sos

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TrustedContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SosSmsTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val sid = "0123456789abcdef0123456789abcdef"
    private val live = "https://rokkhon.com/live/$sid"

    @Test
    fun numbersBecomeInternationalFormat() {
        assertEquals("+8801712345678", SosSmsText.destination("01712345678"))
        assertEquals("+8801712345678", SosSmsText.destination("8801712345678"))
        assertEquals("+8801712345678", SosSmsText.destination("+8801712345678"))
        assertEquals("+8801712345678", SosSmsText.destination("017 1234-5678"))
    }

    @Test
    fun firstTextCarriesTheLiveLinkAndThePosition() {
        val text = SosSmsText.alert("রাফি", live, "https://maps.google.com/?q=23.780600,90.407000", update = false)
        assertTrue(text.startsWith("জরুরি!"))
        assertTrue(text.contains(live))
        assertTrue(text.contains("https://maps.google.com/?q=23.780600,90.407000"))
        assertTrue(text.endsWith("— রাফি"))
    }

    @Test
    fun withoutAPositionOnlyTheLiveLinkIsSent() {
        val text = SosSmsText.alert("", live, null, update = false)
        assertTrue(text.contains(live))
        assertFalse(text.contains("maps.google.com"))
        assertTrue(text.endsWith("— Rokkhon"))
    }

    @Test
    fun updateTextSaysTheSosIsStillRunning() {
        val text = SosSmsText.alert("রাফি", live, "https://maps.google.com/?q=1,2", update = true)
        assertTrue(text.contains("এখনও চলছে"))
        assertFalse(text.startsWith("জরুরি!"))
    }

    /** A phone whose SMS radio accepts a message unless its number is in [rejects]. */
    private class FakeSms(val rejects: Set<String> = emptySet(), val silent: Boolean = false) : SmsTransport {
        val sent = mutableListOf<Pair<String, List<String>>>()
        override fun divide(body: String): ArrayList<String> = ArrayList(body.chunked(60))
        override fun send(destination: String, parts: ArrayList<String>, sentIntents: ArrayList<PendingIntent?>) {
            sent += destination to parts.toList()
            if (destination in rejects) throw IllegalArgumentException("Invalid destinationAddress")
            if (!silent) sentIntents.forEach { it?.send(Activity.RESULT_OK) }
        }
    }

    private fun sendAndPumpLooper(contacts: List<TrustedContact>, body: String, sms: SmsTransport, timeoutMs: Long = 5_000): SmsOutcome {
        val job = CoroutineScope(Dispatchers.IO).async { SosSms.send(app, contacts, body, timeoutMs, sms) }
        // The sent-callbacks arrive on the main looper, which the test thread owns.
        while (!job.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return runBlocking { job.await() }
    }

    @Test
    fun everyNumberGetsTheWholeTextAndIsCountedWhenTheRadioAcceptsIt() {
        val sms = FakeSms()
        val body = SosSmsText.alert("রাফি", live, "https://maps.google.com/?q=23.780600,90.407000", update = false)
        val out = sendAndPumpLooper(listOf(TrustedContact("মা", "01712345678"), TrustedContact("ভাই", "01912345678")), body, sms)
        assertEquals(listOf("+8801712345678", "+8801912345678"), sms.sent.map { it.first })
        sms.sent.forEach { assertEquals(body, it.second.joinToString("")) }
        assertTrue(sms.sent[0].second.size > 1)
        assertEquals(SmsOutcome(ok = 2, failed = 0), out)
    }

    @Test
    fun aRejectedNumberIsReportedAsFailedAndTheOthersStillGo() {
        val sms = FakeSms(rejects = setOf("+8801912345678"))
        val out = sendAndPumpLooper(
            listOf(TrustedContact("মা", "01712345678"), TrustedContact("ভাই", "01912345678"), TrustedContact("বন্ধু", "01812345678")),
            "জরুরি", sms
        )
        assertEquals(3, sms.sent.size)
        assertEquals(SmsOutcome(ok = 2, failed = 1), out)
    }

    @Test
    fun noReportFromTheRadioIsNotCountedAsSent() {
        val out = sendAndPumpLooper(listOf(TrustedContact("মা", "01712345678")), "জরুরি", FakeSms(silent = true), timeoutMs = 200)
        assertEquals(SmsOutcome(ok = 0, failed = 1), out)
    }

    @Test
    fun noContactsMeansNothingIsSent() = runBlocking {
        assertEquals(SmsOutcome(0, 0), SosSms.send(app, emptyList(), "x", timeoutMs = 50))
    }
}
