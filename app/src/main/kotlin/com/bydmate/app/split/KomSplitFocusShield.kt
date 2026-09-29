package com.bydmate.app.split

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.ui.dashboard.KomEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: split-ի «ֆոկուսի վահան» (firmware eng.build.20260320)։
 *
 * RECENTS պատուհաններում վերևի պատուհանի ActivityRecordInputSink-ը ծածկում է ամբողջ էկրանը, և
 * ներքևի պատուհանը հպում չի ստանում («սառած» է)։ STANDARD պատուհանները այստեղ չեն օգնում՝ BYD-ի
 * DecorView-ում ClassCastException (DecorCaptionView → BydSmartMultiIviDecorCaptionView) է լինում
 * առաջին հպումից, և հավելվածը փակվում է։
 *
 * Շրջանցում․ ոչ ակտիվ պատուհանի վրա դնում ենք թափանցիկ overlay։ Սեղմելիս այն պատուհանը
 * բարձրացնում ենք ([HelperClient.raiseFreeformTask]), և նույն կետում կրկնում ենք հպումը ADB-ով,
 * որ այն հասնի արդեն ակտիվ պատուհանին։ Վահանը տեղափոխվում է մյուս պատուհանի վրա։
 */
object KomSplitFocusShield {
    private const val TAG = "KomSplitShield"
    private const val TICK_MS = 800L
    /** BYDMate-ի split pill-ը բաժանման գծի ներքևում է՝ վահանը նրան չի ծածկում։ */
    private const val DIVIDER_GAP = 40

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    @Volatile private var shieldPkg: String? = null
    @Volatile private var shieldRect: IntArray? = null

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        val kom = EntryPointAccessors.fromApplication(app, KomEntryPoint::class.java)
        val cluster = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java)
        scope.launch {
            while (true) {
                delay(TICK_MS)
                val st = runCatching { kom.splitSessionManager().state.value }.getOrNull()
                val active = st as? SplitSessionState.Active
                if (active == null || active.nativePanes) { hide(); continue }
                val (wide, narrow) = boundsFor(active.pair.narrowSide)
                val top = runCatching { cluster.helperClient().getTopTask()?.pkg }.getOrNull()
                val (inactivePkg, b) = when (top) {
                    active.pair.narrowPkg -> active.pair.widePkg to wide
                    active.pair.widePkg -> active.pair.narrowPkg to narrow
                    else -> { hide(); continue }
                }
                val left = if (b.left > 0) b.left + DIVIDER_GAP else b.left
                val right = if (b.right < 1920) b.right - DIVIDER_GAP else b.right
                show(app, cluster, inactivePkg, intArrayOf(left, b.top, right, b.bottom))
            }
        }
    }

    private fun hide() {
        if (view == null) return
        main.post {
            view?.let { v -> runCatching { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } }
            view = null
            shieldPkg = null
            shieldRect = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(ctx: Context, cluster: ClusterEntryPoint, pkg: String, r: IntArray) {
        if (shieldPkg == pkg && shieldRect?.contentEquals(r) == true && view != null) return
        main.post {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = WindowManager.LayoutParams(
                r[2] - r[0], r[3] - r[1],
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = r[0]; y = r[1]
            }
            val v = view ?: View(ctx).also { nv ->
                nv.setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        val target = shieldPkg ?: return@setOnTouchListener true
                        val x = e.rawX.toInt(); val y = e.rawY.toInt()
                        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val h = cluster.helperClient()
                            val raised = runCatching { h.raiseFreeformTask(target, 0, PaneTypePolicy().paneType) }.getOrDefault(false)
                            Log.i(TAG, "tap on inactive $target -> raise=$raised, replay at $x,$y")
                            hide()
                            delay(250)
                            // Նույն հպումը՝ արդեն ակտիվ պատուհանին
                            runCatching {
                                val adb = EntryPointAccessors.fromApplication(ctx, KomEntryPoint::class.java).adbOnDeviceClient()
                                adb.exec("input tap $x $y")
                            }
                        }
                    }
                    true
                }
            }
            runCatching {
                if (view == null) wm.addView(v, lp) else wm.updateViewLayout(v, lp)
                view = v
                shieldPkg = pkg
                shieldRect = r
                Log.i(TAG, "shield over $pkg [${r.joinToString()}]")
            }.onFailure { Log.w(TAG, "overlay failed: ${it.message}") }
        }
    }
}
