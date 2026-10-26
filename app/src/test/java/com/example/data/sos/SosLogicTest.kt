package com.example.data.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosLogicTest {

    @Test
    fun sessionIdsAreRandom128BitHex() {
        val ids = (1..500).map { SosIds.newSessionId() }.toSet()
        assertEquals(500, ids.size)
        ids.forEach { assertTrue(it, SosIds.isSessionId(it)) }
        assertFalse(SosIds.isSessionId("abc"))
        assertFalse(SosIds.isSessionId("0123456789ABCDEF0123456789ABCDEF"))
    }

    @Test
    fun emailKeyMatchesTheRulesTransformation() {
        // Rules: auth.token.email.toLowerCase().replace('.', ',')
        assertEquals("rafi,test@gmail,com", SosIds.emailKey(" Rafi.Test@Gmail.com "))
        assertNull(SosIds.emailKey("not-an-email"))
        assertNull(SosIds.emailKey("a#b@x.com"))
    }

    @Test
    fun phoneKeyNormalisesBangladeshiNumbers() {
        // Rules: auth.token.phone_number.replace('+', '') — E.164 digits.
        assertEquals("8801712345678", SosIds.phoneKey("01712345678"))
        assertEquals("8801712345678", SosIds.phoneKey("+880 1712-345678"))
        assertEquals("8801712345678", SosIds.phoneKey("008801712345678"))
        assertEquals("8801712345678", SosIds.phoneKey("1712345678"))
        assertNull(SosIds.phoneKey("123"))
    }

    @Test
    fun liveLinksYieldOnlyValidSessionIds() {
        val id = SosIds.newSessionId()
        assertEquals(id, SosIds.sessionIdFromLink("https://rokkhon.com/live/$id"))
        assertEquals(id, SosIds.sessionIdFromLink("https://rokkhon.com/live/$id/?utm=x"))
        assertNull(SosIds.sessionIdFromLink("https://rokkhon.com/live/../admin"))
        assertNull(SosIds.sessionIdFromLink("https://rokkhon.com/"))
        assertNull(SosIds.sessionIdFromLink(null))
    }

    @Test
    fun adaptiveIntervalSlowsDownWhenStillOrLowBattery() {
        val a = AdaptiveInterval()
        assertEquals(AdaptiveInterval.FAST_MS, a.onFix(2f, 20f, 80, false))
        // Three still fixes in a row: back off to 10 s.
        a.onFix(0f, 2f, 80, false)
        a.onFix(0f, 2f, 80, false)
        assertEquals(AdaptiveInterval.SLOW_MS, a.onFix(0f, 2f, 80, false))
        // Moving again: fast immediately.
        assertEquals(AdaptiveInterval.FAST_MS, a.onFix(3f, 30f, 80, false))
        // Low battery and not charging: slow even when moving; charging lifts it.
        assertEquals(AdaptiveInterval.SLOW_MS, a.onFix(3f, 30f, 15, false))
        assertEquals(AdaptiveInterval.FAST_MS, a.onFix(3f, 30f, 15, true))
    }

    @Test
    fun permsSummaries() {
        assertTrue(SosPerms(location = false, audio = false, alerts = true, history = false).anyLive)
        assertFalse(SosPerms(location = false, audio = false, alerts = false, history = true).anyLive)
        assertFalse(SosPerms(false, false, false, false).any)
        assertEquals(GrantStatus.ACCEPTED, GrantStatus.from("accepted"))
        assertEquals(GrantStatus.PENDING, GrantStatus.from("garbage"))
    }
}
