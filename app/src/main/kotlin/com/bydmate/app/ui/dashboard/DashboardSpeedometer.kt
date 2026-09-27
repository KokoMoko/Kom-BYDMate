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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Kom-BYDMate: Главная-ի B քարտի սպիդոմետրի ոճերը (swipe-ի հերթականությամբ, լռելյայն՝ MODERN)։ */
enum class SpeedoStyle(val labelRes: Int) {
    MODERN(R.string.kom_speedo_modern),
    LINEAR(R.string.kom_speedo_linear),
    CLASSIC(R.string.kom_speedo_classic),
    DIGITAL(R.string.kom_speedo_digital),
    MINIMAL(R.string.kom_speedo_minimal),
}

object SpeedoPrefs {
    private const val PREFS = "kom_dashboard_widgets"
    private const val KEY_STYLE = "speedo_style"
    private const val KEY_MAX = "speedo_max_speed"
    private const val KEY_TOL = "speedo_tolerance_kmh"
    const val DEFAULT_MAX = 90     // Հայաստան՝ 90 կմ/ժ
    const val DEFAULT_TOL = 10     // +10 կմ/ժ թույլատրելի շեղում (60 → 70, 90 → 100)

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun style(ctx: Context): SpeedoStyle =
        p(ctx).getString(KEY_STYLE, null)?.let { runCatching { SpeedoStyle.valueOf(it) }.getOrNull() }
            ?: SpeedoStyle.MODERN

    fun setStyle(ctx: Context, s: SpeedoStyle) = p(ctx).edit().putString(KEY_STYLE, s.name).apply()
    fun maxSpeed(ctx: Context) = p(ctx).getInt(KEY_MAX, DEFAULT_MAX)
    fun setMaxSpeed(ctx: Context, v: Int) = p(ctx).edit().putInt(KEY_MAX, v).apply()
    fun tolerance(ctx: Context) = p(ctx).getInt(KEY_TOL, DEFAULT_TOL)
    fun setTolerance(ctx: Context, v: Int) = p(ctx).edit().putInt(KEY_TOL, v).apply()
}

private const val VMAX = 180f
private val Track = Color(0xFF2D3C55)
private val DarkRed = Color(0xFF991B1B)

/**
 * Գունային շեմեր․ [limit]՝ Navigator-ի սահմանափակումը, եթե կա, հակառակ դեպքում օգտատիրոջ Max speed-ը։
 * Մինչև limit՝ կանաչ, limit…upper՝ գրադիենտ կանաչից կարմիր, upper-ից վեր՝ մուգ կարմիր։
 */
private data class Thresholds(val limit: Float, val upper: Float, val fromNav: Boolean) {
    fun color(v: Float): Color = when {
        v <= limit -> AccentGreen
        v <= upper -> lerp(AccentGreen, SocRed, ((v - limit) / (upper - limit).coerceAtLeast(1f)).coerceIn(0f, 1f))
        else -> DarkRed
    }
}

@Composable
fun DashboardSpeedometer(speed: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var maxSpeed by remember { mutableIntStateOf(SpeedoPrefs.maxSpeed(context)) }
    var tolerance by remember { mutableIntStateOf(SpeedoPrefs.tolerance(context)) }
    var showSettings by remember { mutableStateOf(false) }
    val nav = rememberNavSpeedLimit()
    val navLimit = nav.limit
    val limit = if (navLimit > 0) navLimit else maxSpeed
    val th = Thresholds(limit.toFloat(), (limit + tolerance).toFloat(), fromNav = navLimit > 0)
    val v by animateFloatAsState(
        targetValue = speed.coerceIn(0, VMAX.toInt()).toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "speed",
    )

    // Swipe՝ ոճերի միջև․ ընտրված էջը պահպանվում է
    val styles = SpeedoStyle.values()
    val pager = rememberPagerState(initialPage = SpeedoPrefs.style(context).ordinal) { styles.size }
    LaunchedEffect(pager.currentPage) { SpeedoPrefs.setStyle(context, styles[pager.currentPage]) }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxHeight()) { page ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (styles[page]) {
                            SpeedoStyle.MODERN -> ModernArc(v, th)
                            SpeedoStyle.LINEAR -> LinearBar(v, th)
                            SpeedoStyle.CLASSIC -> Classic(v, th)
                            SpeedoStyle.DIGITAL -> DigitalLed(v, th)
                            SpeedoStyle.MINIMAL -> MinimalRing(v, th)
                        }
                    }
                }
                if (th.fromNav) {
                    Spacer(Modifier.width(8.dp))
                    LimitSign(navLimit, fresh = nav.fresh)
                }
            }
            // Էջերի կետեր
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
                styles.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pager.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) AccentGreen else Track)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(NavyDark.copy(alpha = 0.5f))
                .clickable { showSettings = true },
            contentAlignment = Alignment.Center,
        ) { Text("⋮", color = TextSecondary, fontSize = 16.sp) }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.kom_speedo_settings), color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.kom_speedo_max_speed, maxSpeed), color = TextPrimary, fontSize = 16.sp)
                    Slider(
                        value = maxSpeed.toFloat(),
                        onValueChange = { maxSpeed = (it / 5).roundToInt() * 5 },
                        onValueChangeFinished = { SpeedoPrefs.setMaxSpeed(context, maxSpeed) },
                        valueRange = 30f..150f,
                    )
                    Text(stringResource(R.string.kom_speedo_tolerance_kmh, tolerance, maxSpeed + tolerance),
                        color = TextPrimary, fontSize = 16.sp)
                    Slider(
                        value = tolerance.toFloat(),
                        onValueChange = { tolerance = it.roundToInt() },
                        onValueChangeFinished = { SpeedoPrefs.setTolerance(context, tolerance) },
                        valueRange = 0f..30f,
                    )
                    Text(stringResource(R.string.kom_speedo_nav_hint), color = TextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SpeedoPrefs.setMaxSpeed(context, maxSpeed); SpeedoPrefs.setTolerance(context, tolerance)
                    showSettings = false
                }) { Text(stringResource(R.string.kom_done)) }
            },
            containerColor = CardSurface,
        )
    }
}

@Composable
private fun SpeedText(v: Float, big: Int, color: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${v.roundToInt()}", color = color, fontSize = big.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(stringResource(R.string.kom_ctx_speed_unit), color = TextMuted, fontSize = 13.sp)
    }
}

/** Աղեղ SOC-ի շրջանի ոճով՝ յուրաքանչյուր հատված ներկված ըստ իր արագության։ */
@Composable
private fun ModernArc(v: Float, th: Thresholds) {
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
                drawArc(th.color(v * t), 135f + sweep * t, sweep / steps + 0.6f, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
            }
        }
        SpeedText(v, big = 40)
    }
}

/**
 * Գծային՝ 0…upper գոտին (85%) ներկվում է կանաչ → գրադիենտ → կարմիր, upper-ից վեր (15%)՝ մուգ կարմիր։
 * Թիվը «վազում» է գծի հետ՝ գտնվում է լցված մասի վերջում։
 */
@Composable
private fun LinearBar(v: Float, th: Thresholds) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.kom_speedo_speed_label), color = TextSecondary, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        val main = 0.85f
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val r = CornerRadius(h * 0.25f)
                drawRoundRect(Track, Offset.Zero, size, r)
                val frac = if (v <= th.upper) main * v / th.upper
                else main + (1f - main) * ((v - th.upper) / 20f).coerceIn(0f, 1f)
                val brush = Brush.horizontalGradient(
                    0f to AccentGreen,
                    main * th.limit / th.upper to AccentGreen,
                    main to SocRed,
                    main + 0.001f to DarkRed,
                    1f to DarkRed,
                    startX = 0f, endX = w,
                )
                if (frac > 0f) drawRoundRect(brush, Offset.Zero, Size(w * frac, h), r)
                // Սահմանի գիծ՝ upper-ում
                drawLine(NavyDark, Offset(w * main, 0f), Offset(w * main, h), strokeWidth = 3.dp.toPx())
            }
            Text(
                "${v.roundToInt()}",
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(main))
            Text("${th.upper.roundToInt()}", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f - main))
        }
    }
}

/** Դասական՝ սլաք և նշաձողեր 0–180, կարմիր գոտի սահմանից վեր։ */
@Composable
private fun Classic(v: Float, th: Thresholds) {
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
            val box = Offset(c.x - r, c.y - r)
            drawArc(Track, a0, span, false, box, Size(2 * r, 2 * r), style = Stroke(r * 0.03f))
            val la = a0 + span * th.limit / VMAX
            val ua = a0 + span * th.upper / VMAX
            drawArc(SocRed, la, ua - la, false, box, Size(2 * r, 2 * r), style = Stroke(r * 0.05f))
            drawArc(DarkRed, ua, a0 + span - ua, false, box, Size(2 * r, 2 * r), style = Stroke(r * 0.05f))
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
            drawLine(th.color(v).takeIf { v > th.limit } ?: Color(0xFFF87171), c, pt(a0 + span * v / VMAX, r * 0.9f),
                strokeWidth = r * 0.045f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFCBD5E1), r * 0.07f, c)
        }
        Box(Modifier.padding(bottom = 2.dp)) { SpeedText(v, big = 22) }
    }
}

/** Թվային՝ մեծ գունավոր թիվ և LED սանդղակ (0…upper+20), limit/upper նշաններով։ */
@Composable
private fun DigitalLed(v: Float, th: Thresholds) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${v.roundToInt()}", color = th.color(v), fontSize = 52.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.kom_ctx_speed_unit), color = TextMuted, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 10.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val scaleMax = th.upper + 20f
            val segs = 32
            val gap = size.width * 0.006f
            val sw = (size.width - gap * (segs - 1)) / segs
            for (i in 0 until segs) {
                val segV = scaleMax * (i + 1) / segs
                val lit = segV <= v + scaleMax / segs / 2
                val h = size.height * (0.45f + 0.55f * i / (segs - 1))
                drawRoundRect(if (lit) th.color(segV) else Track, Offset(i * (sw + gap), size.height - h), Size(sw, h),
                    CornerRadius(sw * 0.25f))
            }
            // limit և upper նշաններ
            listOf(th.limit, th.upper).forEach { m ->
                val x = size.width * m / scaleMax
                drawLine(TextPrimary, Offset(x, 0f), Offset(x, size.height * 0.3f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/** Մինիմալ՝ մեծ թիվ շրջանակի մեջ, որը լցվում է ըստ արագության։ */
@Composable
private fun MinimalRing(v: Float, th: Thresholds) {
    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(th.color(v), -90f, 360f * v / VMAX, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        SpeedText(v, big = 40)
    }
}

/** Navigator-ի սահմանափակման ճանապարհային նշանը։ */
@Composable
fun LimitSign(limit: Int, fresh: Boolean = true, modifier: Modifier = Modifier) {
    // Հին (վերջերս չթարմացված) սահմանափակումը՝ կիսաթափանցիկ
    Box(modifier = modifier.size(54.dp).alpha(if (fresh) 1f else 0.55f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White)
            drawCircle(SocRed, radius = size.minDimension / 2 * 0.9f, style = Stroke(size.minDimension * 0.11f))
        }
        Text("$limit", color = Color(0xFF111111), fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Navigator-ի արագության սահմանափակումը։ [fresh]՝ Navigator-ը վերջին 30 վրկ-ում կարդացվել է։
 * Navigator-ը տվյալ ուղարկում է միայն երբ իր պատուհանը փոխվում է (accessibility), և երբ այն
 * մեր էկրանի հետևում է, NavGuidanceHub-ը 30 վրկ հետո սահմանափակումը «մոռանում» էր․ դրա համար
 * վերջին արժեքը պահում ենք [HOLD_MS]՝ մինչև Navigator-ը նորը ցույց տա։
 */
data class NavLimit(val limit: Int, val fresh: Boolean)

object NavLimitHolder {
    const val HOLD_MS = 5 * 60_000L
    @Volatile private var last = 0
    @Volatile private var lastMs = 0L

    fun read(nowMs: Long = System.currentTimeMillis()): NavLimit {
        val live = runCatching { NavGuidanceHub.snapshot(nowMs).speedLimit }.getOrDefault(0)
        if (live > 0) {
            last = live
            lastMs = nowMs
            return NavLimit(live, fresh = true)
        }
        return if (last > 0 && nowMs - lastMs < HOLD_MS) NavLimit(last, fresh = false) else NavLimit(0, fresh = false)
    }
}

@Composable
fun rememberNavSpeedLimit(): NavLimit {
    val state = produceState(initialValue = NavLimitHolder.read()) {
        while (true) {
            value = NavLimitHolder.read()
            delay(1_000L)
        }
    }
    return state.value
}
