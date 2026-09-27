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
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Kom-BYDMate «My Dashboard»՝ 12×6 ցանց, որտեղ օգտատերը ինքն է դասավորում սալիկները։
 * Խմբագրման ռեժիմում՝ քաշել (տեղաշարժ), ◢ (չափ), ✕ (հեռացնել), ＋ (ավելացնել)։
 */
@Composable
fun MyDashboardScreen(
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }

    var tiles by remember { mutableStateOf(MyDashboardStore.load(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val requestGrant: ((Boolean) -> Unit) -> Unit = { cb -> viewModel.grantWidgetBind(cb) }

    fun update(newTiles: List<Tile>) {
        tiles = newTiles
        MyDashboardStore.save(context, newTiles)
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
            } else {
                TextButton(onClick = { onEditingChange(true) }) {
                    Text("✏️ " + stringResource(R.string.kom_mydash_edit), color = TextSecondary)
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cellW = maxWidth / MyDashboardStore.COLS
            val cellH = maxHeight / MyDashboardStore.ROWS
            tiles.forEach { t ->
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
                    ) { mod -> MyDashboardTileContent(t, state, requestGrant, mod) }
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
