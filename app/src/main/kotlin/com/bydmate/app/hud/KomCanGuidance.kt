package com.bydmate.app.hud

import android.content.Context
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.navdata.NavGuidanceHub
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: Navigator-ի մանևրները՝ մեքենայի CAN նավիգացիոն դաշտերով ([HudCanChannel])։
 *
 * Sealion 06-ում (firmware eng.build.20260320) HUD check-ը ցույց տվեց, որ HUD-ը և վարորդի էկրանի
 * նավիգացիայի քարտը ընդունում են միայն CAN ալիքը (3-րդ քայլ), ոչ SOME/IP-ն։ BYDMate-ը երթուղու
 * ժամանակ բարձրացնում է մեքենայի նավիգացիայի կարգավիճակը, բայց մանևրները CAN-ով չի գրում, և
 * մեքենան ցույց էր տալիս HUD check-ից մնացած «↰ 333 m BYDMATE 3»-ը։
 *
 * Այստեղ․ ծրագրի գործարկման ժամանակ դաշտերը մաքրում ենք, երթուղու ժամանակ (NavGuidanceHub-ը
 * ակտիվ է) վայրկյանը մեկ գրում ենք մանևրը, հեռավորությունը և փողոցը (միայն փոփոխության դեպքում),
 * երթուղին ավարտվելիս՝ մաքրում։ Մանևրի կոդը՝ AutoNavi (gaode), նույնը, ինչ մեքենայի գործարանային
 * քարտեզինը (1 = ձախ, ստուգված)․ մնացածը հաստատվում է [runIconTest]-ով։
 */
object KomCanGuidance {
    private const val TAG = "KomCanGuidance"
    private const val TICK_MS = 1_000L

    @Volatile private var testRunning = false
    private val _testStatus = MutableStateFlow<String?>(null)
    /** Պատկերակների թեստի ընթացքը (Cluster ⋮ պատուհանի համար)։ */
    val testStatus: StateFlow<String?> = _testStatus

    private fun channel(ctx: Context) = HudCanChannel(
        EntryPointAccessors.fromApplication(ctx.applicationContext, ClusterEntryPoint::class.java).helperClient()
    )

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch {
            // Helper-ը պատրաստ լինի, հետո մաքրում ենք մնացած թեստային արժեքները
            delay(15_000L)
            runCatching { channel(app).clear() }
                .onSuccess { Log.i(TAG, "startup clear: ${it.describe()}") }
                .onFailure { Log.w(TAG, "startup clear failed: ${it.message}") }
            var shown = false
            var lastKey: Triple<Int, Int, String>? = null
            while (true) {
                delay(TICK_MS)
                if (testRunning) continue
                val s = NavGuidanceHub.snapshot()
                val guiding = s.active && (s.maneuverGaode > 0 || s.distanceMeters > 0)
                if (guiding) {
                    val kind = toInstrumentKind(s.maneuverGaode)
                    val dist = s.distanceMeters
                    val road = s.road.take(32).ifEmpty { " " }  // մեքենան դատարկ buffer-ը մերժում է
                    // Հեռավորությունը՝ 10 մ ճշտությամբ, որ ամեն մետրի համար չգրենք
                    val key = Triple(kind, dist / 10, road)
                    if (key != lastKey) {
                        runCatching { channel(app).show(kind, dist, road) }
                            .onSuccess { Log.i(TAG, "show kind=$kind dist=$dist road=$road: ${it.describe()}") }
                            .onFailure { Log.w(TAG, "show failed: ${it.message}") }
                        lastKey = key
                        shown = true
                    }
                } else if (shown) {
                    runCatching { channel(app).clear() }
                    Log.i(TAG, "guidance ended -> clear")
                    shown = false
                    lastKey = null
                }
            }
        }
    }

    /**
     * Պատկերակների թեստ․ բարձրացնում է նավիգացիայի կարգավիճակը և 3 վրկ-ը մեկ ցույց տալիս կոդերը
     * (հեռավորությունը = կոդը, փողոցը = «KOM <կոդ>»), որ HUD-ի/վահանակի նկարներից կազմենք աղյուսակը։
     */
    /**
     * AutoNavi (gaode) մանևր → վահանակի TURN_KIND։ CAN icon test-ով (2026-09-29, Sealion 06) ստուգված՝
     * գրեթե բոլորը նույնն են (1 ձախ, 2 աջ, 3 թեթև ձախ, 7/8 կտրուկ, 9/10 հետադարձ, 11 ուղիղ,
     * 13 շրջանաձև, 25–30 = շրջանաձևի 1–6 ելք (24+N), 45 կետ, 47 վճարովի, 48 վերջնակետ, 49 թունել)։
     * Տարբերություն․ gaode 4 = թեթև աջ, իսկ վահանակում 4-ը ձախ է, թեթև աջը՝ 5։
     */
    internal fun toInstrumentKind(gaode: Int): Int = when (gaode) {
        4 -> 5
        0 -> 11  // անհայտ մանևր՝ «ուղիղ» (0-ն վահանակում թունելի պատկերակ է)
        else -> gaode
    }

    private val ownScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun runIconTest(ctx: Context, scope: CoroutineScope = ownScope) {
        if (testRunning) return
        val app = ctx.applicationContext
        val helper = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java).helperClient()
        scope.launch {
            testRunning = true
            val arming = HudArming(helper, app.getSharedPreferences(HudController.PREFS_NAME, Context.MODE_PRIVATE))
            try {
                runCatching { arming.arm() }.onSuccess { Log.i(TAG, "test arm: ${it.describe()}") }
                val codes = (1..30).toList() + listOf(45, 46, 47, 48, 49)
                for (code in codes) {
                    _testStatus.value = "CAN icon test: $code  (${codes.indexOf(code) + 1}/${codes.size})"
                    runCatching { channel(app).show(code, code, "KOM $code") }
                    delay(3_000L)
                }
            } finally {
                runCatching { channel(app).clear() }
                runCatching { arming.disarm() }
                _testStatus.value = "CAN icon test: done"
                testRunning = false
            }
        }
    }
}
