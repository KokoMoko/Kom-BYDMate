package com.bydmate.app.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateLoadTest {
    private class Car(val load: ClimateLoad = ClimateLoad()) {
        var now = 1_000_000L
        fun tick(ac: Boolean?, fan: Int?, compW: Int?, totalW: Double?, speed: Int = 0, seconds: Int = 5): Double? {
            now += seconds * 1000L
            return load.onSample(now, ac, fan, compW, totalW, speed, charging = false)
        }
    }

    @Test fun offIsZeroAndLearnsTheBaseLoad() {
        val c = Car()
        repeat(60) { assertEquals(0.0, c.tick(false, 0, 0, 380.0)!!, 0.0) }
        assertEquals(0.38, c.load.learned().baseKw, 0.005)
        assertEquals(0.0, c.load.smoothedKw(), 0.0)
    }

    @Test fun switchingOnDropsTheRangeAtOnceAndOffRestoresItAtOnce() {
        val c = Car()
        repeat(30) { c.tick(false, 0, 0, 380.0) }
        // The compressor still at 0 on the first sample (it ramps over ~20 s on-car).
        c.tick(true, 7, 0, 440.0)
        assertTrue(c.load.smoothedKw() >= ClimateLoad.DEFAULT_AC_ON_KW)
        c.tick(false, 0, 0, 380.0)
        assertEquals(0.0, c.load.smoothedKw(), 0.0)
    }

    @Test fun climatePowerIsTheCompressorPlusTheLearnedBlowerShare() {
        val c = Car()
        repeat(30) { c.tick(false, 0, 0, 380.0) }
        // On-car fan 7: compressor 2.5 kW, battery 3.31 kW.
        repeat(60) { c.tick(true, 7, 2496, 3310.0) }
        val inst = c.tick(true, 7, 2496, 3310.0)!!
        assertEquals(3.31 - 0.38, inst, 0.02)
        assertEquals(inst, c.load.smoothedKw(), 0.05)
        // Driving: the compressor alone is measured, the blower share comes from parking.
        assertEquals(inst, c.tick(true, 7, 2496, 25_000.0, speed = 60)!!, 0.001)
    }

    @Test fun compressorCyclesAreAveraged() {
        val c = Car()
        repeat(30) { c.tick(false, 0, 0, 380.0) }
        repeat(20) {
            repeat(6) { c.tick(true, 2, 1100, 1500.0) }
            repeat(6) { c.tick(true, 2, 0, 490.0) }
        }
        assertTrue(c.load.smoothedKw() in 0.4..0.9)
    }

    @Test fun unknownAcStateIsNotAClimateReading() {
        assertNull(Car().tick(null, null, null, 380.0))
    }
}
