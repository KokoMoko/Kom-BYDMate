package com.bydmate.app.data.trips

import com.bydmate.app.data.local.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMovingTimeTest {
    private fun p(t: Long, v: Double?) = TripPointEntity(tripId = 1, timestamp = t, lat = 0.0, lon = 0.0, speedKmh = v)

    @Test fun movingAndStandingSplit() {
        // 10 s moving, 20 s standing, 5 s moving
        val pts = listOf(p(0, 30.0), p(10_000, 0.0), p(20_000, 0.0), p(30_000, 20.0), p(35_000, 0.0))
        assertEquals(15_000L, TripMovingTime.movingMs(pts))
    }

    @Test fun longGapCountsAtMostAMinute() {
        assertEquals(60_000L, TripMovingTime.movingMs(listOf(p(0, 50.0), p(600_000, 50.0))))
    }

    @Test fun tooFewPoints() {
        assertNull(TripMovingTime.movingMs(listOf(p(0, 10.0))))
    }
}
