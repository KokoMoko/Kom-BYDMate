package com.bydmate.app.navdata

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.bydmate.app.cluster.SteeringWheelKeyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    private val _debug = MutableStateFlow("—")
    /** Ախտորոշում Cluster ⋮ պատուհանի համար․ գտնվե՞լ է Navigator-ի պատուհանը, ի՞նչ id-ներ կան։ */
    val debug: StateFlow<String> = _debug
    @Volatile private var lastSpeedIds: String = ""

    /**
     * Սեփական ընթերցում 2 վրկ-ը մեկ։ Navigator-ը վարորդի էկրանին (display 3) կամ կանգնած մեքենայում
     * a11y իրադարձություններ գրեթե չի ուղարկում, իսկ BYDMate-ի ժամանակաչափը աշխատում է միայն
     * HUD ռեժիմում և երթուղու ժամանակ։
     */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(2_000L)
                val svc = SteeringWheelKeyService.instance
                if (svc == null) { _debug.value = "a11y service off"; continue }
                val root = runCatching { svc.findNavigatorRoot() }.getOrNull()
                if (root == null) { _debug.value = "navigator window not found"; continue }
                try {
                    lastSpeedIds = ""
                    scan(root)
                    val age = (System.currentTimeMillis() - lastMs) / 1000
                    _debug.value = "window ok · limit=${fresh()} (last $limit, ${age}s ago)" +
                        (if (lastSpeedIds.isNotEmpty()) "\nids: $lastSpeedIds" else "\nids: no *speed* ids")
                } finally {
                    @Suppress("DEPRECATION") runCatching { root.recycle() }
                }
            }
        }
    }

    /** Փնտրում է սահմանափակման տարրը [root]-ի ծառում և հայտնում այն։ */
    fun scan(root: AccessibilityNodeInfo) {
        var visited = 0
        val seen = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int): Int? {
            if (n == null || visited++ > MAX_NODES || depth > 40) return null
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("speed") && seen.length < 200) {
                seen.append(id.substringAfter(":id/")).append('=')
                    .append((n.text ?: n.contentDescription)?.toString()?.take(8) ?: "∅").append(' ')
            }
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
        lastSpeedIds = seen.toString().trim()
    }
}
