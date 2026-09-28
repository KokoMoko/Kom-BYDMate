package com.bydmate.app.ui.dashboard

import android.content.Context
import com.bydmate.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Kom-BYDMate «My Dashboard»՝ սալիկի տեսակը, անունը ⊕ ցանկում և լռելյայն չափը (վանդակներով)։ */
enum class TileType(val labelRes: Int, val defW: Int, val defH: Int) {
    WIDGET(R.string.kom_tile_widget, 4, 3),
    SOC(R.string.kom_tile_soc, 2, 2),
    RANGE(R.string.kom_tile_range, 3, 2),
    TEMPS(R.string.kom_tile_temps, 2, 2),
    APP(R.string.kom_tile_app, 6, 6),  // իսկական հավելված freeform պատուհանում (տես AppTileController)
}

/**
 * Սալիկ ցանցում՝ (x, y) վանդակից, w×h վանդակ։ WIDGET-ի համար [slot]-ը DashboardWidgets-ի
 * սլոտի բանալին է (widget id-ն և մասշտաբը պահվում են այնտեղ)։
 */
data class Tile(
    val id: String,
    val type: TileType,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val slot: String = "tile_$id",
    /** APP սալիկի հավելվածի փաթեթը (օր․ ru.yandex.yandexnavi)։ */
    val pkg: String = "",
) {
    fun overlaps(o: Tile): Boolean =
        x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h

    fun fits(cols: Int, rows: Int): Boolean = x >= 0 && y >= 0 && w >= 1 && h >= 1 && x + w <= cols && y + h <= rows
}

object MyDashboardStore {
    const val COLS = 12
    const val ROWS = 8  // 12×8 (նախկինում 12×6․ հին դասավորությունները load()-ում փոխարկվում են)
    const val LAYOUT_FULL = "full"
    private const val PREFS = "kom_my_dashboard"

    /**
     * Լռելյայն դասավորություն․ վերևում SOC | պաշար | ջերմաստիճան | Phone, ներքևում Music | եղանակ։
     * Widget սալիկները օգտագործում են Classic Главная-ի արդեն կարգավորված սլոտները։
     */
    const val NAVI_PKG = "ru.yandex.yandexnavi"
    private const val LAYOUT_VERSION = 2

    /**
     * Լռելյայն դասավորություն․ ձախում (4 սյուն) պաշար | ջերմաստիճան, Phone, Music widget-ները,
     * աջում (8 սյուն, ամբողջ բարձրությամբ) Yandex Navigator-ը՝ ֆիքսված սալիկ։
     */
    fun defaultFull(): List<Tile> = listOf(
        Tile("range", TileType.RANGE, 0, 0, 2, 2),
        Tile("temps", TileType.TEMPS, 2, 0, 2, 2),
        Tile("phone", TileType.WIDGET, 0, 2, 4, 2, slot = DashboardWidgets.SLOT_PHONE),
        Tile("music", TileType.WIDGET, 0, 4, 4, 4, slot = DashboardWidgets.SLOT_LEFT),
        Tile("navi", TileType.APP, 4, 0, 8, 8, pkg = NAVI_PKG),
    )

    fun load(ctx: Context, layout: String = LAYOUT_FULL): List<Tile> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Տարբերակ 2՝ նոր լռելյայն (widget-ներ ձախում + ֆիքսված Yandex Navigator աջում)
        if (p.getInt("${layout}_ver", 1) < LAYOUT_VERSION) {
            val d = defaultFull()
            save(ctx, d, layout)
            return d
        }
        val raw = p.getString(layout, null) ?: return defaultFull()
        // Քանի տողով է պահվել (մինչև 12×8՝ 6)․ տարբերվելու դեպքում y/h-ը փոխարկում ենք համամասնորեն
        val savedRows = p.getInt("${layout}_rows", 6)
        return runCatching {
            val arr = JSONArray(raw)
            val tiles = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                var y = o.getInt("y")
                var h = o.getInt("h")
                if (savedRows != ROWS) {
                    val top = Math.round(y * ROWS / savedRows.toFloat())
                    val bottom = Math.round((y + h) * ROWS / savedRows.toFloat())
                    y = top
                    h = (bottom - top).coerceAtLeast(1)
                }
                Tile(
                    id = o.getString("id"),
                    type = TileType.valueOf(o.getString("type")),
                    x = o.getInt("x"), y = y, w = o.getInt("w"), h = h,
                    slot = o.optString("slot", "tile_" + o.getString("id")),
                    pkg = o.optString("pkg", ""),
                )
            }.filter { it.fits(COLS, ROWS) }
            if (savedRows != ROWS) save(ctx, tiles, layout)
            tiles
        }.getOrElse { defaultFull() }
    }

    fun save(ctx: Context, tiles: List<Tile>, layout: String = LAYOUT_FULL) {
        val arr = JSONArray()
        tiles.forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type.name)
                .put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h).put("slot", it.slot)
                .put("pkg", it.pkg))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(layout, arr.toString())
            .putInt("${layout}_rows", ROWS)
            .putInt("${layout}_ver", LAYOUT_VERSION)
            .apply()
    }

    fun reset(ctx: Context, layout: String = LAYOUT_FULL) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(layout).remove("${layout}_rows").apply()

    /** Առաջին ազատ տեղը w×h սալիկի համար (վերևից ներքև, ձախից աջ)․ null, եթե տեղ չկա։ */
    fun findFreeSpot(tiles: List<Tile>, w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..ROWS - h) for (x in 0..COLS - w) {
            val probe = Tile("probe", TileType.WIDGET, x, y, w, h)
            if (tiles.none { it.overlaps(probe) }) return x to y
        }
        return null
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** Փոփոխված սալիկը թույլատրելի է, եթե ցանցի մեջ է և չի ծածկում մյուսներին։ */
    fun isValid(tiles: List<Tile>, t: Tile): Boolean =
        t.fits(COLS, ROWS) && tiles.none { it.id != t.id && it.overlaps(t) }
}
