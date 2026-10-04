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
        /** GPS altitude where the current block started; null = this block is not corrected. */
        val bucketAltStartM: Double? = null,
        /** Driving time in the current block and in a provisional stop (short stops count). */
        val bucketMs: Long = 0L,
        val pendingMs: Long = 0L,
        /** Average driving speed with short stops, km/h: turns climate kW into kWh/100 km. */
        val avgSpeedKmh: Double? = null,
        /** BMS energy per kWh on the consumption counter, so both sides of the range agree. */
        val bmsRatio: Double? = null,
    )

    /**
     * What the range uses: temperature prior blended into the reactive window by distance
     * ([driveAvg], counter kWh), in BMS kWh, plus the climate running now ([climateKw]).
     */
    data class Estimate(
        val avg: Double,
        val prior: Double,
        val priorFromTemp: Boolean,
        val reactive: Double?,
        val reactiveKm: Double,
        val weight: Double,
        val band: Int?,
        val driveAvg: Double = avg,
        val climateKw: Double = 0.0,
        val speedKmh: Double = DEFAULT_SPEED_KMH,
        val bmsRatio: Double = 1.0,
    )

    private var state = initial
    private var calCounterKwh: Double? = null
    private var calRemainKwh: Double? = null

    @Synchronized fun snapshot(): State = state

    /**
     * Every block is stored as flat-road energy: the potential energy of its altitude change
     * is taken out ([HILL_KWH_PER_M]). On-car 2026-10-04 a 57-km descent (-544 m) read
     * 12.7 kWh/100 and a 51-km climb (+409 m) 22.2; corrected both are 18, as is the city
     * driving that day. Uncorrected, a climb in the window showed 238 km at 75% where the
     * car really had ~320 km. The future route is unknown, so the range assumes flat road.
     *
     * Prior: the 100-km [average], scaled by how the current exterior-temperature band
     * compares with all bands, trusted by its distance ([TEMP_TRUST_KM]); a band with
     * little history barely moves it, so the route mix it happened to see cannot dominate.
     * Reactive: the recent window, kept within [REACTIVE_MIN_RATIO]..
     * [REACTIVE_MAX_RATIO] of the prior (and above [C_FLOOR]) so a long descent or climb
     * cannot extrapolate itself over the whole battery. Below [BLEND_START_KM] the prior alone
     * is used; from there the reactive share grows linearly to [MAX_REACTIVE_WEIGHT] at
     * [BLEND_FULL_KM] — the prior always keeps the rest. On-car 2026-10-02: a 100% reactive
     * share swung 90% between 448 and 783 km on a hilly start.
     *
     * Climate is kept out of every block as well ([onSample] climateKw) and added back for
     * what runs now: [climateKw] x 100 / the average driving speed. 2.9 kW is +10 kWh/100 km
     * in town at 30 km/h but +3 at 90 km/h, so the AC costs a third of the range in town.
     */
    @Synchronized fun rangeEstimate(nowMs: Long, climateKw: Double = 0.0): Estimate {
        val band = state.tempC?.let(::bandOf)
        val bucket = band?.let { state.tempBuckets[it] }
        val bucketAvg = bucket?.takeIf { it.km >= MIN_BUCKET_KM }?.let { it.kwh / it.km * 100.0 }
        val allKm = state.tempBuckets.values.sumOf { it.km }
        val allAvg = if (allKm > 0.0) state.tempBuckets.values.sumOf { it.kwh } / allKm * 100.0 else Double.NaN
        val priorFromTemp = bucketAvg != null && bucketAvg in SANE_AVG && allAvg in SANE_AVG
        val prior = if (priorFromTemp) {
            val trust = bucket!!.km / (bucket.km + TEMP_TRUST_KM)
            (average() * (1.0 + (bucketAvg!! / allAvg - 1.0) * trust)).coerceIn(SANE_AVG)
        } else average()
        val fresh = state.lastCommitMs?.let { nowMs - it in 0..REACTIVE_RESET_MS } == true
        val reactiveKm = if (fresh) state.reactive.sumOf { it.km } else 0.0
        val reactive = if (reactiveKm > 0.0) {
            (state.reactive.sumOf { it.kwh } / reactiveKm * 100.0)
                .coerceIn(prior * REACTIVE_MIN_RATIO, prior * REACTIVE_MAX_RATIO)
                .coerceIn(SANE_AVG)
        } else null
        val weight = MAX_REACTIVE_WEIGHT *
            ((reactiveKm - BLEND_START_KM) / (BLEND_FULL_KM - BLEND_START_KM)).coerceIn(0.0, 1.0)
        val drive = if (reactive == null) prior else prior * (1.0 - weight) + reactive * weight
        val ratio = (state.bmsRatio ?: 1.0).coerceIn(BMS_RATIO_USE)
        val speed = (state.avgSpeedKmh ?: DEFAULT_SPEED_KMH).coerceIn(MIN_EST_SPEED_KMH, MAX_SPEED_KMH)
        val climate = climateKw.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_CLIMATE_KW) ?: 0.0
        val avg = drive * ratio + climate * 100.0 / speed
        return Estimate(avg, prior, priorFromTemp, reactive, reactiveKm, weight, band, drive, climate, speed, ratio)
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
        altitudeM: Double? = null,
        climateKw: Double? = null,
        bmsRemainKwh: Double? = null,
    ) {
        calibrate(totalKwh, bmsRemainKwh, sessionId == null || chargingOrDischarging)
        val alt = altitudeM?.takeIf { it.isFinite() && it in SANE_ALT_M }
        state = state.copy(tempC = exteriorTempC?.takeIf { it.isFinite() && it in SANE_TEMP_C })
        if (sessionId == null || chargingOrDischarging) {
            // Retain eligible distance; do not attribute charging to a pending stop.
            // excludedKwh reports parked loss since the last charge, so a charge restarts it.
            state = state.copy(
                last = null, stationarySinceMs = null, pendingKwh = 0.0, pendingMs = 0L,
                excludedKwh = if (chargingOrDischarging) 0.0 else state.excludedKwh,
            )
            return
        }
        if (mileageKm == null || totalKwh == null ||
            !mileageKm.isFinite() || mileageKm < 1.0 ||
            !totalKwh.isFinite() || totalKwh < 0.0 || speedKmh == null || speedKmh !in 0..300
        ) {
            state = state.copy(last = null, stationarySinceMs = null, pendingKwh = 0.0, pendingMs = 0L)
            return
        }
        val sample = Sample(nowMs, mileageKm, totalKwh, sessionId)
        val previous = state.last
        if (previous == null || previous.session != sessionId) {
            state = state.copy(last = sample, stationarySinceMs = null, pendingKwh = 0.0, pendingMs = 0L)
            return
        }
        val elapsed = nowMs - previous.timeMs
        val km = mileageKm - previous.km
        val counted = totalKwh - previous.kwh
        // What the climate drew over this interval is not driving.
        val climateKwh = (climateKw?.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_CLIMATE_KW) ?: 0.0) *
            elapsed.coerceAtLeast(0L) / 3_600_000.0
        val energy = counted - climateKwh
        // Permit net regenerative recovery but reject large counter discontinuities.
        val energyBound = 300.0 * elapsed.coerceAtLeast(0L) / 3_600_000.0 + 0.25
        val distanceBound = 300.0 * elapsed.coerceAtLeast(0L) / 3_600_000.0 + 0.1
        if (elapsed !in 1..MAX_GAP_MS || km < -MOVEMENT_EPSILON_KM ||
            km > distanceBound || abs(counted) > energyBound
        ) {
            state = state.copy(last = sample, stationarySinceMs = null, pendingKwh = 0.0, pendingMs = 0L)
            return
        }
        val moving = speedKmh > 0 || km > MOVEMENT_EPSILON_KM
        if (!moving) {
            val start = state.stationarySinceMs ?: previous.timeMs
            val pending = state.pendingKwh + energy
            if (nowMs - start >= STATIONARY_THRESHOLD_MS) {
                state = state.copy(
                    last = sample, stationarySinceMs = start, pendingKwh = 0.0, pendingMs = 0L,
                    excludedKwh = state.excludedKwh + pending,
                )
            } else {
                state = state.copy(
                    last = sample, stationarySinceMs = start, pendingKwh = pending,
                    pendingMs = state.pendingMs + elapsed,
                )
            }
            return
        }
        val start = state.stationarySinceMs
        val shortStop = start != null && nowMs - start < STATIONARY_THRESHOLD_MS
        // At the boundary a previously provisional stop must be excluded retroactively.
        val excluded = if (start != null && !shortStop) state.pendingKwh else 0.0
        // A block that starts without a fix is left uncorrected; the next one starts at a fix.
        val altStart = state.bucketAltStartM ?: alt?.takeIf { state.bucketKm < ALT_LATE_START_KM }
        state = state.copy(
            bucketAltStartM = altStart,
            last = sample, stationarySinceMs = null, pendingKwh = 0.0, pendingMs = 0L,
            bucketKm = state.bucketKm + km.coerceAtLeast(0.0),
            bucketKwh = state.bucketKwh + energy + if (shortStop) state.pendingKwh else 0.0,
            bucketMs = state.bucketMs + elapsed + if (shortStop) state.pendingMs else 0L,
            excludedKwh = state.excludedKwh + excluded,
        )
        if (state.bucketKm >= BLOCK_KM) commitBlock(nowMs, alt)
    }

    private fun commitBlock(nowMs: Long, altEndM: Double?) {
        // Freeze the consumption estimate during a stop: only confirmed movement commits.
        val climb = state.bucketAltStartM?.let { s -> altEndM?.let { it - s } }
            ?.takeIf { abs(it) <= MAX_CLIMB_PER_BLOCK_M }
        val block = Block(state.bucketKm, state.bucketKwh - (climb ?: 0.0) * HILL_KWH_PER_M)
        val hours = state.bucketMs / 3_600_000.0
        val speed = if (hours > 0.0) {
            // Averaged as time per km, so a stop weighs by how long it lasted.
            val pace = 1.0 / (block.km / hours).coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)
            val w = (block.km / SPEED_WINDOW_KM).coerceIn(0.0, 1.0)
            1.0 / (state.avgSpeedKmh?.let { 1.0 / it + (pace - 1.0 / it) * w } ?: pace)
        } else state.avgSpeedKmh
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
            bucketKm = 0.0, bucketKwh = 0.0, bucketMs = 0L,
            bucketAltStartM = altEndM,
            avgSpeedKmh = speed,
        )
    }

    /**
     * Learns [State.bmsRatio] over stretches of [CAL_MIN_KWH] on the counter with no charging
     * in between: the range divides BMS energy by a counter-learned average, so a counter
     * that reads a few % high (on-car 2026-10-04: ~28 kWh counted for ~27 off the pack)
     * would make it that much too short.
     */
    private fun calibrate(counterKwh: Double?, remainKwh: Double?, reset: Boolean) {
        val c = counterKwh?.takeIf { it.isFinite() && it >= 0.0 }
        val r = remainKwh?.takeIf { it.isFinite() && it in 0.0..1000.0 }
        val c0 = calCounterKwh
        val r0 = calRemainKwh
        if (reset || c == null || r == null) {
            if (reset) { calCounterKwh = null; calRemainKwh = null }
            return
        }
        if (c0 == null || r0 == null || c < c0 || r > r0 + CAL_REMAIN_RISE_KWH) {
            calCounterKwh = c; calRemainKwh = r
            return
        }
        if (c - c0 < CAL_MIN_KWH) return
        val ratio = (r0 - r) / (c - c0)
        if (ratio in BMS_RATIO_SANE) {
            state = state.copy(bmsRatio = state.bmsRatio?.let { it + (ratio - it) * CAL_BLEND } ?: ratio)
        }
        calCounterKwh = c; calRemainKwh = r
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
        /** Share of the reactive window at and beyond [BLEND_FULL_KM]; the prior keeps the rest. */
        /**
         * On-car 2026-10-04 a 0.6 share let two short warm-up trips (2.9 km at 34 kWh/100)
         * and a climb drop 90% from ~390 to 261 km. The window now only nudges the prior.
         */
        const val MAX_REACTIVE_WEIGHT = 0.35
        /**
         * The reactive window stays within these multiples of the prior. Hills are already
         * taken out of every block, so what remains is speed, climate and wind: at most
         * [MAX_REACTIVE_WEIGHT] x 30% = ~10% either way.
         */
        const val REACTIVE_MIN_RATIO = 0.8
        const val REACTIVE_MAX_RATIO = 1.3
        const val REACTIVE_RESET_MS = 60 * 60_000L
        const val TEMP_BAND_C = 5.0
        const val MIN_BUCKET_KM = 10.0
        /** A band with this many km moves the prior halfway to its own ratio. */
        const val TEMP_TRUST_KM = 150.0
        const val BUCKET_CAP_KM = 500.0

        /** Lower bound blocks regen-heavy windows from extrapolating an absurd range. */
        const val C_FLOOR = 8.0
        val SANE_AVG = C_FLOOR..100.0
        val SANE_TEMP_C = -45.0..65.0

        /**
         * Potential energy per metre of altitude, kWh: ~2150 kg loaded x g / 3.6e6. Taken in
         * full both ways, as the on-car trips matched: a long descent mostly saves energy
         * (gravity pushes instead of the motor) rather than going through regen losses.
         */
        const val HILL_KWH_PER_M = 2150.0 * 9.81 / 3_600_000.0
        /** More than this per 1-km block is a GPS jump, not a road (15% grade = 150 m). */
        const val MAX_CLIMB_PER_BLOCK_M = 200.0
        /** A fix that arrives after this much of a block is too late to start it. */
        const val ALT_LATE_START_KM = 0.1
        val SANE_ALT_M = -500.0..6000.0

        const val DEFAULT_SPEED_KMH = 40.0
        const val MIN_SPEED_KMH = 3.0
        /** Below this the climate share is not projected: a crawl would zero the range. */
        const val MIN_EST_SPEED_KMH = 15.0
        const val MAX_SPEED_KMH = 150.0
        const val SPEED_WINDOW_KM = 50.0
        const val MAX_CLIMATE_KW = 10.0
        const val CAL_MIN_KWH = 10.0
        const val CAL_BLEND = 0.5
        /** The pack gaining this much without a charge seen means one was missed. */
        const val CAL_REMAIN_RISE_KWH = 0.5
        val BMS_RATIO_SANE = 0.8..1.2
        val BMS_RATIO_USE = 0.85..1.15
    }
}
