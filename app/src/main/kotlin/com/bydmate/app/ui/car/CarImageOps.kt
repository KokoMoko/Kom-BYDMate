package com.bydmate.app.ui.car

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pixel work behind the "My car" images, on plain ARGB [IntArray]s (non-premultiplied, row-major)
 * so it runs and is tested without Android.
 *
 * The pipeline for a captured car: [cutBackground] keeps what is not the DiLink 3D scene's
 * vertical gradient, [cropToContent] trims the margins, [bodyMask] finds the paint for recolouring
 * and [brakeLayer] lights up the tail lights. The same [bodyMask] rule built the Sealion 06 pack
 * (tools/make_car_mask.py), and [recolor] applies a paint colour through any pack's mask.
 */
object CarImageOps {

    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** Relative luminance 0..1 (Rec. 601 weights, as the offline tool). */
    fun lum(c: Int) = (0.299f * red(c) + 0.587f * green(c) + 0.114f * blue(c)) / 255f

    /** HSV-style saturation 0..1. */
    fun sat(c: Int): Float {
        val mx = max(red(c), max(green(c), blue(c)))
        val mn = min(red(c), min(green(c), blue(c)))
        return if (mx == 0) 0f else (mx - mn).toFloat() / mx
    }

    private fun isRed(c: Int) = sat(c) > 0.3f && red(c) > green(c) * 1.3f

    /**
     * Paints [target] (0xRRGGBB) through [mask] (0..255 per pixel). Shading survives: up to the
     * paint's reference luminance [refLum] the colour is scaled by the pixel's own luminance, above
     * it highlights blend toward white, so reflections and edges keep their look on any colour.
     */
    fun recolor(pixels: IntArray, mask: IntArray, refLum: Float, target: Int): IntArray {
        val tr = red(target) / 255f
        val tg = green(target) / 255f
        val tb = blue(target) / 255f
        val ref = refLum.coerceIn(0.05f, 0.95f)
        val out = pixels.copyOf()
        for (i in pixels.indices) {
            val m = mask[i]
            if (m <= 0) continue
            val c = pixels[i]
            val l = lum(c)
            fun ch(t: Float): Float = if (l <= ref) t * (l / ref)
                else t + (1 - t) * ((l - ref) / (1 - ref)).coerceIn(0f, 1f).pow(1.2f) * 0.85f
            val k = m / 255f
            fun mix(orig: Int, t: Float) = (orig * (1 - k) + ch(t).coerceIn(0f, 1f) * 255f * k).toInt().coerceIn(0, 255)
            out[i] = argb(alpha(c), mix(red(c), tr), mix(green(c), tg), mix(blue(c), tb))
        }
        return out
    }

    /**
     * Separates the car from the DiLink 3D scene inside a crop. The scene behind the car is a
     * vertical gradient, so each row's background colour is the median of [EDGE] pixels at both
     * crop edges; from the crop border a flood fill removes every connected pixel closer than
     * [tolerance] (RGB distance) to its row's background. The car itself never touches the border
     * of a sensible crop, so nothing of it is reached. Returns true for kept (car) pixels.
     */
    fun cutBackground(pixels: IntArray, w: Int, h: Int, tolerance: Float): BooleanArray {
        val bgR = IntArray(h); val bgG = IntArray(h); val bgB = IntArray(h)
        val e = min(EDGE, max(1, w / 4))
        val buf = IntArray(e * 2)
        for (y in 0 until h) {
            for (i in 0 until e) { buf[i] = pixels[y * w + i]; buf[e + i] = pixels[y * w + w - 1 - i] }
            bgR[y] = median(buf) { red(it) }; bgG[y] = median(buf) { green(it) }; bgB[y] = median(buf) { blue(it) }
        }
        val tol2 = tolerance * tolerance
        fun bgLike(i: Int): Boolean {
            val y = i / w
            val c = pixels[i]
            val dr = (red(c) - bgR[y]).toFloat(); val dg = (green(c) - bgG[y]).toFloat(); val db = (blue(c) - bgB[y]).toFloat()
            return dr * dr + dg * dg + db * db < tol2
        }
        val removed = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun push(i: Int) { if (!removed[i] && bgLike(i)) { removed[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return BooleanArray(w * h) { !removed[it] }
    }

    private inline fun median(buf: IntArray, f: (Int) -> Int): Int {
        val v = IntArray(buf.size) { f(buf[it]) }
        v.sort()
        return v[v.size / 2]
    }

    /** Applies [kept] as alpha with a one-pixel soft edge; removed pixels become transparent. */
    fun applyCut(pixels: IntArray, w: Int, h: Int, kept: BooleanArray): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!kept[i]) continue
            // Edge pixels (a removed 4-neighbour) get half alpha so the outline is not jagged.
            val edge = (x > 0 && !kept[i - 1]) || (x < w - 1 && !kept[i + 1]) ||
                (y > 0 && !kept[i - w]) || (y < h - 1 && !kept[i + w])
            val c = pixels[i]
            out[i] = argb(if (edge) 128 else 255, red(c), green(c), blue(c))
        }
        return out
    }

    /** Smallest box holding every non-transparent pixel: [left, top, right, bottom) or null. */
    fun contentBounds(pixels: IntArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (alpha(pixels[y * w + x]) > 0) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r + 1, b + 1)
    }

    fun crop(pixels: IntArray, w: Int, box: IntArray): IntArray {
        val cw = box[2] - box[0]; val ch = box[3] - box[1]
        return IntArray(cw * ch) { pixels[(box[1] + it / cw) * w + box[0] + it % cw] }
    }

    /**
     * Paint mask 0..255: opaque, low-saturation pixels bright enough not to be glass or black
     * trim, without the plate box [plate] (l, t, w, h) and without anything red (lights, logo).
     * A 3x3 close then open smooths seams and drops specks; the result is lightly blurred.
     */
    fun bodyMask(pixels: IntArray, w: Int, h: Int, plate: IntArray?): IntArray {
        var m = BooleanArray(w * h) { i ->
            val c = pixels[i]
            val x = i % w; val y = i / w
            val inPlate = plate != null && x >= plate[0] - 5 && x <= plate[0] + plate[2] + 5 &&
                y >= plate[1] - 6 && y <= plate[1] + plate[3] + 6
            alpha(c) > 127 && sat(c) < 0.25f && lum(c) > 0.20f && !inPlate
        }
        m = erode(dilate(m, w, h), w, h)
        m = dilate(erode(m, w, h), w, h)
        val red = dilate(dilate(BooleanArray(w * h) { isRed(pixels[it]) }, w, h), w, h)
        val hard = IntArray(w * h) { if (m[it] && !red[it]) 255 else 0 }
        val soft = blur3(hard, w, h)
        return IntArray(w * h) { soft[it] * alpha(pixels[it]) / 255 }
    }

    /** Median luminance of the masked paint: the recolour reference. */
    fun referenceLum(pixels: IntArray, mask: IntArray): Float {
        val l = ArrayList<Float>()
        for (i in pixels.indices) if (mask[i] > 200) l += lum(pixels[i])
        if (l.isEmpty()) return 0.4f
        l.sort()
        return l[l.size / 2]
    }

    /** Brake-light overlay: the red lamp pixels, brightened; everything else transparent. */
    fun brakeLayer(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val c = pixels[i]
        if (alpha(c) > 127 && isRed(c) && red(c) > 90) {
            argb(255, 255, min(255, green(c) / 2 + 30), min(255, blue(c) / 2 + 30))
        } else 0
    }

    /** Default plate box for a rear view: centred, near the bottom (user adjusts it). */
    fun defaultPlate(w: Int, h: Int) = intArrayOf((w * 0.36f).toInt(), (h * 0.81f).toInt(), (w * 0.28f).toInt(), (h * 0.087f).toInt())

    private fun dilate(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        var any = false
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && m[ny * w + nx]) { any = true }
        }
        any
    }

    private fun erode(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        var all = true
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && !m[ny * w + nx]) { all = false }
        }
        all
    }

    private fun blur3(v: IntArray, w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        var s = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until w && ny in 0 until h) { s += v[ny * w + nx]; n++ }
        }
        s / n
    }

    /** Pixels sampled at each crop edge to estimate a row's background. */
    private const val EDGE = 6

    /** Euclidean RGB distance, exposed for tests. */
    fun distance(a: Int, b: Int): Float {
        val dr = (red(a) - red(b)).toFloat(); val dg = (green(a) - green(b)).toFloat(); val db = (blue(a) - blue(b)).toFloat()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
