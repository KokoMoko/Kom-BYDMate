package com.bydmate.app.ui.car

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/** Image work of the editor: crop + cut, then the finished pack. Blocking; call off Main. */
object CarCutProcess {
    /** Widest stored car image; DiLink screenshots give ~550 px, the built-in pack is 520. */
    private const val MAX_WIDTH = 560

    /** Cuts the car out of [crop] (l, t, r, b in [shot] px), trims it and caps its width. */
    fun cut(shot: Bitmap, crop: IntArray, tolerance: Float): Bitmap? {
        val l = crop[0].coerceIn(0, shot.width - 2); val t = crop[1].coerceIn(0, shot.height - 2)
        val r = crop[2].coerceIn(l + 2, shot.width); val b = crop[3].coerceIn(t + 2, shot.height)
        val w = r - l; val h = b - t
        val px = IntArray(w * h).also { shot.getPixels(it, 0, w, l, t, w, h) }
        val cut = CarImageOps.applyCut(px, w, h, CarImageOps.cutBackground(px, w, h, tolerance))
        val box = CarImageOps.contentBounds(cut, w, h) ?: return null
        val cw = box[2] - box[0]; val ch = box[3] - box[1]
        if (cw < 20 || ch < 20) return null
        val bmp = Bitmap.createBitmap(CarImageOps.crop(cut, w, box), cw, ch, Bitmap.Config.ARGB_8888)
        return if (cw <= MAX_WIDTH) bmp else Bitmap.createScaledBitmap(bmp, MAX_WIDTH, ch * MAX_WIDTH / cw, true)
    }

    /** Builds mask, brake layer and reference from [car] and stores the pack. */
    fun finish(ctx: Context, car: Bitmap, plate: IntArray, name: String): CarPack {
        val w = car.width; val h = car.height
        val px = IntArray(w * h).also { car.getPixels(it, 0, w, 0, 0, w, h) }
        val mask = CarImageOps.bodyMask(px, w, h, plate)
        val ref = CarImageOps.referenceLum(px, mask)
        val maskBmp = Bitmap.createBitmap(IntArray(w * h) { CarImageOps.argb(255, mask[it], mask[it], mask[it]) }, w, h, Bitmap.Config.ARGB_8888)
        val brake = CarImageOps.brakeLayer(px)
        val brakeBmp = if (brake.any { it != 0 }) Bitmap.createBitmap(brake, w, h, Bitmap.Config.ARGB_8888) else null
        return CarPacks.saveUserPack(ctx, name.trim().ifEmpty { "My car" }, car, brakeBmp, maskBmp, plate, ref)
    }
}

/**
 * Turns a DiLink screenshot into a car pack in three steps: frame the car, tune the background
 * removal, mark the plate and name it. [onDone] gets the saved pack, or null when cancelled.
 */
@Composable
fun CarCutEditor(shot: Bitmap, onDone: (CarPack?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shotImage = remember(shot) { shot.asImageBitmap() }
    var step by remember { mutableIntStateOf(0) }
    // Default frame: where the DiLink 5 vehicle app draws its car (right half, middle).
    var crop by remember {
        mutableStateOf(floatArrayOf(shot.width * 0.55f, shot.height * 0.28f, shot.width * 0.83f, shot.height * 0.74f))
    }
    var tolerance by remember { mutableFloatStateOf(28f) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var plate by remember { mutableStateOf<FloatArray?>(null) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Step 2 preview follows the slider with a short debounce (a cut takes ~100 ms).
    LaunchedEffect(step, tolerance) {
        if (step < 1) return@LaunchedEffect
        delay(200)
        val c = crop.map { it.toInt() }.toIntArray()
        val r = withContext(Dispatchers.Default) { CarCutProcess.cut(shot, c, tolerance) }
        result = r
        if (r != null && plate == null) plate = CarImageOps.defaultPlate(r.width, r.height).let {
            floatArrayOf(it[0].toFloat(), it[1].toFloat(), (it[0] + it[2]).toFloat(), (it[1] + it[3]).toFloat())
        }
    }

    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.fillMaxSize().background(CardSurface).padding(16.dp)) {
            Box(Modifier.weight(0.68f).fillMaxHeight().background(Color(0xFF0C1626))) {
                when (step) {
                    // A new frame means a new image: the plate box is placed again for it.
                    0 -> RectEditor(shotImage, crop, Color(0xFF4ADE80)) { crop = it; plate = null }
                    1 -> result?.let { r -> FitImage(remember(r) { r.asImageBitmap() }) }
                    else -> result?.let { r ->
                        val img = remember(r) { r.asImageBitmap() }
                        plate?.let { p -> RectEditor(img, p, Color.White) { plate = it } }
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
            Column(Modifier.weight(0.32f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.kom_car_editor_title), color = TextPrimary, fontSize = 22.sp)
                when (step) {
                    0 -> Text(stringResource(R.string.kom_car_step_crop), color = TextPrimary, fontSize = 15.sp)
                    1 -> {
                        Text(stringResource(R.string.kom_car_step_cut), color = TextPrimary, fontSize = 15.sp)
                        Text(stringResource(R.string.kom_car_sensitivity, tolerance.toInt()), color = TextMuted, fontSize = 14.sp)
                        Slider(value = tolerance, onValueChange = { tolerance = it }, valueRange = 8f..80f)
                        if (result == null) Text(stringResource(R.string.kom_car_cut_empty), color = TextMuted, fontSize = 13.sp)
                    }
                    else -> {
                        Text(stringResource(R.string.kom_car_step_plate), color = TextPrimary, fontSize = 15.sp)
                        OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                            label = { Text(stringResource(R.string.kom_car_name)) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (step == 0) onDone(null) else step-- }) {
                        Text(stringResource(if (step == 0) R.string.kom_cancel else R.string.kom_car_back))
                    }
                    Button(
                        enabled = !busy && (step == 0 || result != null),
                        onClick = {
                            if (step < 2) { step++; return@Button }
                            val r = result ?: return@Button
                            val p = plate ?: return@Button
                            busy = true
                            scope.launch {
                                val box = intArrayOf(p[0].toInt(), p[1].toInt(), (p[2] - p[0]).toInt(), (p[3] - p[1]).toInt())
                                val pack = withContext(Dispatchers.Default) { runCatching { CarCutProcess.finish(context, r, box, name) }.getOrNull() }
                                busy = false
                                onDone(pack)
                            }
                        },
                    ) { Text(stringResource(if (step < 2) R.string.kom_car_next else R.string.kom_car_save)) }
                }
            }
        }
    }
}

/** Image fitted into the box, centred (aspect kept). */
@Composable
private fun FitImage(img: ImageBitmap) {
    Canvas(Modifier.fillMaxSize()) {
        val s = minOf(size.width / img.width, size.height / img.height)
        val w = img.width * s; val h = img.height * s
        drawImage(img, dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()))
    }
}

/**
 * [img] fitted into the box with an editable rectangle [rect] (l, t, r, b in image px): drag a
 * corner to resize, drag inside to move. Everything outside the rectangle is dimmed.
 */
@Composable
private fun RectEditor(img: ImageBitmap, rect: FloatArray, color: Color, onRect: (FloatArray) -> Unit) {
    val current by rememberUpdatedState(rect)
    val emit by rememberUpdatedState(onRect)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val s = minOf(bw / img.width, bh / img.height)
        val ox = (bw - img.width * s) / 2; val oy = (bh - img.height * s) / 2
        Canvas(
            Modifier.fillMaxSize().pointerInput(img) {
                var mode = -1 // 0..3 corner (lt, rt, rb, lb), 4 move
                detectDragGestures(
                    onDragStart = { pos ->
                        val r = current
                        val corners = listOf(r[0] to r[1], r[2] to r[1], r[2] to r[3], r[0] to r[3])
                            .map { (x, y) -> Offset(ox + x * s, oy + y * s) }
                        val near = corners.indices.minByOrNull { hypot(corners[it].x - pos.x, corners[it].y - pos.y) }!!
                        mode = when {
                            hypot(corners[near].x - pos.x, corners[near].y - pos.y) < 48f -> near
                            pos.x in ox + r[0] * s..ox + r[2] * s && pos.y in oy + r[1] * s..oy + r[3] * s -> 4
                            else -> -1
                        }
                    },
                    onDrag = { change, d ->
                        if (mode < 0) return@detectDragGestures
                        change.consume()
                        val dx = d.x / s; val dy = d.y / s
                        val r = current.copyOf()
                        val minSide = 16f
                        when (mode) {
                            0 -> { r[0] += dx; r[1] += dy }
                            1 -> { r[2] += dx; r[1] += dy }
                            2 -> { r[2] += dx; r[3] += dy }
                            3 -> { r[0] += dx; r[3] += dy }
                            4 -> {
                                val mx = dx.coerceIn(-r[0], img.width - r[2]); val my = dy.coerceIn(-r[1], img.height - r[3])
                                r[0] += mx; r[2] += mx; r[1] += my; r[3] += my
                            }
                        }
                        r[0] = r[0].coerceIn(0f, img.width.toFloat()); r[2] = r[2].coerceIn(0f, img.width.toFloat())
                        r[1] = r[1].coerceIn(0f, img.height.toFloat()); r[3] = r[3].coerceIn(0f, img.height.toFloat())
                        if (r[2] - r[0] >= minSide && r[3] - r[1] >= minSide && abs(r[2] - r[0]) > 0) emit(r)
                    },
                )
            },
        ) {
            drawImage(img, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize((img.width * s).toInt(), (img.height * s).toInt()))
            val l = ox + rect[0] * s; val t = oy + rect[1] * s; val r = ox + rect[2] * s; val b = oy + rect[3] * s
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(0f, 0f), Size(size.width, t))
            drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
            drawRect(dim, Offset(0f, t), Size(l, b - t))
            drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
            drawRect(color, Offset(l, t), Size(r - l, b - t), style = Stroke(3f))
            listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b)).forEach { drawCircle(color, 12f, it) }
        }
    }
}


/**
 * App-level host of the capture flow: the screenshot comes back while our app is brought to the
 * front, on whichever screen it was left, so the editor and capture errors live at the root.
 */
@Composable
fun CarCaptureHost() {
    val context = LocalContext.current
    val shot by CarCapture.screenshot.collectAsState()
    val error by CarCapture.error.collectAsState()
    shot?.let { bmp ->
        CarCutEditor(bmp) { saved ->
            CarCapture.clearScreenshot()
            if (saved != null) CarPacks.select(context, saved.id, CarPacks.ORIGINAL)
        }
    }
    error?.let { res ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { CarCapture.clearError() },
            text = { Text(stringResource(res)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { CarCapture.clearError() }) { Text("OK") } },
        )
    }
}
