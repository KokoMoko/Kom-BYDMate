package com.bydmate.app.ui.dashboard

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kom-BYDMate: a cartoon traffic officer who steps onto the dashboard road, waves his baton and
 * blows a whistle once the car has been over the road's speed limit (+ the speedometer tolerance,
 * the overspeed beep's threshold) for [OFFICER_AFTER_MS]; a shorter overspeed only beeps. On by default; tapping him on three separate
 * appearances switches him off, and Settings switches him back on.
 */
object KomPolice {
    private const val TAG = "KomPolice"
    private const val PREFS = "kom_prefs"
    private const val KEY_ON = "police_on"
    private const val KEY_TAPS = "police_taps"
    const val TAPS_TO_DISABLE = 3
    const val WHISTLE_REPEAT_MS = 10_000L
    /** The overspeed beep comes at once; the officer only when the overspeed lasts this long. */
    const val OFFICER_AFTER_MS = 30_000L
    const val DIP_GRACE_MS = 3_000L

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ON, true)

    /** Settings switch: a fresh start also forgets the taps that switched him off. */
    fun setEnabled(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ON, on).putInt(KEY_TAPS, 0).apply()

    /** One tap on one appearance; returns true when this tap switched the officer off. */
    fun tapped(ctx: Context): Boolean {
        val taps = prefs(ctx).getInt(KEY_TAPS, 0) + 1
        val off = taps >= TAPS_TO_DISABLE
        prefs(ctx).edit().putInt(KEY_TAPS, if (off) 0 else taps).apply { if (off) putBoolean(KEY_ON, false) }.apply()
        return off
    }

    /** Over the limit: a fresh road limit is known and the speed is past it plus the tolerance. */
    internal fun isOver(speedKmh: Float, limit: Int, fresh: Boolean, tolerance: Int): Boolean =
        fresh && limit > 0 && speedKmh > limit + tolerance

    /** "Phweet ... phweeeet": a pea whistle, synthesized (no audio asset). */
    fun whistle() {
        Thread {
            runCatching {
                val rate = 44_100
                val pcm = whistlePcm(rate)
                val stream = com.bydmate.app.voice.SherpaTtsEngine.primaryStreamType(
                    com.bydmate.app.platform.LegacyHeadUnit.isAndroid10)
                @Suppress("DEPRECATION")
                val track = AudioTrack(stream, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    pcm.size * 2, AudioTrack.MODE_STATIC)
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / rate + 100)
                track.release()
            }.onFailure { Log.w(TAG, "whistle: ${it.message}") }
        }.start()
    }

    /** Two blasts of a ~2.9 kHz tone trilled by the pea at ~32 Hz, with soft edges. */
    internal fun whistlePcm(rate: Int): ShortArray {
        val blasts = listOf(0.22 to 0.0, 0.55 to 0.10)   // length, gap before it (s)
        val total = blasts.sumOf { it.first + it.second }
        val out = ShortArray((total * rate).toInt())
        var pos = 0
        var phase = 0.0
        for ((len, gap) in blasts) {
            pos += (gap * rate).toInt()
            val n = (len * rate).toInt()
            for (i in 0 until n) {
                if (pos + i >= out.size) break
                val t = i.toDouble() / rate
                val trill = sin(2 * PI * 32 * t)
                val freq = 2900 + 180 * trill
                phase += 2 * PI * freq / rate
                val env = minOf(1.0, t / 0.02, (len - t) / 0.04).coerceAtLeast(0.0)
                val amp = env * (0.75 + 0.25 * trill)
                out[pos + i] = (sin(phase) * amp * 0.55 * Short.MAX_VALUE).toInt().toShort()
            }
            pos += n
        }
        return out
    }
}

/**
 * The officer on the road. [speedKmh] and the road limit decide whether he is there; each
 * overspeed episode he comes from a random side, and a tap sends him away for that episode.
 */
@Composable
internal fun PoliceOverlay(speedKmh: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val nav = rememberNavSpeedLimit()
    var enabled by remember { mutableStateOf(KomPolice.enabled(ctx)) }
    LaunchedEffect(Unit) { while (true) { enabled = KomPolice.enabled(ctx); delay(2_000L) } }
    val over = KomPolice.isOver(speedKmh, nav.limit, nav.fresh, SpeedoPrefs.tolerance(ctx))
    // An episode survives dips below the limit shorter than [KomPolice.DIP_GRACE_MS], so a speed
    // hovering at the threshold does not restart the officer's countdown.
    var episode by remember { mutableStateOf(false) }
    LaunchedEffect(over) { if (over) episode = true else { delay(KomPolice.DIP_GRACE_MS); episode = false } }
    var fromLeft by remember { mutableStateOf(true) }
    var sentAway by remember { mutableStateOf(false) }
    var longOver by remember { mutableStateOf(false) }
    // A brief overspeed only gets the beep (KomSpeedAlert); the officer comes after 30 s of it.
    LaunchedEffect(episode) {
        longOver = false
        if (episode) {
            fromLeft = Random.nextBoolean()
            delay(KomPolice.OFFICER_AFTER_MS)
            longOver = true
        } else sentAway = false
    }
    val visible = enabled && longOver && !sentAway
    LaunchedEffect(visible) {
        while (visible) { KomPolice.whistle(); delay(KomPolice.WHISTLE_REPEAT_MS) }
    }
    BoxWithConstraints(modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(500)) { w -> if (fromLeft) -w else w } + fadeIn(tween(300)),
            exit = slideOutHorizontally(tween(400)) { w -> if (fromLeft) -w else w } + fadeOut(tween(300)),
            modifier = Modifier.align(if (fromLeft) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Box(
                Modifier.fillMaxHeight(0.62f).aspectRatio(0.72f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        sentAway = true
                        if (KomPolice.tapped(ctx)) enabled = false
                    },
            ) { Officer(facingRight = fromLeft) }
        }
    }
}

private val Skin = Color(0xFFF2C094)
private val Uniform = Color(0xFF1D4ED8)
private val UniformDark = Color(0xFF1E3A8A)
private val Navy = Color(0xFF0F172A)
private val Gold = Color(0xFFFBBF24)
private val Silver = Color(0xFFCBD5E1)
private val Blush = Color(0x66F87171)

/** Chibi officer (big head, small body) in a 100 x 140 box, baton waving, whistle sounding. */
@Composable
private fun Officer(facingRight: Boolean) {
    val wave = rememberInfiniteTransition(label = "police")
    val baton by wave.animateFloat(-28f, 22f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "baton")
    val puff by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "puff")
    Canvas(Modifier.aspectRatio(0.72f).fillMaxHeight()) {
        val s = size.height / 140f
        scale(if (facingRight) 1f else -1f, 1f) {
            drawOfficer(s, baton, puff)
        }
    }
}

private fun DrawScope.drawOfficer(s: Float, baton: Float, puff: Float) {
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun r(v: Float) = CornerRadius(v * s)
    // shadow
    drawOval(Color(0x55000000), o(22f, 132f), sz(56f, 8f))
    // legs and shoes
    drawRoundRect(Navy, o(36f, 108f), sz(12f, 24f), r(5f))
    drawRoundRect(Navy, o(52f, 108f), sz(12f, 24f), r(5f))
    drawRoundRect(Color.Black, o(32f, 128f), sz(18f, 7f), r(3f))
    drawRoundRect(Color.Black, o(50f, 128f), sz(18f, 7f), r(3f))
    // body, belt, badge
    drawRoundRect(Uniform, o(30f, 74f), sz(40f, 40f), r(14f))
    drawRect(Navy, o(30f, 102f), sz(40f, 6f))
    drawRect(Gold, o(46f, 102f), sz(8f, 6f))
    drawRoundRect(Gold, o(36f, 82f), sz(9f, 7f), r(2f))
    // back arm, hand on the hip
    drawRoundRect(Uniform, o(22f, 80f), sz(11f, 26f), r(5.5f))
    drawCircle(Skin, 5.5f * s, o(27.5f, 106f))
    // waving arm with the striped baton, pivoting at the shoulder
    rotate(baton, pivot = o(64f, 82f)) {
        drawRoundRect(Uniform, o(60f, 58f), sz(11f, 28f), r(5.5f))
        drawCircle(Skin, 5.5f * s, o(65.5f, 58f))
        drawRoundRect(Color.White, o(62.5f, 22f), sz(6f, 36f), r(3f))
        for (i in 0 until 3) drawRect(Color(0xFFEF4444), o(62.5f, 24f + i * 11f), sz(6f, 5.5f))
    }
    // big head
    drawRoundRect(Skin, o(44f, 66f), sz(12f, 10f), r(3f))
    drawCircle(Skin, 26f * s, o(50f, 46f))
    drawCircle(Blush, 4.5f * s, o(36f, 54f))
    drawCircle(Blush, 4.5f * s, o(62f, 54f))
    // eyes with highlights, stern brows
    drawCircle(Navy, 4.2f * s, o(41f, 45f)); drawCircle(Color.White, 1.4f * s, o(42.3f, 43.6f))
    drawCircle(Navy, 4.2f * s, o(59f, 45f)); drawCircle(Color.White, 1.4f * s, o(60.3f, 43.6f))
    drawLine(Navy, o(35f, 36f), o(46f, 39f), 2.6f * s, StrokeCap.Round)
    drawLine(Navy, o(54f, 39f), o(65f, 36f), 2.6f * s, StrokeCap.Round)
    // whistle at the mouth
    drawRoundRect(Silver, o(50f, 56f), sz(14f, 7f), r(3.5f))
    drawCircle(Silver, 4.5f * s, o(66f, 59.5f))
    drawCircle(Navy, 1.4f * s, o(66f, 59.5f))
    // cap: crown, peak, gold badge
    drawRoundRect(UniformDark, o(26f, 14f), sz(48f, 18f), r(8f))
    drawRoundRect(Navy, o(24f, 28f), sz(52f, 7f), r(3f))
    drawRoundRect(Gold, o(45f, 17f), sz(10f, 9f), r(2f))
    // whistle sound arcs, pulsing outwards
    for (i in 0 until 3) {
        val k = (puff + i / 3f) % 1f
        val rad = (8f + k * 18f) * s
        drawArc(Color(0xFFFDE68A).copy(alpha = 1f - k), -40f, 80f, false,
            topLeft = Offset(72f * s - rad, 59.5f * s - rad), size = Size(rad * 2, rad * 2),
            style = Stroke(width = 2.4f * s, cap = StrokeCap.Round))
    }
}
