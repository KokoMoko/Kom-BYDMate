package com.bydmate.app.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Kom-BYDMate: GPS signal for the dashboards and the cluster, from the head unit's own GNSS
 * status (the satellites a GPS-test app shows). [Signal.used] counts the satellites in the fix,
 * [Signal.bars] grades how far the position can be trusted.
 */
object KomGpsSignal {
    private const val TAG = "KomGpsSignal"

    data class Signal(val used: Int, val visible: Int, val topCn0: Float) {
        /** 0 = no usable fix, 1-2 = weak, 3-4 = good. */
        val bars: Int get() = barsFor(used, topCn0)
    }

    /** No status received yet (GNSS off, no permission): the indicator shows a dash. */
    private val _signal = MutableStateFlow<Signal?>(null)
    val signal: StateFlow<Signal?> = _signal

    private var started = false

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (started) return
        val app = ctx.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) return
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                _signal.value = summarize(status)
            }
            override fun onStopped() { _signal.value = null }
        }
        started = runCatching { lm.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper())) }
            .onFailure { Log.w(TAG, "register: ${it.message}") }.getOrDefault(false)
    }

    private fun summarize(status: GnssStatus): Signal {
        val usedCn0 = ArrayList<Float>()
        for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) usedCn0 += status.getCn0DbHz(i)
        return Signal(usedCn0.size, status.satelliteCount, topAverage(usedCn0))
    }

    /** Mean C/N0 of the four strongest satellites in the fix (what GNSS health reports use). */
    internal fun topAverage(cn0: List<Float>): Float =
        cn0.sortedDescending().take(4).takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f

    /**
     * 4 bars: >= 8 satellites in the fix and strong (>= 30 dB-Hz); 3: >= 6; 2: >= 4 with a usable
     * signal (>= 20 dB-Hz); 1: >= 4 but faint; 0: fewer than 4 — no 3D fix.
     */
    internal fun barsFor(used: Int, topCn0: Float): Int = when {
        used < 4 -> 0
        used >= 8 && topCn0 >= 30f -> 4
        used >= 6 && topCn0 >= 20f -> 3
        topCn0 >= 20f -> 2
        else -> 1
    }
}

private val Amber = Color(0xFFF59E0B)
private val BarOff = Color(0xFF334155)

/** Satellite icon, four signal bars and the number of satellites in the fix, coloured by grade. */
@Composable
internal fun GpsSignalItem(fs: Int = 18) {
    // The app-start registration misses a location permission granted later (first-run wizard).
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { KomGpsSignal.start(ctx) }
    val signal by KomGpsSignal.signal.collectAsStateWithLifecycle()
    val bars = signal?.bars ?: 0
    val color = when {
        signal == null -> Color(0xFF64748B)
        bars >= 3 -> AccentGreen
        bars >= 1 -> Amber
        else -> SocRed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.SatelliteAlt, contentDescription = null, tint = color, modifier = Modifier.size((fs + 2).dp))
        Spacer(Modifier.width(4.dp))
        Canvas(Modifier.size(width = (fs * 0.95f).dp, height = (fs * 0.85f).dp)) {
            val gap = size.width * 0.1f
            val w = (size.width - 3 * gap) / 4
            for (i in 0 until 4) {
                val h = size.height * (0.4f + 0.2f * i)
                drawRoundRect(
                    color = if (i < bars) color else BarOff,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w * 0.25f),
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(signal?.used?.toString() ?: "—", color = color, fontSize = fs.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false)
    }
}

/**
 * Top-right corner of every dashboard title row: GPS signal | altitude. Kept out of the cards and
 * the cluster headers, where it crowded the temperatures and the battery bar (on-car 2026-10-03).
 */
@Composable
internal fun AltitudeGpsStatus(fs: Int = 16) {
    val extras by KomClusterExtras.extras.collectAsStateWithLifecycle()
    // ~0.25 cm off the right edge of the row (on-car 2026-10-03: 0 was too close, 0.5 cm too far)
    Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        GpsSignalItem(fs)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(1.5.dp).height(fs.dp).background(TextSecondary.copy(alpha = 0.5f)))
        Spacer(Modifier.width(8.dp))
        InfoItem(Icons.Outlined.Terrain, extras.altitudeM?.let { "$it m" } ?: "— m", fs)
    }
}
