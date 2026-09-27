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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kom-BYDMate «Application» սալիկ․ իսկական հավելվածը (օր․ Yandex Navigator) բացում է freeform
 * պատուհանում՝ ճիշտ սալիկի տեղում և չափով, նույն helper մեխանիզմով, ինչ split-ը։
 *
 * Android-ը թույլ չի տալիս մի հավելվածը ներդնել մյուսի մեջ, դրա համար պատուհանը «լողում» է
 * սալիկի վրա։ My Dashboard-ից դուրս գալիս (այլ էջ, խմբագրում) պատուհանը դառնում է fullscreen
 * և մեր MainActivity-ն վերադառնում է առաջ․ վերադառնալիս նորից բացվում է սալիկում։
 */
object AppTileController {
    private const val TAG = "KomAppTile"
    private const val DISPLAY_MAIN = 0
    private const val WINDOWING_FULLSCREEN = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Հիմա սալիկում ցույց տրված հավելվածները և դրանց սահմանները։ */
    private val shown = mutableMapOf<String, Rect>()

    private fun entry(ctx: Context) =
        EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)

    /** Բացում/տեղափոխում է [pkg]-ը [rect]-ում (էկրանի px)։ Նույն սահմաններով կրկնակի կանչը՝ no-op։ */
    fun show(ctx: Context, pkg: String, rect: Rect) {
        scope.launch {
            mutex.withLock {
                if (shown[pkg] == rect) return@withLock
                val e = entry(ctx)
                if (!e.helperBootstrap().ensureRunning()) {
                    Log.w(TAG, "helper not running, cannot show $pkg")
                    return@withLock
                }
                val result = runCatching {
                    e.helperClient().launchFreeform(
                        pkg, DISPLAY_MAIN, rect.left, rect.top, rect.right, rect.bottom,
                        PaneTypePolicy().paneType,
                    )
                }.onFailure { Log.w(TAG, "launchFreeform $pkg failed: ${it.message}") }.getOrNull()
                Log.i(TAG, "show $pkg at $rect -> $result")
                shown[pkg] = Rect(rect)
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
                if (shown.remove(pkg) == null) return@withLock
                val e = entry(ctx)
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
