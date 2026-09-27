package com.bydmate.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import com.bydmate.app.MainActivity
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.helper.HelperBinderProtocol
import com.bydmate.app.split.PaneTypePolicy
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kom-BYDMate «Application» սալիկ․ իսկական հավելվածը (օր․ Waze, Yandex Navigator) բացվում է
 * freeform պատուհանում՝ ճիշտ սալիկի տեղում և չափով, իսկ Kom-ը մնում է լիաէկրան իր տակ։
 *
 * Այդպես հավելվածը վերևում է և ստանում է հպումները (հասցե, քարտեզ), իսկ Kom-ի մնացած
 * սալիկները (widget-ները) աշխատում են իրենց տեղում։ Split-ը (Kom-ն էլ պատուհան) այս firmware-ում
 * չի աշխատում․ RECENTS պատուհաններից հպում ստանում է միայն վերևինը, STANDARD-ը փակվում է հպումից։
 *
 * Որոշ հավելվածներ նոր էկրան բացելիս (օր․ Waze-ի երթուղու նախադիտում) պատուհանը ձգում են ամբողջ
 * էկրանով․ [watchdog]-ը վայրկյանը մեկ վերադարձնում է այն սալիկի սահմաններ։
 */
object AppTileController {
    private const val TAG = "KomAppTile"
    private const val DISPLAY_MAIN = 0
    private const val WINDOWING_FULLSCREEN = 1
    private const val WINDOWING_FREEFORM = 5
    /** Թաքցնելու տեղը՝ ներքևի աջ անկյուն, նավիգացիոն վահանակի տակ։ */
    private val PARK = Rect(1918, 1078, 1920, 1080)
    private const val INSET = 48

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Հիմա սալիկում ցույց տրված հավելվածները և դրանց սահմանները։ */
    private val shown = mutableMapOf<String, Rect>()
    private val watchdogs = mutableMapOf<String, Job>()
    /** Էկրանի անկյունում «կայանված» (թաքնված, բայց կենդանի) հավելվածները։ */
    private val parked = mutableSetOf<String>()

    private fun entry(ctx: Context) =
        EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)

    fun isShown(pkg: String) = shown.containsKey(pkg)

    /** Բացում/տեղափոխում է [pkg]-ը [rect]-ում (էկրանի px)։ Նույն սահմաններով կրկնակի կանչը՝ no-op։ */
    fun show(ctx: Context, pkg: String, tileRect: Rect) {
        // Freeform պատուհանը ունի ~45px անտեսանելի եզր (չափը փոխելու համար), որը «խլում» է հպումները․
        // պատուհանը դնում ենք սալիկից INSET ներս, որ եզրը մնա սալիկի մեջ և չծածկի Kom-ի կոճակները
        val rect = Rect(tileRect.left + INSET, tileRect.top + INSET, tileRect.right - INSET, tileRect.bottom - INSET)
        if (rect.width() < 200 || rect.height() < 150) return
        scope.launch {
            mutex.withLock {
                if (shown[pkg] == rect) return@withLock
                val e = entry(ctx)
                if (!e.helperBootstrap().ensureRunning()) {
                    Log.w(TAG, "helper not running, cannot show $pkg")
                    return@withLock
                }
                // «Կայանված» պատուհանը արագ վերադարձնում ենք առանց վերագործարկման
                if (parked.remove(pkg)) {
                    val st = runCatching { e.helperClient().getTaskState(pkg) }.getOrNull()
                    if (st != null && st.windowingMode == WINDOWING_FREEFORM && st.displayId == DISPLAY_MAIN) {
                        val ok = runCatching {
                            e.helperClient().setTaskBounds(st.taskId, rect.left, rect.top, rect.right, rect.bottom)
                            e.helperClient().setFocusedTask(st.taskId)
                        }.isSuccess
                        Log.i(TAG, "unpark $pkg -> $ok")
                        if (ok) {
                            shown[pkg] = Rect(rect)
                            startWatchdog(ctx, pkg)
                            return@withLock
                        }
                    }
                }
                val result = runCatching {
                    e.helperClient().launchFreeform(
                        pkg, DISPLAY_MAIN, rect.left, rect.top, rect.right, rect.bottom,
                        HelperBinderProtocol.PANE_TYPE_STANDARD,
                    )
                }.onFailure { Log.w(TAG, "launchFreeform $pkg failed: ${it.message}") }.getOrNull()
                Log.i(TAG, "show $pkg at $rect -> $result")
                shown[pkg] = Rect(rect)
                startWatchdog(ctx, pkg)
            }
        }
    }

    /** Հավելվածի պատուհանը պահում է սալիկի սահմաններում (ձգվելու դեպքում՝ ետ է բերում)։ */
    private fun startWatchdog(ctx: Context, pkg: String) {
        watchdogs.remove(pkg)?.cancel()
        watchdogs[pkg] = scope.launch {
            val h = entry(ctx).helperClient()
            var missing = 0
            var relaunches = 0
            while (isActive) {
                delay(700L)
                val rect = shown[pkg] ?: break
                val st = runCatching { h.getTaskState(pkg) }.getOrNull()
                if (st == null || st.taskId <= 0 || st.right <= st.left) {
                    // Հավելվածը փակվել է (օր․ պրոցեսը մահացել է)՝ մի քանի վայրկյան հետո բացում ենք նորից
                    if (++missing >= 4 && relaunches < 3) {
                        relaunches++
                        missing = 0
                        Log.i(TAG, "watchdog: $pkg task gone, relaunch #$relaunches")
                        runCatching {
                            h.launchFreeform(pkg, DISPLAY_MAIN, rect.left, rect.top, rect.right, rect.bottom, HelperBinderProtocol.PANE_TYPE_STANDARD)
                        }
                    }
                    continue
                }
                missing = 0
                if (st.displayId != DISPLAY_MAIN) continue
                val drifted = st.left != rect.left || st.top != rect.top || st.right != rect.right || st.bottom != rect.bottom
                if (!drifted) continue
                Log.i(TAG, "watchdog: $pkg drifted to [${st.left},${st.top},${st.right},${st.bottom}] mode=${st.windowingMode}")
                runCatching {
                    if (st.windowingMode == WINDOWING_FREEFORM) {
                        h.setTaskBounds(st.taskId, rect.left, rect.top, rect.right, rect.bottom)
                    } else {
                        h.launchFreeform(pkg, DISPLAY_MAIN, rect.left, rect.top, rect.right, rect.bottom, HelperBinderProtocol.PANE_TYPE_STANDARD)
                    }
                }.onFailure { Log.w(TAG, "watchdog restore failed: ${it.message}") }
            }
        }
    }

    /**
     * Թաքցնում է [pkg]-ի պատուհանը (fullscreen, մեր հավելվածի հետևում)։
     * [bringUsToFront]՝ երբ մենք դեռ էկրանին ենք (էջի փոփոխություն, խմբագրում)։
     */
    fun hide(ctx: Context, pkg: String, bringUsToFront: Boolean) {
        scope.launch {
            mutex.withLock {
                watchdogs.remove(pkg)?.cancel()
                if (shown.remove(pkg) == null) return@withLock
                val e = entry(ctx)
                // Kom-ը դեռ էկրանին է (էջի փոփոխություն, խմբագրում)՝ պատուհանը «կայանում» ենք ներքևի
                // աջ անկյունում՝ նավիգացիոն վահանակի տակ, որ չերևա և չխանգարի (fullscreen-ը ծածկում էր Kom-ը)
                if (bringUsToFront) {
                    val st = runCatching { e.helperClient().getTaskState(pkg) }.getOrNull()
                    if (st != null && st.windowingMode == WINDOWING_FREEFORM) {
                        runCatching { e.helperClient().setTaskBounds(st.taskId, PARK.left, PARK.top, PARK.right, PARK.bottom) }
                        parked += pkg
                        val after = runCatching { e.helperClient().getTaskState(pkg) }.getOrNull()
                        Log.i(TAG, "park $pkg -> [${after?.left},${after?.top},${after?.right},${after?.bottom}]")
                        return@withLock
                    }
                }
                parked -= pkg
                runCatching {
                    val taskId = e.helperClient().getTaskId(pkg)
                    if (taskId != null) {
                        e.helperClient().setTaskWindowingMode(taskId, WINDOWING_FULLSCREEN, HelperBinderProtocol.PANE_TYPE_STANDARD)
                    }
                }.onFailure { Log.w(TAG, "hide $pkg failed: ${it.message}") }
                if (bringUsToFront) {
                    runCatching {
                        ctx.applicationContext.startActivity(
                            Intent(ctx.applicationContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                }
                Log.i(TAG, "hide $pkg (front=$bringUsToFront)")
            }
        }
    }
}
