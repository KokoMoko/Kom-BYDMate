package com.bydmate.app.navdata

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Kom-BYDMate: Navigator-ի արագության սահմանափակումը՝ նաև ԱՌԱՆՑ երթուղու («ազատ ընթացք»)։
 *
 * [NavA11yExtractor]-ը սահմանափակումը վերցնում է միայն ուղեցույցի ժամանակ (maneuver balloon),
 * իսկ առանց երթուղու Navigator-ը նույնպես ցույց է տալիս սահմանափակումը, և այն կորչում էր։
 * Այստեղ ծառի մեջ փնտրում ենք «speedlimit» / «speed_limit» id-ով տարր՝ թվային տեքստով։
 */
object KomNavLimit {
    private const val TAG = "KomNavLimit"
    private const val MAX_NODES = 600

    @Volatile var limit: Int = 0
        private set
    @Volatile var lastMs: Long = 0L
        private set

    fun report(value: Int, nowMs: Long = System.currentTimeMillis()) {
        if (value !in 5..150) return
        if (value != limit) Log.i(TAG, "navigator speed limit $value")
        limit = value
        lastMs = nowMs
    }

    /** Թարմ արժեք (վերջին [maxAgeMs]-ում), այլապես 0։ */
    fun fresh(maxAgeMs: Long = 30_000L, nowMs: Long = System.currentTimeMillis()): Int =
        if (limit > 0 && nowMs - lastMs <= maxAgeMs) limit else 0

    /** Փնտրում է սահմանափակման տարրը [root]-ի ծառում և հայտնում այն։ */
    fun scan(root: AccessibilityNodeInfo) {
        var visited = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int): Int? {
            if (n == null || visited++ > MAX_NODES || depth > 40) return null
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("speedlimit") || id.contains("speed_limit")) {
                val v = (n.text ?: n.contentDescription)?.toString()?.trim()?.toIntOrNull()
                if (v != null) return v
            }
            for (i in 0 until n.childCount) {
                val c = runCatching { n.getChild(i) }.getOrNull() ?: continue
                val r = walk(c, depth + 1)
                @Suppress("DEPRECATION") runCatching { c.recycle() }
                if (r != null) return r
            }
            return null
        }
        runCatching { walk(root, 0) }.getOrNull()?.let { report(it) }
    }
}
