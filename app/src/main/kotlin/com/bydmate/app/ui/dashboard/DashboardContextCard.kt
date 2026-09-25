package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import androidx.compose.ui.res.stringResource
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyMid
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.SocYellow
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import com.bydmate.app.navdata.NavGuidanceHub
import kotlinx.coroutines.delay

/**
 * Kom-BYDMate: Главная-ի B քարտը, որ փոխվում է ըստ իրավիճակի․
 * լիցքավորում → լիցքի առաջընթաց, ընթացք → արագություն և ընթացիկ ուղևորություն, կայանված → պաշար։
 */
@Composable
fun DashboardContextCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(CardSurface)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            state.isCharging -> ChargingContent(state)
            state.sessionStartedAt != null -> DrivingContent(state)
            else -> ParkedContent(state)
        }
    }
}

@Composable
private fun ChargingContent(state: DashboardUiState) {
    val soc = state.soc ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.kom_ctx_charging), color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$soc%", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(16.dp))
            // Առաջընթացի գիծ
            Box(
                Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(NavyMid)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(soc.coerceIn(0, 100) / 100f)
                        .fillMaxHeight()
                        .background(AccentGreen)
                )
            }
        }
        state.estimatedRangeKm?.let {
            Text(stringResource(R.string.kom_ctx_range_left, "%.0f".format(it)), color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DrivingContent(state: DashboardUiState) {
    val minutes by produceState(initialValue = elapsedMin(state.sessionStartedAt), state.sessionStartedAt) {
        while (true) {
            value = elapsedMin(state.sessionStartedAt)
            delay(15_000L)
        }
    }
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // Սպիդոմետր (swipe՝ ոճեր, ⋮՝ կարգավորումներ) | ուղևորություն | երթուղի կամ վազք
        DashboardSpeedometer(speed = state.speed ?: 0, modifier = Modifier.weight(0.5f).fillMaxHeight())
        ColumnDivider()
        Column(modifier = Modifier.weight(0.25f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val km = stringResource(R.string.kom_ctx_km, state.tripDistanceKm?.let { "%.1f".format(it) } ?: "—")
            val time = if (minutes >= 60) {
                stringResource(R.string.kom_ctx_hours_min, (minutes / 60).toInt(), (minutes % 60).toInt())
            } else {
                stringResource(R.string.kom_ctx_min, minutes.toInt())
            }
            Stat(stringResource(R.string.kom_ctx_trip), "$km · $time")
            val cons = state.consumption?.let { stringResource(R.string.kom_ctx_kwh100, "%.1f".format(it)) } ?: "—"
            Stat(stringResource(R.string.kom_ctx_consumption), cons,
                color = state.consumption?.let { consumptionTint(it) } ?: TextMuted)
        }
        ColumnDivider()
        RouteOrOdometer(state, Modifier.weight(0.25f))
    }
}

@Composable
private fun ColumnDivider() {
    Spacer(Modifier.width(12.dp))
    Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(CardBorder))
    Spacer(Modifier.width(14.dp))
}

/**
 * Navigator-ում երթուղի լինելիս՝ մնացած կմ/ժամանակ, ժամանման ժամ և ժամանման պահի լիցքը
 * (SOC-ից և պաշարից)։ Առանց երթուղու՝ վազք և այսօրվա կմ։
 */
@Composable
private fun RouteOrOdometer(state: DashboardUiState, modifier: Modifier) {
    val nav by produceState(initialValue = NavGuidanceHub.Snapshot()) {
        while (true) {
            value = runCatching { NavGuidanceHub.snapshot() }.getOrDefault(NavGuidanceHub.Snapshot())
            delay(2_000L)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (nav.active && nav.totalDistMeters > 0) {
            val km = nav.totalDistMeters / 1000.0
            val min = nav.etaSeconds / 60
            val dur = if (min >= 60) stringResource(R.string.kom_ctx_hours_min, min / 60, min % 60)
            else stringResource(R.string.kom_ctx_min, min)
            Stat(stringResource(R.string.kom_ctx_route), stringResource(R.string.kom_ctx_km, "%.1f".format(km)) + " · " + dur)
            if (nav.etaSeconds > 0) {
                val arrival = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(System.currentTimeMillis() + nav.etaSeconds * 1000L))
                Stat(stringResource(R.string.kom_ctx_arrival), arrival)
            }
            val soc = state.soc
            val range = state.estimatedRangeKm
            if (soc != null && range != null && range > 0) {
                val arrivalSoc = (soc * (1 - km / range)).toInt()
                val col = when {
                    arrivalSoc < 10 -> SocRed
                    arrivalSoc < 20 -> SocYellow
                    else -> AccentGreen
                }
                Stat(stringResource(R.string.kom_ctx_arrival_soc), if (arrivalSoc < 0) "✕" else "≈ $arrivalSoc%", color = col)
            }
        } else {
            val odo = state.odometer?.let { String.format(java.util.Locale.US, "%,.1f", it).replace(',', ' ') } ?: "—"
            Stat(stringResource(R.string.kom_ctx_odometer), stringResource(R.string.kom_ctx_km, odo))
            val today = state.totalKmToday + (state.tripDistanceKm ?: 0.0)
            Stat(stringResource(R.string.kom_ctx_today), stringResource(R.string.kom_ctx_km, "%.1f".format(today)))
        }
    }
}

@Composable
private fun ParkedContent(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.kom_ctx_parked), color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.estimatedRangeKm?.let { "~${"%.0f".format(it)}" } ?: "—", color = AccentGreen,
                fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.kom_ctx_range_soc, state.soc?.toString() ?: "—"), color = TextSecondary, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color = TextPrimary) {
    // Պիտակը վերևում, արժեքը տակը՝ նեղ աջ սյունակում տեղավորվելու համար
    Column {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

private fun elapsedMin(startedAt: Long?): Long =
    startedAt?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0) } ?: 0

private fun consumptionTint(v: Double): Color = when {
    v < 20.0 -> AccentGreen
    v < 25.0 -> SocYellow
    else -> SocRed
}
