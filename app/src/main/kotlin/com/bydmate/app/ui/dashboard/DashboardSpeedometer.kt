package com.bydmate.app.ui.dashboard

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.navdata.NavGuidanceHub
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.SocYellow
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Kom-BYDMate: Главная-ի B քարտի սպիդոմետրի ոճերը (լռելյայն՝ MODERN)։ */
enum class SpeedoStyle(val labelRes: Int) {
    MODERN(R.string.kom_speedo_modern),
    CLASSIC(R.string.kom_speedo_classic),
    DIGITAL(R.string.kom_speedo_digital),
    MINIMAL(R.string.kom_speedo_minimal),
}

object SpeedoPrefs {
    private const val PREFS = "kom_dashboard_widgets"
    private const val KEY = "speedo_style"

    fun get(ctx: Context): SpeedoStyle =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { SpeedoStyle.valueOf(it) }.getOrNull() } ?: SpeedoStyle.MODERN

    fun set(ctx: Context, style: SpeedoStyle) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style.name).apply()
}

private const val VMAX = 180f
private val Track = Color(0xFF2D3C55)

/** Արագության գույնը՝ ըստ Navigator-ի սահմանափակման (առանց սահմանափակման՝ կանաչ)։ */
private fun speedColor(v: Float, limit: Int): Color = when {
    limit <= 0 || v <= limit -> AccentGreen
    v <= limit + 10 -> SocYellow
    else -> SocRed
}

/**
 * Սպիդոմետր՝ մեքենայի իրական արագությամբ, սահուն անիմացիայով և Navigator-ի սահմանափակման նշանով։
 * ⋮-ով ընտրվում է ոճը։
 */
@Composable
fun DashboardSpeedometer(speed: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(SpeedoPrefs.get(context)) }
    var showMenu by remember { mutableStateOf(false) }
    // Սահմանափակումը՝ Navigator-ից (0 = չկա կամ 30 վ-ից հին է)
    val limit by produceState(initialValue = 0) {
        while (true) {
            value = runCatching { NavGuidanceHub.snapshot().speedLimit }.getOrDefault(0)
            delay(1_000L)
        }
    }
    val v by animateFloatAsState(
        targetValue = speed.coerceIn(0, VMAX.toInt()).toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "speed",
    )

    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when (style) {
                    SpeedoStyle.MODERN -> ModernArc(v, limit)
                    SpeedoStyle.CLASSIC -> Classic(v, limit)
                    SpeedoStyle.DIGITAL -> DigitalLed(v, limit)
                    SpeedoStyle.MINIMAL -> MinimalRing(v, limit)
                }
            }
            if (limit > 0) {
                Spacer(Modifier.width(8.dp))
                LimitSign(limit)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(NavyDark.copy(alpha = 0.5f))
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center,
        ) { Text("⋮", color = TextSecondary, fontSize = 16.sp) }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(stringResource(R.string.kom_speedo_style), color = TextPrimary) },
            text = {
                Column {
                    SpeedoStyle.values().forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { style = s; SpeedoPrefs.set(context, s); showMenu = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = style == s, onClick = { style = s; SpeedoPrefs.set(context, s); showMenu = false })
                            Text(stringResource(s.labelRes), color = TextPrimary, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text(stringResource(R.string.kom_cancel)) } },
            containerColor = CardSurface,
        )
    }
}

@Composable
private fun SpeedText(v: Float, limit: Int, big: Int, colored: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${v.roundToInt()}",
            color = if (colored) speedColor(v, limit) else TextPrimary,
            fontSize = big.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        )
        Text(stringResource(R.string.kom_ctx_speed_unit), color = TextMuted, fontSize = 13.sp)
    }
}

/** 2. Աղեղ SOC-ի շրջանի ոճով՝ կանաչից դեղին/կարմիր։ */
@Composable
private fun ModernArc(v: Float, limit: Int) {
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = 270f * v / VMAX
            val steps = 48
            for (i in 0 until steps) {
                val t = i / steps.toFloat()
                val sv = v * t
                val col = when {
                    limit <= 0 -> lerp(AccentGreen, SocYellow, (sv / 120f).coerceIn(0f, 1f))
                    sv <= limit -> lerp(AccentGreen, SocYellow, (sv / limit).coerceIn(0f, 1f) * 0.6f)
                    else -> lerp(SocYellow, SocRed, ((sv - limit) / 20f).coerceIn(0f, 1f))
                }
                drawArc(col, 135f + sweep * t, sweep / steps + 0.6f, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
            }
        }
        SpeedText(v, limit, big = 40)
    }
}

/** 1. Դասական՝ սլաք և նշաձողեր 0–180։ */
@Composable
private fun Classic(v: Float, limit: Int) {
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1.15f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.46f
            val c = Offset(size.width / 2, size.height * 0.55f)
            val a0 = 150f
            val span = 240f
            fun pt(angleDeg: Float, radius: Float): Offset {
                val a = Math.toRadians(angleDeg.toDouble())
                return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
            }
            drawArc(Track, a0, span, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(r * 0.03f))
            if (limit > 0) {
                val la = a0 + span * limit / VMAX
                drawArc(SocRed, la, span * (VMAX - limit) / VMAX, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                    style = Stroke(r * 0.05f))
            }
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = TextMuted.toArgb()
                textSize = r * 0.14f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (s in 0..VMAX.toInt() step 10) {
                val ang = a0 + span * s / VMAX
                val major = s % 20 == 0
                drawLine(if (major) TextPrimary else TextMuted, pt(ang, r * (if (major) 0.84f else 0.9f)), pt(ang, r),
                    strokeWidth = r * (if (major) 0.03f else 0.018f), cap = StrokeCap.Round)
                if (major) {
                    val p = pt(ang, r * 0.68f)
                    drawContext.canvas.nativeCanvas.drawText("$s", p.x, p.y + paint.textSize / 3, paint)
                }
            }
            val needleColor = if (limit > 0 && v > limit) SocRed else Color(0xFFF87171)
            drawLine(needleColor, c, pt(a0 + span * v / VMAX, r * 0.9f), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFCBD5E1), r * 0.07f, c)
        }
        Box(Modifier.padding(bottom = 2.dp)) { SpeedText(v, limit, big = 22) }
    }
}

/** 3. Մեծ գունավոր թիվ և LED սանդղակ։ */
@Composable
private fun DigitalLed(v: Float, limit: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${v.roundToInt()}", color = speedColor(v, limit), fontSize = 52.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.kom_ctx_speed_unit), color = TextMuted, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 10.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(26.dp)) { drawLedBar(v, limit) }
    }
}

private fun DrawScope.drawLedBar(v: Float, limit: Int) {
    val segs = 30
    val gap = size.width * 0.006f
    val sw = (size.width - gap * (segs - 1)) / segs
    val lit = (segs * v / VMAX).roundToInt()
    for (i in 0 until segs) {
        val segV = VMAX * (i + 1) / segs
        val col = if (i < lit) {
            when {
                limit <= 0 -> AccentGreen
                segV <= limit -> AccentGreen
                segV <= limit + 20 -> SocYellow
                else -> SocRed
            }
        } else Track
        val h = size.height * (0.45f + 0.55f * i / (segs - 1))
        drawRoundRect(col, Offset(i * (sw + gap), size.height - h), Size(sw, h), CornerRadius(sw * 0.25f))
    }
}

/** 4. Մինիմալ՝ մեծ թիվ շրջանակի մեջ, որը լցվում է ըստ արագության։ */
@Composable
private fun MinimalRing(v: Float, limit: Int) {
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(speedColor(v, limit), -90f, 360f * v / VMAX, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        SpeedText(v, limit, big = 40)
    }
}

/** Արագության սահմանափակման ճանապարհային նշանը։ */
@Composable
private fun LimitSign(limit: Int) {
    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White)
            drawCircle(SocRed, radius = size.minDimension / 2 * 0.9f, style = Stroke(size.minDimension * 0.11f))
        }
        Text("$limit", color = Color(0xFF111111), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
