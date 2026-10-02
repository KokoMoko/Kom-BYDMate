package com.bydmate.app.ui.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bydmate.app.util.appLocalizedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** One paint colour of a car pack; [names] by language code ("en", "ru", "hy", "zh"). */
data class CarColor(val id: String, val rgb: Int, val names: Map<String, String>)

/**
 * A car image pack: `car.png` (rear view, transparent background), optional `brake.png` (lit
 * brake lights, same size), optional `mask.png` (paint mask for recolouring, grey 0..255) and
 * `meta.json`. Built-in packs live in assets/cars/<id>/, captured and imported ones in
 * filesDir/cars/<id>/ — the same format, so a community pack moves into assets unchanged.
 */
data class CarPack(
    val id: String,
    val names: Map<String, String>,
    /** Plate box in car.png pixels: left, top, width, height; null = no plate drawn. */
    val plate: IntArray?,
    val refLum: Float?,
    val colors: List<CarColor>,
    val builtIn: Boolean,
    val author: String?,
    val dilink: String?,
)

/** The car as it is drawn right now: chosen pack in the chosen colour. */
class CarAppearance(val car: ImageBitmap, val brake: ImageBitmap?, val plate: IntArray?, val key: String)

object CarPacks {
    private const val TAG = "CarPacks"
    const val DEFAULT_ID = "sealion06"
    const val ORIGINAL = "original"
    private const val DIR = "cars"
    private const val PREFS = "kom_prefs"
    private const val KEY_PACK = "car_pack"
    private const val KEY_COLOR = "car_color"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _appearance = MutableStateFlow<CarAppearance?>(null)
    val appearance: StateFlow<CarAppearance?> = _appearance

    /** Bumped whenever a pack is added or deleted, so lists recompose. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** Colours offered for packs that ship none (captured or imported cars). */
    val genericColors = listOf(
        CarColor("white", 0xF2F4F6, mapOf("en" to "White", "ru" to "Белый", "hy" to "Սպիտակ")),
        CarColor("black", 0x26282C, mapOf("en" to "Black", "ru" to "Чёрный", "hy" to "Սև")),
        CarColor("silver", 0xBCBFC2, mapOf("en" to "Silver", "ru" to "Серебристый", "hy" to "Արծաթագույն")),
        CarColor("grey", 0x70757A, mapOf("en" to "Grey", "ru" to "Серый", "hy" to "Մոխրագույն")),
        CarColor("blue", 0x2E5490, mapOf("en" to "Blue", "ru" to "Синий", "hy" to "Կապույտ")),
        CarColor("red", 0x962024, mapOf("en" to "Red", "ru" to "Красный", "hy" to "Կարմիր")),
        CarColor("green", 0x466E5A, mapOf("en" to "Green", "ru" to "Зелёный", "hy" to "Կանաչ")),
    )

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun userRoot(ctx: Context) = File(ctx.applicationContext.filesDir, DIR)

    fun packs(ctx: Context): List<CarPack> {
        val app = ctx.applicationContext
        val builtIn = runCatching { app.assets.list(DIR)?.toList().orEmpty() }.getOrDefault(emptyList())
            .mapNotNull { id -> runCatching { app.assets.open("$DIR/$id/meta.json").use { parseMeta(it.reader().readText(), true) } }.getOrNull() }
        val user = userRoot(app).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
            .mapNotNull { d -> runCatching { parseMeta(File(d, "meta.json").readText(), false) }.getOrNull() }
        return builtIn + user
    }

    fun find(ctx: Context, id: String) = packs(ctx).firstOrNull { it.id == id }

    fun selectedPackId(ctx: Context): String = prefs(ctx).getString(KEY_PACK, DEFAULT_ID) ?: DEFAULT_ID
    fun selectedColorId(ctx: Context): String = prefs(ctx).getString(KEY_COLOR, ORIGINAL) ?: ORIGINAL

    /** Colours offered for [pack]: its own, else the generic set. */
    fun colorsOf(pack: CarPack): List<CarColor> = pack.colors.ifEmpty { genericColors }

    fun select(ctx: Context, packId: String, colorId: String) {
        prefs(ctx).edit().putString(KEY_PACK, packId).putString(KEY_COLOR, colorId).apply()
        refresh(ctx)
    }

    /** Loads the current appearance once; later calls are no-ops until the selection changes. */
    fun ensure(ctx: Context) {
        if (_appearance.value == null && job?.isActive != true) refresh(ctx)
    }

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        job?.cancel()
        job = scope.launch {
            val packId = selectedPackId(app)
            val pack = find(app, packId) ?: find(app, DEFAULT_ID)
            if (pack == null) { _appearance.value = null; return@launch }
            val colorId = selectedColorId(app)
            runCatching { render(app, pack, colorId) }
                .onSuccess { _appearance.value = it }
                .onFailure { Log.w(TAG, "render ${pack.id}/$colorId failed: ${it.message}") }
        }
    }

    /** Renders [pack] in [colorId] (blocking; call off the main thread). */
    fun render(ctx: Context, pack: CarPack, colorId: String): CarAppearance {
        val car = load(ctx, pack, "car.png") ?: error("car.png missing in ${pack.id}")
        val brake = load(ctx, pack, "brake.png")
        val color = colorsOf(pack).firstOrNull { it.id == colorId }
        val painted = if (color == null) car else {
            val mask = load(ctx, pack, "mask.png")
            if (mask == null || mask.width != car.width || mask.height != car.height) car else {
                val w = car.width; val h = car.height
                val px = IntArray(w * h).also { car.getPixels(it, 0, w, 0, 0, w, h) }
                val mpx = IntArray(w * h).also { mask.getPixels(it, 0, w, 0, 0, w, h) }
                // Grey mask: the red channel carries the value, alpha clips it to the car outline.
                val m = IntArray(w * h) { CarImageOps.red(mpx[it]) * CarImageOps.alpha(mpx[it]) / 255 }
                val ref = pack.refLum ?: CarImageOps.referenceLum(px, m)
                val out = CarImageOps.recolor(px, m, ref, color.rgb)
                Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            }
        }
        return CarAppearance(painted.asImageBitmap(), brake?.asImageBitmap(), pack.plate, "${pack.id}/$colorId")
    }

    private fun load(ctx: Context, pack: CarPack, file: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
        return runCatching {
            open(ctx, pack, file)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    fun open(ctx: Context, pack: CarPack, file: String): InputStream? = if (pack.builtIn) {
        runCatching { ctx.applicationContext.assets.open("$DIR/${pack.id}/$file") }.getOrNull()
    } else {
        File(File(userRoot(ctx), pack.id), file).takeIf { it.isFile }?.inputStream()
    }

    fun packFiles(ctx: Context, pack: CarPack): Map<String, ByteArray> =
        PACK_FILES.mapNotNull { f -> open(ctx, pack, f)?.use { f to it.readBytes() } }.toMap()

    fun delete(ctx: Context, pack: CarPack) {
        if (pack.builtIn) return
        File(userRoot(ctx), pack.id).deleteRecursively()
        if (selectedPackId(ctx) == pack.id) select(ctx, DEFAULT_ID, ORIGINAL)
        _version.value++
    }

    /** Stores a user pack from finished images; returns it. */
    fun saveUserPack(
        ctx: Context, name: String, car: Bitmap, brake: Bitmap?, mask: Bitmap?, plate: IntArray?, refLum: Float?,
        dilink: String? = null,
    ): CarPack {
        val id = "user_" + System.currentTimeMillis()
        val dir = File(userRoot(ctx), id).apply { mkdirs() }
        fun save(b: Bitmap, f: String) = File(dir, f).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        save(car, "car.png")
        brake?.let { save(it, "brake.png") }
        mask?.let { save(it, "mask.png") }
        val meta = JSONObject().apply {
            put("format", 1); put("id", id)
            put("name", JSONObject().put("en", name))
            plate?.let { put("plate", JSONArray(it.toList())) }
            refLum?.let { put("refLum", it.toDouble()) }
            dilink?.let { put("dilink", it) }
        }
        File(dir, "meta.json").writeText(meta.toString(2))
        _version.value++
        return parseMeta(meta.toString(), false)
    }

    /** Stores an imported pack ([files] by name, already validated); returns it. */
    fun saveImported(ctx: Context, files: Map<String, ByteArray>): CarPack {
        val id = "user_" + System.currentTimeMillis()
        val dir = File(userRoot(ctx), id).apply { mkdirs() }
        // The pack keeps its own metadata but gets a local id, so two imports never collide.
        val meta = JSONObject(String(files.getValue("meta.json"))).put("id", id)
        files.forEach { (f, bytes) -> if (f != "meta.json") File(dir, f).writeBytes(bytes) }
        File(dir, "meta.json").writeText(meta.toString(2))
        _version.value++
        return parseMeta(meta.toString(), false)
    }

    fun language(ctx: Context): String =
        runCatching { ctx.appLocalizedContext().resources.configuration.locales[0].language }.getOrDefault("en")

    fun name(names: Map<String, String>, lang: String): String =
        names[lang] ?: names["en"] ?: names.values.firstOrNull().orEmpty()

    /** Pack files, in the order a share archive lists them. */
    val PACK_FILES = listOf("meta.json", "car.png", "brake.png", "mask.png")

    internal fun parseMeta(json: String, builtIn: Boolean): CarPack {
        val o = JSONObject(json)
        fun names(n: Any?): Map<String, String> = when (n) {
            is JSONObject -> n.keys().asSequence().associateWith { n.getString(it) }
            is String -> mapOf("en" to n)
            else -> emptyMap()
        }
        val id = o.getString("id")
        require(id.matches(Regex("[A-Za-z0-9_\\-]{1,64}"))) { "bad id" }
        val plate = o.optJSONArray("plate")?.let { a -> IntArray(4) { a.getInt(it) } }
        val colors = o.optJSONArray("colors")?.let { a ->
            List(a.length()) { i ->
                val c = a.getJSONObject(i)
                CarColor(c.getString("id"), android.graphics.Color.parseColor(c.getString("rgb")) and 0xFFFFFF, names(c.opt("name")))
            }
        }.orEmpty()
        return CarPack(
            id = id, names = names(o.opt("name")).ifEmpty { mapOf("en" to id) }, plate = plate,
            refLum = if (o.has("refLum")) o.getDouble("refLum").toFloat() else null,
            colors = colors, builtIn = builtIn,
            author = o.optString("author").ifEmpty { null }, dilink = o.optString("dilink").ifEmpty { null },
        )
    }
}
