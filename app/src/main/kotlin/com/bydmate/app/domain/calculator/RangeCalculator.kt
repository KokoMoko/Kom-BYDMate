package com.bydmate.app.domain.calculator

import android.util.Log
import com.bydmate.app.data.repository.SettingsRepository
import javax.inject.Singleton

/** Test-friendly seam: production binding is DrivingRangeSource. */
interface ConsumptionAvgSource {
    suspend fun recentAvgConsumption(): Double
}

/**
 * Intermediate values produced by the range estimation.
 *
 *   rangeKm       = remainingKwh / avgKwhPer100 * 100
 *   remainingKwh  = SOC * capacityKwh / 100 - carryOver
 */
data class RangeEstimate(
    val rangeKm: Double,
    val avgKwhPer100: Double,
    val capacityKwh: Double,
    val remainingKwh: Double,
    val energySource: String = "capacity_soc",
)

@Singleton
class RangeCalculator(
    private val buffer: ConsumptionAvgSource,
    private val capacityProvider: suspend () -> Double,
    private val socInterpolator: SocInterpolator,
    private val manualCalculator: ManualRangeCalculator = ManualRangeCalculator(),
    private val methodProvider: suspend () -> String = { SettingsRepository.RANGE_CALC_AUTO },
    private val manualTableProvider: suspend () -> List<SettingsRepository.ManualRangePoint> = { emptyList() },
) {
    /**
     * Returns full estimation breakdown, or null when inputs are insufficient.
     *
     * When SettingsRepository.KEY_RANGE_CALC_METHOD is "manual", delegates to
     * [ManualRangeCalculator] (a user-edited temperature table) instead of the
     * driving-only consumption model below. [batteryTempC] is only consulted
     * in that mode, and the table can only be applied when it is known: without a
     * battery temperature (or with a table the manual calculator rejects) we fall
     * back to the automatic estimate silently rather than guessing a temperature.
     *
     *   remaining_kwh = SOC * cap / 100 - socInterpolator.carryOver(totalElec, soc)
     *   range_km      = remaining_kwh / recent_avg * 100
     */
    suspend fun estimateDetailed(
        soc: Int?, totalElecKwh: Double?, batteryTempC: Int? = null,
        batteryRemainingKwh: Double? = null,
    ): RangeEstimate? {
        if (methodProvider() == SettingsRepository.RANGE_CALC_MANUAL && batteryTempC != null) {
            manualCalculator.estimateDetailed(
                soc = soc,
                temperatureC = batteryTempC,
                table = manualTableProvider(),
                fallbackCapacityKwh = capacityProvider(),
            )?.let { return it.copy(energySource = "manual_table") }
        }

        if (soc == null || soc !in 0..100) return null
        val cap = capacityProvider()
        var bmsEnergy = batteryRemainingKwh?.takeIf { it.isFinite() && it in 0.0..1000.0 }
        // A glitchy BMS read far from what SOC implies is not trusted; SOC × capacity is.
        // Only checkable with a sane capacity setting and a non-empty SOC.
        if (bmsEnergy != null && soc > 0 && cap in CAPACITY_SANE_KWH) {
            val expected = soc / 100.0 * cap
            if (bmsEnergy !in expected * BMS_MIN_RATIO..expected * BMS_MAX_RATIO) {
                Log.w(TAG, "BMS remaining=$bmsEnergy outside SOC expectation $expected; using SOC")
                bmsEnergy = null
            }
        }
        if (soc == 0 && bmsEnergy == null) return null
        // Capacity is a free-form user setting: outside the sane EV range it is a
        // typo, not a battery (also rejects NaN/Infinity via the range check).
        if (bmsEnergy == null && cap !in CAPACITY_SANE_KWH) return null
        val avg = buffer.recentAvgConsumption()
        if (!avg.isFinite() || avg <= 0.0) return null
        // BMS energy already includes stationary losses: never subtract carry twice.
        var carry = if (bmsEnergy != null) 0.0 else socInterpolator.carryOver(totalElecKwh, soc)
        if (!carry.isFinite()) return null
        // carry is the energy spent since the last 1% SOC step, so it cannot sanely exceed a
        // few percent of capacity. A single odometer/counter glitch can otherwise hand back
        // carry close to the full capacity, zeroing remainingKwh on an otherwise normal drive.
        if (carry > cap * CARRY_SANE_FRACTION) {
            Log.w(TAG, "carryOver=$carry exceeds sane bound (cap=$cap); treating as 0")
            carry = 0.0
        }
        val remainingKwh = bmsEnergy ?: ((soc / 100.0) * cap - carry)
        if (!remainingKwh.isFinite() || remainingKwh < 0.0 || (remainingKwh == 0.0 && bmsEnergy == null)) return null
        val rangeKm = remainingKwh / avg * 100.0
        if (!rangeKm.isFinite()) return null
        return RangeEstimate(
            rangeKm = rangeKm,
            avgKwhPer100 = avg,
            capacityKwh = cap.takeIf { it in CAPACITY_SANE_KWH } ?: 0.0,
            remainingKwh = remainingKwh,
            energySource = if (bmsEnergy != null) "bms" else "capacity_soc",
        )
    }

    /** Returns estimated range in km, or null when inputs are insufficient. */
    suspend fun estimate(
        soc: Int?, totalElecKwh: Double?, batteryTempC: Int? = null,
        batteryRemainingKwh: Double? = null,
    ): Double? = estimateDetailed(soc, totalElecKwh, batteryTempC, batteryRemainingKwh)?.rangeKm

    companion object {
        private const val TAG = "RangeCalculator"

        /** Plausible EV battery capacity bounds for the user-entered setting, kWh. */
        val CAPACITY_SANE_KWH = 1.0..1000.0

        /** carryOver above this fraction of capacity is a counter glitch, not real energy spent
         *  since the last SOC step (~1% of capacity, plus headroom). */
        const val CARRY_SANE_FRACTION = 0.03

        /** Accepted BMS remaining energy relative to SOC × capacity. */
        const val BMS_MIN_RATIO = 0.70
        const val BMS_MAX_RATIO = 1.15
    }
}
