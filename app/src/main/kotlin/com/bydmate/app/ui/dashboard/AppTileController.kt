package com.bydmate.app.ui.dashboard

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.helper.HelperBinderProtocol
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate «Application» սալիկ․ իսկական հավելվածը (օր․ Yandex Navigator, Waze) բացվում է
 * freeform պատուհանում՝ սալիկի տեղում, իսկ Kom-ը մնում է լիաէկրան իր տակ։ Հավելվածը վերևում է և
 * ստանում է հպումները, Kom-ի մնացած սալիկներն էլ աշխատում են իրենց տեղում։
 *
 * Սխեման՝ «ցանկալի վիճակ + մեկ պահակ»․ սալիկը միայն հայտնում է [setDesired]-ով, թե ինչ պետք է
 * լինի (ցույց տալ տվյալ սահմաններում կամ թաքցնել), իսկ [loop]-ը կես վայրկյանը մեկ համեմատում է
 * իրական վիճակը ցանկալիի հետ և ուղղում։ Առաջ «ցույց տուր/թաքցրու» հրամանները տարբեր պահերի
 * էին գալիս (էջի swipe, դադար, վերադարձ) և երբեմն պատուհանը մնում էր ուրիշ էջի վրա։
 *
 * Նկատառումներ (firmware eng.build.20260320, ստուգված մեքենայում)․
 *  - STANDARD տիպ․ RECENTS-ի դեպքում InputSink-ը ծածկում էր ամբողջ էկրանը, և Kom-ը հպում չէր ստանում;
 *  - պատուհանը սալիկից [INSET] ներս է, որ freeform-ի ~45px անտեսանելի եզրը չխլի Kom-ի հպումները;
 *  - թաքցնել = «կայանել» ([PARK], նավիգացիոն վահանակի տակ)՝ հավելվածը կենդանի է, վերադարձը ակնթարթային;
 *  - հավելվածը նոր էկրան բացելիս պատուհանը կարող է ձգվել լիաէկրան․ պահակը ետ է բերում։
 */
object AppTileController {
    private const val TAG = "KomAppTile"
    private const val DISPLAY_MAIN = 0
    private const val WINDOWING_FREEFORM = 5
    private const val WINDOWING_FULLSCREEN = 1
    private const val INSET = 48
    private const val TICK_MS = 500L
    /** Թաքցնելու տեղը՝ ներքևի աջ անկյուն, նավիգացիոն վահանակի տակ (WM-ը մի փոքր սեղմում է)։ */
    private val PARK = Rect(1918, 1078, 1920, 1080)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ցանկալի սահմանները ըստ հավելվածի (բացակայում է՝ թաքցնել)։ */
    private val desired = java.util.concurrent.ConcurrentHashMap<String, Rect>()
    private val loops = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val behind = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastFrontMs = 0L
    /**
     * true՝ հավելվածը «հետև» ուղարկվեց, քանի որ Kom-ի dashboard-ը ծածկվեց ուրիշ պատուհանով
     * (ոչ թե օգտատերը ինքը գնաց ուրիշ հավելված՝ 🏠)։ Միայն այդ դեպքում ենք Kom-ը վերադարձնում։
     */
    @Volatile var komWasOnDashboard = false
    @Volatile private var coveredAtMs = 0L

    private fun entry(ctx: Context) =
        EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java)

    /**
     * [tileRect]՝ սալիկի սահմանները էկրանի px-ով (null՝ թաքցնել)։ Կանչել ամեն փոփոխության ժամանակ․
     * կրկնակի կանչը անվտանգ է։
     */
    fun setDesired(ctx: Context, pkg: String, tileRect: Rect?, komOnScreen: Boolean = true, covered: Boolean = false) {
        if (pkg.isEmpty()) return
        // Kom-ը էկրանին չէ (🏠, տեսախցիկ, ուրիշ հավելված)՝ պատուհանը ուղարկում ենք հետև (fullscreen),
        // որ նրա կայանված անկյունն էլ չերևա ուրիշ հավելվածների վրա
        if (tileRect == null && !komOnScreen) behind += pkg else behind -= pkg
        // Kom-ը ծածկվեց համակարգային պատուհանով (տեսախցիկ, DiLink-ի վահանակ)՝ ոչ թե օգտատերը գնաց 🏠
        if (tileRect != null) komWasOnDashboard = false
        else if (covered) { komWasOnDashboard = true; coveredAtMs = System.currentTimeMillis() }
        else if (!komOnScreen) komWasOnDashboard = false
        val rect = tileRect?.let { Rect(it.left + INSET, it.top + INSET, it.right - INSET, it.bottom - INSET) }
            ?.takeIf { it.width() >= 200 && it.height() >= 150 }
        if (rect == null) desired.remove(pkg) else desired[pkg] = rect
        if (loops[pkg]?.isActive != true) {
            val app = ctx.applicationContext
            loops[pkg] = scope.launch { loop(app, pkg) }
        }
    }

    private suspend fun CoroutineScope.loop(ctx: Context, pkg: String) {
        val e = entry(ctx)
        if (!e.helperBootstrap().ensureRunning()) {
            Log.w(TAG, "helper not running")
            loops.remove(pkg)
            return
        }
        val h = e.helperClient()
        var missing = 0
        var launches = 0
        var lastWant: Rect? = null
        while (isActive) {
            val want = desired[pkg]
            if (want != null && want != lastWant) launches = 0  // նոր ցուցադրում՝ նոր փորձեր
            lastWant = want
            val st = runCatching { h.getTaskState(pkg) }.getOrNull()
                ?.takeIf { it.taskId > 0 && it.right > it.left && it.displayId == DISPLAY_MAIN }
            if (want == null) {
                // «Հետև» ուղարկված (fullscreen) հավելվածը հայտնվել է ամենավերևում (օր․ ուրիշ պատուհանը
                // փակվեց)՝ Kom-ը վերադարձնում ենք առաջին պլան, որ այն չծածկի dashboard-ը
                if (st != null && st.windowingMode == WINDOWING_FULLSCREEN && pkg in behind &&
                    System.currentTimeMillis() - lastFrontMs > 3_000L &&
                    runCatching { h.getTopTaskPackageOrSkip() }.getOrNull() == pkg && komWasOnDashboard &&
                    System.currentTimeMillis() - coveredAtMs < 120_000L
                ) {
                    lastFrontMs = System.currentTimeMillis()
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(ctx, com.bydmate.app.MainActivity::class.java).addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                            )
                        )
                    }
                    Log.i(TAG, "$pkg covered Kom while hidden -> Kom to front")
                }
                // Թաքցնել՝ «կայանել», եթե պատուհանը դեռ երևում է
                if (st != null && st.windowingMode == WINDOWING_FREEFORM) {
                    if (pkg in behind) {
                        runCatching { h.setTaskWindowingMode(st.taskId, WINDOWING_FULLSCREEN, HelperBinderProtocol.PANE_TYPE_STANDARD) }
                        Log.i(TAG, "send $pkg behind")
                    } else if (st.left < PARK.left - 200) {
                        runCatching { h.setTaskBounds(st.taskId, PARK.left, PARK.top, PARK.right, PARK.bottom) }
                        Log.i(TAG, "park $pkg")
                    }
                }
            } else if (st == null) {
                // Հավելվածը չկա (դեռ չի գործարկվել կամ փակվել է)՝ գործարկում ենք սալիկում
                if (++missing >= 2 && launches < 5) {
                    missing = 0
                    launches++
                    val r = runCatching {
                        h.launchFreeform(pkg, DISPLAY_MAIN, want.left, want.top, want.right, want.bottom,
                            HelperBinderProtocol.PANE_TYPE_STANDARD)
                    }.getOrNull()
                    Log.i(TAG, "launch $pkg at $want -> $r (#$launches)")
                }
            } else {
                missing = 0
                val wrong = st.left != want.left || st.top != want.top || st.right != want.right || st.bottom != want.bottom
                if (wrong) {
                    runCatching {
                        if (st.windowingMode == WINDOWING_FREEFORM) {
                            h.setTaskBounds(st.taskId, want.left, want.top, want.right, want.bottom)
                        } else {
                            h.launchFreeform(pkg, DISPLAY_MAIN, want.left, want.top, want.right, want.bottom,
                                HelperBinderProtocol.PANE_TYPE_STANDARD)
                        }
                    }.onFailure { Log.w(TAG, "restore $pkg failed: ${it.message}") }
                    Log.i(TAG, "restore $pkg [${st.left},${st.top},${st.right},${st.bottom}] mode=${st.windowingMode} -> $want")
                }
            }
            delay(TICK_MS)
        }
    }
}
