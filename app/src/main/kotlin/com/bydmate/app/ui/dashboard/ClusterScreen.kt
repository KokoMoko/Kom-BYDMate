package com.bydmate.app.ui.dashboard

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.navdata.NavGuidanceHub
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.NavyDeep
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Kom-BYDMate «Cluster»՝ սարքերի ոճերը (սարքի վրա սեղմելով փոխվում է հերթականությամբ)։ */
enum class GaugeStyle { NEEDLE, ARC, DIGITAL, BAR }

object ClusterPrefs {
    private const val PREFS = "kom_cluster"
    private const val KEY_POWER_STYLE = "power_style"
    private const val KEY_SPEED_STYLE = "speed_style"
    private const val KEY_TOP = "top_pct"
    const val DEFAULT_TOP = 65

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun style(ctx: Context, key: String) =
        p(ctx).getString(key, null)?.let { runCatching { GaugeStyle.valueOf(it) }.getOrNull() } ?: GaugeStyle.NEEDLE

    fun powerStyle(ctx: Context) = style(ctx, KEY_POWER_STYLE)
    fun speedStyle(ctx: Context) = style(ctx, KEY_SPEED_STYLE)
    fun setPowerStyle(ctx: Context, s: GaugeStyle) = p(ctx).edit().putString(KEY_POWER_STYLE, s.name).apply()
    fun setSpeedStyle(ctx: Context, s: GaugeStyle) = p(ctx).edit().putString(KEY_SPEED_STYLE, s.name).apply()
    fun topPct(ctx: Context) = p(ctx).getInt(KEY_TOP, DEFAULT_TOP)
    fun setTopPct(ctx: Context, v: Int) = p(ctx).edit().putInt(KEY_TOP, v).apply()
}

private val Track = Color(0xFF1B2B45)
private val RegenBlue = Color(0xFF4AA3FF)
private val Amber = Color(0xFFFFB020)
private val Needle = Color(0xFFFF5A3C)
private val Road = Color(0xFF0F1B2D)
private val RoadEdge = Color(0xFF3A5580)

private const val P_MIN = -50f
private const val P_MAX = 150f
private const val S_MAX = 160f

/**
 * Kom-BYDMate «Cluster» էջ․ վերևում (օգտատիրոջ ընտրած 50–75%) ձախից հզորության սարքը (kW),
 * աջից արագաչափը, մեջտեղում՝ D | մարտկոց·պաշար·ծախս | ջերմաստիճաններ և ճանապարհ մեքենայով,
 * որը «գնում է» միայն շարժման ժամանակ։ Ներքևում՝ Phone, Music, Weather widget-ները։
 */
@Composable
fun ClusterScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestGrant: ((Boolean) -> Unit) -> Unit = { cb -> viewModel.grantWidgetBind(cb) }
    var topPct by remember { mutableIntStateOf(ClusterPrefs.topPct(context)) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rememberDashboardTitle(), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(CardSurface).clickable { showSettings = true },
                contentAlignment = Alignment.Center,
            ) { Text("⋮", color = TextSecondary, fontSize = 18.sp) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val topH = maxHeight * (topPct / 100f)
            Column(Modifier.fillMaxSize()) {
                ClusterTop(state, Modifier.fillMaxWidth().height(topH))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_PHONE), stringResource(R.string.kom_widget_hint_phone),
                        requestGrant, Modifier.weight(1f).fillMaxHeight())
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_LEFT), stringResource(R.string.kom_widget_hint_music),
                        requestGrant, Modifier.weight(1f).fillMaxHeight())
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_RIGHT), stringResource(R.string.kom_widget_hint_weather),
                        requestGrant, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.kom_cluster_settings), color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.kom_cluster_top_height, topPct), color = TextPrimary, fontSize = 16.sp)
                    Slider(
                        value = topPct.toFloat(),
                        onValueChange = { topPct = (it / 5).roundToInt() * 5 },
                        onValueChangeFinished = { ClusterPrefs.setTopPct(context, topPct) },
                        valueRange = 50f..75f,
                    )
                    Text(stringResource(R.string.kom_cluster_style_hint), color = TextMuted, fontSize = 13.sp)
                    // Ժամանակավոր՝ DiLink-ի 3D մեքենայի մաքուր screenshot (Cluster-ի մեքենայի պատկերի համար)
                    var captureMsg by remember { mutableStateOf<String?>(null) }
                    TextButton(onClick = {
                        captureMsg = context.getString(R.string.kom_capture_wait)
                        KomScreenCapture.captureLater(context, 10_000L) { path ->
                            captureMsg = path?.let { context.getString(R.string.kom_capture_saved, it) }
                                ?: context.getString(R.string.kom_capture_failed)
                            android.widget.Toast.makeText(context, captureMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text(stringResource(R.string.kom_capture_button)) }
                    captureMsg?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
                    // Ախտորոշում՝ մեքենայի տեսախցիկի նշանների ազդանշանները (TSR)
                    val tsrStatus by CarSignReader.status.collectAsStateWithLifecycle()
                    val tsrRaw by CarSignReader.raw.collectAsStateWithLifecycle()
                    Text(
                        "TSR: $tsrStatus\n" + tsrRaw.entries.joinToString("\n") {
                            it.key.removePrefix("Instrument.INSTRUMENT_") + " = " + (it.value ?: "—")
                        },
                        color = TextMuted, fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ClusterPrefs.setTopPct(context, topPct); showSettings = false }) {
                    Text(stringResource(R.string.kom_done))
                }
            },
            containerColor = CardSurface,
        )
    }
}

@Composable
private fun ClusterTop(state: DashboardUiState, modifier: Modifier) {
    val context = LocalContext.current
    var powerStyle by remember { mutableStateOf(ClusterPrefs.powerStyle(context)) }
    var speedStyle by remember { mutableStateOf(ClusterPrefs.speedStyle(context)) }

    // Արագության գույնը՝ նույն կանոնով, ինչ Classic-ի արագաչափում (Navigator-ի սահմանափակում կամ Max speed + շեղում)
    val nav = rememberNavSpeedLimit()
    val navLimit = nav.limit
    val limit = (if (navLimit > 0) navLimit else SpeedoPrefs.maxSpeed(context)).toFloat()
    val upper = limit + SpeedoPrefs.tolerance(context)

    val charging = state.isCharging
    val rawPower = (state.powerKw ?: 0.0).toFloat()
    // Լիցքավորման ժամանակ ձախ սարքը ցույց է տալիս լիցքի հզորությունը (դրական, կանաչ)
    val powerTarget = if (charging) -rawPower else rawPower
    val power by animateFloatAsState(powerTarget.coerceIn(P_MIN, P_MAX), tween(500, easing = FastOutSlowInEasing), label = "power")
    val speed by animateFloatAsState((state.speed ?: 0).toFloat().coerceIn(0f, S_MAX), tween(600, easing = FastOutSlowInEasing), label = "speed")

    val powerColor = when {
        charging -> AccentGreen
        power < -0.5f -> RegenBlue
        power > P_MAX * 0.75f -> SocRed
        power > P_MAX * 0.45f -> Amber
        else -> AccentGreen
    }
    val speedColor = when {
        speed <= limit -> AccentGreen
        speed <= upper -> lerp(AccentGreen, SocRed, ((speed - limit) / (upper - limit).coerceAtLeast(1f)).coerceIn(0f, 1f))
        else -> Color(0xFF991B1B)
    }
    val powerLabel = stringResource(
        when {
            charging -> R.string.kom_cluster_charging
            power < -0.5f -> R.string.kom_cluster_regen
            else -> R.string.kom_cluster_power
        }
    )

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier.clip(shape).border(1.dp, CardBorder, shape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Gauge(
            value = power, min = if (charging) 0f else P_MIN, max = P_MAX, step = 25f, unit = "kW", label = powerLabel,
            color = powerColor, style = powerStyle,
            onTap = { powerStyle = next(powerStyle); ClusterPrefs.setPowerStyle(context, powerStyle) },
            modifier = Modifier.weight(0.3f).fillMaxHeight(),
        )
        Column(Modifier.weight(0.4f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            ClusterHeader(state)
            Spacer(Modifier.height(6.dp))
            RoadScene(speedKmh = speed, braking = power < -1f || (speed < 0.5f && state.gear != 1),
                modifier = Modifier.fillMaxWidth().weight(1f))
        }
        Box(Modifier.weight(0.3f).fillMaxHeight()) {
            Gauge(
                value = speed, min = 0f, max = S_MAX, step = 20f, unit = "km/h", label = stringResource(R.string.kom_speedo_speed_label),
                color = speedColor, style = speedStyle,
                onTap = { speedStyle = next(speedStyle); ClusterPrefs.setSpeedStyle(context, speedStyle) },
                modifier = Modifier.fillMaxSize(),
            )
            // Navigator-ի սահմանափակման նշանը
            if (navLimit > 0) LimitSign(navLimit, fresh = nav.fresh, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun next(s: GaugeStyle) = GaugeStyle.values()[(s.ordinal + 1) % GaugeStyle.values().size]

/** Վերևի տողը՝ D | մարտկոց, պաշար / ծախս | ջերմաստիճաններ (բաժանարար գծերով)։ */
@Composable
private fun ClusterHeader(state: DashboardUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        val (g, gc) = when (state.gear) {
            1 -> "P" to TextSecondary
            2 -> "R" to Amber
            3 -> "N" to TextSecondary
            4 -> "D" to AccentGreen
            else -> "–" to TextMuted
        }
        Text(g, color = gc, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        HeaderDivider()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryBar(state.soc ?: 0, state.isCharging)
                Spacer(Modifier.width(12.dp))
                Text(state.estimatedRangeKm?.let { "~${"%.0f".format(it)} km" } ?: "— km", color = TextPrimary, fontSize = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(state.consumption?.let { "%.1f kWh/100".format(it) } ?: "— kWh/100", color = TextSecondary, fontSize = 15.sp)
        }
        HeaderDivider()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TempRow(state.insideTemp, Icons.Outlined.DirectionsCar)
            TempRow(state.exteriorTemp, Icons.Outlined.WbSunny)
        }
    }
}

@Composable
private fun HeaderDivider() {
    Spacer(Modifier.width(14.dp))
    Box(Modifier.width(1.dp).height(56.dp).background(CardBorder))
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun TempRow(temp: Int?, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(temp?.let { "$it°" } ?: "—", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Մարտկոցի պատկեր՝ լցված մասը SOC-ն է, գույնը կարմիր → դեղին → կանաչ, լիցքավորվելիս՝ «շնչում» է։ */
@Composable
private fun BatteryBar(soc: Int, charging: Boolean) {
    val f by animateFloatAsState((soc / 100f).coerceIn(0f, 1f), tween(800), label = "soc")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (charging) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.width(132.dp).height(34.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val tip = 6.dp.toPx()
            val w = size.width - tip
            val h = size.height
            val r = CornerRadius(h * 0.22f)
            drawRoundRect(Road, size = Size(w, h), cornerRadius = r)
            drawRoundRect(Color(0xFF5C7699), size = Size(w, h), cornerRadius = r, style = Stroke(2.dp.toPx()))
            drawRoundRect(Color(0xFF5C7699), topLeft = Offset(w + 1.dp.toPx(), h * 0.3f), size = Size(tip - 1.dp.toPx(), h * 0.4f),
                cornerRadius = CornerRadius(2.dp.toPx()))
            val inset = 3.dp.toPx()
            clipRect(left = inset, top = inset, right = inset + (w - 2 * inset) * f, bottom = h - inset) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFFE5322D), Color(0xFFF5C518), AccentGreen), startX = 0f, endX = w),
                    topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h - 2 * inset),
                    cornerRadius = CornerRadius(h * 0.16f), alpha = pulse,
                )
                drawRect(Color.White.copy(alpha = 0.2f), topLeft = Offset(inset, inset), size = Size(w, (h - 2 * inset) * 0.3f))
            }
        }
        Text(
            "$soc%", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(Color(0xCC0B1422), Offset(0f, 1f), 4f)),
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}

/** Սարք՝ ընտրված ոճով։ Սեղմելիս փոխվում է հաջորդ ոճին։ */
@Composable
private fun Gauge(
    value: Float, min: Float, max: Float, step: Float, unit: String, label: String,
    color: Color, style: GaugeStyle, onTap: () -> Unit, modifier: Modifier,
) {
    BoxWithConstraints(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val side = min(maxWidth, maxHeight)
        when (style) {
            GaugeStyle.DIGITAL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = TextMuted, fontSize = (side.value * 0.07f).sp)
                Text(value.roundToInt().toString(), color = color, fontSize = (side.value * 0.3f).sp, fontWeight = FontWeight.Bold)
                Text(unit, color = TextSecondary, fontSize = (side.value * 0.08f).sp)
            }
            GaugeStyle.BAR -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(side)) {
                Text(value.roundToInt().toString(), color = TextPrimary, fontSize = (side.value * 0.24f).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth(0.9f).height(side * 0.09f)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Track, cornerRadius = r)
                    val zero = size.width * ((0f.coerceIn(min, max) - min) / (max - min))
                    val end = size.width * ((value.coerceIn(min, max) - min) / (max - min))
                    drawRoundRect(color, topLeft = Offset(kotlin.math.min(zero, end), 0f),
                        size = Size(kotlin.math.abs(end - zero), size.height), cornerRadius = r)
                }
                Spacer(Modifier.height(8.dp))
                Text("$unit · $label", color = TextSecondary, fontSize = (side.value * 0.07f).sp)
            }
            else -> Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawDial(value, min, max, step, color, style == GaugeStyle.NEEDLE) }
                // Թիվը՝ ճիշտ կենտրոնում (սլաքի առանցքի շրջանում), միավորը՝ առանձին, կենտրոնից ներքև
                val needle = style == GaugeStyle.NEEDLE
                Text(
                    value.roundToInt().toString(), color = TextPrimary, fontWeight = FontWeight.Bold,
                    fontSize = (side.value * if (needle) 0.11f else 0.22f).sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    unit, color = TextSecondary, fontSize = (side.value * if (needle) 0.065f else 0.07f).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = side * if (needle) 0.21f else 0.17f),
                )
            }
        }
    }
}

private const val START = 135f
private const val SWEEP = 270f

private fun DrawScope.drawDial(value: Float, min: Float, max: Float, step: Float, color: Color, needle: Boolean) {
    val stroke = size.minDimension * 0.05f
    val radius = size.minDimension / 2 - stroke
    val c = center
    val tl = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    fun ang(v: Float) = START + SWEEP * ((v.coerceIn(min, max) - min) / (max - min))

    drawArc(Track, START, SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val zeroA = ang(0f)
    if (min < 0f) drawArc(RegenBlue.copy(alpha = 0.3f), START, zeroA - START, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val a = ang(value)
    val from = kotlin.math.min(zeroA, a)
    val sweep = kotlin.math.abs(a - zeroA).coerceAtLeast(0.5f)
    drawArc(color, from, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

    if (!needle) return
    val paint = android.graphics.Paint().apply {
        this.color = TextMuted.toArgb(); textSize = size.minDimension * 0.055f
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
    }
    var v = min
    while (v <= max + 0.01f) {
        val rad = Math.toRadians(ang(v).toDouble())
        val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        drawLine(TextMuted, Offset(c.x + cs * radius * 0.82f, c.y + sn * radius * 0.82f),
            Offset(c.x + cs * radius * 0.92f, c.y + sn * radius * 0.92f), strokeWidth = 3f)
        drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(),
            c.x + cs * radius * 0.68f, c.y + sn * radius * 0.68f + paint.textSize / 3, paint)
        v += step
    }
    val rad = Math.toRadians(a.toDouble())
    drawLine(Needle, c, Offset(c.x + cos(rad).toFloat() * radius * 0.86f, c.y + sin(rad).toFloat() * radius * 0.86f),
        strokeWidth = size.minDimension * 0.018f, cap = StrokeCap.Round)
    drawCircle(CardSurface, radius * 0.26f, c)
    drawCircle(CardBorder, radius * 0.26f, c, style = Stroke(2f))
}

/**
 * Ճանապարհ հեռանկարով և մեքենա (հետևից)։ Գծերը շարժվում են արագությանը համեմատ․ կանգնած
 * ժամանակ անիմացիա չկա (և frame-եր չենք ծախսում)։ Արգելակելիս/կանգնած՝ կարմիր լույսերը վառ են։
 */
@Composable
private fun RoadScene(speedKmh: Float, braking: Boolean, modifier: Modifier) {
    var offset by remember { mutableFloatStateOf(0f) }
    val speedNow by rememberUpdatedState(speedKmh)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            if (speedNow < 0.5f) { last = 0L; delay(250); continue }
            withFrameNanos { t ->
                if (last != 0L) offset = (offset + speedNow / 45f * ((t - last) / 1e9f)) % 1f
                last = t
            }
        }
    }
    Canvas(modifier) {
        val cx = size.width / 2
        val hy = 0f
        val by = size.height
        val hw = size.width * 0.48f
        val nw = hw * 0.12f
        val road = Path().apply { moveTo(cx - nw, hy); lineTo(cx + nw, hy); lineTo(cx + hw, by); lineTo(cx - hw, by); close() }
        drawPath(road, Road)
        drawLine(RoadEdge, Offset(cx - nw, hy), Offset(cx - hw, by), strokeWidth = 3f)
        drawLine(RoadEdge, Offset(cx + nw, hy), Offset(cx + hw, by), strokeWidth = 3f)
        val n = 10
        for (lane in listOf(-1f / 3f, 1f / 3f)) {
            for (i in 0 until n) {
                val z0 = ((i + offset) / n) % 1f
                val z1 = z0 + 0.045f
                if (z1 > 1f) continue
                val y0 = hy + (by - hy) * z0 * z0; val y1 = hy + (by - hy) * z1 * z1
                val w0 = nw + (hw - nw) * z0 * z0; val w1 = nw + (hw - nw) * z1 * z1
                val t0 = 1.5f + z0 * 2f; val t1 = 1.5f + z1 * 3f
                val dash = Path().apply {
                    moveTo(cx + lane * w0 - t0, y0); lineTo(cx + lane * w0 + t0, y0)
                    lineTo(cx + lane * w1 + t1, y1); lineTo(cx + lane * w1 - t1, y1); close()
                }
                drawPath(dash, Color(0xFFC8D7EB).copy(alpha = 0.25f + z0 * 0.6f))
            }
        }
        drawCar(cx, by, hw * 0.46f, braking)
    }
}

/**
 * BYD Sealion 06-ը հետևից․ բարձր կրոսովեր՝ կլորացված ուսերով, տանիքի սփոյլեր, նեղ հետևի ապակի,
 * ամբողջ լայնքով լույսի գիծ՝ մեջտեղում «BYD», ներքևում մուգ բամպեր։ Արգելակելիս լույսերը պայծառ են։
 */
private fun DrawScope.drawCar(cx: Float, bottom: Float, cw: Float, braking: Boolean) {
    val ch = cw * 0.72f
    val top = bottom - ch
    val l = cx - cw / 2
    fun x(f: Float) = l + cw * f
    fun y(f: Float) = top + ch * f
    val bodyLight = Color(0xFFE3E9F2)
    val bodyShade = Color(0xFFB9C4D4)
    val glass = Color(0xFF1B2C47)
    val dark = Color(0xFF151F2E)
    val lampOn = Color(0xFFFF2D2D)
    val lampOff = Color(0xFF7A1C1C)
    val lamp = if (braking) lampOn else lampOff

    // Ստվեր ճանապարհին
    drawOval(Color.Black.copy(alpha = 0.4f), Offset(x(-0.06f), y(0.9f)), Size(cw * 1.12f, ch * 0.16f))
    // Անիվներ (երևում են թափքի տակից)
    drawRoundRect(dark, Offset(x(0.05f), y(0.72f)), Size(cw * 0.17f, ch * 0.26f), CornerRadius(ch * 0.05f))
    drawRoundRect(dark, Offset(x(0.78f), y(0.72f)), Size(cw * 0.17f, ch * 0.26f), CornerRadius(ch * 0.05f))

    // Թափք (ուսերը կլորացված, ներքևում մի փոքր նեղանում է)
    val body = Path().apply {
        moveTo(x(0.02f), y(0.46f))
        cubicTo(x(0.0f), y(0.40f), x(0.03f), y(0.34f), x(0.10f), y(0.33f))
        lineTo(x(0.90f), y(0.33f))
        cubicTo(x(0.97f), y(0.34f), x(1.0f), y(0.40f), x(0.98f), y(0.46f))
        lineTo(x(0.97f), y(0.80f))
        quadraticBezierTo(x(0.96f), y(0.86f), x(0.90f), y(0.86f))
        lineTo(x(0.10f), y(0.86f))
        quadraticBezierTo(x(0.04f), y(0.86f), x(0.03f), y(0.80f))
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(bodyLight, bodyShade), startY = y(0.33f), endY = y(0.86f)))

    // Սրահ՝ սյուներ և տանիք (վերևում նեղ)
    val cabin = Path().apply {
        moveTo(x(0.12f), y(0.36f))
        cubicTo(x(0.15f), y(0.20f), x(0.19f), y(0.08f), x(0.27f), y(0.06f))
        lineTo(x(0.73f), y(0.06f))
        cubicTo(x(0.81f), y(0.08f), x(0.85f), y(0.20f), x(0.88f), y(0.36f))
        close()
    }
    drawPath(cabin, bodyShade)
    // Տանիքի սփոյլեր
    drawRoundRect(dark, Offset(x(0.24f), y(0.03f)), Size(cw * 0.52f, ch * 0.05f), CornerRadius(ch * 0.025f))
    // Հետևի ապակի
    val window = Path().apply {
        moveTo(x(0.19f), y(0.33f))
        lineTo(x(0.26f), y(0.11f))
        lineTo(x(0.74f), y(0.11f))
        lineTo(x(0.81f), y(0.33f))
        close()
    }
    drawPath(window, glass)
    drawLine(Color.White.copy(alpha = 0.12f), Offset(x(0.30f), y(0.14f)), Offset(x(0.24f), y(0.31f)), strokeWidth = cw * 0.012f)

    // Լույսի գիծ ամբողջ լայնքով (ծայրերում հաստ, կողքերին թեքվող)
    drawRoundRect(lamp, Offset(x(0.04f), y(0.435f)), Size(cw * 0.92f, ch * 0.028f), CornerRadius(ch * 0.014f))
    val leftLamp = Path().apply {
        moveTo(x(0.02f), y(0.41f)); lineTo(x(0.21f), y(0.41f)); lineTo(x(0.19f), y(0.50f)); lineTo(x(0.04f), y(0.52f)); close()
    }
    val rightLamp = Path().apply {
        moveTo(x(0.98f), y(0.41f)); lineTo(x(0.79f), y(0.41f)); lineTo(x(0.81f), y(0.50f)); lineTo(x(0.96f), y(0.52f)); close()
    }
    drawPath(leftLamp, lamp)
    drawPath(rightLamp, lamp)
    if (braking) {
        drawRoundRect(lampOn.copy(alpha = 0.25f), Offset(x(0.0f), y(0.37f)), Size(cw, ch * 0.2f), CornerRadius(ch * 0.08f))
    }
    // «BYD» լույսի գծի տակ
    val paint = android.graphics.Paint().apply {
        color = Color(0xFF5B6B82).toArgb(); textSize = ch * 0.085f; isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; letterSpacing = 0.25f
    }
    drawContext.canvas.nativeCanvas.drawText("BYD", cx, y(0.60f), paint)

    // Բամպեր՝ մուգ դիֆուզոր, համարանիշ, ռեֆլեկտորներ
    drawRoundRect(dark, Offset(x(0.07f), y(0.74f)), Size(cw * 0.86f, ch * 0.09f), CornerRadius(ch * 0.04f))
    drawRoundRect(Color(0xFFCBD3DF), Offset(cx - cw * 0.13f, y(0.64f)), Size(cw * 0.26f, ch * 0.08f), CornerRadius(ch * 0.015f))
    drawRoundRect(lamp.copy(alpha = 0.8f), Offset(x(0.09f), y(0.77f)), Size(cw * 0.08f, ch * 0.025f), CornerRadius(ch * 0.01f))
    drawRoundRect(lamp.copy(alpha = 0.8f), Offset(x(0.83f), y(0.77f)), Size(cw * 0.08f, ch * 0.025f), CornerRadius(ch * 0.01f))
}
