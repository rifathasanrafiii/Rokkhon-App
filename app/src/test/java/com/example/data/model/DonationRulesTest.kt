package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DonationRulesTest {
    private val today = "2026-10-27"

    @Test
    fun recentDayAfterAnOldDonationIsAccepted() {
        assertNull(donationDateProblem("2026-10-27", "2016-10-10", today))
        assertNull(donationDateProblem("2026-10-25", "", today))
    }

    @Test
    fun futureAndOldDatesAreRefused() {
        assertEquals(DonationProblem.FUTURE, donationDateProblem("2026-10-28", "", today))
        assertEquals(DonationProblem.TOO_OLD, donationDateProblem("2026-10-20", "", today))
        assertEquals(DonationProblem.INVALID, donationDateProblem("27/10/2026", "", today))
    }

    @Test
    fun aSecondDonationInside90DaysIsRefused() {
        // Already recorded today: a second tap does nothing.
        assertEquals(DonationProblem.NOT_AFTER_LAST, donationDateProblem("2026-10-27", "2026-10-27", today))
        assertEquals(DonationProblem.TOO_SOON_AFTER_LAST, donationDateProblem("2026-10-27", "2026-09-01", today))
        assertNull(donationDateProblem("2026-10-27", "2026-07-29", today))
    }
}
