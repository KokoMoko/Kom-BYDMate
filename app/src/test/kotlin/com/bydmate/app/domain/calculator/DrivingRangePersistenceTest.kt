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

    @Test fun firstBuildHistoryLoadsWithoutTheNewFields() {
        val legacy = """{"blocks":[{"km":5.0,"kwh":1.0}],"bucketKm":0.0,"bucketKwh":0.0,""" +
            """"pendingKwh":0.0,"stationarySinceMs":null,"excludedKwh":0.4,"baseline":18.8,""" +
            """"baselineFromVehicle":true,"last":null}"""
        val state = DrivingRangeSource.decode(legacy)
        assertEquals(listOf(DrivingRangeModel.Block(5.0, 1.0)), state.blocks)
        assertEquals(emptyMap<Int, DrivingRangeModel.Block>(), state.tempBuckets)
        assertEquals(18.8, state.baseline, 0.0)
    }

    @Test fun corruptedStorageStartsFreshInsteadOfImportingOldTripTotals() {
        assertEquals(DrivingRangeModel.State(), DrivingRangeSource.decode("{broken"))
        assertEquals(DrivingRangeModel.State(), DrivingRangeSource.decode(null))
    }
}
