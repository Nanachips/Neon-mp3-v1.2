package com.neonplayer

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.math.abs

class EqInfo(val bands: Int, val min: Int, val max: Int, val freqs: IntArray)

fun TrackUi.toMediaItem(): MediaItem {
    val uri = Uri.parse(track.uri)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistText.ifBlank { null })
                .setAlbumTitle(album?.name)
                .setArtworkUri(cover?.let { Uri.fromFile(File(it)) })
                .build()
        ).build()
}

class PlayerHub(private val ctx: Context, private val dao: LibraryDao, private val prefs: AppPrefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var connecting = false

    val connected = MutableStateFlow(false)
    val currentId = MutableStateFlow<Long?>(null)
    val isPlaying = MutableStateFlow(false)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val speed = MutableStateFlow(1f)
    val queue = MutableStateFlow<List<Long>>(emptyList())
    val sleepLeftMs = MutableStateFlow<Long?>(null)
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync(player) }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            sessionMs = 0L; sessionCounted = false
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) { loopA.value = null; loopB.value = null }
        }
    }

    // Statistiques d'écoute (enregistrées en base toutes les 10 s)
    private val pendingMs = HashMap<Long, Long>()
    private val pendingPlays = HashMap<Long, Int>()
    private var sessionMs = 0L
    private var sessionCounted = false
    private var ticks = 0

    // Boucle d'un passage (A-B)
    val loopA = MutableStateFlow<Long?>(null)
    val loopB = MutableStateFlow<Long?>(null)

    fun setLoopA(ms: Long) {
        loopA.value = ms
        val b = loopB.value
        if (b != null && b <= ms) loopB.value = null
    }

    fun setLoopB(ms: Long) {
        loopB.value = ms
        val a = loopA.value
        if (a != null && a >= ms) loopA.value = null
    }

    fun clearLoop() { loopA.value = null; loopB.value = null }

    init {
        scope.launch {
            var n = 0
            while (true) {
                delay(100)
                val c = controller
                if (c != null) { abTick(c); fadeTick(c) }
                if (++n % 10 == 0) tick()
            }
        }
    }

    private fun abTick(c: MediaController) {
        val b = loopB.value ?: return
        val a = loopA.value ?: 0L
        if (a < b && c.isPlaying && c.currentPosition >= b) c.seekTo(a)
    }

    private fun fadeTick(c: MediaController) {
        val fadeMs = prefs.fadeSec * 1000L
        var v = 1f
        if (fadeMs > 0 && c.isPlaying) {
            val d = c.duration
            if (d > fadeMs * 2) {
                val p = c.currentPosition
                v = minOf(1f, p / fadeMs.toFloat(), (d - p) / fadeMs.toFloat()).coerceIn(0f, 1f)
            }
        }
        if (abs(c.volume - v) > 0.02f || (v == 1f && c.volume != 1f)) c.volume = v
    }

    private fun tick() {
        val c = controller ?: return
        if (c.isPlaying) {
            val id = c.currentMediaItem?.mediaId?.toLongOrNull()
            if (id != null) {
                pendingMs[id] = (pendingMs[id] ?: 0L) + 1000L
                sessionMs += 1000L
                val dur = c.duration.takeIf { it > 0 } ?: 30_000L
                if (!sessionCounted && sessionMs >= minOf(30_000L, dur / 2)) {
                    sessionCounted = true
                    pendingPlays[id] = (pendingPlays[id] ?: 0) + 1
                }
            }
        }
        ticks++
        if (ticks % 10 == 0 || !c.isPlaying) flush()
    }

    private fun flush() {
        if (pendingMs.isEmpty() && pendingPlays.isEmpty()) return
        val ms = HashMap(pendingMs)
        val pl = HashMap(pendingPlays)
        pendingMs.clear(); pendingPlays.clear()
        val now = System.currentTimeMillis()
        scope.launch {
            for (id in (ms.keys + pl.keys).toSet()) {
                dao.ensureStat(id)
                dao.addStat(id, ms[id] ?: 0L, pl[id] ?: 0, now)
                val day = epochDay(now)
                dao.ensureDay(day, id)
                dao.addDay(day, id, ms[id] ?: 0L, pl[id] ?: 0)
            }
        }
    }

    fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            val c = try { f.get() } catch (e: Exception) { connecting = false; return@addListener }
            controller = c
            c.addListener(listener)
            sync(c)
            connected.value = true
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withC(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.add(block)
    }

    private fun sync(p: Player) {
        currentId.value = p.currentMediaItem?.mediaId?.toLongOrNull()
        isPlaying.value = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        shuffle.value = p.shuffleModeEnabled
        repeat.value = p.repeatMode
        speed.value = p.playbackParameters.speed
        val q = List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        if (q != queue.value) queue.value = q
    }

    fun position(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun playQueue(list: List<TrackUi>, start: Int, shuffleOn: Boolean? = null) {
        if (list.isEmpty()) return
        val items = list.map { it.toMediaItem() }
        withC { c ->
            if (shuffleOn != null) c.shuffleModeEnabled = shuffleOn
            c.setMediaItems(items, start.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playShuffled(list: List<TrackUi>) {
        if (list.isEmpty()) return
        playQueue(list, list.indices.random(), true)
    }

    fun playNext(t: TrackUi) = withC { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(t.toMediaItem()); c.prepare(); c.play() }
        else c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
    }

    fun enqueue(t: TrackUi) = withC { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(t.toMediaItem()); c.prepare(); c.play() }
        else c.addMediaItem(t.toMediaItem())
    }

    fun removeAt(index: Int) = withC { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }
    fun jumpTo(index: Int) = withC { c -> c.seekTo(index, 0L); c.play() }

    fun removeTracks(ids: Set<Long>) = withC { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            if (id != null && id in ids) c.removeMediaItem(i)
        }
    }

    fun togglePlay() = withC { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause()
        else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() = withC { it.seekToNext() }
    fun previous() = withC { it.seekToPrevious() }
    fun seekTo(ms: Long) = withC { it.seekTo(ms) }
    fun setShuffle(on: Boolean) = withC { it.shuffleModeEnabled = on }
    fun setRepeat(mode: Int) = withC { it.repeatMode = mode }

    /** Multiplie à la fois la vitesse et la hauteur (pitch). */
    fun setSpeed(s: Float) = withC { it.playbackParameters = PlaybackParameters(s, s) }

    /** Met à jour les métadonnées (titre, artiste, pochette) des éléments de la file, sauf celui en cours. */
    fun syncMetadata(list: List<TrackUi>) {
        val c = controller ?: return
        val map = list.associateBy { it.id }
        for (i in 0 until c.mediaItemCount) {
            if (i == c.currentMediaItemIndex) continue
            val old = c.getMediaItemAt(i)
            val t = map[old.mediaId.toLongOrNull()] ?: continue
            val n = t.toMediaItem()
            val m = old.mediaMetadata
            if (m.title?.toString() != t.title ||
                m.artist?.toString() != t.artistText.ifBlank { null } ||
                m.albumTitle?.toString() != t.album?.name ||
                m.artworkUri != n.mediaMetadata.artworkUri
            ) c.replaceMediaItem(i, n)
        }
    }

    // Minuterie de sommeil
    fun startSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            var left = minutes * 60_000L
            while (left > 0) {
                sleepLeftMs.value = left
                delay(1000)
                left -= 1000
            }
            sleepLeftMs.value = null
            controller?.pause()
        }
    }

    fun cancelSleep() { sleepJob?.cancel(); sleepLeftMs.value = null }

    // Égaliseur
    fun eqInfo(cb: (EqInfo?) -> Unit) {
        val c = controller ?: return cb(null)
        val f = c.sendCustomCommand(SessionCommand(CMD_EQ_INFO, Bundle.EMPTY), Bundle.EMPTY)
        f.addListener({
            val b = runCatching { f.get() }.getOrNull()?.extras
            val n = b?.getInt("bands") ?: 0
            cb(
                if (b != null && n > 0) EqInfo(n, b.getInt("min"), b.getInt("max"), b.getIntArray("freqs") ?: IntArray(n))
                else null
            )
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun eqApply() {
        controller?.sendCustomCommand(SessionCommand(CMD_EQ_APPLY, Bundle.EMPTY), Bundle.EMPTY)
    }
}
