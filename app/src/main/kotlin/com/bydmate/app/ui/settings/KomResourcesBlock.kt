package com.bydmate.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.diagnostics.KomResources
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

private val HelperBlue = Color(0xFF60A5FA)

/** Kom-BYDMate: Settings → Diagnostics → Resources: RAM and CPU over 24 h, storage, cache. */
@Composable
fun KomResourcesBlock() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val samples by KomResources.samples.collectAsStateWithLifecycle()
    var storage by remember { mutableStateOf<KomResources.Storage?>(null) }
    LaunchedEffect(Unit) { storage = KomResources.storage(ctx) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.kom_res_title), color = TextPrimary, fontSize = 16.sp)
            val last = samples.lastOrNull()
            if (last == null) {
                Text(stringResource(R.string.kom_res_waiting), color = TextMuted, fontSize = 13.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Legend(AccentGreen, "Kom-BYDMate: ${mb(last.appMb)} · ${pct(last.appCpu)}")
                    Legend(HelperBlue, "helper: ${last.helperMb?.let(::mb) ?: "—"} · ${last.helperCpu?.let(::pct) ?: "—"}")
                }
                Text(stringResource(R.string.kom_res_ram), color = TextSecondary, fontSize = 13.sp)
                Chart(samples, { it.appMb }, { it.helperMb }, unit = " MB")
                Text(stringResource(R.string.kom_res_cpu), color = TextSecondary, fontSize = 13.sp)
                Chart(samples, { it.appCpu }, { it.helperCpu }, unit = "%")
                Row {
                    Text(stringResource(R.string.kom_res_24h_ago), color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.kom_res_now), color = TextMuted, fontSize = 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
            val st = storage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_res_storage) + (st?.let { ": " + size(it.total) } ?: ""),
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch { KomResources.clearCache(ctx); storage = KomResources.storage(ctx) }
                }) { Text(stringResource(R.string.kom_res_clear_cache)) }
            }
            st?.parts?.take(8)?.forEach { (name, bytes) ->
                Row {
                    Text(name, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(size(bytes), color = TextSecondary, fontSize = 13.sp)
                }
            }
            Text(stringResource(R.string.kom_res_hint), color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextPrimary, fontSize = 13.sp)
    }
}

/** Two lines (app green, helper blue) over the last 24 h by time; the scale tops at the largest value. */
@Composable
private fun Chart(
    samples: List<KomResources.Sample>,
    app: (KomResources.Sample) -> Float,
    helper: (KomResources.Sample) -> Float?,
    unit: String,
) {
    val top = (samples.map(app) + samples.mapNotNull(helper)).maxOrNull()?.takeIf { it > 0f }?.let(::niceCeil) ?: 1f
    val now = System.currentTimeMillis()
    val span = (KomResources.KEEP * KomResources.SAMPLE_MS).toFloat()
    Row {
        Canvas(Modifier.weight(1f).height(70.dp)) {
            val w = size.width
            val h = size.height
            for (i in 0..2) drawLine(CardBorder, Offset(0f, h * i / 2f), Offset(w, h * i / 2f), 1f)
            fun line(value: (KomResources.Sample) -> Float?, color: Color) {
                val path = Path()
                var prevMs = 0L
                samples.forEach { smp ->
                    val v = value(smp)
                    // a gap longer than three samples (app or car off) breaks the line
                    val joined = v != null && prevMs != 0L && smp.timeMs - prevMs <= 3 * KomResources.SAMPLE_MS
                    prevMs = if (v == null) 0L else smp.timeMs
                    if (v == null) return@forEach
                    val x = w * (1f - ((now - smp.timeMs) / span).coerceIn(0f, 1f))
                    val y = h - h * (v / top).coerceIn(0f, 1f)
                    if (joined) path.lineTo(x, y) else path.moveTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            line(helper, HelperBlue)
            line(app, AccentGreen)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.height(70.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(top) + unit, color = TextMuted, fontSize = 11.sp)
            Text("0", color = TextMuted, fontSize = 11.sp)
        }
    }
}

/** 1, 2, 5 × 10ⁿ above [v], so the scale reads round. */
internal fun niceCeil(v: Float): Float {
    var step = 1f
    while (step * 10 <= v) step *= 10
    while (step > v * 10) step /= 10
    return listOf(1f, 2f, 5f, 10f).map { it * step }.first { it >= v }
}

private fun fmt(v: Float) = if (v >= 10f) "%.0f".format(Locale.US, v) else "%.1f".format(Locale.US, v)
private fun mb(v: Float) = "%.0f MB".format(Locale.US, v)
private fun pct(v: Float) = "%.1f%%".format(Locale.US, v)
private fun size(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(Locale.US, bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1e6)
    else -> "%.0f KB".format(Locale.US, bytes / 1e3)
}
