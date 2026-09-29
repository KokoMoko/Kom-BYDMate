package com.bydmate.app.hud

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import com.bydmate.app.cluster.ClusterEntryPoint
import com.bydmate.app.media.MediaSessionListenerService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: Yandex Music-ի ընթացիկ երգը՝ վարորդի վահանակի Music քարտում։
 *
 * Firmware-ի `android.hardware.bydauto.instrument.MediaStateDelegate`-ը (BYD mediacenter-ի համար)
 * վահանակին գրում է INSTRUMENT սարքի (1007) դաշտերը․ sendMusicSource / sendMusicState /
 * sendMusicName (MUSIC_INFO_SET, buffer) / sendMusicPlaybackProgress (0…100)։ Yandex Music-ը
 * BYD-ի հավելված չէ, դրա համար քարտը դատարկ էր (միայն ▶)։ Այստեղ նույն դաշտերը գրում ենք մենք՝
 * Media session-ից (getActiveSessions, MediaSessionListenerService-ի թույլտվությամբ)։
 */
object KomCanMusic {
    private const val TAG = "KomCanMusic"
    private const val DEV = 1007
    private const val FID_MUSIC_INFO = 1140527112          // INSTRUMENT_MUSIC_INFO_SET (buffer)
    private const val FID_MUSIC_STATE = 1138753546         // INSTRUMENT_MUSIC_STATE_SET
    private const val FID_MUSIC_PROGRESS = 1138753552      // INSTRUMENT_MUSIC_PLAYBACK_PROGRESS_SET
    private const val FID_MUSIC_SOURCE_OLD = 1138753584    // INSTRUMENT_MUSIC_SOURCE_SET_OLD
    private const val FID_MUSIC_SOURCE = 871366704         // INSTRUMENT_MUSIC_SOURCE_SET
    private const val STATE_PLAY = 1
    private const val STATE_PAUSE = 2
    private const val STATE_STOP = 3
    private const val SOURCE_OTHERS = 11
    private val PREFERRED = listOf("ru.yandex.music", "com.yandex.music")
    private const val BYD_MEDIA = "com.byd.mediacenter"

    private val _status = MutableStateFlow<String?>(null)
    /** Ախտորոշում Cluster ⋮ պատուհանի համար։ */
    val status: StateFlow<String?> = _status

    /** Անվան ձևաչափը (փորձարկման համար)․ 0 = UTF-16LE, 1 = UTF-8։ */
    @Volatile var encoding = 0

    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        val helper = EntryPointAccessors.fromApplication(app, ClusterEntryPoint::class.java).helperClient()
        val msm = app.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(app, MediaSessionListenerService::class.java)
        scope.launch {
            delay(20_000L)
            var lastName: String? = null
            var lastState = -1
            var lastProgress = -1
            var lastSourceMs = 0L
            var forceAt = 0L
            while (true) {
                delay(2_000L)
                val c = runCatching { pick(msm.getActiveSessions(component)) }
                    .onFailure { _status.value = "Music→cluster: no session access (${it.javaClass.simpleName})" }
                    .getOrNull()
                if (c == null) {
                    if (lastState != STATE_STOP && lastState != -1) {
                        runCatching { helper.writeStatus(DEV, FID_MUSIC_STATE, STATE_STOP) }
                        lastState = STATE_STOP
                    }
                    continue
                }
                val md = c.metadata
                val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
                val name = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - ").take(60)
                val pb = c.playbackState
                val state = if (pb?.state == PlaybackState.STATE_PLAYING) STATE_PLAY else STATE_PAUSE
                val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                val pos = pb?.let { estimatePosition(it) } ?: 0L
                val progress = if (dur > 0) (pos * 100 / dur).toInt().coerceIn(0, 100) else 0

                val now = System.currentTimeMillis()
                // Mediacenter-ը կարող է վերագրել՝ 15 վրկ-ը մեկ կրկնում ենք ամբողջը
                val force = now - forceAt > 15_000L
                if (force) forceAt = now
                val sb = StringBuilder()
                if (force || now - lastSourceMs > 60_000L) {
                    val a = runCatching { helper.writeStatus(DEV, FID_MUSIC_SOURCE_OLD, SOURCE_OTHERS) }.getOrNull()
                    val b = runCatching { helper.writeStatus(DEV, FID_MUSIC_SOURCE, SOURCE_OTHERS) }.getOrNull()
                    sb.append("src=$a/$b ")
                    lastSourceMs = now
                }
                if (force || name != lastName) {
                    val bytes = if (encoding == 1) name.toByteArray(Charsets.UTF_8) else name.toByteArray(Charsets.UTF_16LE)
                    val r = runCatching { helper.writeBufferStatus(DEV, FID_MUSIC_INFO, bytes) }.getOrNull()
                    sb.append("name=$r ")
                    if (name != lastName) Log.i(TAG, "track: $name (${c.packageName}) st=$r")
                    lastName = name
                }
                if (force || state != lastState) {
                    val r = runCatching { helper.writeStatus(DEV, FID_MUSIC_STATE, state) }.getOrNull()
                    sb.append("state=$state:$r ")
                    lastState = state
                }
                if (force || progress != lastProgress) {
                    runCatching { helper.writeStatus(DEV, FID_MUSIC_PROGRESS, progress) }
                    lastProgress = progress
                }
                if (sb.isNotEmpty()) {
                    _status.value = "Music→cluster: ${c.packageName.substringAfterLast('.')} «$name» " +
                        "${if (state == STATE_PLAY) "▶" else "⏸"} $progress% · $sb"
                }
            }
        }
    }

    private fun pick(sessions: List<MediaController>): MediaController? {
        val usable = sessions.filter { it.packageName != BYD_MEDIA }
        return usable.firstOrNull { it.packageName in PREFERRED }
            ?: usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    private fun estimatePosition(pb: PlaybackState): Long {
        if (pb.state != PlaybackState.STATE_PLAYING || pb.lastPositionUpdateTime <= 0) return pb.position
        val elapsed = SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime
        return pb.position + (elapsed * pb.playbackSpeed).toLong()
    }
}
