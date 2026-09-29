package com.bydmate.app.hud

import android.content.Context
import android.os.Environment
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.data.nativestack.FidCatalog
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.DumpFidsResult
import com.bydmate.app.helper.HelperBinderProtocol
import com.bydmate.app.helper.push.FID_REC_NO_ERROR
import com.bydmate.app.navdata.KomNavLimit
import com.bydmate.app.navdata.KomOsmSpeedLimit
import com.bydmate.app.navdata.NavGuidanceHub
import com.bydmate.app.service.TrackingService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kom-BYDMate: մեքենայի տեսախցիկի նշանների (TSR) ազդանշանի որոնում ուղևորության ընթացքում։
 *
 * Երկու ֆայլ /sdcard/Download-ում․
 *  1. `fidrec-…` — BYDMate-ի push գրանցիչը ([com.bydmate.app.helper.push.FidRecorder]) ADAS (1038)
 *     և INSTRUMENT (1007) սարքերի ԲՈԼՈՐ fid-երի փոփոխություններով (daemon-ն է գրում)։
 *  2. `kom-tsr-….csv` — համատեքստ 2 վրկ-ը մեկ․ արագություն, GPS, Navigator-ի / OSM-ի սահմանափակում,
 *     և թեկնածու fid-երի (SLA / ISLA / TSR / SPEED_LIMIT / TRAFFIC_SIGN) արժեքները՝ միայն փոփոխվելիս։
 * Հետո երկու ֆայլը համեմատում ենք․ որ fid-ն է փոխվում այնտեղ, որտեղ Navigator-ի սահմանափակումը։
 */
object KomTsrLogger {
    private const val TAG = "KomTsrLogger"
    private const val TICK_MS = 2_000L
    private val REC_DEVICES = intArrayOf(1038, 1007)
    private val CANDIDATE = Regex("SLA|ISLA|SPEED_LIMIT|LIMIT_SPEED|TSR_TARGET_(TYPE|STATE)|TRAFFIC_SIGN|SIGN_VALUE|TSR_SPEED")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    val running: Boolean get() = job?.isActive == true

    @Synchronized
    fun toggle(ctx: Context) {
        if (running) stop(ctx) else start(ctx)
    }

    @Synchronized
    private fun start(ctx: Context) {
        val app = ctx.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java)
        job = scope.launch {
            _status.value = "TSR log: starting…"
            if (!entry.helperBootstrap().ensureRunning()) {
                _status.value = "TSR log: helper not running"
                return@launch
            }
            val helper = entry.helperClient()
            val rec = helper.recStart(REC_DEVICES)
            val recInfo = when {
                rec == null -> "push rec: no reply"
                rec.error != FID_REC_NO_ERROR -> "push rec: ${rec.error}"
                else -> "push rec: ${rec.registered} fids"
            }
            Log.i(TAG, "start: $recInfo")

            // Թեկնածու fid-երը՝ firmware-ի կատալոգից (համարները ամեն firmware-ում տարբեր են)
            val candidates = runCatching {
                val dump = helper.dumpFids() as? DumpFidsResult.Success ?: return@runCatching emptyList()
                val cat = FidCatalog.parse(dump.dump)
                val adas = cat.deviceOf("ADAS") ?: 1038
                val inst = cat.deviceOf("INSTRUMENT") ?: 1007
                cat.symbols.entries
                    .filter { (s, _) ->
                        (s.startsWith("Adas.") || s.startsWith("Instrument.")) &&
                            !s.endsWith("_SET") && CANDIDATE.containsMatchIn(s)
                    }
                    .sortedBy { it.key }
                    .take(HelperBinderProtocol.MAX_BATCH_ITEMS)
                    .map { (s, fid) -> Triple(s, if (s.startsWith("Adas.")) adas else inst, fid) }
            }.getOrDefault(emptyList())

            val file = openFile(app)
            if (file == null) {
                _status.value = "TSR log: no storage · $recInfo"
                return@launch
            }
            val out = FileWriter(file, true)
            try {
                out.write("# Kom TSR context log · $recInfo · candidates=${candidates.size}\n")
                candidates.forEach { out.write("# cand ${it.first} dev=${it.second} fid=${it.third}\n") }
                out.write("time,speed,lat,lon,navLimit,osmLimit,maneuver,dist,changes\n")
                val last = HashMap<String, Int?>()
                var lines = 0
                val tf = SimpleDateFormat("HH:mm:ss", Locale.US)
                while (isActive) {
                    val values = if (candidates.isEmpty()) emptyList() else runCatching {
                        helper.readBatch(candidates.map { BatchReadItem(5, it.second, it.third) })
                    }.getOrNull().orEmpty()
                    val changes = StringBuilder()
                    values.forEachIndexed { i, (st, raw) ->
                        val sym = candidates.getOrNull(i)?.first ?: return@forEachIndexed
                        val v = if (st == 0) SentinelDecoder.decodeInt(raw) else null
                        if (!last.containsKey(sym) || last[sym] != v) {
                            if (last.containsKey(sym) || v != null) {
                                changes.append(sym.substringAfter('.')).append('=').append(v ?: "-").append(' ')
                            }
                            last[sym] = v
                        }
                    }
                    val loc = TrackingService.lastLocation.value
                    val nav = NavGuidanceHub.snapshot()
                    out.write(
                        listOf(
                            tf.format(Date()),
                            TrackingService.lastData.value?.speed ?: "",
                            loc?.latitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                            loc?.longitude?.let { "%.6f".format(Locale.US, it) } ?: "",
                            KomNavLimit.fresh(),
                            KomOsmSpeedLimit.fresh(),
                            if (nav.active) nav.maneuverGaode else "",
                            if (nav.active) nav.distanceMeters else "",
                            "\"" + changes.toString().trim() + "\"",
                        ).joinToString(",") + "\n"
                    )
                    out.flush()
                    lines++
                    val recStatus = runCatching { helper.recStatus() }.getOrNull()
                    _status.value = "TSR log: ● recording $lines rows · push ${recStatus?.totalEvents ?: "?"} events" +
                        (if (recStatus?.running == false) " (push stopped)" else "") + "\n${file.name}"
                    delay(TICK_MS)
                }
            } finally {
                runCatching { out.close() }
            }
        }
    }

    @Synchronized
    private fun stop(ctx: Context) {
        job?.cancel()
        job = null
        val entry = EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)
        scope.launch {
            val st = runCatching { entry.helperClient().recStatus() }.getOrNull()
            runCatching { entry.helperClient().recStop() }
            _status.value = "TSR log: stopped · push ${st?.totalEvents ?: "?"} events\n${st?.filePath.orEmpty()}"
            Log.i(TAG, "stop: ${st?.filePath}")
        }
    }

    private fun openFile(ctx: Context): File? {
        val name = "kom-tsr-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".csv"
        val dir = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Download"),
            ctx.getExternalFilesDir(null),
        ).firstOrNull { it != null && (it.exists() || it.mkdirs()) && it.canWrite() } ?: return null
        return File(dir, name)
    }
}
