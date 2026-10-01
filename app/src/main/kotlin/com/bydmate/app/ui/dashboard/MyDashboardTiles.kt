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
import androidx.compose.runtime.collectAsState
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
    appActive: Boolean = false,
    appVisibleOnScreen: Boolean = true,
) {
    when (tile.type) {
        TileType.APP -> AppTile(tile, appActive, appVisibleOnScreen, modifier)
        TileType.WIDGET -> DashboardWidgetSlot(
            // Classic-ի ընդհանուր սլոտները My Dashboard-ում՝ առանձին (իր widget-ն ու չափը)
            slot = if (tile.slot.startsWith("tile_")) tile.slot else DashboardWidgets.scoped("my", tile.slot),
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
                val manual = state.rangeEnergySource == "manual_table"
                val band = state.rangeTempBandC
                Text(when {
                    manual -> stringResource(R.string.settings_range_calc_manual)
                    state.rangeProvisional -> stringResource(R.string.kom_range_provisional)
                    band != null -> stringResource(R.string.kom_range_by_temp, band, band + 5)
                    else -> stringResource(R.string.kom_range_learned)
                },
                    color = TextSecondary, fontSize = 11.sp)
                state.rangeConsumptionKwhPer100?.let { avg ->
                    Text(stringResource(R.string.kom_range_consumption, avg), color = TextSecondary, fontSize = 11.sp)
                }
                if (!manual) state.rangeReactiveKwhPer100?.let { now ->
                    Text(stringResource(R.string.kom_range_reactive, now), color = TextSecondary, fontSize = 11.sp)
                }
                if (state.rangeEnergySource == "capacity_soc") {
                    Text(stringResource(R.string.kom_range_soc_fallback), color = TextSecondary, fontSize = 11.sp)
                }
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
 * «Application» սալիկ․ հավելվածը (օր․ Waze, Navigator) բացվում է «լողացող» պատուհանով հենց սալիկի
 * տեղում և չափով (տես [AppTileController])՝ ամբողջովին ինտերակտիվ, իսկ Kom-ը մնում է լիաէկրան։
 * [active]՝ My Dashboard-ը երևում է և խմբագրում չէ․ այդ ժամանակ պատուհանը բացվում է ինքնաբերաբար։
 */
@Composable
private fun AppTile(tile: Tile, active: Boolean, visibleOnScreen: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val rootView = LocalView.current
    var rect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val pm = context.packageManager
    val label = remember(tile.pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(tile.pkg, 0)).toString() }.getOrDefault(tile.pkg)
    }
    val icon = remember(tile.pkg) { runCatching { pm.getApplicationIcon(tile.pkg) }.getOrNull() }

    // Առաջին պլանի հավելվածը (UsageStats + accessibility)․ եթե Kom-ը կամ սալիկի հավելվածը չէ
    // (օր․ 360° տեսախցիկ, զանգ, ուրիշ հավելված), «լողացող» պատուհանը չպետք է մնա նրանց վրա
    val monitor = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext, com.bydmate.app.cluster.ClusterEntryPoint::class.java,
        ).cameraStateMonitor()
    }
    val fg by monitor.foregroundPackage.collectAsState()
    val cameraOn by monitor.active.collectAsState()
    val othersOnTop = cameraOn || (fg != null && fg != context.packageName && fg != tile.pkg)
    val activeNow = active && !othersOnTop

    TileCard(
        modifier
            .onGloballyPositioned { c ->
                // Սալիկի սահմանները էկրանի px-ով (պատուհանի դիրք + դիրքը պատուհանում)
                val loc = IntArray(2)
                rootView.getLocationOnScreen(loc)
                val b = c.boundsInWindow()
                rect = android.graphics.Rect(
                    loc[0] + b.left.toInt(), loc[1] + b.top.toInt(), loc[0] + b.right.toInt(), loc[1] + b.bottom.toInt(),
                )
            }
            .clickable(enabled = tile.pkg.isNotEmpty()) {
                rect?.let { AppTileController.setDesired(context, tile.pkg, it) }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                AndroidView(factory = { android.widget.ImageView(it).apply { setImageDrawable(icon) } },
                    modifier = Modifier.size(64.dp))
            }
            Text(label, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.kom_tile_app_hint), color = TextMuted, fontSize = 13.sp)
        }
    }

    val r = rect
    LaunchedEffect(activeNow, r, tile.pkg) {
        android.util.Log.i("KomAppTile", "tile ${tile.pkg} active=$active activeNow=$activeNow fg=$fg camera=$cameraOn rect=$r")
        // Ուրիշ հավելված/տեսախցիկ/էջի փոփոխություն՝ անմիջապես թաքցնել, ցույց տալ՝ փոքր դադարից հետո
        // (էջի swipe, չափի փոփոխություն), որ պատուհանը չթրթռա
        if (activeNow && r != null && r.width() > 0 && r.height() > 0) {
            delay(400)
            AppTileController.setDesired(context, tile.pkg, r)
        } else {
            val byLauncher = fg?.contains("launcher") == true
            AppTileController.setDesired(context, tile.pkg, null, komOnScreen = visibleOnScreen && !othersOnTop,
                covered = othersOnTop && !byLauncher)
        }
    }
    DisposableEffect(tile.pkg) {
        onDispose { AppTileController.setDesired(context, tile.pkg, null) }
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
private fun TileCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
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
