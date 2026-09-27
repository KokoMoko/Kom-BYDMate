package com.bydmate.app.ui.dashboard

import android.content.Context
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.data.nativestack.FidCatalog
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.data.vehicle.DumpFidsResult
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

/**
 * Kom-BYDMate: մեքենայի սեփական ճանապարհային նշանների ճանաչումը (TSR, առջևի տեսախցիկ)՝
 * նույնը, ինչ HUD-ն է ցույց տալիս առանց Navigator-ի։
 *
 * Firmware-ում կան `Instrument.INSTRUMENT_TRAFFIC_SIGN_IDENTIFY*` ազդանշանները։ Դրանց fid
 * համարները ամեն firmware-ում տարբեր են, դրա համար helper daemon-ից վերցնում ենք fid-երի
 * ամբողջական կատալոգը, գտնում ենք «TRAFFIC_SIGN» պարունակող բոլոր Instrument սիմվոլները
 * և կարդում դրանք վայրկյանը մեկ։ [raw]-ը ախտորոշման համար է (Cluster ⋮), [limit]-ը՝
 * ճանաչված սահմանափակումը (0՝ չկա)։
 */
object CarSignReader {
    private const val TAG = "KomCarSign"
    private const val VALUE_SYMBOL = "Instrument.INSTRUMENT_TRAFFIC_SIGN_IDENTIFY_VALUE"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _raw = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val raw: StateFlow<Map<String, Long?>> = _raw

    private val _limit = MutableStateFlow(0)
    val limit: StateFlow<Int> = _limit

    private val _status = MutableStateFlow("not started")
    val status: StateFlow<String> = _status

    @Synchronized
    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val entry = EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)
        job = scope.launch {
            var targets: List<Triple<String, Int, Int>> = emptyList()  // symbol, dev, fid
            while (isActive && targets.isEmpty()) {
                targets = runCatching { discover(entry) }.onFailure { _status.value = "error: ${it.message}" }
                    .getOrDefault(emptyList())
                if (targets.isEmpty()) delay(30_000L)
            }
            _status.value = "reading ${targets.size} signals"
            while (isActive) {
                val values = LinkedHashMap<String, Long?>()
                for ((symbol, dev, fid) in targets) {
                    val v = runCatching { entry.helperClient().read(dev, fid, 5) }.getOrNull()
                    values[symbol] = v?.takeIf { SentinelDecoder.decodeInt(it.toInt()) != null }
                }
                _raw.value = values
                // Սահմանափակումը՝ միայն եթե արժեքը նման է իրական նշանի (10…150, 5-ի բազմապատիկ)
                val v = values[VALUE_SYMBOL]?.toInt() ?: 0
                _limit.value = if (v in 10..150 && v % 5 == 0) v else 0
                delay(1_000L)
            }
        }
    }

    private suspend fun discover(entry: ClusterEntryPoint): List<Triple<String, Int, Int>> {
        if (!entry.helperBootstrap().ensureRunning()) {
            _status.value = "helper not running"
            return emptyList()
        }
        val dump = entry.helperClient().dumpFids()
        if (dump !is DumpFidsResult.Success) {
            _status.value = "fid dump failed: $dump"
            return emptyList()
        }
        val catalog = FidCatalog.parse(dump.dump)
        val dev = catalog.deviceOf("INSTRUMENT")
        if (dev == null) {
            _status.value = "no INSTRUMENT device in catalog"
            return emptyList()
        }
        val found = catalog.symbols
            .filterKeys { it.startsWith("Instrument.") && it.contains("TRAFFIC_SIGN") }
            .map { (symbol, fid) -> Triple(symbol, dev, fid) }
        Log.i(TAG, "dev=$dev signals=${found.map { "${it.first}=${it.third}" }}")
        if (found.isEmpty()) _status.value = "no TRAFFIC_SIGN symbols in catalog"
        return found
    }
}
