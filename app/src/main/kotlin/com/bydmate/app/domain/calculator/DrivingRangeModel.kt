package com.bydmate.app.domain.calculator

import kotlin.math.abs
import kotlin.math.floor

/** Range-only accounting. Ordinary trip totals are never modified. */
class DrivingRangeModel(initial: State = State()) {
    data class Block(val km: Double, val kwh: Double)
    data class Sample(val timeMs: Long, val km: Double, val kwh: Double, val session: Long)
    data class State(
        val blocks: List<Block> = emptyList(),
        val bucketKm: Double = 0.0,
        val bucketKwh: Double = 0.0,
        val pendingKwh: Double = 0.0,
        val stationarySinceMs: Long? = null,
        val last: Sample? = null,
        val excludedKwh: Double = 0.0,
        val baseline: Double = FALLBACK_AVG,
        val baselineFromVehicle: Boolean = false,
        /** Long-term driving consumption per exterior-temperature band (key = band index). */
        val tempBuckets: Map<Int, Block> = emptyMap(),
        /** Last ~25 eligible km of the current drive: tracks AC/heat, hills, wind right now. */
        val reactive: List<Block> = emptyList(),
        val lastCommitMs: Long? = null,
        val tempC: Double? = null,
    )

    /** What the range uses: temperature prior blended into the reactive window by distance. */
    data class Estimate(
        val avg: Double,
        val prior: Double,
        val priorFromTemp: Boolean,
        val reactive: Double?,
        val reactiveKm: Double,
        val weight: Double,
        val band: Int?,
    )

    private var state = initial

    @Synchronized fun snapshot(): State = state

    /**
     * Prior: the long-term average for the current exterior-temperature band, else the
     * 100-km [average]. Reactive: the recent window, floored so regen-heavy stretches cannot
     * extrapolate an absurd range. Below [BLEND_START_KM] the prior alone is used, above
     * [BLEND_FULL_KM] the reactive window alone, linear in between. Climate load is not
     * modelled separately: it is already in measured energy, and the band captures its
     * typical seasonal share.
     */
    @Synchronized fun rangeEstimate(nowMs: Long): Estimate {
        val band = state.tempC?.let(::bandOf)
        val bucket = band?.let { state.tempBuckets[it] }
        val bucketAvg = bucket?.takeIf { it.km >= MIN_BUCKET_KM }?.let { it.kwh / it.km * 100.0 }
        val priorFromTemp = bucketAvg != null && bucketAvg in SANE_AVG
        val prior = if (priorFromTemp) bucketAvg!! else average()
        val fresh = state.lastCommitMs?.let { nowMs - it in 0..REACTIVE_RESET_MS } == true
        val reactiveKm = if (fresh) state.reactive.sumOf { it.km } else 0.0
        val reactive = if (reactiveKm > 0.0) {
            (state.reactive.sumOf { it.kwh } / reactiveKm * 100.0).coerceIn(SANE_AVG)
        } else null
        val weight = ((reactiveKm - BLEND_START_KM) / (BLEND_FULL_KM - BLEND_START_KM)).coerceIn(0.0, 1.0)
        val avg = if (reactive == null) prior else prior * (1.0 - weight) + reactive * weight
        return Estimate(avg, prior, priorFromTemp, reactive, reactiveKm, weight, band)
    }

    @Synchronized fun setProvisionalBaseline(avg: Double) {
        if (avg in SANE_AVG) state = state.copy(baseline = avg, baselineFromVehicle = true)
    }

    @Synchronized fun average(): Double {
        val km = state.blocks.sumOf { it.km }
        val energy = state.blocks.sumOf { it.kwh }
        val avg = if (km >= MIN_LEARNING_KM) energy / km * 100.0 else Double.NaN
        return if (avg in SANE_AVG) avg else state.baseline
    }

    @Synchronized fun isLearned(): Boolean =
        state.blocks.sumOf { it.km } >= MIN_LEARNING_KM &&
            (state.blocks.sumOf { it.kwh } / state.blocks.sumOf { it.km } * 100.0) in SANE_AVG

    @Synchronized fun stationaryMinutes(nowMs: Long): Long =
        state.stationarySinceMs?.let { ((nowMs - it).coerceAtLeast(0L)) / 60_000L } ?: 0L

    /** Missing data is not evidence of continuous parking. */
    @Synchronized fun onSample(
        nowMs: Long,
        mileageKm: Double?,
        totalKwh: Double?,
        speedKmh: Int?,
        sessionId: Long?,
        chargingOrDischarging: Boolean = false,
        exteriorTempC: Double? = null,
    ) {
        state = state.copy(tempC = exteriorTempC?.takeIf { it.isFinite() && it in SANE_TEMP_C })
        if (sessionId == null || chargingOrDischarging) {
            // Retain eligible distance; do not attribute charging to a pending stop.
            // excludedKwh reports parked loss since the last charge, so a charge restarts it.
            state = state.copy(
                last = null, stationarySinceMs = null, pendingKwh = 0.0,
                excludedKwh = if (chargingOrDischarging) 0.0 else state.excludedKwh,
            )
            return
        }
        if (mileageKm == null || totalKwh == null ||
            !mileageKm.isFinite() || mileageKm < 1.0 ||
            !totalKwh.isFinite() || totalKwh < 0.0 || speedKmh == null || speedKmh !in 0..300
        ) {
            state = state.copy(last = null, stationarySinceMs = null, pendingKwh = 0.0)
            return
        }
        val sample = Sample(nowMs, mileageKm, totalKwh, sessionId)
        val previous = state.last
        if (previous == null || previous.session != sessionId) {
            state = state.copy(last = sample, stationarySinceMs = null, pendingKwh = 0.0)
            return
        }
        val elapsed = nowMs - previous.timeMs
        val km = mileageKm - previous.km
        val energy = totalKwh - previous.kwh
        // Permit net regenerative recovery but reject large counter discontinuities.
        val energyBound = 300.0 * elapsed.coerceAtLeast(0L) / 3_600_000.0 + 0.25
        val distanceBound = 300.0 * elapsed.coerceAtLeast(0L) / 3_600_000.0 + 0.1
        if (elapsed !in 1..MAX_GAP_MS || km < -MOVEMENT_EPSILON_KM ||
            km > distanceBound || abs(energy) > energyBound
        ) {
            state = state.copy(last = sample, stationarySinceMs = null, pendingKwh = 0.0)
            return
        }
        val moving = speedKmh > 0 || km > MOVEMENT_EPSILON_KM
        if (!moving) {
            val start = state.stationarySinceMs ?: previous.timeMs
            val pending = state.pendingKwh + energy
            if (nowMs - start >= STATIONARY_THRESHOLD_MS) {
                state = state.copy(
                    last = sample, stationarySinceMs = start, pendingKwh = 0.0,
                    excludedKwh = state.excludedKwh + pending,
                )
            } else {
                state = state.copy(last = sample, stationarySinceMs = start, pendingKwh = pending)
            }
            return
        }
        val start = state.stationarySinceMs
        val shortStop = start != null && nowMs - start < STATIONARY_THRESHOLD_MS
        // At the boundary a previously provisional stop must be excluded retroactively.
        val excluded = if (start != null && !shortStop) state.pendingKwh else 0.0
        state = state.copy(
            last = sample, stationarySinceMs = null, pendingKwh = 0.0,
            bucketKm = state.bucketKm + km.coerceAtLeast(0.0),
            bucketKwh = state.bucketKwh + energy + if (shortStop) state.pendingKwh else 0.0,
            excludedKwh = state.excludedKwh + excluded,
        )
        if (state.bucketKm >= BLOCK_KM) commitBlock(nowMs)
    }

    private fun commitBlock(nowMs: Long) {
        // Freeze the consumption estimate during a stop: only confirmed movement commits.
        val block = Block(state.bucketKm, state.bucketKwh)
        // A long pause (overnight, a parked hour) means new conditions: restart the reactive
        // window so the temperature prior leads again until fresh distance accumulates.
        val reactiveBase = if (state.lastCommitMs?.let { nowMs - it in 0..REACTIVE_RESET_MS } == true) {
            state.reactive
        } else emptyList()
        val band = state.tempC?.let(::bandOf)
        val buckets = if (band == null) state.tempBuckets else {
            val old = state.tempBuckets[band]
            var merged = if (old == null) block else Block(old.km + block.km, old.kwh + block.kwh)
            // Scale rather than drop: the band keeps its seasonal average but slowly adapts.
            if (merged.km > BUCKET_CAP_KM) {
                val f = BUCKET_CAP_KM / merged.km
                merged = Block(merged.km * f, merged.kwh * f)
            }
            state.tempBuckets + (band to merged)
        }
        state = state.copy(
            blocks = trimToKm(state.blocks + block, WINDOW_KM),
            reactive = trimToKm(reactiveBase + block, REACTIVE_KM),
            tempBuckets = buckets,
            lastCommitMs = nowMs,
            bucketKm = 0.0, bucketKwh = 0.0,
        )
    }

    private fun trimToKm(source: List<Block>, limitKm: Double): List<Block> {
        val blocks = source.toMutableList()
        var km = blocks.sumOf { it.km }
        while (blocks.isNotEmpty() && km - blocks.first().km >= limitKm) {
            km -= blocks.removeAt(0).km
        }
        if (km > limitKm && blocks.isNotEmpty()) {
            val first = blocks.first()
            val keep = first.km - (km - limitKm)
            blocks[0] = Block(keep, first.kwh * keep / first.km)
        }
        return blocks
    }

    companion object {
        fun bandOf(tempC: Double): Int = floor(tempC / TEMP_BAND_C).toInt()

        const val STATIONARY_THRESHOLD_MS = 15 * 60_000L
        const val WINDOW_KM = 100.0
        const val MIN_LEARNING_KM = 5.0
        const val BLOCK_KM = 1.0
        const val MAX_GAP_MS = 60_000L
        const val MOVEMENT_EPSILON_KM = 0.001
        const val FALLBACK_AVG = 18.0
        const val REACTIVE_KM = 25.0
        const val BLEND_START_KM = 5.0
        const val BLEND_FULL_KM = 20.0
        const val REACTIVE_RESET_MS = 60 * 60_000L
        const val TEMP_BAND_C = 5.0
        const val MIN_BUCKET_KM = 10.0
        const val BUCKET_CAP_KM = 500.0

        /** Lower bound blocks regen-heavy windows from extrapolating an absurd range. */
        const val C_FLOOR = 8.0
        val SANE_AVG = C_FLOOR..100.0
        val SANE_TEMP_C = -45.0..65.0
    }
}
