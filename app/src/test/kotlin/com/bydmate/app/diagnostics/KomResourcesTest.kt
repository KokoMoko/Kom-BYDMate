package com.bydmate.app.diagnostics

import com.bydmate.app.helper.procCpuTicks
import com.bydmate.app.ui.settings.niceCeil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KomResourcesTest {
    @Test fun cpuTicksAreUtimePlusStimeEvenWithSpacesInTheName() {
        val stat = "1234 (bydmate helper) S 1 1234 1234 0 -1 4194560 100 0 0 0 250 75 0 0 20 0 30 0"
        assertEquals(325L, procCpuTicks(stat))
    }

    @Test fun cpuPercentIsOfOneCore() {
        // 600 ticks = 6 s of CPU over 60 s = 10% of one core
        assertEquals(10f, KomResources.cpuPercent(600, 60.0), 0.001f)
        assertEquals(0f, KomResources.cpuPercent(10, 0.0), 0f)
    }

    @Test fun samplesSurviveTheFileRoundTripWithoutHelper() {
        val s = KomResources.Sample(1_700_000_000_000, 210.5f, 8.2f, null, null)
        val back = KomResources.decode(KomResources.encode(s))!!
        assertEquals(s.appMb, back.appMb, 0.001f)
        assertNull(back.helperMb)
    }

    @Test fun chartScaleIsRound() {
        assertEquals(500f, niceCeil(237f), 0f)
        assertEquals(20f, niceCeil(14.8f), 0f)
        assertEquals(1f, niceCeil(0.9f), 0f)
    }
}
