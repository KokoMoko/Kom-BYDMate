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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
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
    /** true՝ հզորությունը ձախից, արագաչափը՝ աջից։ */
    fun swapGauges(ctx: Context) = p(ctx).getBoolean("swap_gauges", false)
    fun setSwapGauges(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("swap_gauges", v).apply()

    /** Ոճը, 3D-ն, կամարի թվերը և ջերմաստիճանների դասավորությունը (տես KomClusterThemes.kt)։ */
    fun look(ctx: Context): ClusterLook {
        val s = p(ctx)
        fun <E : Enum<E>> e(key: String, def: E, all: Array<E>) = s.getString(key, null)?.let { n -> all.firstOrNull { it.name == n } } ?: def
        return ClusterLook(
            theme = e("theme", ClusterTheme.CLASSIC, ClusterTheme.values()),
            d3 = s.getBoolean("gauge_3d", true),
            archLabels = e("arch_labels", ArchLabels.ENDS, ArchLabels.values()),
            infoRows = e("info_rows", InfoRows.TWO, InfoRows.values()),
        )
    }
    /** «Լիճ» վահանակի վերին մասի թափանցիկությունը՝ 0…90 %։ */
    fun lagoonClear(ctx: Context) = p(ctx).getInt("lagoon_clear", 50)
    fun setLagoonClear(ctx: Context, v: Int) = p(ctx).edit().putInt("lagoon_clear", v).apply()
    fun setLook(ctx: Context, l: ClusterLook) = p(ctx).edit()
        .putString("theme", l.theme.name).putBoolean("gauge_3d", l.d3)
        .putString("arch_labels", l.archLabels.name).putString("info_rows", l.infoRows.name).apply()
}

private val Track = Color(0xFF1B2B45)
private val RegenBlue = Color(0xFF4AA3FF)
/** Սովորական (նորմալ) վիճակի գույնը՝ նուրբ կապույտ․ ռեկուպերացիան՝ կանաչ (AccentGreen)։ */
private val SoftBlue = Color(0xFF6FB6FF)
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
    var swapGauges by remember { mutableStateOf(ClusterPrefs.swapGauges(context)) }
    var look by remember { mutableStateOf(ClusterPrefs.look(context)) }
    var lagoonClear by remember { mutableIntStateOf(ClusterPrefs.lagoonClear(context)) }

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
                ClusterTop(state, swapGauges, look, lagoonClear, Modifier.fillMaxWidth().height(topH))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_PHONE), stringResource(R.string.kom_widget_hint_generic),
                        requestGrant, Modifier.weight(1f).fillMaxHeight(), suggestion = stringResource(R.string.kom_widget_suggest_phone))
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_LEFT), stringResource(R.string.kom_widget_hint_generic),
                        requestGrant, Modifier.weight(1f).fillMaxHeight(), suggestion = stringResource(R.string.kom_widget_suggest_music))
                    DashboardWidgetSlot(DashboardWidgets.scoped("cluster", DashboardWidgets.SLOT_RIGHT), stringResource(R.string.kom_widget_hint_generic),
                        requestGrant, Modifier.weight(1f).fillMaxHeight(), suggestion = stringResource(R.string.kom_widget_suggest_weather))
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.kom_cluster_settings), color = TextPrimary) },
            text = {
                Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.kom_cluster_top_height, topPct), color = TextPrimary, fontSize = 16.sp)
                    Slider(
                        value = topPct.toFloat(),
                        onValueChange = { topPct = (it / 5).roundToInt() * 5 },
                        onValueChangeFinished = { ClusterPrefs.setTopPct(context, topPct) },
                        valueRange = 50f..75f,
                    )
                    // Ոճ, 3D, կամարի թվեր, ջերմաստիճանների դասավորություն
                    fun upd(l: ClusterLook) { look = l; ClusterPrefs.setLook(context, l) }
                    Text(stringResource(R.string.kom_cluster_theme), color = TextPrimary, fontSize = 15.sp)
                    LookChips(
                        listOf(ClusterTheme.CLASSIC to R.string.kom_cluster_theme_classic, ClusterTheme.LAGOON to R.string.kom_cluster_theme_lagoon,
                            ClusterTheme.TIDE to R.string.kom_cluster_theme_tide, ClusterTheme.ARCH to R.string.kom_cluster_theme_arch,
                            ClusterTheme.ROAD to R.string.kom_cluster_theme_road),
                        look.theme,
                    ) { upd(look.copy(theme = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.kom_cluster_3d), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = look.d3, onCheckedChange = { upd(look.copy(d3 = it)) })
                    }
                    if (look.theme == ClusterTheme.LAGOON || look.theme == ClusterTheme.TIDE || look.theme == ClusterTheme.ROAD) {
                        Text(stringResource(R.string.kom_cluster_numbers), color = TextPrimary, fontSize = 15.sp)
                        LookChips(listOf(ArchLabels.ENDS to R.string.kom_cluster_numbers_sides, ArchLabels.CYCLE to R.string.kom_cluster_numbers_road),
                            look.archLabels) { upd(look.copy(archLabels = it)) }
                    }
                    if (look.theme == ClusterTheme.LAGOON) {
                        Text(stringResource(R.string.kom_cluster_lagoon_clear, lagoonClear), color = TextPrimary, fontSize = 15.sp)
                        Slider(
                            value = lagoonClear.toFloat(), valueRange = 0f..90f,
                            onValueChange = { lagoonClear = (it / 5).roundToInt() * 5 },
                            onValueChangeFinished = { ClusterPrefs.setLagoonClear(context, lagoonClear) },
                        )
                    }
                    if (look.theme == ClusterTheme.ARCH) {
                        Text(stringResource(R.string.kom_cluster_arch_numbers), color = TextPrimary, fontSize = 15.sp)
                        LookChips(listOf(ArchLabels.ENDS to R.string.kom_cluster_arch_ends, ArchLabels.CYCLE to R.string.kom_cluster_arch_cycle),
                            look.archLabels) { upd(look.copy(archLabels = it)) }
                        Text(stringResource(R.string.kom_cluster_info_rows), color = TextPrimary, fontSize = 15.sp)
                        LookChips(listOf(InfoRows.TWO to R.string.kom_cluster_info_two, InfoRows.ONE to R.string.kom_cluster_info_one),
                            look.infoRows) { upd(look.copy(infoRows = it)) }
                    }
                    Text(stringResource(R.string.kom_cluster_style_hint), color = TextMuted, fontSize = 13.sp)
                    // Արագաչափը և հզորությունը՝ տեղերով փոխել
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.kom_cluster_swap), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = swapGauges, onCheckedChange = {
                            swapGauges = it; ClusterPrefs.setSwapGauges(context, it)
                        })
                    }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> LookChips(options: List<Pair<T, Int>>, selected: T, onPick: (T) -> Unit) {
    // Փոքր տառ և նեղ միջակայք՝ որ ոճերի հինգ կոճակը մեկ տողում տեղավորվեն
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (v, label) ->
            androidx.compose.material3.FilterChip(selected = v == selected, onClick = { onPick(v) },
                label = { Text(stringResource(label), fontSize = 12.sp, maxLines = 1) })
        }
    }
}

@Composable
private fun ClusterTop(state: DashboardUiState, swap: Boolean, look: ClusterLook, lagoonClear: Int, modifier: Modifier) {
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

    // Արգելակում ընթացքի ժամանակ՝ արագությունը նկատելիորեն նվազում է (≥ 3 կմ/ժ վայրկյանում)
    // կամ ուժեղ ռեկուպերացիա է․ լույսերը մնում են վառ ևս 1.2 վրկ, որ չթարթեն
    var lastSpeed by remember { mutableStateOf<Pair<Float, Long>?>(null) }
    var brakeUntil by remember { mutableStateOf(0L) }
    val rawSpeed = (state.speed ?: 0).toFloat()
    LaunchedEffect(rawSpeed) {
        val now = System.currentTimeMillis()
        val prev = lastSpeed
        if (prev != null) {
            val dt = (now - prev.second) / 1000f
            if (dt in 0.2f..5f && (prev.first - rawSpeed) / dt >= 3f && rawSpeed > 0f) brakeUntil = now + 1_200L
        }
        lastSpeed = rawSpeed to now
    }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(brakeUntil) {
        while (System.currentTimeMillis() < brakeUntil) { nowMs = System.currentTimeMillis(); delay(200) }
        nowMs = System.currentTimeMillis()
    }
    val decelBraking = nowMs < brakeUntil

    val powerColor = when {
        charging -> AccentGreen
        power < -0.5f -> AccentGreen
        power > P_MAX * 0.75f -> SocRed
        power > P_MAX * 0.45f -> Amber
        else -> SoftBlue
    }
    val speedColor = when {
        speed <= limit -> SoftBlue
        speed <= upper -> lerp(SoftBlue, SocRed, ((speed - limit) / (upper - limit).coerceAtLeast(1f)).coerceIn(0f, 1f))
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
    val theme = look.theme
    val waves = theme == ClusterTheme.LAGOON || theme == ClusterTheme.TIDE
    val waveT = rememberWavePhase(waves, speed)
    val soc = state.soc ?: 0
    val rangeText = state.estimatedRangeKm?.let { "~${"%.0f".format(it)} km" } ?: "~— km"
    val (gearText, gearColor) = when (state.gear) {
        1 -> "P" to TextSecondary
        2 -> "R" to Amber
        3 -> "N" to TextSecondary
        4 -> "D" to AccentGreen
        else -> "–" to TextMuted
    }
    val inside = state.insideTemp?.let { "$it°" } ?: "—"
    val outside = state.exteriorTemp?.let { "$it°" } ?: "—"
    // 3D սլաքով սարքում «Միջ.» տեքստը շրջանակի ներսում է (տակը առանձին տող չկա)
    val speedCaptionInside = look.d3 && speedStyle == GaugeStyle.NEEDLE
    val powerCaptionInside = look.d3 && powerStyle == GaugeStyle.NEEDLE
    val captionH = if (speedCaptionInside) 0.dp else 22.dp
    BoxWithConstraints(modifier.clip(shape).border(1.dp, CardBorder, shape)) {
        val innerW = maxWidth - 24.dp
        val innerH = maxHeight - 16.dp
        val gaugeSide = min(innerW * 0.3f, innerH - captionH)
        if (theme == ClusterTheme.LAGOON) {
            // Լիճ՝ կենտրոնում լրիվ, դեպի սարքերի կենտրոնները գրադիենտով մարում է (այնտեղ՝ 100% թափանցիկ),
            // կենտրոնական մասի եզրերին՝ նուրբ ուղղահայաց գծեր (սարքերի և լճի շրջանակների փոխարեն)
            Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val padX = 12.dp.toPx(); val padY = 8.dp.toPx(); val iw = size.width - 2 * padX
                val xL = padX + iw * 0.15f; val x0 = padX + iw * 0.3f; val x1 = padX + iw * 0.7f; val xR = padX + iw * 0.85f
                val topA = 1f - lagoonClear / 100f
                drawRect(Brush.verticalGradient(0f to Color(0xFF0B2A5A).copy(alpha = topA), 0.5f to Color(0xFF0A3D7A),
                    1f to Color(0xFF06214A)), topLeft = Offset(0f, padY), size = Size(size.width, size.height - 2 * padY))
                drawRect(Brush.radialGradient(listOf(Color(0x5978C8FF), Color.Transparent), center = Offset(size.width / 2, size.height * 0.56f),
                    radius = (x1 - x0) * 0.6f))
                val top = padY + 64.dp.toPx(); val bot = size.height - padY - 30.dp.toPx()
                val level = bot - (bot - top) * soc / 100f
                drawWave(0f, size.width, level, waveT, Color(0xFF35E0FF), Color(0xFF0A5FB4), 14.dp.toPx())
                val w = size.width
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, xL / w to Color.Transparent, x0 / w to Color.Black,
                    x1 / w to Color.Black, xR / w to Color.Transparent, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                // գծերը՝ ջերմաստիճանների բաժանարարների գույնով, վերին թափանցիկությունը դրանց վրա էլ է ազդում
                val sepC = TextSecondary.copy(alpha = 0.5f)
                val lineBrush = Brush.verticalGradient(0f to sepC.copy(alpha = 0.5f * topA), 0.5f to sepC, 1f to sepC,
                    startY = padY, endY = size.height - padY)
                drawLine(lineBrush, Offset(x0, padY), Offset(x0, size.height - padY), strokeWidth = 1.5.dp.toPx())
                drawLine(lineBrush, Offset(x1, padY), Offset(x1, size.height - padY), strokeWidth = 1.5.dp.toPx())
            }
        }
        if (theme == ClusterTheme.TIDE) {
            // Մակընթացություն՝ ֆոնը և ալիքը ամբողջ լայնքով (սարքերի տակով)
            val (c1, c2) = tideColors(soc)
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A2248), Color(0xFF061633))))
                drawRect(Brush.radialGradient(listOf(Color(0x405AAAFF), Color.Transparent), center = Offset(size.width / 2, size.height * 0.6f), radius = size.width * 0.5f))
                val level = size.height - 24.dp.toPx() - (size.height * 0.62f) * soc / 100f
                drawWave(0f, size.width, level, waveT, c1, c2, 18.dp.toPx() * size.height / 480.dp.toPx())
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val extras by KomClusterExtras.extras.collectAsStateWithLifecycle()
        val altText = extras.altitudeM?.let { "$it m" } ?: "— m"
        // Արագաչափը՝ թույլատրելի արագությունը սանդղակի վրա կարմիր շրջանակով, տակը՝ միջին արագությունը
        val speedCaption = stringResource(R.string.kom_cluster_avg_speed, extras.avgSpeedKmh?.toString() ?: "—")
        val powerCaption = stringResource(R.string.kom_cluster_avg_consumption, state.consumption?.let { "%.1f".format(it) } ?: "—")
        val gaugeFrame = Modifier
        val speedGauge: @Composable (Modifier) -> Unit = { m ->
            GaugeWithCaption(if (speedCaptionInside) null else speedCaption, m.then(gaugeFrame)) {
                Gauge(
                    value = speed, min = 0f, max = S_MAX, step = 20f, unit = "km/h", label = stringResource(R.string.kom_speedo_speed_label),
                    color = speedColor, style = speedStyle,
                    onTap = { speedStyle = next(speedStyle); ClusterPrefs.setSpeedStyle(context, speedStyle) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    limit = if (navLimit > 0) navLimit.toFloat() else 0f,
                    overLimit = navLimit > 0 && speed > upper,
                    d3 = look.d3, glass = theme == ClusterTheme.TIDE, caption = speedCaption,
                )
            }
        }
        // Հզորությունը, տակը՝ միջին ծախսը
        val powerGauge: @Composable (Modifier) -> Unit = { m ->
            GaugeWithCaption(if (powerCaptionInside) null else powerCaption, m.then(gaugeFrame)) {
                Gauge(
                    value = power, min = if (charging) 0f else P_MIN, max = P_MAX, step = 25f, unit = "kW", label = powerLabel,
                    color = powerColor, style = powerStyle,
                    onTap = { powerStyle = next(powerStyle); ClusterPrefs.setPowerStyle(context, powerStyle) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    d3 = look.d3, glass = theme == ClusterTheme.TIDE, caption = powerCaption,
                )
            }
        }
        // Արգելակման լույսեր՝ իրական ոտնակից (ակնթարթային)․ եթե ազդանշան չկա՝ արագության նվազումից
        val stoppedInGear = rawSpeed < 0.5f && state.gear != null && state.gear != 1
        val pedal = state.brakePedal
        val braking = if (pedal != null) pedal > 0 || stoppedInGear
            else decelBraking || (rawSpeed > 2f && rawPower < -5f && !state.isCharging) || stoppedInGear
        val road: @Composable (Modifier) -> Unit = { m ->
            RoadScene(speedKmh = speed, braking = braking, headlights = state.headlightsOn, modifier = m, translucent = theme != ClusterTheme.CLASSIC,
                edgeColor = if (theme == ClusterTheme.ROAD) roadEdgeColor(soc) else null)
        }
        if (swap) powerGauge(Modifier.weight(0.3f).fillMaxHeight()) else speedGauge(Modifier.weight(0.3f).fillMaxHeight())
        Box(Modifier.weight(0.4f).fillMaxHeight()) {
            when (theme) {
                ClusterTheme.CLASSIC -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ClusterHeader(state, extras)
                    Spacer(Modifier.height(6.dp))
                    road(Modifier.fillMaxWidth().weight(1f))
                }
                // «Ճանապարհ»՝ Լճի դասավորությունը առանց լճի և ուղղահայաց գծերի (լճի Canvas-ը միայն LAGOON-ի համար է)
                ClusterTheme.LAGOON, ClusterTheme.TIDE, ClusterTheme.ROAD -> Box(Modifier.fillMaxSize()) {
                    road(Modifier.fillMaxSize().padding(top = 70.dp))
                    WaveHeader(gearText, gearColor, inside, outside, altText, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                    if (look.archLabels == ArchLabels.ENDS) {
                        BigNumber("$soc%", Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
                        BigNumber(rangeText, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
                    } else {
                        // ճանապարհի վերջում, կենտրոնում՝ 5 վ-ը մեկ պարզ fade (տեքստերը չեն շարժվում),
                        // ~0.5 սմ (19dp) ավելի հետ՝ դեպի ճանապարհի ծայրը, փոքր տառով
                        val idx = rememberCycleIndex(true)
                        androidx.compose.animation.Crossfade(targetState = idx, animationSpec = tween(600), label = "wave-cycle",
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 55.dp)) { i ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                BigNumber(if (i == 0) rangeText else "$soc%", Modifier, 26)
                            }
                        }
                    }
                }
                ClusterTheme.ARCH -> ArchCenter(look, gaugeSide, captionH, if (speedCaptionInside) 0.005f else 0.025f,
                    soc, rangeText, gearText, gearColor, inside, outside, altText, road)
            }
        }
        if (swap) speedGauge(Modifier.weight(0.3f).fillMaxHeight()) else powerGauge(Modifier.weight(0.3f).fillMaxHeight())
        }
    }
}

private fun next(s: GaugeStyle) = GaugeStyle.values()[(s.ordinal + 1) % GaugeStyle.values().size]

/** Վերևի տողը՝ D | մարտկոց, պաշար / ծախս | ջերմաստիճաններ (բաժանարար գծերով)։ */
@Composable
private fun GaugeWithCaption(caption: String?, modifier: Modifier, gauge: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        gauge()
        // ~1 սմ վերև՝ սարքի աղեղի բացվածքի մեջ (3D-ում՝ null, տեքստը գծվում է շրջանակի ներսում)
        if (caption != null) Text(caption, color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(y = (-36).dp))
    }
}

@Composable
private fun ClusterHeader(state: DashboardUiState, extras: KomClusterExtras.Extras) {
    // Մարտկոցը՝ ճիշտ մեջտեղում (ճանապարհի վրա), փոխանցումը՝ ձախ, ջերմաստիճանները՝ աջ
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        val (g, gc) = when (state.gear) {
            1 -> "P" to TextSecondary
            2 -> "R" to Amber
            3 -> "N" to TextSecondary
            4 -> "D" to AccentGreen
            else -> "–" to TextMuted
        }
        Text(g, color = gc, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
        BatteryBar(state.soc ?: 0, state.isCharging,
            state.estimatedRangeKm?.let { "~ ${"%.0f".format(it)} km" } ?: "~ — km")
        // Առաջին տողում բարձրությունը, երկրորդում ջերմաստիճանները՝ «մեքենա | դրսում» (ձախից հավասարեցված)
        CornerInfo(InfoRows.TWO, withAlt = true, state.insideTemp?.let { "$it°" } ?: "—", state.exteriorTemp?.let { "$it°" } ?: "—",
            extras.altitudeM?.let { "$it m" } ?: "— m", Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun HeaderDivider() {
    Spacer(Modifier.width(14.dp))
    Box(Modifier.width(1.dp).height(56.dp).background(CardBorder))
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun TempRow(value: String?, icon: ImageVector) {
    // Icon-ը ձախից, թիվը՝ հետո (ինչպես մեքենայի վահանակում)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(value ?: "—", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Մարտկոցի պատկեր՝ լցված մասը SOC-ն է, գույնը կարմիր → դեղին → կանաչ, լիցքավորվելիս՝ «շնչում» է։ */
@Composable
private fun BatteryBar(soc: Int, charging: Boolean, range: String) {
    // Չափն ու տեսքը՝ օգտատիրոջ նմուշով (առանց շրջանակի և «ծայրի»), լցված մասը՝ SOC-ի չափով, մնացածը՝ մուգ։
    // Գույնը՝ ըստ լիցքի․ ≥ 60% ամբողջը կանաչ, 60 → 30% աստիճանաբար դեղին, 30 → 10% կարմիր
    val f by animateFloatAsState((soc / 100f).coerceIn(0f, 1f), tween(800), label = "soc")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = if (charging) 0.6f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.width(240.dp).height(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(12.dp.toPx())
            drawRoundRect(Road, cornerRadius = r)
            clipRect(right = size.width * f) {
                // նախկին գրադիենտը՝ կարմիր → դեղին → կանաչ ամբողջ լայնքով, երևում է լցված մասը
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFE5322D), Color(0xFFF5C518), AccentGreen), startX = 0f, endX = size.width),
                    cornerRadius = r, alpha = pulse)
            }
            // Նուրբ ուրվագիծ՝ որ երևա, թե որքան է պակասել 100%-ից
            val sw = 1.5.dp.toPx()
            drawRoundRect(Color(0xFF5C7699).copy(alpha = 0.7f), topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw), cornerRadius = r, style = Stroke(sw))
        }
        Text(
            "$soc% $range", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 4f)),
        )
    }
}

private val SocGreen = Color(0xFF4CD964)
private val SocYellow = Color(0xFFF5C518)
private val SocLow = Color(0xFFE5322D)

/** Մարտկոցի գույնը՝ ըստ լիցքի (սահուն անցումներով)։ */
private fun socColor(soc: Float): Color = when {
    soc >= 60f -> SocGreen
    soc >= 30f -> lerp(SocYellow, SocGreen, (soc - 30f) / 30f)
    soc >= 10f -> lerp(SocLow, SocYellow, (soc - 10f) / 20f)
    else -> SocLow
}

/** Սարք՝ ընտրված ոճով։ Սեղմելիս փոխվում է հաջորդ ոճին։ */
@Composable
private fun Gauge(
    value: Float, min: Float, max: Float, step: Float, unit: String, label: String,
    color: Color, style: GaugeStyle, onTap: () -> Unit, modifier: Modifier,
    limit: Float = 0f, overLimit: Boolean = false, d3: Boolean = false, glass: Boolean = false, caption: String = "",
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
                if (d3 && style == GaugeStyle.NEEDLE) {
                    // 3D․ ստատիկ շերտը (եզր, դեմք, սանդղակ) առանձին է, որ սլաքի շարժման ժամանակ չվերագծվի
                    Dial3DStatic(min, max, step, limit, overLimit, glass, caption)
                    Canvas(Modifier.fillMaxSize()) { drawDial3DDynamic(value, min, max, color) }
                } else {
                    Canvas(Modifier.fillMaxSize()) { drawDial(value, min, max, step, color, style == GaugeStyle.NEEDLE, limit, overLimit) }
                }
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

@Composable
private fun Dial3DStatic(min: Float, max: Float, step: Float, limit: Float, overLimit: Boolean, glass: Boolean, caption: String) {
    Canvas(Modifier.fillMaxSize()) { drawDial3DStatic(min, max, step, limit, overLimit, glass, caption) }
}

/** 3D սարքի ստատիկ մասը․ ստվեր, մետաղյա եզր, խորությամբ դեմք, ուղի, սանդղակ, սահմանափակման նշան։ */
private fun DrawScope.drawDial3DStatic(min: Float, max: Float, step: Float, limit: Float, overLimit: Boolean, glass: Boolean, caption: String) {
    // 3D․ եզրը՝ ամբողջ քառակուսու չափով, սանդղակը՝ փոքր, որ «Միջ.» տեքստը տեղավորվի ներքևի բացվածքում
    val stroke = size.minDimension * 0.045f
    val radius = size.minDimension * 0.40f
    val c = center
    val bezel = size.minDimension * 0.495f
    fun ang(v: Float) = START + SWEEP * ((v.coerceIn(min, max) - min) / (max - min))
    // փափուկ ստվեր ներքևում
    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = c + Offset(0f, radius * 0.08f), radius = bezel * 1.08f),
        radius = bezel * 1.08f, center = c + Offset(0f, radius * 0.08f))
    // մետաղյա եզր
    drawCircle(Brush.linearGradient(
        listOf(Color(0xFFDFE7F2), Color(0xFF7F8DA3), Color(0xFF2B3649), Color(0xFF8A98AD), Color(0xFF1B2433)),
        start = Offset(c.x - bezel, c.y - bezel), end = Offset(c.x + bezel, c.y + bezel),
    ), radius = bezel, center = c)
    drawCircle(Color(0xFF05080F), radius = bezel * 0.94f, center = c)
    // դեմք՝ վերևից լուսավորված
    val faceColors = if (glass) listOf(Color(0x5A28466E), Color(0x730A1428), Color(0xB303060E))
        else listOf(Color(0xFF1D3152), Color(0xFF0C1628), Color(0xFF03060E))
    drawCircle(Brush.radialGradient(faceColors, center = c + Offset(0f, -radius * 0.25f), radius = bezel), radius = bezel * 0.925f, center = c)
    val tl = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawArc(Color.Black.copy(alpha = 0.55f), START, SWEEP, false, tl, arcSize, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round))
    drawArc(Color(0x2E5A78AA), START, SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    if (min < 0f) drawArc(AccentGreen.copy(alpha = 0.3f), START, ang(0f) - START, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.rgb(200, 212, 232); textSize = size.minDimension * 0.055f
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
        setShadowLayer(3f, 0f, 2f, android.graphics.Color.argb(160, 0, 0, 0))
    }
    var v = min
    while (v <= max + 0.01f) {
        val rad = Math.toRadians(ang(v).toDouble())
        val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        drawLine(Color(0xFFC8D4E8), Offset(c.x + cs * radius * 0.80f, c.y + sn * radius * 0.80f),
            Offset(c.x + cs * radius * 0.92f, c.y + sn * radius * 0.92f), strokeWidth = 4f, cap = StrokeCap.Round)
        val h = v + step / 2
        if (h < max) {
            val rh = Math.toRadians(ang(h).toDouble())
            drawLine(Color(0x80C8D4E8), Offset(c.x + cos(rh).toFloat() * radius * 0.85f, c.y + sin(rh).toFloat() * radius * 0.85f),
                Offset(c.x + cos(rh).toFloat() * radius * 0.92f, c.y + sin(rh).toFloat() * radius * 0.92f), strokeWidth = 2f)
        }
        if (limit <= 0f || kotlin.math.abs(v - limit) > 0.5f) {
            drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(),
                c.x + cs * radius * 0.66f, c.y + sn * radius * 0.66f + paint.textSize / 3, paint)
        }
        v += step
    }
    if (limit > 0f) {
        val rad = Math.toRadians(ang(limit).toDouble())
        val p = Offset(c.x + cos(rad).toFloat() * radius * 0.66f, c.y + sin(rad).toFloat() * radius * 0.66f)
        val r = size.minDimension * 0.075f * LIMIT_SIGN_SCALE
        drawCircle(if (overLimit) Color(0xFF991B1B) else Color(0xFFF4F7FB), r, p)
        drawCircle(Color(0xFFE53935), r, p, style = Stroke(size.minDimension * 0.014f * LIMIT_SIGN_SCALE))
        val tp = android.graphics.Paint().apply {
            this.color = if (overLimit) android.graphics.Color.WHITE else android.graphics.Color.rgb(17, 17, 17)
            textSize = size.minDimension * 0.06f * LIMIT_SIGN_SCALE; isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(limit.roundToInt().toString(), p.x, p.y + tp.textSize / 3, tp)
    }
    if (caption.isNotEmpty()) {
        val cp = android.graphics.Paint().apply {
            this.color = android.graphics.Color.rgb(185, 198, 220); textSize = size.minDimension * 0.05f; isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        val maxW = size.minDimension * 0.56f
        val tw = cp.measureText(caption)
        if (tw > maxW) cp.textSize *= maxW / tw
        drawContext.canvas.nativeCanvas.drawText(caption, c.x, c.y + size.minDimension * 0.37f, cp)
    }
}

/** 3D սարքի շարժվող մասը․ փայլող արժեքի աղեղ, երկգույն սլաք՝ ստվերով, ուռուցիկ կենտրոն, ապակու փայլ։ */
private fun DrawScope.drawDial3DDynamic(value: Float, min: Float, max: Float, color: Color) {
    val stroke = size.minDimension * 0.045f
    val radius = size.minDimension * 0.40f
    val c = center
    fun ang(v: Float) = START + SWEEP * ((v.coerceIn(min, max) - min) / (max - min))
    val tl = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val zeroA = ang(0f)
    val a = ang(value)
    val from = kotlin.math.min(zeroA, a)
    val sweep = kotlin.math.abs(a - zeroA).coerceAtLeast(0.5f)
    drawArc(color.copy(alpha = 0.28f), from, sweep, false, tl, arcSize, style = Stroke(stroke * 2.1f, cap = StrokeCap.Round))
    drawArc(color, from, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    // սլաք
    val rad = Math.toRadians(a.toDouble())
    val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
    val nrm = Offset(-dir.y, dir.x)
    val tip = c + dir * (radius * 0.86f)
    val tail = c - dir * (radius * 0.16f)
    val half = radius * 0.05f
    val left = c + nrm * half
    val right = c - nrm * half
    val sh = Offset(radius * 0.03f, radius * 0.045f)
    val shadow = Path().apply { moveTo(tip.x + sh.x, tip.y + sh.y); lineTo(left.x + sh.x, left.y + sh.y); lineTo(tail.x + sh.x, tail.y + sh.y); lineTo(right.x + sh.x, right.y + sh.y); close() }
    drawPath(shadow, Color.Black.copy(alpha = 0.4f))
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(tail.x, tail.y); close() }, Color(0xFFFF7A5C))
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(right.x, right.y); lineTo(tail.x, tail.y); close() }, Color(0xFFC2321A))
    // կենտրոնական «գլխիկ»
    val hr = radius * 0.28f
    drawCircle(Color.Black.copy(alpha = 0.45f), hr * 1.08f, c + Offset(0f, hr * 0.18f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF44536D), Color(0xFF1A2438), Color(0xFF0A0F1A)), center = c + Offset(-hr * 0.3f, -hr * 0.4f), radius = hr * 1.2f), hr, c)
    drawCircle(Color(0x59C8D7F0), hr, c, style = Stroke(3f))
    // ապակու փայլ վերևում
    val gb = size.minDimension * 0.46f
    drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), startY = c.y - gb, endY = c.y),
        topLeft = Offset(c.x - gb * 0.95f, c.y - gb * 1.0f), size = Size(gb * 1.8f, gb * 1.0f))
}

private const val START = 135f
private const val SWEEP = 270f

private fun DrawScope.drawDial(
    value: Float, min: Float, max: Float, step: Float, color: Color, needle: Boolean,
    limit: Float = 0f, overLimit: Boolean = false,
) {
    val stroke = size.minDimension * 0.05f
    val radius = size.minDimension / 2 - stroke
    val c = center
    val tl = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    fun ang(v: Float) = START + SWEEP * ((v.coerceIn(min, max) - min) / (max - min))

    drawArc(Track, START, SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val zeroA = ang(0f)
    if (min < 0f) drawArc(AccentGreen.copy(alpha = 0.3f), START, zeroA - START, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val a = ang(value)
    val from = kotlin.math.min(zeroA, a)
    val sweep = kotlin.math.abs(a - zeroA).coerceAtLeast(0.5f)
    drawArc(color, from, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

    // Թույլատրելի արագությունը սանդղակի վրա՝ կարմիր շրջանակով թիվ (գերազանցելիս՝ լցված մուգ կարմիր)
    fun drawLimitMarker() {
        if (limit <= 0f) return
        val rad = Math.toRadians(ang(limit).toDouble())
        val p = Offset(c.x + cos(rad).toFloat() * radius * 0.68f, c.y + sin(rad).toFloat() * radius * 0.68f)
        val r = size.minDimension * 0.075f * LIMIT_SIGN_SCALE
        if (overLimit) {
            drawCircle(Color(0xFF991B1B), r, p)
        } else {
            drawCircle(CardSurface, r, p)
        }
        drawCircle(Color(0xFFE53935), r, p, style = Stroke(size.minDimension * 0.012f * LIMIT_SIGN_SCALE))
        val tp = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE; textSize = size.minDimension * 0.06f * LIMIT_SIGN_SCALE; isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(limit.roundToInt().toString(), p.x, p.y + tp.textSize / 3, tp)
    }

    if (!needle) { drawLimitMarker(); return }
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
        if (limit <= 0f || kotlin.math.abs(v - limit) > 0.5f) {
            drawContext.canvas.nativeCanvas.drawText(v.roundToInt().toString(),
                c.x + cs * radius * 0.68f, c.y + sn * radius * 0.68f + paint.textSize / 3, paint)
        }
        v += step
    }
    drawLimitMarker()
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
/** Արագաչափի սանդղակի վրայի սահմանափակման նշանի չափը (օգտատիրոջ խնդրանքով՝ 15%-ով փոքր)։ */
private const val LIMIT_SIGN_SCALE = 0.85f

@Composable
private fun RoadScene(speedKmh: Float, braking: Boolean, modifier: Modifier, headlights: Boolean = false, translucent: Boolean = false,
                      edgeColor: Color? = null) {
    // Համարանիշը՝ Settings → Application → «License plate» (թարմացվում է 2 վրկ-ը մեկ)
    val ctx = LocalContext.current
    val plate by produceState(initialValue = KomPrefs.plate(ctx)) {
        while (true) { value = KomPrefs.plate(ctx); delay(2_000L) }
    }
    // Ընտրված մեքենան ընտրված գույնով (Settings → «Իմ մեքենան»)՝ նկարը, արգելակման լույսերի շերտը
    // և համարանիշի տեղը։ Հաշվարկվում է ֆոնում, մինչ այդ մեքենան պարզապես չի նկարվում։
    val car by com.bydmate.app.ui.car.CarPacks.appearance.collectAsState()
    LaunchedEffect(Unit) { com.bydmate.app.ui.car.CarPacks.ensure(ctx) }
    val brakeAlpha by animateFloatAsState(if (braking) 1f else 0f, tween(250), label = "brake")
    val headAlpha by animateFloatAsState(if (headlights) 1f else 0f, tween(500), label = "headlights")
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
        if (translucent) {
            // նոր ոճերում՝ լուսավոր, կիսաթափանցիկ ճանապարհ (ալիքը/կամարը երևում են)
            drawPath(road, Brush.verticalGradient(listOf(Color(0x00C8E1FF), Color(0x38C8E1FF)), startY = hy, endY = by))
            if (edgeColor != null) {
                // «Ճանապարհ» ոճ՝ եզրագծերը լիցքի գույնով։ Soft edge՝ շողը եզրից դեպի դուրս (կողքեր) է տարածվում
                // և մարում, մոտիկում լայն, հորիզոնում նեղ (հեռանկար) — 3D տպավորության համար։
                val spreadBottom = size.width * 0.06f
                val spreadTop = spreadBottom * 0.15f
                val layers = 12
                for (side in listOf(-1f, 1f)) {
                    val xTop = cx + side * nw
                    val xBottom = cx + side * hw
                    for (k in layers downTo 1) {
                        val f = k / layers.toFloat()
                        val fade = (1f - f) * (1f - f)
                        drawLine(
                            Brush.verticalGradient(listOf(edgeColor.copy(alpha = 0.10f * fade), edgeColor.copy(alpha = 0.55f * fade)),
                                startY = hy, endY = by),
                            Offset(xTop + side * spreadTop * f, hy), Offset(xBottom + side * spreadBottom * f, by),
                            strokeWidth = spreadBottom / layers * 1.6f,
                        )
                    }
                    drawLine(Brush.verticalGradient(listOf(edgeColor.copy(alpha = 0.55f), edgeColor), startY = hy, endY = by),
                        Offset(xTop, hy), Offset(xBottom, by), strokeWidth = 5f)
                }
            } else {
                drawLine(Color(0xC078BEFF), Offset(cx - nw, hy), Offset(cx - hw, by), strokeWidth = 3f)
                drawLine(Color(0xC078BEFF), Offset(cx + nw, hy), Offset(cx + hw, by), strokeWidth = 3f)
            }
        } else {
            drawPath(road, Road)
            drawLine(RoadEdge, Offset(cx - nw, hy), Offset(cx - hw, by), strokeWidth = 3f)
            drawLine(RoadEdge, Offset(cx + nw, hy), Offset(cx + hw, by), strokeWidth = 3f)
        }
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
        // Մեքենան՝ ճանապարհի ներքևի մասում, կենտրոնում
        val look = car ?: return@Canvas
        val carImg = look.car
        val cw = hw * 0.62f
        val ch = cw * carImg.height / carImg.width
        // Լուսարձակներ՝ մեքենայից առաջ ճանապարհին ընկնող լույսի շող (ինչպես վարորդի վահանակում)
        if (headAlpha > 0.01f) {
            val carTop = by - ch * 0.55f
            val reach = (by - hy) * 0.62f
            val beam = Path().apply {
                moveTo(cx - cw * 0.42f, carTop)
                lineTo(cx - cw * 0.20f, carTop - reach)
                lineTo(cx + cw * 0.20f, carTop - reach)
                lineTo(cx + cw * 0.42f, carTop)
                close()
            }
            drawPath(beam, Brush.verticalGradient(
                listOf(Color(0x00FFF4D6), Color(0x55FFF4D6), Color(0xAAFFF8E6)),
                startY = carTop - reach, endY = carTop,
            ), alpha = headAlpha)
        }
        val dst = androidx.compose.ui.unit.IntOffset((cx - cw / 2).toInt(), (by - ch).toInt())
        val dstSize = androidx.compose.ui.unit.IntSize(cw.toInt(), ch.toInt())
        drawOval(Color.Black.copy(alpha = 0.45f), Offset(cx - cw * 0.52f, by - ch * 0.07f), Size(cw * 1.04f, ch * 0.12f))
        drawImage(carImg, dstOffset = dst, dstSize = dstSize, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        look.plate?.let { drawPlate(plate, dst.x.toFloat(), dst.y.toFloat(), cw / carImg.width, it) }
        val brakeImg = look.brake
        if (brakeImg != null && brakeAlpha > 0.01f) {
            drawImage(brakeImg, dstOffset = dst, dstSize = dstSize, alpha = brakeAlpha,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        }
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

/**
 * Հայկական համարանիշ մեքենայի պատկերի վրա [box]-ում (պատկերի px-երով՝ left, top, width, height,
 * մեքենայի փաթեթից)․ սպիտակ ֆոն, սև եզր, ձախում դրոշ և «AM», մեջտեղում՝ տեքստը։ [text]-ը
 * դատարկ է՝ մաքուր սպիտակ համարանիշ։
 */
internal fun DrawScope.drawPlate(text: String, ox: Float, oy: Float, s: Float, box: IntArray) {
    val l = ox + box[0] * s
    val t = oy + box[1] * s
    val w = box[2] * s
    val h = box[3] * s
    val r = CornerRadius(h * 0.14f)
    drawRoundRect(Color(0xFFF4F4F4), Offset(l, t), Size(w, h), r)
    drawRoundRect(Color(0xFF151515), Offset(l, t), Size(w, h), r, style = Stroke(h * 0.05f))
    // Դրոշ և «AM»
    val fx = l + w * 0.03f
    val fy = t + h * 0.15f
    val fw = w * 0.12f
    val fh = h * 0.3f
    listOf(Color(0xFFD90012), Color(0xFF0033A0), Color(0xFFF2A800)).forEachIndexed { i, c ->
        drawRect(c, Offset(fx, fy + fh * i / 3f), Size(fw, fh / 3f))
    }
    val am = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(20, 20, 20); textSize = h * 0.26f; isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText("AM", fx + fw / 2f, t + h * 0.84f, am)
    drawLine(Color(0xFF9AA3AF), Offset(fx + fw + w * 0.025f, t + h * 0.14f), Offset(fx + fw + w * 0.025f, t + h * 0.86f), strokeWidth = w * 0.012f)
    if (text.isBlank()) return
    // Տեքստը՝ նեղ, թավ տառատեսակով, որ տեղավորվի մնացած լայնքում
    val x0 = fx + fw + w * 0.05f
    val x1 = l + w * 0.97f
    val p = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(15, 15, 15)
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        textSize = h * 0.72f
    }
    val tw = p.measureText(text)
    if (tw > x1 - x0) p.textSize *= (x1 - x0) / tw
    val fm = p.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(text, (x0 + x1) / 2f, t + h / 2f - (fm.ascent + fm.descent) / 2f, p)
}
