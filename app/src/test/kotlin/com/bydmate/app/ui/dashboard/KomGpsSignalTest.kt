package com.bydmate.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KomGpsSignalTest {
    @Test fun fewerThanFourSatellitesIsNoFix() {
        assertEquals(0, KomGpsSignal.barsFor(3, 45f))
    }

    @Test fun manyStrongSatellitesIsFourBars() {
        assertEquals(4, KomGpsSignal.barsFor(18, 42f))
    }

    @Test fun manyFaintSatellitesIsNotGood() {
        assertEquals(1, KomGpsSignal.barsFor(12, 15f))
    }

    @Test fun sixUsableSatellitesIsThreeBars() {
        assertEquals(3, KomGpsSignal.barsFor(6, 28f))
    }

    @Test fun fourUsableSatellitesIsWeak() {
        assertEquals(2, KomGpsSignal.barsFor(4, 25f))
    }

    @Test fun topAverageUsesTheFourStrongest() {
        assertEquals(40f, KomGpsSignal.topAverage(listOf(10f, 38f, 42f, 40f, 40f, 20f)), 0.01f)
        assertEquals(0f, KomGpsSignal.topAverage(emptyList()), 0.0f)
    }
}
