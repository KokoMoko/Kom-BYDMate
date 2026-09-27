package com.bydmate.app.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: մեքենան միացնելիս՝
 *  1. Yandex Music-ին միանում ենք MediaBrowser-ով (հավելվածը և widget-ը «արթնանում» են),
 *     բայց նվագարկում ՉԵՆՔ սկսում;
 *  2. բացում ենք Panel-ը (split)՝ Kom-ը 1/3, Navigator-ը 2/3 (My Dashboard-ի «Application»
 *     սալիկի հավելվածը, իսկ եթե այն չկա՝ Yandex Navigator)։ Navigator-ը երևալով է նաև
 *     արագության սահմանափակումը հասանելի դառնում։
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
            Log.i(TAG, "car on -> autostart (music=${KomPrefs.autostartMusic(ctx)}, split=${KomPrefs.autostartSplit(ctx)})")
            if (KomPrefs.autostartMusic(ctx)) connectMusic(ctx)
            if (KomPrefs.autostartSplit(ctx)) {
                delay(4_000L)  // թող համակարգը և helper-ը պատրաստ լինեն
                val tilePkg = MyDashboardStore.load(ctx)
                    .firstOrNull { it.type == TileType.APP && it.pkg.isNotEmpty() }?.pkg
                val pkg = tilePkg ?: NAVI_PKG
                if (isInstalled(ctx, pkg)) {
                    KomPanelAuto.done = true  // My Dashboard-ը երկրորդ անգամ չբացի
                    KomPanel.open(ctx, pkg, appOnRight = true)
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
                    override fun onConnected() { Log.i(TAG, "music connected (no playback)") }
                    override fun onConnectionFailed() { Log.w(TAG, "music connection failed") }
                }, null)
                browser = b
                b.connect()
            }.onFailure { Log.w(TAG, "music connect error: ${it.message}") }
        }
    }

    private fun isInstalled(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
}
