package com.bydmate.app.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrivingRangePersistenceTest {
    @Test fun pendingStopAndHistorySurviveSerialization() {
        val state = DrivingRangeModel.State(
            blocks = listOf(DrivingRangeModel.Block(5.0, 1.0)),
            pendingKwh = 0.28, stationarySinceMs = 1_000_000L,
            last = DrivingRangeModel.Sample(1_840_000L, 105.0, 501.28, 1),
            baseline = 18.8, baselineFromVehicle = true,
        )
        assertEquals(state, DrivingRangeSource.decode(DrivingRangeSource.encode(state)))
    }

    @Test fun temperatureBandsAndReactiveWindowSurviveSerialization() {
        val state = DrivingRangeModel.State(
            blocks = listOf(DrivingRangeModel.Block(5.0, 1.0)),
            reactive = listOf(DrivingRangeModel.Block(5.0, 1.0)),
            tempBuckets = mapOf(-1 to DrivingRangeModel.Block(40.0, 11.2), 4 to DrivingRangeModel.Block(80.0, 13.6)),
            lastCommitMs = 2_000_000L, tempC = -3.5,
        )
        assertEquals(state, DrivingRangeSource.decode(DrivingRangeSource.encode(state)))
    }

    @Test fun historyLearnedBeforeHillCorrectionIsDroppedButTheBaselineKept() {
        val legacy = """{"blocks":[{"km":5.0,"kwh":1.0}],"bucketKm":0.0,"bucketKwh":0.0,""" +
            """"pendingKwh":0.0,"stationarySinceMs":null,"excludedKwh":0.4,"baseline":18.8,""" +
            """"baselineFromVehicle":true,"last":null,"tempBuckets":{"4":{"km":104,"kwh":24.2}}}"""
        val state = DrivingRangeSource.decode(legacy)
        assertEquals(DrivingRangeModel.State(baseline = 18.8, baselineFromVehicle = true), state)
    }

    @Test fun climateLearningSurvivesSerialization() {
        val c = ClimateLoad.Learned(baseKw = 0.38, fanExtraKw = mapOf(1 to 0.06, 7 to 0.43), acOnKw = 2.9)
        assertEquals(c, DrivingRangeSource.decodeClimate(DrivingRangeSource.encodeClimate(c)))
        assertEquals(ClimateLoad.Learned(), DrivingRangeSource.decodeClimate("{bad"))
    }

    @Test fun blockStartAltitudeSurvivesSerialization() {
        val state = DrivingRangeModel.State(
            bucketKm = 0.4, bucketKwh = 0.1, bucketAltStartM = 1234.5, bucketMs = 24_000L,
            pendingMs = 60_000L, avgSpeedKmh = 37.5, bmsRatio = 0.96,
        )
        assertEquals(state, DrivingRangeSource.decode(DrivingRangeSource.encode(state)))
    }

    @Test fun corruptedStorageStartsFreshInsteadOfImportingOldTripTotals() {
        assertEquals(DrivingRangeModel.State(), DrivingRangeSource.decode("{broken"))
        assertEquals(DrivingRangeModel.State(), DrivingRangeSource.decode(null))
    }
}
