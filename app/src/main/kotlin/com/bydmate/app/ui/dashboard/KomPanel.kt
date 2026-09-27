package com.bydmate.app.ui.dashboard

import android.content.Context
import android.util.Log
import com.bydmate.app.BuildConfig
import com.bydmate.app.split.SplitPair
import com.bydmate.app.split.SplitSessionManager
import com.bydmate.app.split.SplitSide
import com.bydmate.app.split.SplitStartResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KomEntryPoint {
    fun splitSessionManager(): SplitSessionManager
    fun adbOnDeviceClient(): com.bydmate.app.data.autoservice.AdbOnDeviceClient
}

/**
 * Kom-BYDMate: էկրանի մաքուր screenshot ADB-ով (screencap)՝ [delayMs] հետո, որ օգտատերը հասցնի
 * բացել DiLink-ի 3D մեքենայի էկրանը։ Պահվում է /sdcard/Download/kom_car_<ժամ>.png։
 */
object KomScreenCapture {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun captureLater(ctx: Context, delayMs: Long, onDone: (String?) -> Unit) {
        val adb = EntryPointAccessors.fromApplication(ctx.applicationContext, KomEntryPoint::class.java).adbOnDeviceClient()
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            val name = "/sdcard/Download/kom_car_" +
                java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date()) + ".png"
            val ok = runCatching {
                if (!adb.isConnected()) adb.connect()
                adb.exec("screencap -p $name")
                adb.exec("ls $name")?.contains("kom_car_") == true
            }.getOrDefault(false)
            Log.i("KomCapture", "screencap $name -> $ok")
            withContext(Dispatchers.Main) { onDone(if (ok) name else null) }
        }
    }
}

/**
 * Kom-BYDMate «Panel» ռեժիմ․ My Dashboard-ի «Application» սալիկի հավելվածը (օր․ Navigator)
 * բացվում է BYDMate-ի split-ով՝ Kom-ը նեղ 1/3 մասում, հավելվածը լայն 2/3 մասում։
 *
 * Freeform պատուհանը սալիկի վրա DiLink-ի այս firmware-ում չի աշխատում (ամեն ինչ դառնում է
 * fullscreen), իսկ split-ը աշխատում է, դրա համար սալիկը հիմա split է բացում։
 */
object KomPanel {
    private const val TAG = "KomPanel"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** [appOnRight]՝ սալիկը ցանցի աջ կեսում է → Kom-ը ձախ (նեղ), հավելվածը աջ (լայն)։ */
    fun open(ctx: Context, pkg: String, appOnRight: Boolean, onResult: (SplitStartResult?) -> Unit = {}) {
        val mgr = EntryPointAccessors.fromApplication(ctx.applicationContext, KomEntryPoint::class.java).splitSessionManager()
        val pair = SplitPair(
            narrowPkg = BuildConfig.APPLICATION_ID,
            widePkg = pkg,
            narrowSide = if (appOnRight) SplitSide.LEFT else SplitSide.RIGHT,
        )
        scope.launch {
            val r = runCatching { mgr.start(pair) }
                .onFailure { Log.w(TAG, "split start failed: ${it.message}") }
                .getOrNull()
            Log.i(TAG, "open $pkg appOnRight=$appOnRight -> $r")
            withContext(Dispatchers.Main) { onResult(r) }
        }
    }
}
