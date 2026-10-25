package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodAndSafetyRulesTest {

    @Test
    fun `O negative can give to everyone, AB positive receives from everyone`() {
        BloodGroup.ALL_LABELS.forEach { patient -> assertTrue(canDonateTo("O-", patient)) }
        BloodGroup.ALL_LABELS.forEach { donor -> assertTrue(canDonateTo(donor, "AB+")) }
    }

    @Test
    fun `incompatible groups are rejected`() {
        assertFalse(canDonateTo("A+", "O+"))
        assertFalse(canDonateTo("B-", "A-"))
        assertFalse(canDonateTo("AB-", "B+"))
        assertFalse(canDonateTo("O+", "O-"))
        assertEquals(setOf("A+", "A-", "O+", "O-"), compatibleDonorGroupsFor("A+"))
    }

    @Test
    fun `donor is not eligible within 90 days of the last donation`() {
        val day = 24L * 60L * 60L * 1000L
        val now = parseDonationDateMillis("2026-10-24")!!
        assertEquals(0, daysUntilEligible("", now))
        assertEquals(0, daysUntilEligible("Never", now))
        assertEquals(90, daysUntilEligible("2026-10-24", now))
        assertEquals(30, daysUntilEligible("2026-08-25", now))
        assertEquals(0, daysUntilEligible("2026-07-26", now + day))
    }

    @Test
    fun `donation date parser only accepts real yyyy-MM-dd dates`() {
        assertNull(parseDonationDateMillis("Available now"))
        assertNull(parseDonationDateMillis("2026-13-40"))
        assertTrue(parseDonationDateMillis("2026-02-28") != null)
    }

    @Test
    fun `available donor inside the gap is not eligible now`() {
        val recent = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        assertFalse(DonorNode(isAvailableDonor = true, lastDonationDate = recent).isEligibleNow)
        assertTrue(DonorNode(isAvailableDonor = true, lastDonationDate = "").isEligibleNow)
        assertFalse(DonorNode(isAvailableDonor = false, lastDonationDate = "").isEligibleNow)
    }

    @Test
    fun `bangladesh mobile validation`() {
        assertTrue(isValidBangladeshMobile("01712345678"))
        assertTrue(isValidBangladeshMobile("+8801912345678"))
        assertTrue(isValidBangladeshMobile("8801312345678"))
        assertFalse(isValidBangladeshMobile("+8801711"))
        assertFalse(isValidBangladeshMobile("01012345678"))
        assertFalse(isValidBangladeshMobile("0171234567"))
        assertFalse(isValidBangladeshMobile(""))
    }

    @Test
    fun `sos evidence shield needs real evidence or a responder`() {
        assertFalse(SosAlert(falseAlarmReports = 5).isShieldedByEvidenceOrResponders)
        assertTrue(SosAlert(hasLiveAudioVerification = true).isShieldedByEvidenceOrResponders)
        assertTrue(SosAlert(responderCount = 1).isShieldedByEvidenceOrResponders)
        assertTrue(SosAlert(falseAlarmReports = 3).isCommunityDisputed)
    }

    @Test
    fun `maps link uses dot decimals regardless of locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("https://maps.google.com/?q=23.806900,90.368700", mapsLink(23.8069, 90.3687))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
