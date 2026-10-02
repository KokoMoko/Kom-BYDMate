package com.bydmate.app.ui.car

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.bydmate.app.MainActivity
import com.bydmate.app.R
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.ui.dashboard.KomEntryPoint
import com.bydmate.app.util.appLocalizedContext
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Capture my car": opens DiLink's own vehicle app with its 3D car, floats a small capture button
 * over it, and on a tap takes a clean screenshot through the on-device ADB (the same `screencap`
 * the Sea Lion 06 image came from). The screenshot then waits in [screenshot] for the editor,
 * and our app is brought back to the front.
 */
object CarCapture {
    private const val TAG = "CarCapture"
    private const val SHOT = "/sdcard/Download/kom_car_capture.png"

    /** Vehicle apps with a rotatable 3D car, tried in order (DiLink 5 first). */
    private val VEHICLE_APPS = listOf("com.byd.mycar", "com.byd.carinfo", "com.byd.vehiclesetting")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _screenshot = MutableStateFlow<Bitmap?>(null)
    val screenshot: StateFlow<Bitmap?> = _screenshot

    private val _error = MutableStateFlow<Int?>(null)
    /** String resource of the last failure, shown once by the settings block. */
    val error: StateFlow<Int?> = _error

    private var bubble: View? = null

    fun clearScreenshot() { _screenshot.value = null }
    fun clearError() { _error.value = null }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            if (!ensureOverlay(app)) { _error.value = R.string.kom_car_capture_no_overlay; return@launch }
            val launched = VEHICLE_APPS.firstNotNullOfOrNull { pkg ->
                app.packageManager.getLaunchIntentForPackage(pkg)?.also {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { app.startActivity(it) }.onFailure { e -> Log.w(TAG, "launch $pkg: ${e.message}") }
                }
            }
            Log.i(TAG, "vehicle app launched=${launched?.`package`}")
            showBubble(app)
        }
    }

    private suspend fun ensureOverlay(app: Context): Boolean {
        if (Settings.canDrawOverlays(app)) return true
        val ep = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java)
        withContext(Dispatchers.IO) {
            runCatching { if (ep.helperBootstrap().ensureRunning()) ep.helperClient().grantOverlayPermission() }
        }
        repeat(10) { if (Settings.canDrawOverlays(app)) return true; delay(200) }
        return Settings.canDrawOverlays(app)
    }

    private fun showBubble(app: Context) {
        removeBubble(app)
        val l = app.appLocalizedContext()
        fun px(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, app.resources.displayMetrics).toInt()
        fun pill(text: String, color: Int, onClick: () -> Unit) = TextView(app).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(px(22f), px(12f), px(22f), px(12f))
            background = GradientDrawable().apply { cornerRadius = px(28f).toFloat(); setColor(color) }
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pill(l.getString(R.string.kom_car_capture_button), Color.rgb(0x16, 0xA3, 0x4A)) { capture(app) })
            addView(View(app), LinearLayout.LayoutParams(px(10f), 1))
            addView(pill("✕", Color.argb(0xCC, 0x33, 0x41, 0x55)) { removeBubble(app) })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = px(70f) }
        runCatching { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(row, params); bubble = row }
            .onFailure { Log.w(TAG, "bubble: ${it.message}"); _error.value = R.string.kom_car_capture_no_overlay }
    }

    private fun removeBubble(app: Context) {
        val v = bubble ?: return
        bubble = null
        runCatching { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v) }
    }

    private fun capture(app: Context) {
        removeBubble(app)
        scope.launch {
            // Let the bubble's removal reach the screen before the screenshot.
            delay(600)
            val shot = withContext(Dispatchers.IO) { screencap(app) }
            if (shot == null) _error.value = R.string.kom_car_capture_failed else _screenshot.value = shot
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private suspend fun screencap(app: Context): Bitmap? {
        val adb = EntryPointAccessors.fromApplication(app, KomEntryPoint::class.java).adbOnDeviceClient()
        return runCatching {
            if (!adb.isConnected()) adb.connect()
            adb.exec("screencap -p $SHOT")
            val bmp = BitmapFactory.decodeFile(SHOT)
            adb.exec("rm -f $SHOT")
            bmp
        }.onFailure { Log.w(TAG, "screencap: ${it.message}") }.getOrNull()
    }
}
