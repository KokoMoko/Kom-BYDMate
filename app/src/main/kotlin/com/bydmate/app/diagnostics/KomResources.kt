package com.bydmate.app.diagnostics

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kom-BYDMate: what the app and its helper daemon cost the head unit, for Settings → Resources.
 * A sample a minute (RAM in MB, CPU in % of one core) is kept for the last 24 hours in a small
 * file, so a slow leak shows up as a rising line across restarts. Storage is measured on demand.
 */
object KomResources {
    private const val TAG = "KomResources"
    const val SAMPLE_MS = 60_000L
    const val KEEP = 24 * 60
    private const val FILE = "kom_resources.csv"
    /** Linux USER_HZ on Android: /proc CPU times are in 1/100 s. */
    private const val TICKS_PER_SEC = 100.0

    data class Sample(
        val timeMs: Long,
        val appMb: Float,
        val appCpu: Float,
        val helperMb: Float?,
        val helperCpu: Float?,
    )

    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = _samples

    private var started = false

    fun start(ctx: Context, scope: CoroutineScope) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        scope.launch(Dispatchers.IO) {
            _samples.value = load(app)
            var lastWall = SystemClock.elapsedRealtime()
            var lastApp = appCpuTicks()
            var lastHelper: com.bydmate.app.data.vehicle.HelperSelfStats? = null
            var sinceSave = 0
            while (true) {
                delay(SAMPLE_MS)
                runCatching {
                    val wall = SystemClock.elapsedRealtime()
                    val seconds = (wall - lastWall) / 1000.0
                    val appTicks = appCpuTicks()
                    val appCpu = cpuPercent(appTicks - lastApp, seconds)
                    val helper = runCatching {
                        EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java).helperClient().selfStats()
                    }.getOrNull()
                    val prev = lastHelper
                    val helperCpu = if (helper != null && prev != null && prev.pid == helper.pid) {
                        cpuPercent(helper.cpuTicks - prev.cpuTicks, seconds)
                    } else null
                    val sample = Sample(
                        timeMs = System.currentTimeMillis(),
                        appMb = appPssKb() / 1024f,
                        appCpu = appCpu,
                        helperMb = helper?.rssKb?.div(1024f),
                        helperCpu = helperCpu,
                    )
                    _samples.value = (_samples.value + sample).takeLast(KEEP)
                    lastWall = wall; lastApp = appTicks; lastHelper = helper
                    if (++sinceSave >= 10) { save(app, _samples.value); sinceSave = 0 }
                }.onFailure { Log.w(TAG, "sample: ${it.message}") }
            }
        }
    }

    /** CPU time over wall time, in % of one core (a busy core is 100). */
    internal fun cpuPercent(ticks: Long, seconds: Double): Float =
        if (seconds <= 0.0 || ticks < 0) 0f else (ticks / TICKS_PER_SEC / seconds * 100.0).toFloat()

    private fun appPssKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss

    private fun appCpuTicks(): Long = runCatching {
        com.bydmate.app.helper.procCpuTicks(File("/proc/self/stat").readText())
    }.getOrDefault(0L)

    // --- persistence: one CSV line per sample, "-" for a missing helper value ---

    internal fun encode(s: Sample): String =
        "${s.timeMs},${s.appMb},${s.appCpu},${s.helperMb ?: "-"},${s.helperCpu ?: "-"}"

    internal fun decode(line: String): Sample? = runCatching {
        val p = line.split(',')
        Sample(p[0].toLong(), p[1].toFloat(), p[2].toFloat(), p[3].toFloatOrNull(), p[4].toFloatOrNull())
    }.getOrNull()

    private fun load(ctx: Context): List<Sample> = runCatching {
        val cutoff = System.currentTimeMillis() - KEEP * SAMPLE_MS
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readLines()
            ?.mapNotNull(::decode)?.filter { it.timeMs >= cutoff }?.takeLast(KEEP) ?: emptyList()
    }.getOrDefault(emptyList())

    private fun save(ctx: Context, samples: List<Sample>) = runCatching {
        File(ctx.filesDir, FILE).writeText(samples.joinToString("\n", transform = ::encode))
    }

    // --- storage ---

    data class Storage(val parts: List<Pair<String, Long>>) {
        val total: Long get() = parts.sumOf { it.second }
    }

    /** APK, databases, cache and every top-level folder of the app's files, largest first. */
    suspend fun storage(ctx: Context): Storage = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val info = app.applicationInfo
        val apk = (listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList()))
            .sumOf { File(it).length() }
        val parts = mutableListOf("APK" to apk)
        File(info.dataDir, "databases").let { parts += "databases" to sizeOf(it) }
        File(info.dataDir, "shared_prefs").let { parts += "shared_prefs" to sizeOf(it) }
        parts += "cache" to (sizeOf(app.cacheDir) + (app.externalCacheDir?.let(::sizeOf) ?: 0L))
        app.filesDir.listFiles()?.forEach { f -> parts += "files/${f.name}" to sizeOf(f) }
        app.getExternalFilesDir(null)?.listFiles()?.forEach { f -> parts += "sdcard/${f.name}" to sizeOf(f) }
        Storage(parts.filter { it.second > 0 }.sortedByDescending { it.second })
    }

    suspend fun clearCache(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        app.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
