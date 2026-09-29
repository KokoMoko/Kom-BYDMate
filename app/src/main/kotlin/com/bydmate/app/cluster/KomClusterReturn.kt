package com.bydmate.app.cluster

import android.content.Context
import android.util.Log
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.ui.dashboard.KomPrefs
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: Navigator-ը վարորդի էկրանին ինքնաբերաբար վերադարձնելը, երբ վարորդը ղեկի կոճակով
 * աջ քարտը թերթել է (օր․ Music)։
 *
 * Թերթելիս Navigator-ը մնում է display 3-ում, բայց վահանակը դուրս է գալիս projection ռեժիմից՝
 * `INSTRUMENT_NAVI_TYPE` 2 → 1։ Կոմպոզիտորի «միացում» հրամանը (auto_container 16, նույնը, ինչ
 * projection-ը սկսելիս) այն 20 մվ-ում վերադարձնում է (ստուգված 2026-09-30, Sealion 06)։ Այստեղ՝
 * եթե projection-ը ակտիվ է, իսկ NAVI_TYPE-ը [KomPrefs.clusterReturnSec] վայրկյան մնում է 1,
 * ուղարկում ենք այդ հրամանը։
 */
object KomClusterReturn {
    private const val TAG = "KomClusterReturn"
    private const val DEV_INSTRUMENT = 1007
    private const val FID_NAVI_TYPE = 1086337074  // INSTRUMENT_NAVI_TYPE: 2 = projection քարտ
    private const val NAVI_PROJECTION = 2

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        val helper = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java).helperClient()
        scope.launch(Dispatchers.IO) {
            var seenProjection = false
            var awaySinceMs = 0L
            var last: Int? = null
            while (true) {
                delay(1_000L)
                val sec = KomPrefs.clusterReturnSec(app)
                if (sec <= 0 || !ClusterProjectionManager.isProjectionActive()) {
                    seenProjection = false; awaySinceMs = 0L; last = null
                    continue
                }
                val v = runCatching { helper.read(DEV_INSTRUMENT, FID_NAVI_TYPE, 5) }.getOrNull()
                    ?.let { SentinelDecoder.decodeInt(it.toInt()) }
                if (v != last) { Log.i(TAG, "NAVI_TYPE $last -> $v"); last = v }
                when {
                    v == null -> Unit
                    v == NAVI_PROJECTION -> { seenProjection = true; awaySinceMs = 0L }
                    // Քարտը փոխվել է․ սպասում ենք, որ վարորդը տեսնի այն, հետո վերադարձնում
                    seenProjection -> {
                        val now = System.currentTimeMillis()
                        if (awaySinceMs == 0L) awaySinceMs = now
                        else if (now - awaySinceMs >= sec * 1_000L) {
                            val ok = runCatching { helper.setClusterContainerMode(true) }.getOrDefault(false)
                            Log.i(TAG, "card away ${sec}s -> compositor on ok=$ok")
                            awaySinceMs = now  // չստացվելու դեռ՝ կրկին փորձ ևս [sec] հետո
                        }
                    }
                }
            }
        }
    }
}
