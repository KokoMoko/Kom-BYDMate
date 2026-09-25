package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.components.SocGauge
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/** «My Dashboard»-ի սալիկի բովանդակությունը՝ ըստ տեսակի։ */
@Composable
fun MyDashboardTileContent(
    tile: Tile,
    state: DashboardUiState,
    requestGrant: ((Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (tile.type) {
        TileType.WIDGET -> DashboardWidgetSlot(
            slot = tile.slot,
            emptyHint = stringResource(R.string.kom_widget_hint_generic),
            requestGrant = requestGrant,
            modifier = modifier,
        )
        TileType.SOC -> TileCard(modifier) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SocGauge(soc = state.soc ?: 0, modifier = Modifier.size(min(maxWidth, maxHeight) * 0.92f),
                    isCharging = state.isCharging)
            }
        }
        TileType.RANGE -> TileCard(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.estimatedRangeKm?.let { "~${"%.0f".format(it)}" } ?: "—", color = AccentGreen,
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_unit_km), color = AccentGreen.copy(alpha = 0.7f),
                        fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(stringResource(R.string.dashboard_range_label), color = TextMuted, fontSize = 12.sp)
            }
        }
        TileType.TEMPS -> TileCard(modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TileTempRow(state.insideTemp, Icons.Outlined.DirectionsCar)
                TileTempRow(state.exteriorTemp, Icons.Outlined.WbSunny)
            }
        }
    }
}

@Composable
private fun TileCard(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier.clip(shape).background(CardSurface).border(1.dp, CardBorder, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TileTempRow(temp: Int?, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(temp?.let { "$it°" } ?: "—", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
    }
}
