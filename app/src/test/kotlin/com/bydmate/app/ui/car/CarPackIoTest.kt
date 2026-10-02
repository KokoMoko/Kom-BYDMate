package com.bydmate.app.ui.car

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarPackIoTest {
    private val meta = """{"format":1,"id":"user_1","name":{"en":"Song Plus"},"plate":[10,20,30,8],
        "colors":[{"id":"w","rgb":"#F2F4F6","name":{"en":"White","zh":"白"}}]}"""

    private fun png(w: Int, h: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun rawZip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z -> entries.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
    }.toByteArray()

    @Test fun `zip round trip keeps the pack files`() {
        val files = mapOf("meta.json" to meta.toByteArray(), "car.png" to png(40, 30), "mask.png" to png(40, 30))
        val back = CarPackIo.unzip(ByteArrayInputStream(CarPackIo.zip(files)))!!
        assertEquals(files.keys, back.keys)
        assertTrue(CarPackIo.validate(back))
    }

    @Test fun `paths are stripped and unknown files ignored`() {
        val z = rawZip(listOf("../../evil/meta.json" to meta.toByteArray(), "x/car.png" to png(4, 4), "run.sh" to "x".toByteArray()))
        val files = CarPackIo.unzip(ByteArrayInputStream(z))!!
        assertEquals(setOf("meta.json", "car.png"), files.keys)
    }

    @Test fun `archive without car image is rejected`() {
        assertNull(CarPackIo.unzip(ByteArrayInputStream(rawZip(listOf("meta.json" to meta.toByteArray())))))
    }

    @Test fun `mask of another size fails validation`() {
        val files = mapOf("meta.json" to meta.toByteArray(), "car.png" to png(40, 30), "mask.png" to png(20, 30))
        assertFalse(CarPackIo.validate(files))
    }

    @Test fun `bad id fails validation`() {
        val files = mapOf("meta.json" to meta.replace("user_1", "../x").toByteArray(), "car.png" to png(4, 4))
        assertFalse(CarPackIo.validate(files))
    }

    @Test fun `meta parses names plate and colours`() {
        val p = CarPacks.parseMeta(meta, builtIn = false)
        assertEquals("Song Plus", CarPacks.name(p.names, "hy"))
        assertEquals(listOf(10, 20, 30, 8), p.plate!!.toList())
        assertEquals(0xF2F4F6, p.colors.single().rgb)
        assertEquals("白", p.colors.single().names["zh"])
        assertNotNull(p)
    }

    @Test fun `built-in pack meta is valid`() {
        val text = java.io.File("src/main/assets/cars/sealion06/meta.json").readText()
        val p = CarPacks.parseMeta(text, builtIn = true)
        assertEquals("sealion06", p.id)
        assertEquals(6, p.colors.size)
        assertEquals(listOf(185, 378, 146, 40), p.plate!!.toList())
    }
}
