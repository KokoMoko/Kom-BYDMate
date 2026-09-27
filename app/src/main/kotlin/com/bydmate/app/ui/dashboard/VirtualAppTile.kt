package com.bydmate.app.ui.dashboard

import android.content.Context
import android.util.Log
import android.view.Surface
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.helper.HelperBinderProtocol
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kom-BYDMate «Application» սալիկ՝ վիրտուալ էկրանով (ինչպես BYDMate-ի կլաստերի պրոյեկցիան)։
 *
 * Սալիկի SurfaceView-ի համար helper-ը ստեղծում է VirtualDisplay, և հավելվածը (օր․ Navigator)
 * գործարկվում է հենց այդ էկրանի վրա՝ այն երևում է սալիկի ներսում, իսկ Kom-ը մնում է լիաէկրան։
 * PUBLIC էկրանը accessibility-ի համար տեսանելի է, դրա համար Navigator-ի արագության
 * սահմանափակումը կարդացվում է նաև սալիկից։
 *
 * Սալիկը միայն ցուցադրում է (հպումները հավելվածին չեն փոխանցվում)․ սեղմելիս հավելվածը
 * տեղափոխվում է հիմնական էկրան՝ լիաէկրան, իսկ Kom վերադառնալիս նորից «մտնում» է սալիկի մեջ։
 */
object VirtualAppTile {
    private const val TAG = "KomVTile"
    private const val VD_NAME = "Kom_Tile_VD"
    private const val VD_FLAGS_PRIVATE = 322  // TRUSTED | OWN_CONTENT_ONLY | PRESENTATION
    private const val VD_FLAG_PUBLIC = 1
    private const val MAIN_DISPLAY = 0
    private const val WINDOWING_FULLSCREEN = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Ընթացիկ վիրտուալ էկրանը (null՝ հավելվածը սալիկում չէ)։ */
    @Volatile private var displayId: Int? = null
    @Volatile private var shownPkg: String? = null

    private fun helper(ctx: Context) =
        EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)

    /**
     * Նոր Surface (սալիկը երևաց կամ չափը փոխվեց)․ ստեղծում ենք նոր VD, հավելվածը տեղափոխում
     * ենք այնտեղ, հետո հինն ենք ազատում (այդպես հավելվածը երբեք չի «ցատկում» հիմնական էկրան)։
     */
    fun attach(ctx: Context, pkg: String, surface: Surface, width: Int, height: Int, dpi: Int) {
        scope.launch {
            mutex.withLock {
                val e = helper(ctx)
                if (!e.helperBootstrap().ensureRunning()) {
                    Log.w(TAG, "helper not running")
                    return@withLock
                }
                val h = e.helperClient()
                val id = h.createVirtualDisplay(VD_NAME, width, height, dpi, VD_FLAGS_PRIVATE or VD_FLAG_PUBLIC, surface)
                    ?: h.createVirtualDisplay(VD_NAME, width, height, dpi, VD_FLAGS_PRIVATE, surface)
                if (id == null) {
                    Log.w(TAG, "createVirtualDisplay failed")
                    return@withLock
                }
                val r = runCatching {
                    h.launchFreeform(pkg, id, 0, 0, width, height, HelperBinderProtocol.PANE_TYPE_STANDARD)
                }.onFailure { Log.w(TAG, "launch $pkg on $id failed: ${it.message}") }.getOrNull()
                Log.i(TAG, "attach $pkg display=$id ${width}x$height@$dpi -> $r")
                val old = displayId
                displayId = id
                shownPkg = pkg
                if (old != null && old != id) runCatching { h.releaseVirtualDisplay(old) }
            }
        }
    }

    /** Սեղմում սալիկի վրա՝ հավելվածը բացվում է հիմնական էկրանին՝ լիաէկրան։ */
    fun openFull(ctx: Context, pkg: String) {
        scope.launch {
            mutex.withLock {
                val h = helper(ctx).helperClient()
                val taskId = runCatching { h.getTaskId(pkg) }.getOrNull()
                if (taskId != null && displayId != null) {
                    runCatching {
                        h.setTaskWindowingMode(taskId, WINDOWING_FULLSCREEN, HelperBinderProtocol.PANE_TYPE_STANDARD)
                        h.moveTaskToDisplay(taskId, MAIN_DISPLAY)
                    }.onFailure { Log.w(TAG, "move to main failed: ${it.message}") }
                } else {
                    runCatching { h.launchApp(pkg) }
                }
                displayId?.let { old -> runCatching { h.releaseVirtualDisplay(old) } }
                displayId = null
                shownPkg = null
                Log.i(TAG, "openFull $pkg task=$taskId")
            }
        }
    }
}
