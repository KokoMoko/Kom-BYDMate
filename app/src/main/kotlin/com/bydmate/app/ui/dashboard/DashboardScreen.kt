package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bydmate.app.R
import com.bydmate.app.data.autoservice.AdbVerdict
import com.bydmate.app.data.remote.DynamicMetric
import com.bydmate.app.data.trips.TripAutoResetMode
import com.bydmate.app.data.trips.TripCounterUi
import com.bydmate.app.domain.calculator.Trend
import com.bydmate.app.ui.components.AdbVerdictDialog
import com.bydmate.app.ui.components.CardDetailDialog
import com.bydmate.app.ui.components.SocGauge
import com.bydmate.app.ui.components.TripCard
import com.bydmate.app.ui.components.adbVerdictColor
import com.bydmate.app.ui.components.adbVerdictText
import com.bydmate.app.ui.components.consumptionColor
import com.bydmate.app.ui.components.rememberSocGaugeMinSize
import com.bydmate.app.ui.theme.*
import com.bydmate.app.ui.widget.TRIP_DISTANCE_TREND_THRESHOLD_KM
import com.bydmate.app.ui.widget.formatDurationShort
import com.bydmate.app.ui.widget.formatTripKm
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun DashboardScreen(
    onOpenTechPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The verdict the dialog was opened for; cleared when the problem goes away so the dialog
    // cannot pop up by itself the next time a verdict appears.
    var openAdbVerdict by remember { mutableStateOf<AdbVerdict?>(null) }
    LaunchedEffect(state.adbVerdict) {
        if (state.adbVerdict == null || state.adbVerdict == AdbVerdict.OK) openAdbVerdict = null
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TopBar(
            isServiceRunning = state.isServiceRunning,
            vehicleDataConnected = state.vehicleDataConnected,
            adbVerdict = state.adbVerdict,
            adbChecking = state.adbChecking,
            onAdbTap = { openAdbVerdict = state.adbVerdict },
        )
        val adbVerdict = state.adbVerdict
        if (openAdbVerdict != null && adbVerdict != null && adbVerdict != AdbVerdict.OK) {
            AdbVerdictDialog(
                verdict = adbVerdict,
                restoreEnabled = viewModel.adbRestoreEnabled,
                onCheck = { viewModel.recheckAdb() },
                onEnableRestore = { viewModel.enableAdbRestore() },
                onOpenDiagnostics = onOpenSettings,
                onDismiss = { openAdbVerdict = null },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT COLUMN: blocks keep their own height, only the gauge yields when space runs out
            BoxWithConstraints(modifier = Modifier.weight(0.4f)) {
                val scrollState = rememberScrollState()
                // The scroll container reaches GaugeGlowRoom above the column so its clip leaves the
                // glow over the gauge alone; the content starts that much lower to stay in place.
                val density = LocalDensity.current
                val glowRoomPx = with(density) { GaugeGlowRoom.roundToPx() }
                val contentMinHeight = with(density) { (constraints.maxHeight + glowRoomPx).toDp() }
                // Ghost car background
                Image(
                    painter = painterResource(R.drawable.leopard3),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.06f },
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
                // Kom-BYDMate: կոմպակտ տող (SOC | պաշար | ջերմաստիճան) + Phone widget + ինսայթ + Music widget
                // Նույն համամասնությունները, ինչ աջ սյունակում (36% / 64%)․ վերևի քարտերը հավասար
                // բարձրության են, իսկ Music-ի և եղանակի ներքևի եզրերը համընկնում են։
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DashboardTopRow(state = state, modifier = Modifier.weight(0.36f))
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(0.64f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Երկու սլոտ մեկ տողում՝ Phone | երկրորդ widget
                        Row(
                            modifier = Modifier.fillMaxWidth().height(88.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DashboardWidgetSlot(
                                slot = DashboardWidgets.SLOT_PHONE,
                                emptyHint = stringResource(R.string.kom_widget_hint_phone),
                                requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            DashboardWidgetSlot(
                                slot = DashboardWidgets.SLOT_PHONE2,
                                emptyHint = stringResource(R.string.kom_widget_hint_generic),
                                requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        DashboardWidgetSlot(
                            slot = DashboardWidgets.SLOT_LEFT,
                            emptyHint = stringResource(R.string.kom_widget_hint_music),
                            requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }

                    // Pop-up dialogs
                    if (state.insightExpanded) {
                        val insightDialogColor = when (state.effectiveInsightTone) {
                            "critical" -> SocRed
                            "warning" -> SocYellow
                            else -> AccentGreen
                        }
                        CardDetailDialog(
                            title = null,
                            borderColor = insightDialogColor,
                            onDismiss = { viewModel.toggleInsightExpanded() }
                        ) {
                            val dynamics = state.insightDynamics
                            val insights = state.insightInsights
                            val hasContent = dynamics.isNotEmpty() || insights.isNotEmpty()
                            if (hasContent) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = state.insightPeriodDays == 7,
                                        onClick = { viewModel.setInsightPeriod(7) },
                                        label = { Text(stringResource(R.string.insight_period_week), fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentGreen.copy(alpha = 0.25f),
                                            selectedLabelColor = AccentGreen
                                        ),
                                    )
                                    FilterChip(
                                        selected = state.insightPeriodDays == 30,
                                        onClick = { viewModel.setInsightPeriod(30) },
                                        label = { Text(stringResource(R.string.insight_period_month), fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentGreen.copy(alpha = 0.25f),
                                            selectedLabelColor = AccentGreen
                                        ),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                // Dynamics table
                                if (dynamics.isNotEmpty()) {
                                    Column {
                                        dynamics.forEach { metric ->
                                            if (metric.section != null) {
                                                Text(
                                                    metric.section,
                                                    color = TextMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                                                )
                                            }
                                            DynamicsRow(metric = metric)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                // Divider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFF2A2A2E))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                // Insights as bullet points
                                if (insights.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        insights.forEach { text ->
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 7.dp, end = 8.dp)
                                                        .size(5.dp)
                                                        .background(
                                                            insightDialogColor.copy(alpha = 0.5f),
                                                            shape = CircleShape
                                                        )
                                                )
                                                StyledInsightText(text = text, bulletColor = insightDialogColor)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                // Footer
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    state.insightDate?.let {
                                        Text(it, color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Text(stringResource(R.string.dashboard_insight_no_data), color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                    // TRIP 1 popup
                    state.trip1?.let { t ->
                        if (state.trip1Expanded) TripCounterDialog(
                            stringResource(R.string.dashboard_trip1_label), t, state.currencySymbol,
                            mode = state.trip1AutoReset,
                            onModeChange = { viewModel.setTripAutoReset(1, it) },
                        ) { viewModel.toggleTripExpanded(1) }
                    }
                    // TRIP 2 popup
                    state.trip2?.let { t ->
                        if (state.trip2Expanded) TripCounterDialog(
                            stringResource(R.string.dashboard_trip2_label), t, state.currencySymbol,
                            mode = state.trip2AutoReset,
                            onModeChange = { viewModel.setTripAutoReset(2, it) },
                        ) { viewModel.toggleTripExpanded(2) }
                    }
                }
            }

            // Kom-BYDMate RIGHT COLUMN — B (իրավիճակային քարտ) + widget (օր․ AccuWeather)
            Column(
                modifier = Modifier.weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardContextCard(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(0.36f),
                    onInsightClick = { viewModel.toggleInsightExpanded() },
                )
                DashboardWidgetSlot(
                    slot = DashboardWidgets.SLOT_RIGHT,
                    emptyHint = stringResource(R.string.kom_widget_hint_weather),
                    requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                    modifier = Modifier.fillMaxWidth().weight(0.64f),
                )
            }
        }
    }
}

private val GaugeMaxSize = 180.dp

private const val DASHBOARD_TRIP_ROWS = 6

// The gauge glow reaches 10.5 dp above its square; the column's 4 dp top padding covers the rest.
private val GaugeGlowRoom = 8.dp

/**
 * Left column of Главная. [content] emits the gauge, the rows under it and the card stack, in
 * that order. Rows and cards keep their own height and the cards sit at the bottom; the gauge
 * takes what is left, between [gaugeMinSize] (its text still clear of the arc) and [GaugeMaxSize].
 * When even the smallest gauge does not fit the minimum height, the column grows taller than
 * it and the caller's scroll takes over.
 */
@Composable
private fun GaugeYieldingColumn(
    gaugeMinSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val free = Constraints(maxWidth = width)
        val rows = measurables[1].measure(free)
        val cards = measurables[2].measure(free)
        val target = constraints.minHeight
        val gaugeSize = (target - rows.height - cards.height)
            .coerceIn(ceil(gaugeMinSize.toPx()).toInt(), maxOf(gaugeMinSize, GaugeMaxSize).roundToPx())
            .coerceAtMost(width)
        val gauge = measurables[0].measure(Constraints.fixed(gaugeSize, gaugeSize))
        val needed = gaugeSize + rows.height + cards.height
        val height = constraints.constrainHeight(maxOf(target, needed))
        layout(width, height) {
            gauge.place((width - gaugeSize) / 2, 0)
            rows.place((width - rows.width) / 2, gaugeSize)
            cards.place((width - cards.width) / 2, height - cards.height)
        }
    }
}

/**
 * Vertical list of at most [maxRows] rows that only shows rows which fit completely within the
 * available height; a row cut in half by the bottom edge is never shown. When the list is full
 * (capped by [maxRows] or by the height), the height left over is shared out among the rows so
 * the last one ends exactly at the bottom edge. Rows keep [rowSpacing] between them.
 */
@Composable
private fun WholeRowsColumn(
    rowSpacing: Dp,
    maxRows: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Rows are measured twice: once at their natural height to pick the count, then at the fitted
    // height. A measurable can be measured only once per pass, hence two subcompositions.
    SubcomposeLayout(modifier) { constraints ->
        val spacingPx = rowSpacing.roundToPx()
        val width = constraints.maxWidth
        val natural = subcompose(WholeRowsSlot.Natural, content)
            .take(maxRows)
            .map { it.measure(Constraints(maxWidth = width)).height }
        val heights = fitWholeRows(natural, spacingPx, constraints.maxHeight, maxRows)
        val placeables = subcompose(WholeRowsSlot.Fitted, content)
            .zip(heights) { measurable, h -> measurable.measure(Constraints(maxWidth = width, minHeight = h, maxHeight = h)) }
        val usedHeight = heights.sum() + spacingPx * (heights.size - 1).coerceAtLeast(0)
        layout(width, constraints.constrainHeight(usedHeight)) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height + spacingPx
            }
        }
    }
}

private enum class WholeRowsSlot { Natural, Fitted }

/**
 * Heights of the rows [WholeRowsColumn] shows, given their [naturalHeights], the [spacing] between
 * them and the [availableHeight] ([Constraints.Infinity] when unbounded). Takes as many whole rows
 * as fit, at most [maxRows]. When the list is full ([maxRows] rows, or fewer because the height
 * ran out), the leftover is spread over the shown rows so they fill [availableHeight] exactly; the
 * first rows take the remainder pixels. A list shorter than [maxRows] that fits entirely keeps its
 * natural heights, and so does an unbounded one.
 */
internal fun fitWholeRows(naturalHeights: List<Int>, spacing: Int, availableHeight: Int, maxRows: Int): List<Int> {
    val candidates = naturalHeights.take(maxRows)
    if (availableHeight == Constraints.Infinity) return candidates
    var count = 0
    var used = 0
    for (height in candidates) {
        val next = used + (if (count > 0) spacing else 0) + height
        if (next > availableHeight) break
        used = next
        count++
    }
    if (count == 0) return emptyList()
    if (count == candidates.size && count < maxRows) return candidates
    val leftover = availableHeight - used
    val extra = leftover / count
    val remainder = leftover % count
    return candidates.take(count).mapIndexed { i, height -> height + extra + if (i < remainder) 1 else 0 }
}

@Composable
private fun TopBar(
    isServiceRunning: Boolean,
    vehicleDataConnected: Boolean,
    adbVerdict: AdbVerdict?,
    adbChecking: Boolean,
    onAdbTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MyBYD",  // Kom-BYDMate
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        // Status side yields width to the brand: long verdict texts ellipsize instead of wrapping.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The ADB verdict takes the slot of «no vehicle data»: when both apply, only ADB shows.
            if (isServiceRunning && adbChecking) {
                Text(
                    stringResource(R.string.adb_verdict_checking),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (isServiceRunning && adbVerdict != null && adbVerdict != AdbVerdict.OK) {
                Text(
                    text = adbVerdictText(adbVerdict),
                    color = adbVerdictColor(adbVerdict),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { onAdbTap() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (isServiceRunning && !vehicleDataConnected) {
                Text(
                    text = stringResource(R.string.dashboard_vehicle_data_offline),
                    color = SocYellow,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isServiceRunning) AccentGreen else TextMuted)
            )
            Text(
                text = if (isServiceRunning) stringResource(R.string.dashboard_status_online) else stringResource(R.string.dashboard_status_offline),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

// ============================================================================
// AI Insight card — text-based, different from value-pair cards
// ============================================================================

@Composable
internal fun InsightCard(  // Kom-BYDMate: internal՝ B քարտում օգտագործելու համար
    title: String?,
    summary: String?,
    borderColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (title != null) borderColor.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", fontSize = 16.sp, color = if (title != null) borderColor else TextMuted)
            Spacer(modifier = Modifier.width(8.dp))
            if (title != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = borderColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (summary != null) {
                        Text(
                            summary,
                            color = borderColor.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.dashboard_insight_no_data),
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ============================================================================
// TRIP 1 / TRIP 2 counter button
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripCounterButton(
    label: String,
    ui: TripCounterUi?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGreen.copy(alpha = 0.5f)),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f".format(ui?.km ?: 0.0), color = AccentGreen,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.dashboard_unit_km), color = TextMuted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(ui?.kwh ?: 0.0), color = AccentGreen,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.dashboard_unit_kwh), color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun BatteryCompactCard(
    sohText: String,
    sohColor: Color,
    tempText: String,
    tempColor: Color,
    voltageText: String,
    voltageColor: Color,
    cellDeltaText: String,
    cellDeltaColor: Color,
    insulationText: String,
    insulationColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BatteryCell(value = sohText, label = "SoH", color = sohColor)
            BatteryCell(value = tempText, label = stringResource(R.string.dashboard_battery_temp_label), color = tempColor)
            BatteryCell(value = voltageText, label = stringResource(R.string.dashboard_battery_voltage_label), color = voltageColor)
            BatteryCell(value = cellDeltaText, label = stringResource(R.string.battery_health_cell_delta_label), color = cellDeltaColor)
            BatteryCell(value = insulationText, label = stringResource(R.string.dashboard_battery_insulation_label), color = insulationColor)
        }
    }
}

@Composable
private fun BatteryCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace)
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

// ============================================================================
// TRIP counter detail popup helpers
// ============================================================================

@Composable
private fun TripDetailRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    labelColor: Color = TextSecondary,
    indent: Boolean = false,
    small: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = labelColor, fontSize = if (small) 12.sp else 13.sp,
            modifier = if (indent) Modifier.padding(start = 14.dp) else Modifier)
        Text(value, color = valueColor, fontSize = if (small) 12.sp else 13.sp,
            fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TripCounterDialog(
    label: String,
    ui: TripCounterUi,
    currencySymbol: String,
    mode: TripAutoResetMode,
    onModeChange: (TripAutoResetMode) -> Unit,
    onDismiss: () -> Unit,
) {
    CardDetailDialog(title = null, borderColor = AccentGreen, onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom) {
            Text(label, color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                if (ui.resetTs == 0L) stringResource(R.string.dashboard_trip_since_all_time)
                else stringResource(R.string.dashboard_trip_since,
                    java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault())
                        .format(java.util.Date(ui.resetTs)),
                    ((System.currentTimeMillis() - ui.resetTs) / 86_400_000L)),
                color = TextMuted, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(ui.km), color = TextPrimary, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.dashboard_unit_km), color = TextSecondary, fontSize = 14.sp)
        }
        val totalMin = ui.drivingMs / 60_000L
        val timeStr = if (totalMin >= 60)
            stringResource(R.string.dashboard_trip_time_hours_min, totalMin / 60, totalMin % 60)
        else stringResource(R.string.dashboard_trip_time_min, totalMin)
        TripDetailRow(stringResource(R.string.dashboard_trip_count_label), ui.tripCount.toString())
        TripDetailRow(stringResource(R.string.dashboard_trip_driving_time_label), timeStr)
        HorizontalDivider(color = CardBorder)
        TripDetailRow(stringResource(R.string.dashboard_trip_kwh_total_label),
            stringResource(R.string.dashboard_trip_kwh_value, "%.1f".format(ui.kwh)), valueColor = AccentGreen)
        TripDetailRow(stringResource(R.string.dashboard_trip_kwh_driving_label),
            stringResource(R.string.dashboard_trip_kwh_value, "%.1f".format(ui.drivingKwh)),
            labelColor = TextMuted, indent = true, small = true)
        TripDetailRow(stringResource(R.string.dashboard_trip_kwh_idle_label),
            stringResource(R.string.dashboard_trip_kwh_value, "%.1f".format(ui.idleKwh)),
            labelColor = TextMuted, indent = true, small = true)
        if (ui.km > 0.0) TripDetailRow(stringResource(R.string.dashboard_trip_avg_label),
            stringResource(R.string.dashboard_trip_avg_value, "%.1f".format(ui.kwh / ui.km * 100.0)))
        HorizontalDivider(color = CardBorder)
        TripDetailRow(stringResource(R.string.dashboard_trip_cost_label),
            "%.2f %s".format(ui.cost, currencySymbol))
        HorizontalDivider(color = CardBorder)
        Text(stringResource(R.string.dashboard_trip_auto_reset_label), color = TextSecondary, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TripAutoResetMode.entries.forEach { m ->
                val chipLabel = when (m) {
                    TripAutoResetMode.OFF -> R.string.dashboard_trip_auto_reset_off
                    TripAutoResetMode.ANY -> R.string.dashboard_trip_auto_reset_any
                    TripAutoResetMode.AC -> R.string.dashboard_trip_auto_reset_ac
                    TripAutoResetMode.DC -> R.string.dashboard_trip_auto_reset_dc
                    TripAutoResetMode.FULL -> R.string.dashboard_trip_auto_reset_full
                }
                TripAutoResetChip(stringResource(chipLabel), selected = m == mode) { onModeChange(m) }
            }
        }
        Text(stringResource(R.string.dashboard_trip_reset_hint), color = TextMuted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DynamicsRow(metric: DynamicMetric) {
    val changeColor = when (metric.sentiment) {
        "good" -> AccentGreen
        "bad" -> SocRed
        else -> TextMuted
    }
    val arrow = when {
        metric.changePct == null -> ""
        metric.changePct > 0 -> "▲"
        metric.changePct < 0 -> "▼"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            metric.label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(
            metric.current,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        if (metric.previous != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text("\u2190", color = TextMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                metric.previous,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (metric.changePct != null) {
            Text(
                "$arrow${"%.0f".format(kotlin.math.abs(metric.changePct))}%",
                color = changeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Highlight numbers in insight text with amber color, bold text before first dash
@Composable
private fun StyledInsightText(text: String, bulletColor: Color) {
    val numberPattern = Regex("""(\d+[.,]\d+|\d+)(%| кВтч?/ч| кВтч?/100км| кВтч?| км/ч| км| мВ|°C)?""")
    val annotated = buildAnnotatedString {
        var lastEnd = 0
        // Bold: text before first " —" or " —"
        val dashIdx = text.indexOf(" — ").takeIf { it > 0 }
            ?: text.indexOf(" — ").takeIf { it > 0 }
        if (dashIdx != null && dashIdx < 40) {
            withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(0, dashIdx))
            }
            lastEnd = dashIdx
        }
        // Highlight numbers
        val remaining = text.substring(lastEnd)
        var rLastEnd = 0
        for (match in numberPattern.findAll(remaining)) {
            if (match.range.first > rLastEnd) {
                withStyle(SpanStyle(color = TextSecondary)) {
                    append(remaining.substring(rLastEnd, match.range.first))
                }
            }
            val num = match.value
            // Only highlight if it looks like a real metric (has digits)
            if (num.any { it.isDigit() } && num.length > 1) {
                withStyle(SpanStyle(color = SocYellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                    append(num)
                }
            } else {
                withStyle(SpanStyle(color = TextSecondary)) {
                    append(num)
                }
            }
            rLastEnd = match.range.last + 1
        }
        if (rLastEnd < remaining.length) {
            withStyle(SpanStyle(color = TextSecondary)) {
                append(remaining.substring(rLastEnd))
            }
        }
    }
    Text(annotated, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace)
    }
}

// ============================================================================
// Shared UI components
// ============================================================================

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DashboardPeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurface,
            labelColor = TextSecondary
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}

// DashboardPeriodChip on an elevated container: the TRIP popup itself is CardSurface.
@Composable
private fun TripAutoResetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentGreen,
            selectedLabelColor = Color.White,
            containerColor = CardSurfaceElevated,
            labelColor = TextSecondary
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected
        )
    )
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String?, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Text(subtitle ?: "", color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaceholderText(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        fontWeight = FontWeight.Medium
    )
}

/**
 * Compact widget-style stat used in the top section of the dashboard left column.
 * Mirrors the IconText composable in FloatingWidgetView (icon muted gray + monospace value).
 * Set iconLast=true for the right-aligned variant where the value comes before the icon.
 */
/**
 * Cabin and outside temperature in the one slot the car icon already owns (#210): «22° / 8°»,
 * cabin first. Without an outside reading the slot shows the cabin alone, exactly as before,
 * so a car that does not report it loses nothing. Numbers only — the row carries no words.
 */
internal fun formatCabinOutside(inside: Int?, outside: Int?): String = when {
    inside == null && outside == null -> "—"
    outside == null -> "$inside°"
    inside == null -> "— / $outside°"
    else -> "$inside° / $outside°"
}

@Composable
private fun CornerStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconLast: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!iconLast) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
        )
        if (iconLast) {
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
