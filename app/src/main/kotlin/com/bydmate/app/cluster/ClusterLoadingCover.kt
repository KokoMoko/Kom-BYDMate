package com.bydmate.app.cluster

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bydmate.app.R
import com.bydmate.app.camera.BlindSpotVisibility
import com.bydmate.app.util.appLocalizedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hides a projected navigator's one restart on the cluster.
 *
 * Moving the task onto the cluster recreates an app that does not handle every configuration
 * change of that display (Yandex Navigator: touchscreen, HDR, smallest width), and its map then
 * rebuilds from a bare grid for about a second. The cover goes up the moment the task is on the
 * display — before the compositor switches the cluster over, so the driver goes from the stock
 * cluster straight to the cover — and fades out once the map has had [HOLD_AFTER_PLACEMENT_MS].
 * An overlay is not drawn on the projection display until a task sits there, so showing it any
 * earlier only left the cluster black (on-car 2026-10-02).
 *
 * Never above a blind-spot camera: not shown while one is visible, removed the moment one shows.
 * Bounded by [MAX_SHOW_MS] so a lost release can never leave the cluster covered.
 */
internal object ClusterLoadingCover {
    private const val TAG = "ClusterLoadingCover"

    const val HOLD_AFTER_PLACEMENT_MS = 1500L
    private const val MAX_SHOW_MS = 20_000L
    private const val CAMERA_POLL_MS = 100L
    private const val FADE_MS = 400L
    private const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Main thread only.
    private var view: View? = null
    private var wm: WindowManager? = null
    private var guard: Job? = null
    private var release: Job? = null

    /** Covers [bounds] (left, top, right, bottom in [display] pixels). Fail-soft: never throws. */
    suspend fun show(context: Context, display: Display, bounds: IntArray) = withContext(Dispatchers.Main) {
        removeNow()
        if (BlindSpotVisibility.shown) return@withContext
        runCatching {
            val text = context.appLocalizedContext().getString(R.string.kom_cluster_map_loading)
            val displayContext = context.createDisplayContext(display)
            val manager = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = displayContext.resources.displayMetrics
            fun px(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()
            val content = LinearLayout(displayContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(displayContext).apply {
                    this.text = text
                    setTextColor(Color.argb(0xCC, 0xF4, 0xF7, 0xFB))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                })
                // Indeterminate sweep in the dashboard's accent green: the wait looks alive, not stuck.
                addView(ProgressBar(displayContext, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(Color.rgb(0x4A, 0xDE, 0x80))
                }, LinearLayout.LayoutParams(px(220f), px(6f)).apply { topMargin = px(14f) })
            }
            val root = FrameLayout(displayContext).apply {
                setBackgroundColor(Color.rgb(0x06, 0x16, 0x33))
                addView(content, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                ))
            }
            val params = WindowManager.LayoutParams(
                bounds[2] - bounds[0], bounds[3] - bounds[1],
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FLAGS, PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bounds[0]
                y = bounds[1]
                title = TAG
            }
            manager.addView(root, params)
            view = root
            wm = manager
            guard = scope.launch {
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < MAX_SHOW_MS) {
                    if (BlindSpotVisibility.shown) {
                        Log.i(TAG, "blind-spot camera shown; cover removed")
                        removeNow()
                        return@launch
                    }
                    delay(CAMERA_POLL_MS)
                }
                Log.w(TAG, "cover never released; removed after ${MAX_SHOW_MS}ms")
                removeNow()
            }
        }.onFailure { Log.w(TAG, "cover not shown: ${it.message}") }
    }

    /** Fades the cover out after [delayMs]; a no-op when nothing is shown. */
    fun releaseAfter(delayMs: Long) {
        scope.launch {
            release?.cancel()
            val target = view ?: return@launch
            release = scope.launch {
                delay(delayMs)
                if (view !== target) return@launch
                target.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                    if (view === target) removeNow()
                }.start()
            }
        }
    }

    /** Removes the cover immediately (pull-back). */
    fun hide() {
        scope.launch { removeNow() }
    }

    private fun removeNow() {
        guard?.cancel(); guard = null
        release?.cancel(); release = null
        val v = view ?: return
        view = null
        runCatching { wm?.removeViewImmediate(v) }
        wm = null
    }
}
