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
        var altM: Double? = null
        var climateKw: Double? = null
        var remain: Double? = null
        init { sample(0) }
        fun sample(speed: Int, charging: Boolean = false) =
            model.onSample(now, km, energy, speed, 1L, charging, tempC, altM, climateKw, remain)
        /**
         * [distance] km at [avg] kWh/100 km, in 0.5-km steps, climbing [climbM] in total
         * (needs [altM]); [avg] is what the car's counter shows, hill included.
         */
        fun drive(distance: Double, avg: Double, climbM: Double = 0.0) {
            val steps = (distance * 2).toInt()
            repeat(steps) {
                now += 30_000L
                km += 0.5
                energy += avg / 200.0
                altM = altM?.plus(climbM / steps)
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

    @Test fun coldStartScalesThePriorByItsTemperatureBandAsItsHistoryGrows() {
        val d = Drive()
        d.drive(20.0, avg = 16.0)
        d.tempC = -5.0
        d.drive(20.0, avg = 28.0)
        d.pause(8 * 60)
        val e = d.estimate()
        assertTrue(e.priorFromTemp)
        assertEquals(-1, e.band)
        assertEquals(0.0, e.reactiveKm, 0.001)
        // 20 km of band history: (28/22 - 1) x 20/170 of the way up from the 22 average.
        val trust = 20.0 / (20.0 + DrivingRangeModel.TEMP_TRUST_KM)
        assertEquals(22.0 * (1 + (28.0 / 22.0 - 1) * trust), e.prior, 0.001)
        assertEquals(e.prior, e.avg, 0.001)
        d.tempC = 22.0
        d.sample(0)
        assertTrue(d.estimate().avg < 22.0)
    }

    @Test fun aLongBandHistoryLeadsThePrior() {
        val d = Drive()
        d.drive(500.0, avg = 16.0)
        d.tempC = -5.0
        d.drive(500.0, avg = 28.0)
        d.tempC = 22.0
        d.drive(100.0, avg = 16.0)
        d.tempC = -5.0
        d.pause(8 * 60)
        // Bands 16 vs 28 at 500 km each: cold is 28/22 of the average, trusted 500/650.
        val e = d.estimate()
        assertEquals(16.0 * (1 + (28.0 / 22.0 - 1) * 500.0 / 650.0), e.prior, 0.01)
    }

    @Test fun climbsAndDescentsAreLearnedAsFlatRoad() {
        val d = Drive()
        d.altM = 1200.0
        // 51 km climbing 409 m at 22.2 on the counter, 57 km descending 544 m at 12.7 (on-car).
        d.drive(51.0, avg = 22.2, climbM = 409.0)
        val up = d.model.average()
        assertEquals((11.322 - 409 * DrivingRangeModel.HILL_KWH_PER_M) / 51 * 100, up, 0.1)
        assertTrue(up in 17.0..18.5)
        d.drive(49.0, avg = 12.7, climbM = -467.0)
        assertTrue(d.model.average() in 17.0..18.5)
    }

    @Test fun climateEnergyIsNotLearnedAsDrivingAndIsAddedBackBySpeed() {
        val d = Drive()
        d.climateKw = 1.0
        // 0.5 km per 30 s = 60 km/h; the counter includes the 1 kW climate (1.667 kWh/100 km).
        d.drive(30.0, avg = 20.0 + 100.0 / 60.0)
        assertEquals(20.0, d.model.average(), 0.01)
        assertEquals(60.0, d.model.snapshot().avgSpeedKmh!!, 0.01)
        val off = d.model.rangeEstimate(d.now, climateKw = 0.0)
        assertEquals(20.0, off.avg, 0.01)
        // 3 kW running now at 60 km/h: +5 kWh/100 km.
        val on = d.model.rangeEstimate(d.now, climateKw = 3.0)
        assertEquals(25.0, on.avg, 0.01)
    }

    @Test fun shortStopsCountAsDrivingTime() {
        val d = Drive()
        d.drive(10.0, avg = 20.0)
        repeat(10) { d.now += 30_000L; d.sample(0) }   // 5 minutes at a light
        d.drive(10.0, avg = 20.0)
        // The block with the stop took 6 minutes: its time, not its count, lowers the speed.
        assertEquals(1.0 / (1.0 / 60 + (0.1 - 1.0 / 60) * 0.02), d.model.snapshot().avgSpeedKmh!!, 1.5)
    }

    @Test fun counterIsCalibratedToTheBmsOverLongStretches() {
        val d = Drive()
        d.remain = 60.0
        d.sample(60)
        // The counter reads 5% high: 0.95 kWh leaves the pack per counted kWh.
        repeat(120) {
            d.now += 30_000L; d.km += 0.5; d.energy += 0.1; d.remain = d.remain!! - 0.095
            d.sample(60)
        }
        assertEquals(0.95, d.model.snapshot().bmsRatio!!, 0.002)
        val e = d.estimate()
        assertEquals(e.driveAvg * 0.95, e.avg, 0.001)
    }

    @Test fun chargingRestartsTheCalibrationStretch() {
        val d = Drive()
        d.remain = 60.0
        d.sample(60)
        repeat(60) { d.now += 30_000L; d.km += 0.5; d.energy += 0.1; d.remain = d.remain!! - 0.095; d.sample(60) }
        d.remain = 70.0
        d.sample(0, charging = true)
        repeat(60) { d.now += 30_000L; d.km += 0.5; d.energy += 0.1; d.remain = d.remain!! - 0.095; d.sample(60) }
        assertNull(d.model.snapshot().bmsRatio)
    }

    @Test fun aBlockWithoutAltitudeIsNotCorrected() {
        val d = Drive()
        d.drive(10.0, avg = 20.0)
        assertEquals(20.0, d.model.average(), 0.001)
        d.altM = 1000.0
        d.drive(10.0, avg = 20.0, climbM = 0.0)
        assertEquals(20.0, d.model.average(), 0.001)
    }

    @Test fun aGpsAltitudeJumpIsNotAHill() {
        val d = Drive()
        d.altM = 1000.0
        d.drive(5.0, avg = 20.0)
        d.altM = 1600.0
        d.drive(5.0, avg = 20.0)
        assertEquals(20.0, d.model.average(), 0.001)
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
        val w = DrivingRangeModel.MAX_REACTIVE_WEIGHT
        assertEquals(e.prior * (1 - w) + 26.0 * w, e.avg, 0.001)
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
        val worst = 1 - DrivingRangeModel.MAX_REACTIVE_WEIGHT * (1 - DrivingRangeModel.REACTIVE_MIN_RATIO)
        assertTrue(e.avg >= e.prior * worst - 0.001)
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
