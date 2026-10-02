package com.bydmate.app.ui.car

import com.bydmate.app.ui.car.CarImageOps.alpha
import com.bydmate.app.ui.car.CarImageOps.argb
import com.bydmate.app.ui.car.CarImageOps.blue
import com.bydmate.app.ui.car.CarImageOps.green
import com.bydmate.app.ui.car.CarImageOps.red
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarImageOpsTest {
    private val w = 60
    private val h = 40

    /** A vertical blue gradient (the DiLink scene) with a grey "car" box and red lamps in it. */
    private fun scene(): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val inCar = x in 15..44 && y in 10..34
        val lamp = inCar && y == 18 && (x in 16..20 || x in 39..43)
        when {
            lamp -> argb(255, 220, 30, 30)
            inCar -> argb(255, 160, 158, 150)
            else -> argb(255, 20 + y, 40 + y, 90 + y * 2)
        }
    }

    @Test fun `background gradient is removed and the car is kept`() {
        val px = scene()
        val kept = CarImageOps.cutBackground(px, w, h, 28f)
        assertFalse(kept[0])
        assertFalse(kept[(h - 1) * w + w - 1])
        assertTrue(kept[20 * w + 30])
        assertEquals((30 * 25), kept.count { it })
    }

    @Test fun `content bounds trim to the car`() {
        val px = scene()
        val cut = CarImageOps.applyCut(px, w, h, CarImageOps.cutBackground(px, w, h, 28f))
        assertArrayEquals(intArrayOf(15, 10, 45, 35), CarImageOps.contentBounds(cut, w, h))
        assertEquals(128, alpha(cut[10 * w + 15]))
        assertEquals(255, alpha(cut[20 * w + 30]))
    }

    @Test fun `empty image has no bounds`() {
        assertNull(CarImageOps.contentBounds(IntArray(16), 4, 4))
    }

    @Test fun `body mask covers paint but not lamps or plate`() {
        val px = scene()
        val plate = intArrayOf(26, 28, 8, 4)
        val m = CarImageOps.bodyMask(px, w, h, plate)
        assertEquals(255, m[13 * w + 30])
        assertEquals(0, m[18 * w + 18])
        assertEquals(0, m[30 * w + 30])
        assertEquals(0, m[2 * w + 2])
    }

    @Test fun `recolor keeps alpha and shading order`() {
        val dark = argb(255, 80, 80, 80)
        val mid = argb(255, 120, 120, 120)
        val light = argb(200, 230, 230, 230)
        val out = CarImageOps.recolor(intArrayOf(dark, mid, light), intArrayOf(255, 255, 255), 0.47f, 0x3E7076)
        assertEquals(200, alpha(out[2]))
        assertTrue(blue(out[0]) < blue(out[1]) && blue(out[1]) < blue(out[2]))
        assertTrue(green(out[1]) > red(out[1]))
    }

    @Test fun `recolor leaves unmasked pixels alone`() {
        val c = argb(255, 10, 200, 30)
        assertEquals(c, CarImageOps.recolor(intArrayOf(c), intArrayOf(0), 0.4f, 0xFF0000)[0])
    }

    @Test fun `brake layer lights only red lamps`() {
        val out = CarImageOps.brakeLayer(intArrayOf(argb(255, 220, 30, 30), argb(255, 160, 158, 150), 0))
        assertEquals(255, red(out[0]))
        assertEquals(0, out[1])
        assertEquals(0, out[2])
    }

    @Test fun `default plate sits centred near the bottom`() {
        val p = CarImageOps.defaultPlate(520, 464)
        assertEquals(520 / 2, p[0] + p[2] / 2, 3)
        assertTrue(p[1] > 464 * 0.75)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertTrue("expected $expected±$delta, was $actual", kotlin.math.abs(expected - actual) <= delta)
}
