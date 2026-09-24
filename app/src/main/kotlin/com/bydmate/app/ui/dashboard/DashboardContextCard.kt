package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Արագություն՝ մեծ թվերով
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${state.speed ?: 0}", color = TextPrimary, fontSize = 56.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.kom_ctx_speed_unit), color = TextSecondary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.width(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun elapsedMin(startedAt: Long?): Long =
    startedAt?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0) } ?: 0

private fun consumptionTint(v: Double): Color = when {
    v < 20.0 -> AccentGreen
    v < 25.0 -> SocYellow
    else -> SocRed
}
