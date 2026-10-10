package com.bydmate.app.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bydmate.app.MainActivity
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.service.TrackingService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: մեքենան միացնելիս՝
 *  1. Yandex Music-ին միանում ենք MediaBrowser-ով (հավելվածը և widget-ը «արթնանում» են),
 *     բայց նվագարկում ՉԵՆՔ սկսում;
 *  2. գործարկում ենք Yandex Navigator-ը և մի քանի վայրկյան հետո Kom-ը վերադարձնում ենք
 *     առաջին պլան․ Navigator-ը մնում է աշխատած հետին պլանում (split չենք բացում)։
 *
 * «Միացում»՝ powerState-ի անցումը OFF-ից ON/DRIVE, կամ պրոցեսի առաջին տվյալը արդեն միացված
 * մեքենայից (DiLink-ը սովորաբար հենց միացնելիս է գործարկվում)։
 */
object KomAutostart {
    private const val TAG = "KomAutostart"
    const val MUSIC_PKG = "ru.yandex.music"
    const val NAVI_PKG = "ru.yandex.yandexnavi"
    private const val MIN_INTERVAL_MS = 10 * 60_000L

    @Volatile private var lastRunMs = 0L
    private var browser: MediaBrowser? = null  // պահում ենք՝ որ կապը չկտրվի
    @Volatile private var controller: android.media.session.MediaController? = null

    /**
     * «Play»՝ Yandex Music-ի սեսիայով։ [restartIfPlaying]՝ եթե սեսիան արդեն «PLAYING» է, բայց
     * ձայն չկա (միացնելու պահին ձայնային ուղին դեռ պատրաստ չէր), pause → play՝ ձայնը վերագործարկելու համար։
     */
    private suspend fun startPlayback(restartIfPlaying: Boolean) {
        val c = controller ?: return
        val playing = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        Log.i(TAG, "startPlayback playing=$playing restart=$restartIfPlaying")
        runCatching {
            if (playing && restartIfPlaying) {
                c.transportControls.pause()
                delay(800L)
                c.transportControls.play()
            } else if (!playing) {
                c.transportControls.play()
            }
        }.onFailure { Log.w(TAG, "music play failed: ${it.message}") }
    }

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch {
            var wasOn: Boolean? = null
            TrackingService.lastData.collect { d ->
                val ps = d?.powerState ?: return@collect
                val on = ps >= 1
                if (on && wasOn != true) trigger(app, scope)
                wasOn = on
            }
        }
    }

    private fun trigger(ctx: Context, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        if (now - lastRunMs < MIN_INTERVAL_MS) return
        lastRunMs = now
        scope.launch {
            Log.i(TAG, "car on -> autostart (music=${KomPrefs.autostartMusic(ctx)}, navi=${KomPrefs.autostartNavi(ctx)})")
            if (KomPrefs.autostartMusic(ctx)) connectMusic(ctx)
            if (KomPrefs.autostartNavi(ctx) && isInstalled(ctx, NAVI_PKG)) {
                delay(4_000L)  // թող համակարգը և helper-ը պատրաստ լինեն
                launchInBackground(ctx, NAVI_PKG)
            }
            // Նվագարկումը՝ միայն երբ Navigator-ը արդեն գործարկվել է և ձայնային համակարգը պատրաստ է
            // (միացնելուց անմիջապես հետո «play»-ը ընդունվում էր, բայց ձայն չէր գալիս)
            if (KomPrefs.autostartMusic(ctx) && KomPrefs.autostartMusicPlay(ctx)) {
                delay(5_000L)
                startPlayback(restartIfPlaying = true)
                delay(10_000L)
                startPlayback(restartIfPlaying = false)  // երկրորդ փորձ, եթե դեռ չի նվագում
            }
            // Վիջեթներ, որոնք թարմանում են միայն հավելվածը բացելիս (AccuWeather)՝ ինտերնետից հետո մի պահ
            val wake = KomWidgetRefresh.packagesToOpen(ctx)
            if (wake.isNotEmpty() && KomWidgetRefresh.awaitInternet(ctx)) {
                for (pkg in wake) {
                    Log.i(TAG, "widget refresh: open $pkg")
                    launchInBackground(ctx, pkg)
                }
            }
        }
    }

    /** Yandex Music-ի MediaBrowserService-ին միանալը գործարկում է հավելվածը առանց նվագարկման։ */
    fun connectMusic(ctx: Context) {
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(MUSIC_PKG)
        val svc = runCatching { ctx.packageManager.queryIntentServices(intent, 0).firstOrNull()?.serviceInfo }.getOrNull()
        if (svc == null) {
            Log.w(TAG, "no MediaBrowserService in $MUSIC_PKG")
            return
        }
        Handler(Looper.getMainLooper()).post {
            runCatching {
                browser?.disconnect()
                val b = MediaBrowser(ctx, ComponentName(svc.packageName, svc.name), object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        Log.i(TAG, "music connected")
                        controller = runCatching { android.media.session.MediaController(ctx, browser!!.sessionToken) }.getOrNull()
                    }
                    override fun onConnectionFailed() { Log.w(TAG, "music connection failed") }
                }, null)
                browser = b
                b.connect()
            }.onFailure { Log.w(TAG, "music connect error: ${it.message}") }
        }
    }

    /** Գործարկում է [pkg]-ը, հետո Kom-ը վերադարձնում առաջին պլան (հավելվածը մնում է հետին պլանում)։ */
    private suspend fun launchInBackground(ctx: Context, pkg: String) {
        val helper = runCatching {
            EntryPointAccessors.fromApplication(ctx, ClusterEntryPoint::class.java).helperClient()
        }.getOrNull()
        val launched = runCatching { helper?.launchApp(pkg) == true }.getOrDefault(false) ||
            runCatching {
                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                    ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
                } ?: false
            }.getOrDefault(false)
        Log.i(TAG, "launch $pkg -> $launched")
        if (!launched) return
        delay(3_000L)  // Navigator-ը հասցնի սկսել
        runCatching {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }.onFailure { Log.w(TAG, "bring Kom to front failed: ${it.message}") }
    }

    internal fun isInstalled(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
}
