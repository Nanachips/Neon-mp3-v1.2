package com.neonplayer

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

const val CMD_EQ_APPLY = "neon.eq.apply"
const val CMD_EQ_INFO = "neon.eq.info"

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var eq: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(pi).setCallback(Callback()).build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { rebuildEq(audioSessionId) }
        })
        rebuildEq(player.audioSessionId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        eq?.release(); eq = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private fun rebuildEq(sessionId: Int) {
        try { eq?.release() } catch (_: Exception) {}
        eq = null
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            eq = Equalizer(0, sessionId)
            applyEq()
        } catch (_: Exception) {}
    }

    private fun applyEq() {
        val e = eq ?: return
        val p = (application as App).prefs
        try {
            e.enabled = p.eqEnabled
            val lv = p.eqLevels
            val min = e.bandLevelRange[0].toInt()
            val max = e.bandLevelRange[1].toInt()
            for (i in 0 until e.numberOfBands.toInt()) {
                e.setBandLevel(i.toShort(), lv.getOrElse(i) { 0 }.coerceIn(min, max).toShort())
            }
        } catch (_: Exception) {}
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_EQ_APPLY, Bundle.EMPTY))
                .add(SessionCommand(CMD_EQ_INFO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds).build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_EQ_APPLY -> {
                    applyEq()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_EQ_INFO -> {
                    val b = Bundle()
                    eq?.let { e ->
                        val n = e.numberOfBands.toInt()
                        b.putInt("bands", n)
                        b.putInt("min", e.bandLevelRange[0].toInt())
                        b.putInt("max", e.bandLevelRange[1].toInt())
                        b.putIntArray("freqs", IntArray(n) { e.getCenterFreq(it.toShort()) / 1000 })
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, b))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map {
                val uri = it.requestMetadata.mediaUri ?: it.localConfiguration?.uri
                if (uri != null) it.buildUpon().setUri(uri).build() else it
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
