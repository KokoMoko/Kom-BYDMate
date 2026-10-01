package com.bydmate.app.ui.dashboard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.service.TrackingService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Kom-BYDMate: Cluster էջի լրացուցիչ տվյալներ՝ միջին արագություն և ծովի մակարդակից բարձրություն։
 *
 * Միջին արագությունը՝ մեքենայից (INSTRUMENT_AVERAGE_SPEED, եթե չկա՝ STATISTIC_MILEAGE1_AVERAGE_SPEED)։
 * Բարձրությունը՝ մեքենայի LOCATION_ALTITUDE-ից, եթե չկա՝ GPS-ից (Android Location.altitude)։
 * Կոդավորումը (int կամ float) firmware-ից կախված է, դրա համար կարդում ենք երկու ձևով
 * և ընտրում խելամիտ արժեքը․ [debug]-ը Cluster ⋮ պատուհանի համար է։
 */
object KomClusterExtras {
    private const val TAG = "KomClusterExtras"
    private const val DEV_INSTRUMENT = 1007
    private const val DEV_STATISTIC = 1014
    private const val DEV_LOCATION = 1017
    private const val FID_INST_AVG_SPEED = 1246777388   // Instrument.INSTRUMENT_AVERAGE_SPEED
    private const val FID_STAT_AVG_SPEED = 578553       // Statistic.STATISTIC_MILEAGE1_AVERAGE_SPEED
    private const val FID_ALTITUDE = 689157             // Location.LOCATION_ALTITUDE

    data class Extras(val avgSpeedKmh: Int? = null, val altitudeM: Int? = null, val altitudeFromGps: Boolean = false)

    private val _extras = MutableStateFlow(Extras())
    val extras: StateFlow<Extras> = _extras
    private val _debug = MutableStateFlow("—")
    val debug: StateFlow<String> = _debug

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        val helper = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java).helperClient()
        scope.launch(Dispatchers.IO) {
            delay(10_000L)
            while (true) {
                suspend fun both(dev: Int, fid: Int): Pair<Int?, Float?> {
                    val i = runCatching { helper.read(dev, fid, 5) }.getOrNull()?.let { SentinelDecoder.decodeInt(it.toInt()) }
                    val f = runCatching { helper.read(dev, fid, 7) }.getOrNull()?.let { SentinelDecoder.parseFloatFromShellInt(it.toInt()) }
                    return i to f
                }
                val inst = both(DEV_INSTRUMENT, FID_INST_AVG_SPEED)
                val stat = both(DEV_STATISTIC, FID_STAT_AVG_SPEED)
                val alt = both(DEV_LOCATION, FID_ALTITUDE)
                val gps = TrackingService.lastLocation.value?.takeIf { it.hasAltitude() }?.altitude

                fun speedOf(p: Pair<Int?, Float?>): Int? =
                    p.second?.takeIf { it in 1f..250f }?.roundToInt() ?: p.first?.takeIf { it in 1..250 }
                val avg = speedOf(inst) ?: speedOf(stat)
                val carAlt = alt.second?.takeIf { it in -500f..9000f && it != 0f }?.roundToInt()
                    ?: alt.first?.takeIf { it in -500..9000 && it != 0 }
                val altitude = carAlt ?: gps?.roundToInt()
                _extras.value = Extras(avg, altitude, altitudeFromGps = carAlt == null && gps != null)
                _debug.value = "avgSpeed inst=${inst.first}/${inst.second} stat=${stat.first}/${stat.second}\n" +
                    "altitude car=${alt.first}/${alt.second} gps=${gps?.roundToInt()}"
                delay(3_000L)
            }
        }
    }
}

/**
 * Kom-BYDMate: ձայնային ազդանշան, երբ արագությունը գերազանցում է սահմանափակումը + շեղումը
 * (նույն շեմը, որից արագաչափի շրջանը դառնում է մուգ կարմիր)։ Երկակի «բիփ» գերազանցելու պահին,
 * հետո՝ [REPEAT_MS]-ը մեկ, քանի դեռ գերազանցված է։ Սահմանափակումը՝ [NavLimitHolder]-ից
 * (Navigator / OSM), միայն թարմ արժեքը։
 */
object KomSpeedAlert {
    private const val TAG = "KomSpeedAlert"
    private const val REPEAT_MS = 10_000L

    /**
     * Same route as the voice assistant's earcons: the BYD "Voice" stream (navigation stream on
     * Android 10 units), music as the fallback. STREAM_NOTIFICATION was silent in the car even
     * at a non-zero volume (user report 2026-10-02).
     */
    private fun newTone(): ToneGenerator? = runCatching {
        ToneGenerator(com.bydmate.app.voice.SherpaTtsEngine.primaryStreamType(
            com.bydmate.app.platform.LegacyHeadUnit.isAndroid10), 100)
    }.recoverCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
        .onFailure { Log.w(TAG, "tone: ${it.message}") }.getOrNull()

    /** Settings «Test» button: the exact alert sound, once. */
    fun test() {
        val tone = newTone() ?: return
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        Thread { Thread.sleep(600L); tone.release() }.start()
    }

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch(Dispatchers.Default) {
            var tone: ToneGenerator? = null
            var over = false
            var lastBeepMs = 0L
            while (true) {
                delay(1_000L)
                if (!KomPrefs.speedAlert(app)) { over = false; continue }
                val speed = TrackingService.lastData.value?.speed ?: 0
                val nav = NavLimitHolder.read()
                val limit = if (nav.fresh) nav.limit else 0
                val nowOver = limit > 0 && speed > limit + SpeedoPrefs.tolerance(app)
                val now = System.currentTimeMillis()
                if (nowOver && (!over || now - lastBeepMs >= REPEAT_MS)) {
                    if (tone == null) tone = newTone()
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                    if (!over) Log.i(TAG, "overspeed $speed > $limit")
                    lastBeepMs = now
                }
                over = nowOver
            }
        }
    }
}
