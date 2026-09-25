package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.components.SocGauge
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * Kom-BYDMate: Главная-ի ձախ սյունակի կոմպակտ վերին տողը․ փոքր SOC | պաշար | ջերմաստիճաններ։
 * (Ժամանակը, ուղևորության կմ-ը, ծախսը և վազքը B քարտում են։)
 */
@Composable
fun DashboardTopRow(state: DashboardUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.34f), contentAlignment = Alignment.Center) {
            SocGauge(soc = state.soc ?: 0, modifier = Modifier.size(104.dp), isCharging = state.isCharging)
        }
        Divider()
        Column(Modifier.weight(0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.estimatedRangeKm?.let { "~${"%.0f".format(it)}" } ?: "—", color = AccentGreen,
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_unit_km), color = AccentGreen.copy(alpha = 0.7f), fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(stringResource(R.string.dashboard_range_label), color = TextMuted, fontSize = 12.sp)
        }
        Divider()
        Column(Modifier.weight(0.28f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TempRow(state.insideTemp, Icons.Outlined.DirectionsCar)
            TempRow(state.exteriorTemp, Icons.Outlined.WbSunny)
        }
    }
}

@Composable
private fun TempRow(temp: Int?, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(temp?.let { "$it°" } ?: "—", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(CardBorder))
}
