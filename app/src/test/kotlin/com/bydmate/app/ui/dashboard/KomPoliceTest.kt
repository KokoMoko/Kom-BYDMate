package com.bydmate.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KomPoliceTest {
    @Test fun overOnlyPastLimitPlusTolerance() {
        assertFalse(KomPolice.isOver(95f, 90, fresh = true, tolerance = 5))
        assertTrue(KomPolice.isOver(96f, 90, fresh = true, tolerance = 5))
    }

    @Test fun staleOrUnknownLimitNeverCallsTheOfficer() {
        assertFalse(KomPolice.isOver(150f, 90, fresh = false, tolerance = 5))
        assertFalse(KomPolice.isOver(150f, 0, fresh = true, tolerance = 5))
    }

    @Test fun whistleIsTwoBlastsLong() {
        val pcm = KomPolice.whistlePcm(44_100)
        assertEquals(((0.22 + 0.10 + 0.55) * 44_100).toInt(), pcm.size)
        assertTrue(pcm.any { it > 10_000 })
    }
}
