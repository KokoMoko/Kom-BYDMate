package com.bydmate.app.ui.car

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Car packs as files people can pass around: a zip of meta.json, car.png and the optional
 * brake.png / mask.png. Export saves it to Download (always reachable on DiLink) and offers the
 * system share sheet; import accepts only those four names, with size caps and image checks, so a
 * foreign archive can neither write elsewhere nor smuggle anything in.
 */
object CarPackIo {
    private const val TAG = "CarPackIo"
    const val SUFFIX = ".kcar.zip"
    private const val MAX_FILE = 8 * 1024 * 1024
    private const val MAX_SIDE = 2048

    fun zip(files: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            CarPacks.PACK_FILES.forEach { name ->
                val data = files[name] ?: return@forEach
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** Reads a pack archive; returns its files or null when it is not a valid pack. */
    fun unzip(input: InputStream): Map<String, ByteArray>? {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                // Only the base name counts and only known names are kept: no paths, no extras.
                val name = e.name.substringAfterLast('/')
                if (name !in CarPacks.PACK_FILES || name in out) continue
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = z.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE) return null
                    buf.write(chunk, 0, n)
                }
                out[name] = buf.toByteArray()
            }
        }
        if ("meta.json" !in out || "car.png" !in out) return null
        return out
    }

    /** Image dimensions of [png] or null when it does not decode / is out of bounds. */
    private fun size(png: ByteArray): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, o)
        return if (o.outWidth in 1..MAX_SIDE && o.outHeight in 1..MAX_SIDE) o.outWidth to o.outHeight else null
    }

    /** Validates an unzipped pack: parseable meta, decodable images of one size. */
    fun validate(files: Map<String, ByteArray>): Boolean = runCatching {
        CarPacks.parseMeta(String(files.getValue("meta.json")), builtIn = false)
        val car = size(files.getValue("car.png")) ?: return false
        listOf("brake.png", "mask.png").all { f -> files[f]?.let { size(it) == car } ?: true }
    }.getOrDefault(false)

    fun import(ctx: Context, uri: Uri): CarPack? = runCatching {
        val files = ctx.contentResolver.openInputStream(uri)?.use { unzip(it) } ?: return null
        if (!validate(files)) return null
        CarPacks.saveImported(ctx, files)
    }.onFailure { Log.w(TAG, "import: ${it.message}") }.getOrNull()

    fun import(ctx: Context, file: File): CarPack? = runCatching {
        val files = file.inputStream().use { unzip(it) } ?: return null
        if (!validate(files)) return null
        CarPacks.saveImported(ctx, files)
    }.onFailure { Log.w(TAG, "import: ${it.message}") }.getOrNull()

    /** Pack archives sitting in Download, newest first (import without a file picker). */
    fun downloads(): List<File> =
        File("/sdcard/Download").listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    /**
     * Writes [pack] to Download/<name>.kcar.zip and opens the share sheet when the head unit has
     * one. Returns the saved file, or null on failure.
     */
    fun export(ctx: Context, pack: CarPack): File? = runCatching {
        val data = zip(CarPacks.packFiles(ctx, pack))
        val base = CarPacks.name(pack.names, "en").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { pack.id }
        val file = File("/sdcard/Download", "kom_car_$base$SUFFIX")
        file.writeBytes(data)
        share(ctx, data, file.name)
        file
    }.onFailure { Log.w(TAG, "export: ${it.message}") }.getOrNull()

    private fun share(ctx: Context, data: ByteArray, name: String) {
        runCatching {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, name).apply { writeBytes(data) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (send.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.onFailure { Log.w(TAG, "share: ${it.message}") }
    }
}
