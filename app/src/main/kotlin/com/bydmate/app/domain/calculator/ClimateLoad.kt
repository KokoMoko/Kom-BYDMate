package com.bydmate.app.domain.calculator

import kotlin.math.exp

/**
 * Power the cabin climate draws, kW, so the range can react the moment the AC changes.
 *
 * Measured on-car 2026-10-05 (Sealion 06, parked, 20°C outside): 0.38 kW with the AC off;
 * the compressor (its own power sensor) 0 -> 2.5 kW within ~20 s of switching on, cycling
 * when the cabin is cool; the blower and the rest 0.06-0.43 kW by fan level. The cluster
 * took a flat ~10 km (2.7%) off for any level, which at city speed understates it 7-fold.
 *
 * Climate power = compressor + what the blower adds at this fan level. The blower share is
 * learned while parked (battery power minus the base load minus the compressor); the
 * smoothed value spans a compressor cycle, and is seeded with the typical AC-on draw so
 * the range drops as soon as the AC is switched on rather than after the 20-s ramp.
 */
class ClimateLoad(initial: Learned = Learned()) {
    data class Learned(
        /** Battery draw parked with the climate off: screens, modules, 12 V. */
        val baseKw: Double = DEFAULT_BASE_KW,
        /** Fan level -> what the AC adds on top of the compressor (blower, valves). */
        val fanExtraKw: Map<Int, Double> = emptyMap(),
        /** Typical climate power while the AC is on; seeds the smoothing at switch-on. */
        val acOnKw: Double? = null,
    )

    private var learned = initial
    private var smoothKw = 0.0
    private var lastMs: Long? = null
    private var acWasOn = false

    @Synchronized fun learned(): Learned = learned

    /** Smoothed climate power for the estimate, kW. */
    @Synchronized fun smoothedKw(): Double = smoothKw

    /**
     * Feeds one telemetry sample; returns the climate power right now, kW (energy to take
     * out of driving consumption), or null when the climate state is unknown.
     */
    @Synchronized fun onSample(
        nowMs: Long,
        acOn: Boolean?,
        fanLevel: Int?,
        compressorW: Int?,
        batteryPowerW: Double?,
        speedKmh: Int?,
        charging: Boolean,
    ): Double? {
        val dtS = lastMs?.let { (nowMs - it) / 1000.0 }?.takeIf { it > 0.0 && it <= MAX_GAP_S }
        lastMs = nowMs
        if (acOn == null) return null
        val compKw = compressorW?.takeIf { it in 0..MAX_COMPRESSOR_W }?.let { it / 1000.0 }
        val fan = fanLevel?.takeIf { it in 1..MAX_FAN }
        val totalKw = batteryPowerW?.takeIf { it.isFinite() }?.let { it / 1000.0 }

        // Parked and not charging: battery power is only the base load plus the climate.
        if (!charging && speedKmh == 0 && dtS != null && totalKw != null && totalKw in 0.0..MAX_PARKED_KW) {
            val a = 1.0 - exp(-dtS / LEARN_TAU_S)
            if (!acOn && (compKw ?: 0.0) == 0.0) {
                learned = learned.copy(baseKw = learned.baseKw + a * (totalKw - learned.baseKw))
            } else if (acOn && compKw != null && fan != null) {
                val extra = (totalKw - learned.baseKw - compKw).coerceIn(0.0, MAX_EXTRA_KW)
                val old = learned.fanExtraKw[fan] ?: defaultExtraKw(fan)
                learned = learned.copy(fanExtraKw = learned.fanExtraKw + (fan to old + a * (extra - old)))
            }
        }

        val instKw = when {
            compKw != null -> compKw + if (acOn && fan != null) learned.fanExtraKw[fan] ?: defaultExtraKw(fan) else 0.0
            // A firmware without the compressor sensor: parked readings are all there is.
            acOn -> learned.acOnKw ?: DEFAULT_AC_ON_KW
            else -> 0.0
        }
        when {
            instKw == 0.0 -> smoothKw = 0.0 // off is off at once: no tail of the last cycle
            acOn && !acWasOn -> smoothKw = maxOf(instKw, learned.acOnKw ?: DEFAULT_AC_ON_KW)
            dtS != null -> smoothKw += (1.0 - exp(-dtS / SMOOTH_TAU_S)) * (instKw - smoothKw)
            else -> smoothKw = instKw
        }
        acWasOn = acOn
        // Learned after seeding, from the smoothed value: the ramp and cycles average out.
        if (acOn && dtS != null) {
            val a = 1.0 - exp(-dtS / TYPICAL_TAU_S)
            learned = learned.copy(acOnKw = learned.acOnKw?.let { it + a * (smoothKw - it) } ?: smoothKw)
        }
        return instKw
    }

    companion object {
        const val DEFAULT_BASE_KW = 0.4
        const val DEFAULT_AC_ON_KW = 1.5
        const val MAX_FAN = 7
        const val MAX_COMPRESSOR_W = 10_000
        const val MAX_EXTRA_KW = 2.0
        const val MAX_PARKED_KW = 12.0
        const val MAX_GAP_S = 60.0
        /** Spans a compressor on/off cycle (~30-60 s at the cool end). */
        const val SMOOTH_TAU_S = 90.0
        const val LEARN_TAU_S = 60.0
        const val TYPICAL_TAU_S = 600.0

        /** Blower before it is learned: 0.06 kW at level 1 .. 0.43 at 7 on-car. */
        fun defaultExtraKw(fan: Int): Double = 0.06 * fan
    }
}
