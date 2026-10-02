package com.bydmate.app.ui.car

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.dashboard.KomPrefs
import com.bydmate.app.ui.dashboard.drawPlate
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Application → «My car»: which car is drawn on the cluster and dashboard road, in
 * which paint, plus capturing one's own car from DiLink's 3D model and passing car packs around.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarSettingsBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = remember { CarPacks.language(context) }
    val version by CarPacks.version.collectAsState()
    val packs = remember(version) { CarPacks.packs(context) }
    var packId by remember { mutableStateOf(CarPacks.selectedPackId(context)) }
    var colorId by remember { mutableStateOf(CarPacks.selectedColorId(context)) }
    val pack = packs.firstOrNull { it.id == packId } ?: packs.firstOrNull()
    val look by CarPacks.appearance.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var showCaptureHelp by remember { mutableStateOf(false) }
    var downloadsPick by remember { mutableStateOf<List<java.io.File>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { CarPacks.ensure(context) }
    // Follow selections made elsewhere (a freshly captured or imported car selects itself).
    LaunchedEffect(look?.key) {
        look?.key?.split('/')?.takeIf { it.size == 2 }?.let { (p, c) -> packId = p; colorId = c }
    }

    fun choose(id: String, color: String) {
        packId = id; colorId = color
        CarPacks.select(context, id, color)
    }

    val importedText = stringResource(R.string.kom_car_imported)
    val sharedText = stringResource(R.string.kom_car_shared)
    val importFailedText = stringResource(R.string.kom_car_import_failed)
    val shareFailedText = stringResource(R.string.kom_car_share_failed)
    val noPickerText = stringResource(R.string.kom_car_import_no_picker)
    fun imported(p: CarPack?) {
        message = if (p == null) importFailedText else String.format(importedText, CarPacks.name(p.names, lang))
        if (p != null) choose(p.id, CarPacks.ORIGINAL)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { imported(withContext(Dispatchers.IO) { CarPackIo.import(context, uri) }) }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.kom_car_title), color = TextPrimary, fontSize = 16.sp)
            Text(stringResource(R.string.kom_car_hint), color = TextMuted, fontSize = 13.sp)

            // Preview: the car exactly as the road draws it, plate included.
            val plateText = remember { KomPrefs.plate(context) }
            Canvas(
                Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A2248), Color(0xFF061633)))),
            ) {
                val l = look ?: return@Canvas
                val img = l.car
                val h = size.height * 0.88f
                val w = h * img.width / img.height
                val x = (size.width - w) / 2; val y = (size.height - h) / 2
                drawImage(img, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                l.plate?.let { drawPlate(plateText, x, y, w / img.width, it) }
            }

            Text(stringResource(R.string.kom_car_model), color = TextSecondary, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                packs.forEach { p ->
                    FilterChip(selected = p.id == pack?.id, onClick = { choose(p.id, CarPacks.ORIGINAL) },
                        label = { Text(CarPacks.name(p.names, lang) + if (p.builtIn) "" else " ✎", fontSize = 13.sp) })
                }
            }

            if (pack != null) {
                val colors = CarPacks.colorsOf(pack)
                val selected = colors.firstOrNull { it.id == colorId }
                Text(stringResource(R.string.kom_car_color) + ": " + (selected?.let { c ->
                    CarPacks.name(c.names, lang) + (c.names["zh"]?.takeIf { lang != "zh" }?.let { " · $it" } ?: "")
                } ?: stringResource(R.string.kom_car_original)), color = TextSecondary, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch(null, colorId == CarPacks.ORIGINAL) { choose(pack.id, CarPacks.ORIGINAL) }
                    colors.forEach { c -> Swatch(Color(0xFF000000.toInt() or c.rgb), colorId == c.id) { choose(pack.id, c.id) } }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showCaptureHelp = true }) { Text(stringResource(R.string.kom_car_capture)) }
                if (pack != null) OutlinedButton(onClick = {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { CarPackIo.export(context, pack) }
                        message = if (f != null) String.format(sharedText, f.path) else shareFailedText
                    }
                }) { Text(stringResource(R.string.kom_car_share)) }
                // Download first: DiLink's file picker is often missing or awkward; it stays one tap away.
                OutlinedButton(onClick = { downloadsPick = CarPackIo.downloads() }) { Text(stringResource(R.string.kom_car_import)) }
                if (pack != null && !pack.builtIn) OutlinedButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.kom_car_delete))
                }
            }
            Text(stringResource(R.string.kom_car_share_hint), color = TextMuted, fontSize = 12.sp)
        }
    }

    if (showCaptureHelp) AlertDialog(
        onDismissRequest = { showCaptureHelp = false },
        title = { Text(stringResource(R.string.kom_car_capture)) },
        text = { Text(stringResource(R.string.kom_car_capture_hint)) },
        confirmButton = { TextButton(onClick = { showCaptureHelp = false; CarCapture.start(context) }) { Text(stringResource(R.string.kom_car_next)) } },
        dismissButton = { TextButton(onClick = { showCaptureHelp = false }) { Text(stringResource(R.string.kom_cancel)) } },
    )

    downloadsPick?.let { files ->
        AlertDialog(
            onDismissRequest = { downloadsPick = null },
            title = { Text(stringResource(R.string.kom_car_import)) },
            text = {
                Column {
                    if (files.isEmpty()) Text(stringResource(R.string.kom_car_import_none), color = TextMuted)
                    files.forEach { f ->
                        Text(f.name, color = AccentGreen, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable {
                            downloadsPick = null
                            scope.launch { imported(withContext(Dispatchers.IO) { CarPackIo.import(context, f) }) }
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { downloadsPick = null }) { Text(stringResource(R.string.kom_cancel)) } },
            dismissButton = {
                TextButton(onClick = {
                    downloadsPick = null
                    val launched = runCatching { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }.isSuccess
                    if (!launched) message = noPickerText
                }) { Text(stringResource(R.string.kom_car_import_other)) }
            },
        )
    }

    if (confirmDelete && pack != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.kom_car_delete_confirm, CarPacks.name(pack.names, lang))) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            CarPacks.delete(context, pack)
            packId = CarPacks.selectedPackId(context); colorId = CarPacks.selectedColorId(context)
        }) { Text(stringResource(R.string.kom_car_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.kom_cancel)) } },
    )

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}

/** Paint swatch; [color] null is «Original» (split circle). */
@Composable
private fun Swatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) AccentGreen else Color(0x66FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            if (color != null) drawCircle(color)
            else {
                drawCircle(Color(0xFFA8A49B))
                drawArc(Color(0xFF3A3F47), startAngle = 270f, sweepAngle = 180f, useCenter = true)
            }
        }
    }
}
