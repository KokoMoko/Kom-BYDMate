package com.bydmate.app.domain.calculator

import android.content.Context
import com.bydmate.app.data.autoservice.AutoserviceClient
import com.bydmate.app.data.nativestack.FidAddresses
import com.bydmate.app.data.remote.DiParsData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class DrivingRangeStatus(
    val avgKwhPer100: Double = DrivingRangeModel.FALLBACK_AVG,
    val learned: Boolean = false,
    val vehicleBaseline: Boolean = false,
    val eligibleKm: Double = 0.0,
    val stationaryMinutes: Long = 0,
    val excludedKwh: Double = 0.0,
    val priorKwhPer100: Double = DrivingRangeModel.FALLBACK_AVG,
    val priorFromTemp: Boolean = false,
    val reactiveKwhPer100: Double? = null,
    val reactiveKm: Double = 0.0,
    val blendWeight: Double = 0.0,
    val tempBand: Int? = null,
)

/** Persistent range-only history; no legacy aggregate trip is imported. */
@Singleton
class DrivingRangeSource @Inject constructor(
    @ApplicationContext context: Context,
    private val autoservice: AutoserviceClient,
) : ConsumptionAvgSource {
    private val prefs = context.getSharedPreferences("kom_driving_range_v1", Context.MODE_PRIVATE)
    private val model = DrivingRangeModel(decode(prefs.getString("state", null)))
    private val _status = MutableStateFlow(DrivingRangeStatus())
    val status: StateFlow<DrivingRangeStatus> = _status
    private var lastBaselineReadMs = 0L
    private val baselineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var baselineJob: Job? = null
    @Volatile private var lastSampleMs: Long? = null
    private var lastWriteMs = 0L
    private var lastWrittenKey: Any? = null

    fun onSample(nowMs: Long, data: DiParsData, sessionId: Long?) {
        // Keep optional initialization IO outside the main telemetry coroutine.
        // One in-flight request at a time; failed reads retry on a later poll.
        if (!model.snapshot().baselineFromVehicle && !model.isLearned() &&
            baselineJob?.isActive != true && nowMs - lastBaselineReadMs >= 60_000L
        ) {
            lastBaselineReadMs = nowMs
            baselineJob = baselineScope.launch {
                val avg = runCatching {
                    val address = FidAddresses.of("lifetimeAvgPhm")
                    autoservice.getFloat(address.device, address.fid)
                }.getOrNull()
                avg?.toDouble()?.let { model.setProvisionalBaseline(it) }
            }
        }
        model.onSample(
            nowMs = nowMs, mileageKm = data.mileage, totalKwh = data.totalElecConsumption,
            speedKmh = data.speed, sessionId = sessionId,
            chargingOrDischarging = isChargingOrDischarging(data),
            exteriorTempC = data.exteriorTemp?.toDouble(),
        )
        lastSampleMs = nowMs
        val state = model.snapshot()
        // Write when learned history or the stop classification changes, otherwise at most
        // once a minute. Losing the last poll is harmless: after a restart the stale anchor
        // is older than MAX_GAP_MS and gets re-anchored anyway.
        val key = listOf(
            state.blocks, state.reactive, state.tempBuckets, state.stationarySinceMs,
            state.baseline, state.baselineFromVehicle,
        )
        if (key != lastWrittenKey || nowMs - lastWriteMs !in 0 until WRITE_INTERVAL_MS) {
            prefs.edit().putString("state", encode(state)).apply()
            lastWrittenKey = key
            lastWriteMs = nowMs
        }
        val estimate = model.rangeEstimate(nowMs)
        _status.value = DrivingRangeStatus(
            avgKwhPer100 = estimate.avg, learned = model.isLearned(),
            vehicleBaseline = state.baselineFromVehicle,
            eligibleKm = state.blocks.sumOf { it.km },
            stationaryMinutes = model.stationaryMinutes(nowMs), excludedKwh = state.excludedKwh,
            priorKwhPer100 = estimate.prior, priorFromTemp = estimate.priorFromTemp,
            reactiveKwhPer100 = estimate.reactive, reactiveKm = estimate.reactiveKm,
            blendWeight = estimate.weight, tempBand = estimate.band,
        )
    }

    override suspend fun recentAvgConsumption(): Double =
        model.rangeEstimate(lastSampleMs ?: System.currentTimeMillis()).avg

    companion object {
        private const val WRITE_INTERVAL_MS = 60_000L

        /** Below this (kW, negative = into the pack) a standstill can only be charging. */
        private const val STANDSTILL_CHARGE_KW = -1.0

        /**
         * Nothing that happens on a charger is driving: its energy is dropped outright, never
         * averaged in. Gun state is the primary signal; firmwares that do not report it are
         * caught by energy flowing into the pack at standstill (regen needs motion).
         */
        internal fun isChargingOrDischarging(data: DiParsData): Boolean =
            data.chargeGunState?.let { it in 2..5 } == true ||
                (data.speed == 0 && (data.power ?: 0.0) < STANDSTILL_CHARGE_KW)

        private fun blocksJson(blocks: List<DrivingRangeModel.Block>) = JSONArray().apply {
            blocks.forEach { put(JSONObject().put("km", it.km).put("kwh", it.kwh)) }
        }

        private fun parseBlocks(array: JSONArray, maxKm: Double): List<DrivingRangeModel.Block> {
            require(array.length() <= 110)
            val blocks = List(array.length()) { i ->
                val b = array.getJSONObject(i)
                val km = b.getDouble("km")
                val kwh = b.getDouble("kwh")
                require(km.isFinite() && km > 0.0 && km <= maxKm)
                require(kwh.isFinite())
                DrivingRangeModel.Block(km, kwh)
            }
            require(blocks.sumOf { it.km } <= maxKm + 0.001)
            return blocks
        }

        internal fun encode(s: DrivingRangeModel.State): String = JSONObject().apply {
            put("blocks", blocksJson(s.blocks))
            put("reactive", blocksJson(s.reactive))
            put("tempBuckets", JSONObject().apply {
                s.tempBuckets.forEach { (band, b) ->
                    put(band.toString(), JSONObject().put("km", b.km).put("kwh", b.kwh))
                }
            })
            put("lastCommitMs", s.lastCommitMs ?: JSONObject.NULL)
            put("tempC", s.tempC ?: JSONObject.NULL)
            put("bucketKm", s.bucketKm)
            put("bucketKwh", s.bucketKwh)
            put("pendingKwh", s.pendingKwh)
            put("stationarySinceMs", s.stationarySinceMs ?: JSONObject.NULL)
            put("excludedKwh", s.excludedKwh)
            put("baseline", s.baseline)
            put("baselineFromVehicle", s.baselineFromVehicle)
            put("last", s.last?.let {
                JSONObject().put("timeMs", it.timeMs).put("km", it.km)
                    .put("kwh", it.kwh).put("session", it.session)
            } ?: JSONObject.NULL)
        }.toString()

        internal fun decode(raw: String?): DrivingRangeModel.State = runCatching {
            if (raw == null) return@runCatching DrivingRangeModel.State()
            val o = JSONObject(raw)
            val blocks = parseBlocks(o.getJSONArray("blocks"), DrivingRangeModel.WINDOW_KM)
            // Fields added with the temperature/reactive model are optional, so the
            // first build's saved history still loads.
            val reactive = o.optJSONArray("reactive")
                ?.let { parseBlocks(it, DrivingRangeModel.REACTIVE_KM) } ?: emptyList()
            val tempBuckets = o.optJSONObject("tempBuckets")?.let { obj ->
                obj.keys().asSequence().associate { key ->
                    val b = obj.getJSONObject(key)
                    val km = b.getDouble("km")
                    val kwh = b.getDouble("kwh")
                    require(km.isFinite() && km > 0.0 && km <= DrivingRangeModel.BUCKET_CAP_KM + 0.001)
                    require(kwh.isFinite())
                    key.toInt() to DrivingRangeModel.Block(km, kwh)
                }
            } ?: emptyMap()
            val lastCommitMs = if (o.isNull("lastCommitMs")) null else o.getLong("lastCommitMs")
            val tempC = if (o.isNull("tempC")) null else o.getDouble("tempC")
            val baseline = o.getDouble("baseline")
            require(baseline in DrivingRangeModel.SANE_AVG)
            val bucketKm = o.getDouble("bucketKm")
            val bucketKwh = o.getDouble("bucketKwh")
            val pending = o.getDouble("pendingKwh")
            val excluded = o.getDouble("excludedKwh")
            require(bucketKm.isFinite() && bucketKm in 0.0..DrivingRangeModel.BLOCK_KM)
            require(bucketKwh.isFinite() && pending.isFinite() && excluded.isFinite())
            val last = o.optJSONObject("last")?.let {
                val sample = DrivingRangeModel.Sample(
                    it.getLong("timeMs"), it.getDouble("km"),
                    it.getDouble("kwh"), it.getLong("session"),
                )
                require(sample.timeMs > 0 && sample.km.isFinite() && sample.km >= 1.0)
                require(sample.kwh.isFinite() && sample.kwh >= 0.0)
                sample
            }
            DrivingRangeModel.State(
                blocks = blocks, bucketKm = bucketKm, bucketKwh = bucketKwh,
                pendingKwh = pending,
                stationarySinceMs = if (o.isNull("stationarySinceMs")) null else o.getLong("stationarySinceMs"),
                last = last, excludedKwh = excluded, baseline = baseline,
                baselineFromVehicle = o.optBoolean("baselineFromVehicle"),
                tempBuckets = tempBuckets, reactive = reactive,
                lastCommitMs = lastCommitMs, tempC = tempC,
            )
        }.getOrElse { DrivingRangeModel.State() }
    }
}
