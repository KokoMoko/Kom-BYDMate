package com.bydmate.app.navdata

import android.location.Location
import android.util.Log
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Kom-BYDMate: արագության սահմանափակում OpenStreetMap-ից (Overpass API)՝ պահեստային աղբյուր,
 * երբ Navigator-ը չի երևում (հետին պլանում է, և Android-ը նրա պատուհանը չի տալիս accessibility-ին)։
 *
 * Շարժման ժամանակ (≥ 10 կմ/ժ), 80 մ-ը կամ 20 վրկ-ը մեկ, հարցնում ենք GPS դիրքի 30 մ շրջակայքի
 * ճանապարհների `maxspeed`-ը։ Երևանում OSM-ի ծածկույթը ամբողջական չէ․ որտեղ նշված չէ, արժեք չկա։
 */
object KomOsmSpeedLimit {
    private const val TAG = "KomOsmLimit"
    private val ENDPOINTS = listOf(
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass-api.de/api/interpreter",
    )
    private const val MIN_SPEED_MS = 10f / 3.6f
    private const val MIN_MOVE_M = 80f
    private const val MIN_INTERVAL_MS = 20_000L

    @Volatile var limit: Int = 0
        private set
    @Volatile var lastMs: Long = 0L
        private set

    private val http = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Թարմ արժեք (վերջին [maxAgeMs]-ում), այլապես 0։ */
    fun fresh(maxAgeMs: Long = 60_000L, nowMs: Long = System.currentTimeMillis()): Int =
        if (limit > 0 && nowMs - lastMs <= maxAgeMs) limit else 0

    fun start(scope: CoroutineScope) {
        scope.launch {
            var lastQueryLoc: Location? = null
            var lastQueryMs = 0L
            while (true) {
                delay(3_000L)
                val loc = TrackingService.lastLocation.value ?: continue
                val speedMs = if (loc.hasSpeed()) loc.speed
                    else (TrackingService.lastData.value?.speed ?: 0) / 3.6f
                if (speedMs < MIN_SPEED_MS) continue
                val now = System.currentTimeMillis()
                val moved = lastQueryLoc?.distanceTo(loc) ?: Float.MAX_VALUE
                if (moved < MIN_MOVE_M && now - lastQueryMs < MIN_INTERVAL_MS) continue
                lastQueryLoc = Location(loc)
                lastQueryMs = now
                val v = runCatching { query(loc.latitude, loc.longitude) }
                    .onFailure { Log.w(TAG, "overpass failed: ${it.message}") }
                    .getOrNull()
                if (v != null && v > 0) {
                    if (v != limit) Log.i(TAG, "osm speed limit $v")
                    limit = v
                    lastMs = now
                }
            }
        }
    }

    private suspend fun query(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        val q = "[out:json][timeout:8];way(around:30,$lat,$lon)[highway][maxspeed];out tags center 8;"
        for (url in ENDPOINTS) {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Kom-BYDMate (personal, noncommercial)")
                .post(FormBody.Builder().add("data", q).build())
                .build()
            val body = runCatching { http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }
                .getOrNull() ?: continue
            val els = JSONObject(body).optJSONArray("elements") ?: return@withContext null
            var best: Int? = null
            var bestDist = Double.MAX_VALUE
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val ms = parseMaxspeed(e.optJSONObject("tags")?.optString("maxspeed")) ?: continue
                val c = e.optJSONObject("center")
                val d = if (c != null) {
                    val r = FloatArray(1)
                    Location.distanceBetween(lat, lon, c.optDouble("lat"), c.optDouble("lon"), r)
                    r[0].toDouble()
                } else 1e9
                if (d < bestDist) { bestDist = d; best = ms }
            }
            return@withContext best
        }
        null
    }

    /** "60", "50 mph", "RU:urban" / "AM:urban" (60), "…:rural" (90), "…:motorway" (110)։ */
    internal fun parseMaxspeed(raw: String?): Int? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it.takeIf { v -> v in 5..150 } }
        Regex("""^(\d+)\s*mph$""").find(s)?.let { return (it.groupValues[1].toInt() * 1.609).toInt() }
        return when {
            s.endsWith(":urban") -> 60
            s.endsWith(":rural") -> 90
            s.endsWith(":motorway") -> 110
            s.endsWith(":living_street") -> 20
            else -> null
        }
    }
}
