package com.bydmate.app.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
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
    onOpenApp: () -> Unit = {},
) {
    when (tile.type) {
        TileType.APP -> AppTile(tile, onOpenApp, modifier)
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
                Text(stringResource(R.string.kom_tile_range), color = TextSecondary, fontSize = 14.sp)
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

/**
 * «Application» սալիկ (Panel ռեժիմ)։ Սեղմելիս հավելվածը բացվում է split-ով՝ Kom-ը 1/3, հավելվածը
 * 2/3 (տես [KomPanel])։ My Dashboard-ը split-ը բացում է նաև ինքնաբերաբար։
 */
@Composable
private fun AppTile(tile: Tile, onOpen: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    val label = remember(tile.pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(tile.pkg, 0)).toString() }.getOrDefault(tile.pkg)
    }
    val icon = remember(tile.pkg) { runCatching { pm.getApplicationIcon(tile.pkg) }.getOrNull() }

    TileCard(modifier.clickable(enabled = tile.pkg.isNotEmpty(), onClick = onOpen)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                AndroidView(factory = { android.widget.ImageView(it).apply { setImageDrawable(icon) } },
                    modifier = Modifier.size(64.dp))
            }
            Text(label, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.kom_tile_app_panel_hint), color = TextMuted, fontSize = 13.sp)
        }
    }
}

/** true, երբ MainActivity-ն multi-window (DiLink split) ռեժիմում է։ Թարմանում է կոնֆիգուրացիայի փոփոխությամբ։ */
@Composable
fun rememberInMultiWindow(): Boolean {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(config) {
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.isInMultiWindowMode == true
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
