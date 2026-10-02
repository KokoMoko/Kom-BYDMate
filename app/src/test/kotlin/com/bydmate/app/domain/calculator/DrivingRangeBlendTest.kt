package com.bydmate.app.domain.calculator

import com.bydmate.app.data.remote.DiParsData
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRangeBlendTest {
    private class Drive(val model: DrivingRangeModel = DrivingRangeModel()) {
        var now = 1_000_000L
        var km = 100.0
        var energy = 500.0
        var tempC: Double? = 20.0
        init { sample(0) }
        fun sample(speed: Int, charging: Boolean = false) =
            model.onSample(now, km, energy, speed, 1L, charging, tempC)
        /** [distance] km at [avg] kWh/100 km, in 0.5-km steps. */
        fun drive(distance: Double, avg: Double) {
            repeat((distance * 2).toInt()) {
                now += 30_000L
                km += 0.5
                energy += avg / 200.0
                sample(60)
            }
        }
        fun pause(minutes: Long) {
            // Ignition off: the anchor is lost, nothing is learned from the gap.
            now += minutes * 60_000L
            sample(0)
        }
        fun estimate() = model.rangeEstimate(now)
    }

    @Test fun coldStartUsesItsTemperatureBandBeforeReactiveDistance() {
        val d = Drive()
        d.drive(20.0, avg = 16.0)
        d.tempC = -5.0
        d.drive(20.0, avg = 28.0)
        d.pause(8 * 60)
        val e = d.estimate()
        assertTrue(e.priorFromTemp)
        assertEquals(-1, e.band)
        assertEquals(28.0, e.prior, 0.001)
        assertEquals(0.0, e.reactiveKm, 0.001)
        assertEquals(28.0, e.avg, 0.001)
        d.tempC = 22.0
        d.sample(0)
        assertEquals(16.0, d.estimate().avg, 0.001)
    }

    @Test fun reactiveWindowBlendsInLinearlyBetweenFiveAndTwentyKm() {
        val d = Drive()
        d.drive(20.0, avg = 20.0)
        d.pause(120)
        d.drive(4.0, avg = 30.0)
        assertEquals(0.0, d.estimate().weight, 0.001)
        d.drive(8.0, avg = 30.0)
        val e = d.estimate()
        assertEquals(12.0, e.reactiveKm, 0.001)
        assertEquals(30.0, e.reactive!!, 0.001)
        val w = DrivingRangeModel.MAX_REACTIVE_WEIGHT * 7.0 / 15.0
        assertEquals(w, e.weight, 0.001)
        // The band keeps learning during the drive: 20 km at 20 plus 12 km at 30.
        assertEquals(23.75, e.prior, 0.001)
        assertEquals(23.75 * (1 - w) + 30.0 * w, e.avg, 0.001)
    }

    @Test fun beyondTwentyKmTheReactiveShareIsCappedAndTheWindowIsBoundedToTwentyFiveKm() {
        val d = Drive()
        d.drive(30.0, avg = 18.0)
        d.drive(25.0, avg = 26.0)
        val e = d.estimate()
        assertEquals(DrivingRangeModel.MAX_REACTIVE_WEIGHT, e.weight, 0.001)
        assertEquals(25.0, e.reactiveKm, 0.001)
        assertEquals(26.0, e.reactive!!, 0.001)
        assertEquals(e.prior * 0.4 + 26.0 * 0.6, e.avg, 0.001)
        // The long-term 100-km average is unaffected by the reactive split.
        assertEquals((30 * 18.0 + 25 * 26.0) / 55.0, d.model.average(), 0.001)
    }

    @Test fun reactiveWindowRestartsAfterAnHourWithoutDriving() {
        val d = Drive()
        d.drive(25.0, avg = 25.0)
        assertEquals(DrivingRangeModel.MAX_REACTIVE_WEIGHT, d.estimate().weight, 0.001)
        d.pause(61)
        assertEquals(0.0, d.estimate().reactiveKm, 0.001)
        d.drive(2.0, avg = 25.0)
        assertEquals(2.0, d.estimate().reactiveKm, 0.001)
    }

    @Test fun shortStopKeepsTheReactiveWindow() {
        val d = Drive()
        d.drive(10.0, avg = 25.0)
        d.pause(20)
        d.drive(2.0, avg = 25.0)
        assertEquals(12.0, d.estimate().reactiveKm, 0.001)
    }

    @Test fun unknownTemperatureFallsBackToTheGlobalAverage() {
        val d = Drive()
        d.drive(20.0, avg = 16.0)
        d.tempC = null
        d.pause(120)
        val e = d.estimate()
        assertFalse(e.priorFromTemp)
        assertNull(e.band)
        assertEquals(d.model.average(), e.prior, 0.001)
    }

    @Test fun thinBandFallsBackToTheGlobalAverage() {
        val d = Drive()
        d.drive(30.0, avg = 16.0)
        d.tempC = 2.0
        d.drive(6.0, avg = 30.0)
        d.pause(120)
        val e = d.estimate()
        assertFalse(e.priorFromTemp)
        assertEquals(d.model.average(), e.prior, 0.001)
    }

    @Test fun implausibleTemperatureIsIgnored() {
        val d = Drive()
        d.tempC = 215.0
        d.sample(0)
        assertNull(d.model.snapshot().tempC)
    }

    @Test fun regenHeavyReactiveWindowIsFloored() {
        val d = Drive()
        d.drive(22.0, avg = 2.0)
        val e = d.estimate()
        assertEquals(maxOf(e.prior * DrivingRangeModel.REACTIVE_MIN_RATIO, DrivingRangeModel.C_FLOOR), e.reactive!!, 0.001)
        assertTrue(d.estimate().avg >= DrivingRangeModel.C_FLOOR)
    }

    @Test fun longDescentCannotPullTheEstimateFarBelowThePrior() {
        val d = Drive()
        d.drive(100.0, avg = 17.0)
        d.pause(120)
        // 25 km downhill at 4 kWh/100 km, as on a regen-heavy start.
        d.drive(25.0, avg = 4.0)
        val e = d.estimate()
        assertEquals(e.prior * DrivingRangeModel.REACTIVE_MIN_RATIO, e.reactive!!, 0.001)
        // At worst 0.4 × prior + 0.6 × 0.95 × prior = 0.97 × prior: range +3% at most.
        assertTrue(e.avg >= e.prior * 0.97 - 0.001)
    }

    @Test fun longClimbCannotPushTheEstimateFarAboveThePrior() {
        val d = Drive()
        d.drive(100.0, avg = 17.0)
        d.pause(120)
        d.drive(25.0, avg = 45.0)
        val e = d.estimate()
        assertEquals(e.prior * DrivingRangeModel.REACTIVE_MAX_RATIO, e.reactive!!, 0.001)
    }

    @Test fun bandHistoryIsCappedButKeepsItsAverage() {
        val d = Drive()
        d.drive(600.0, avg = 17.0)
        val bucket = d.model.snapshot().tempBuckets.getValue(4)
        assertEquals(DrivingRangeModel.BUCKET_CAP_KM, bucket.km, 0.001)
        assertEquals(17.0, bucket.kwh / bucket.km * 100.0, 0.001)
    }

    @Test fun energyOnAChargerIsDroppedOutrightEvenWhenDrivingResumesQuickly() {
        val d = Drive()
        d.drive(12.0, avg = 20.0)
        val before = d.model.average()
        // A few minutes parked (would count as a traffic stop), then a 10-minute top-up
        // during which the counter keeps moving (cooling, losses).
        repeat(6) { d.now += 30_000L; d.energy += 0.05; d.sample(0) }
        repeat(20) { d.now += 30_000L; d.energy += 0.10; d.sample(0, charging = true) }
        d.now += 30_000L
        d.sample(0)
        d.drive(4.0, avg = 20.0)
        assertEquals(before, d.model.average(), 0.001)
        assertEquals(20.0, d.model.snapshot().reactive.let { r -> r.sumOf { it.kwh } / r.sumOf { it.km } * 100 }, 0.001)
    }

    @Test fun standstillWithEnergyFlowingInCountsAsChargingWithoutGunState() {
        fun data(speed: Int?, power: Double?, gun: Int? = null) = mockk<DiParsData> {
            every { this@mockk.speed } returns speed
            every { this@mockk.power } returns power
            every { chargeGunState } returns gun
        }
        assertTrue(DrivingRangeSource.isChargingOrDischarging(data(0, -6.5)))
        assertTrue(DrivingRangeSource.isChargingOrDischarging(data(0, 0.0, gun = 2)))
        assertFalse(DrivingRangeSource.isChargingOrDischarging(data(0, 0.8)))
        // Regen needs motion: negative power while moving is driving, not charging.
        assertFalse(DrivingRangeSource.isChargingOrDischarging(data(35, -20.0)))
        assertFalse(DrivingRangeSource.isChargingOrDischarging(data(0, null)))
    }

    @Test fun chargingRestartsExcludedParkedEnergy() {
        val d = Drive()
        repeat(40) {
            d.now += 30_000L
            d.energy += 0.01
            d.sample(0)
        }
        assertTrue(d.model.snapshot().excludedKwh > 0.0)
        d.now += 30_000L
        d.sample(0, charging = true)
        assertEquals(0.0, d.model.snapshot().excludedKwh, 0.0)
    }
}
