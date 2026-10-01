package com.bydmate.app.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRangeModelTest {
    private class Drive(val model: DrivingRangeModel = DrivingRangeModel()) {
        var now = 1_000_000L
        var km = 100.0
        var energy = 500.0
        var session = 1L
        init { model.onSample(now, km, energy, 0, session) }
        fun moving(distance: Double = 0.5, kwh: Double = 0.1, speed: Int = 60) {
            now += 30_000L
            km += distance
            energy += kwh
            model.onSample(now, km, energy, speed, session)
        }
        fun parked(minutes: Int, kwhPerMinute: Double = 0.02) {
            repeat(minutes * 2) {
                now += 30_000L
                energy += kwhPerMinute / 2
                model.onSample(now, km, energy, 0, session)
            }
        }
    }

    @Test fun provisionalUntilFiveEligibleKm() {
        val d = Drive()
        d.model.setProvisionalBaseline(18.8)
        repeat(8) { d.moving() }
        assertFalse(d.model.isLearned())
        assertEquals(18.8, d.model.average(), 0.001)
        repeat(4) { d.moving() }
        assertTrue(d.model.isLearned())
        assertEquals(20.0, d.model.average(), 0.001)
    }

    @Test fun fourHourStopExcludedFromItsBeginningAndDoesNotLeakOnResume() {
        val d = Drive()
        repeat(12) { d.moving() }
        val before = d.model.average()
        d.parked(240)
        assertEquals(before, d.model.average(), 0.001)
        assertEquals(4.8, d.model.snapshot().excludedKwh, 0.001)
        assertEquals(0.0, d.model.snapshot().pendingKwh, 0.001)
        d.moving()
        d.moving()
        assertEquals(before, d.model.average(), 0.001)
    }

    @Test fun firstFourteenMinutesRemainProvisionalThenExcludedRetroactively() {
        val d = Drive()
        d.parked(14)
        assertEquals(0.28, d.model.snapshot().pendingKwh, 0.001)
        assertEquals(0.0, d.model.snapshot().excludedKwh, 0.001)
        d.parked(1)
        assertEquals(0.30, d.model.snapshot().excludedKwh, 0.001)
        assertEquals(0.0, d.model.snapshot().pendingKwh, 0.001)
    }

    @Test fun shortTrafficStopIncludedOnResume() {
        val d = Drive()
        d.parked(5)
        d.moving()
        assertEquals(0.20, d.model.snapshot().bucketKwh, 0.001)
        d.moving()
        assertEquals(0.30, d.model.snapshot().blocks.single().kwh, 0.001)
    }

    @Test fun crawlingTrafficResetsTimerEvenWithCoarseOdometer() {
        val d = Drive()
        d.parked(14)
        d.moving(distance = 0.0, kwh = 0.001, speed = 1)
        assertNull(d.model.snapshot().stationarySinceMs)
        assertEquals(0.0, d.model.snapshot().excludedKwh, 0.001)
        d.parked(14)
        assertEquals(0.0, d.model.snapshot().excludedKwh, 0.001)
    }

    @Test fun odometerMovementDetectedWhenIntegerSpeedRoundsToZero() {
        val d = Drive()
        d.parked(14)
        d.moving(distance = 0.01, kwh = 0.001, speed = 0)
        assertNull(d.model.snapshot().stationarySinceMs)
    }

    @Test fun rollingHistorySurvivesIgnitionCyclesAndIsDistanceWeighted() {
        val d = Drive()
        repeat(20) { d.moving() } // 10 km at 20
        d.model.onSample(d.now + 1000, d.km, d.energy, 0, null)
        d.session = 2
        d.now += 2000
        d.model.onSample(d.now, d.km, d.energy, 0, d.session)
        repeat(40) { d.moving(kwh = 0.15) } // 20 km at 30
        assertEquals(26.666666, d.model.average(), 0.001)
    }

    @Test fun windowIsBoundedToLastHundredKm() {
        val d = Drive()
        repeat(200) { d.moving() } // 100 km at 20
        repeat(200) { d.moving(kwh = 0.15) } // 100 km at 30
        assertEquals(100.0, d.model.snapshot().blocks.sumOf { it.km }, 0.001)
        assertEquals(30.0, d.model.average(), 0.001)
    }

    @Test fun processRestartRecoversPendingStopWithoutDoubleCounting() {
        val d = Drive()
        d.parked(10)
        val restored = DrivingRangeModel(d.model.snapshot())
        repeat(10) {
            d.now += 30_000L
            d.energy += 0.01
            restored.onSample(d.now, d.km, d.energy, 0, d.session)
        }
        assertEquals(0.30, restored.snapshot().excludedKwh, 0.001)
    }

    @Test fun dataGapCannotConfirmStationaryDurationOrLearnUnobservedEnergy() {
        val d = Drive()
        repeat(12) { d.moving() }
        d.parked(10)
        d.now += 4 * 3_600_000L
        d.energy += 5.0
        d.model.onSample(d.now, d.km, d.energy, 0, d.session)
        assertNull(d.model.snapshot().stationarySinceMs)
        assertEquals(20.0, d.model.average(), 0.001)
        assertEquals(0.0, d.model.snapshot().pendingKwh, 0.001)
    }

    @Test fun chargingAndInvalidSamplesNeverTrainHistory() {
        val d = Drive()
        d.model.onSample(d.now + 1000, d.km, d.energy + 50, 0, 1, true)
        assertNull(d.model.snapshot().last)
        d.model.onSample(d.now + 2000, Double.NaN, d.energy, 0, 1)
        assertNull(d.model.snapshot().last)
        assertFalse(d.model.isLearned())
    }

    @Test fun counterResetAndOdometerRegressionReanchorWithoutPoisoningAverage() {
        val d = Drive()
        repeat(12) { d.moving() }
        d.now += 30_000
        d.energy = 1.0
        d.model.onSample(d.now, d.km, d.energy, 0, d.session)
        d.now += 30_000
        d.model.onSample(d.now, d.km - 10, d.energy, 0, d.session)
        assertEquals(20.0, d.model.average(), 0.001)
    }

    @Test fun validRegenerativeRecoveryIsRetained() {
        val d = Drive()
        repeat(12) { d.moving() }
        d.moving(kwh = -0.05)
        d.moving(kwh = 0.10)
        assertEquals(0.05, d.model.snapshot().blocks.last().kwh, 0.001)
        assertTrue(d.model.average() < 20.0)
    }
}
