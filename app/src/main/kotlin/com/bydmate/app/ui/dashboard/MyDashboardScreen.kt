package com.bydmate.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.NavyDeep
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.bydmate.app.split.SplitStartResult

/**
 * Kom-BYDMate «My Dashboard»՝ 12×6 ցանց, որտեղ օգտատերը ինքն է դասավորում սալիկները։
 * Խմբագրման ռեժիմում՝ քաշել (տեղաշարժ), ◢ (չափ), ✕ (հեռացնել), ＋ (ավելացնել)։
 */
@Composable
fun MyDashboardScreen(
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    pageVisible: Boolean = true,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }

    var visible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, _ -> visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // Split-ում (Kom-ը 1/3 մասում) ցույց ենք տալիս միայն widget-ները՝ առանց «Application» սալիկի
    val inSplit = rememberInMultiWindow()

    var tiles by remember { mutableStateOf(MyDashboardStore.load(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val requestGrant: ((Boolean) -> Unit) -> Unit = { cb -> viewModel.grantWidgetBind(cb) }

    fun update(newTiles: List<Tile>) {
        tiles = newTiles
        MyDashboardStore.save(context, newTiles)
    }

    // «Application» սալիկ → Panel (split)՝ Kom-ը նեղ մասում, հավելվածը լայն մասում
    val appTile = tiles.firstOrNull { it.type == TileType.APP && it.pkg.isNotEmpty() }
    fun openPanel(t: Tile) {
        KomPanel.open(context, t.pkg, appOnRight = t.x * 2 + t.w >= MyDashboardStore.COLS) { r ->
            if (r != SplitStartResult.OK) message = context.getString(R.string.kom_panel_failed, r?.name ?: "error")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Վերնագիր․ MyBYD (+ էջի կետերը DashboardHost-ում) և խմբագրման կոճակները
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rememberDashboardTitle(), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (editing) {
                TextButton(onClick = { MyDashboardStore.reset(context); tiles = MyDashboardStore.load(context) }) {
                    Text(stringResource(R.string.kom_mydash_reset), color = TextSecondary)
                }
                TextButton(onClick = { showAdd = true }) {
                    Text("＋ " + stringResource(R.string.kom_mydash_add), color = AccentGreen)
                }
                Button(
                    onClick = { onEditingChange(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = NavyDark),
                ) { Text(stringResource(R.string.kom_done)) }
            } else if (!inSplit) {
                TextButton(onClick = { onEditingChange(true) }) {
                    Text("✏️ " + stringResource(R.string.kom_mydash_edit), color = TextSecondary)
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Split-ում՝ միայն ոչ-Application սալիկները, և նրանց զբաղեցրած սյուները ձգվում են ամբողջ լայնքով
            val shown = if (inSplit && !editing) tiles.filter { it.type != TileType.APP } else tiles
            val minX = if (inSplit && !editing) shown.minOfOrNull { it.x } ?: 0 else 0
            val cols = if (inSplit && !editing) ((shown.maxOfOrNull { it.x + it.w } ?: MyDashboardStore.COLS) - minX).coerceAtLeast(1)
                else MyDashboardStore.COLS
            val cellW = maxWidth / cols
            val cellH = maxHeight / MyDashboardStore.ROWS
            shown.forEach { orig ->
                val t = if (minX != 0) orig.copy(x = orig.x - minX) else orig
                key(t.id) {
                    TileBox(
                        tile = t,
                        cellW = cellW,
                        cellH = cellH,
                        editing = editing,
                        onChange = { changed ->
                            if (MyDashboardStore.isValid(tiles, changed)) update(tiles.map { if (it.id == changed.id) changed else it })
                        },
                        onDelete = {
                            // Միայն «My Dashboard»-ի սեփական սլոտն ենք մաքրում, ոչ թե Classic-ի ընդհանուրը
                            if (t.type == TileType.WIDGET && t.slot.startsWith("tile_")) DashboardWidgets.clear(context, t.slot)
                            update(tiles.filter { it.id != t.id })
                        },
                    ) { mod -> MyDashboardTileContent(orig, state, requestGrant, mod, onOpenApp = { openPanel(orig) }) }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.kom_mydash_add_title), color = TextPrimary) },
            text = {
                Column {
                    TileType.values().forEach { type ->
                        Text(
                            stringResource(type.labelRes), color = TextPrimary, fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showAdd = false
                                if (type == TileType.APP) { showAppPicker = true; return@clickable }
                                val spot = MyDashboardStore.findFreeSpot(tiles, type.defW, type.defH)
                                    ?: MyDashboardStore.findFreeSpot(tiles, 1, 1)
                                if (spot == null) {
                                    message = context.getString(R.string.kom_mydash_no_space)
                                } else {
                                    val fitsDefault = MyDashboardStore.findFreeSpot(tiles, type.defW, type.defH) != null
                                    val w = if (fitsDefault) type.defW else 1
                                    val h = if (fitsDefault) type.defH else 1
                                    update(tiles + Tile(MyDashboardStore.newId(), type, spot.first, spot.second, w, h))
                                }
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.kom_cancel)) } },
            containerColor = CardSurface,
        )
    }
    if (showAppPicker) {
        val pm = context.packageManager
        val apps = remember {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
                .map { pkg -> pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                .sortedBy { it.second.lowercase() }
        }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(stringResource(R.string.kom_tile_app_pick), color = TextPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps) { (pkg, name) ->
                        Text(
                            name, color = TextPrimary, fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showAppPicker = false
                                val type = TileType.APP
                                val fits = MyDashboardStore.findFreeSpot(tiles, type.defW, type.defH)
                                val spot = fits ?: MyDashboardStore.findFreeSpot(tiles, 1, 1)
                                if (spot == null) {
                                    message = context.getString(R.string.kom_mydash_no_space)
                                } else {
                                    val w = if (fits != null) type.defW else 1
                                    val h = if (fits != null) type.defH else 1
                                    update(tiles + Tile(MyDashboardStore.newId(), type, spot.first, spot.second, w, h, pkg = pkg))
                                }
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text(stringResource(R.string.kom_cancel)) } },
            containerColor = CardSurface,
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text, color = TextPrimary) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            containerColor = CardSurface,
        )
    }
}

/**
 * Մեկ սալիկ ցանցում։ Խմբագրման ռեժիմում վրան շերտ է․ քաշելով տեղաշարժվում է, ◢-ով փոխվում է
 * չափը․ բաց թողնելիս «կպչում» է մոտակա վանդակին։ Սխալ (ծածկող) դիրքը չի ընդունվում։
 */
@Composable
private fun TileBox(
    tile: Tile,
    cellW: Dp,
    cellH: Dp,
    editing: Boolean,
    onChange: (Tile) -> Unit,
    onDelete: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val cellWpx = with(density) { cellW.toPx() }
    val cellHpx = with(density) { cellH.toPx() }
    var drag by remember(tile) { mutableStateOf(Offset.Zero) }
    var resize by remember(tile) { mutableStateOf(Offset.Zero) }

    val baseX = cellWpx * tile.x
    val baseY = cellHpx * tile.y
    val width = with(density) { (cellWpx * tile.w + resize.x).coerceAtLeast(cellWpx * 0.6f).toDp() }
    val height = with(density) { (cellHpx * tile.h + resize.y).coerceAtLeast(cellHpx * 0.6f).toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset((baseX + drag.x).roundToInt(), (baseY + drag.y).roundToInt()) }
            .size(width, height)
            .padding(4.dp)
    ) {
        content(Modifier.fillMaxSize())
        if (editing) {
            val shape = RoundedCornerShape(12.dp)
            // Քաշելու շերտ՝ նաև փակում է widget-ի հպումները խմբագրման ժամանակ
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(AccentGreen.copy(alpha = 0.08f))
                    .border(2.dp, AccentGreen, shape)
                    .pointerInput(tile) {
                        detectDragGestures(
                            onDragEnd = {
                                val nx = tile.x + (drag.x / cellWpx).roundToInt()
                                val ny = tile.y + (drag.y / cellHpx).roundToInt()
                                drag = Offset.Zero
                                onChange(tile.copy(x = nx, y = ny))
                            },
                            onDragCancel = { drag = Offset.Zero },
                        ) { change, amount -> change.consume(); drag += amount }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SocRed)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = TextPrimary, fontSize = 16.sp) }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(44.dp)
                    .pointerInput(tile) {
                        detectDragGestures(
                            onDragEnd = {
                                val nw = tile.w + (resize.x / cellWpx).roundToInt()
                                val nh = tile.h + (resize.y / cellHpx).roundToInt()
                                resize = Offset.Zero
                                onChange(tile.copy(w = nw.coerceAtLeast(1), h = nh.coerceAtLeast(1)))
                            },
                            onDragCancel = { resize = Offset.Zero },
                        ) { change, amount -> change.consume(); resize += amount }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("◢", color = AccentGreen, fontSize = 28.sp) }
        }
    }
}

/** «MyBYD ● ○»՝ էջի կետեր (սեղմելով էլ է փոխվում)։ */
@Composable
fun DashboardPageDots(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(26.dp)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (i == current) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == current) AccentGreen else TextSecondary.copy(alpha = 0.5f))
                )
            }
            if (i < count - 1) Spacer(Modifier.width(2.dp))
        }
    }
}

/** Panel-ի ինքնաբերաբար բացումը՝ մեկ անգամ ծրագրի պրոցեսի ընթացքում։ */
internal object KomPanelAuto {
    @Volatile var done = false
}
