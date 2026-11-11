package com.neonplayer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Décode un fichier audio, le rééchantillonne d'un facteur `factor` (vitesse ET hauteur multipliées
 * du même facteur) puis l'encode en AAC dans un fichier M4A.
 */
object AudioExport {
    suspend fun render(ctx: Context, src: Uri, factor: Float, out: File, onProgress: (Float) -> Unit): Boolean {
        val pcm = File(ctx.cacheDir, "export_pcm.tmp")
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        var enc: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxStarted = false
        try {
            ex.setDataSource(ctx, src, null)
            var idx = -1
            var found: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { idx = i; found = f; break }
            }
            val format = found ?: return false
            ex.selectTrack(idx)
            var sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            // Passe 1 : décodage vers un fichier PCM temporaire
            val d = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            dec = d
            d.configure(format, null, null, 0)
            d.start()
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            FileOutputStream(pcm).use { fos ->
                val chan = fos.channel
                while (!outEos) {
                    currentCoroutineContext().ensureActive()
                    if (!inEos) {
                        val ii = d.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val b = d.getInputBuffer(ii)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0) {
                                d.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inEos = true
                            } else {
                                d.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = d.dequeueOutputBuffer(info, 10_000)
                    if (oi >= 0) {
                        if (info.size > 0) {
                            val ob = d.getOutputBuffer(oi)!!
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            chan.write(ob)
                        }
                        d.releaseOutputBuffer(oi, false)
                        if (durUs > 0) onProgress((0.5f * info.presentationTimeUs / durUs).coerceIn(0f, 0.5f))
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = d.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
                fos.flush()
            }
            d.stop(); d.release(); dec = null
            if (pcm.length() < 4L) return false

            // Passe 2 : rééchantillonnage + encodage AAC
            val outCh = minOf(ch, 2)
            val outRate = if (sr > 48_000) 48_000 else sr
            val step = factor.toDouble() * sr / outRate

            val ef = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, outRate, outCh)
            ef.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            ef.setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            ef.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            val e = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc = e
            e.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            e.start()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            var muxTrack = -1

            RandomAccessFile(pcm, "r").use { raf ->
                val mb = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                val sb = mb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frames = (sb.limit() / ch).toLong()
                if (frames < 2) return false
                val totalOut = ((frames - 1) / step).toLong() + 1
                var frameIdx = 0L
                var inDone = false
                var outDone = false
                val oinfo = MediaCodec.BufferInfo()
                while (!outDone) {
                    currentCoroutineContext().ensureActive()
                    if (!inDone) {
                        val ii = e.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = e.getInputBuffer(ii)!!
                            buf.clear()
                            buf.order(ByteOrder.LITTLE_ENDIAN)
                            val maxFrames = buf.capacity() / (2 * outCh)
                            val startFrame = frameIdx
                            var n = 0
                            while (n < maxFrames && frameIdx < totalOut) {
                                val pos = frameIdx * step
                                val i0 = pos.toLong()
                                val fr = (pos - i0).toFloat()
                                val i1 = if (i0 + 1 < frames) i0 + 1 else i0
                                for (c in 0 until outCh) {
                                    val a = sb.get((i0 * ch + c).toInt()).toInt()
                                    val b = sb.get((i1 * ch + c).toInt()).toInt()
                                    buf.putShort((a + (b - a) * fr).toInt().coerceIn(-32768, 32767).toShort())
                                }
                                n++
                                frameIdx++
                            }
                            val pts = startFrame * 1_000_000L / outRate
                            if (n == 0) {
                                e.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                e.queueInputBuffer(ii, 0, n * outCh * 2, pts, 0)
                            }
                            onProgress(0.5f + 0.5f * frameIdx / totalOut)
                        }
                    }
                    val oi = e.dequeueOutputBuffer(oinfo, 10_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = m.addTrack(e.outputFormat)
                        m.start()
                        muxStarted = true
                    } else if (oi >= 0) {
                        val ob = e.getOutputBuffer(oi)!!
                        if (oinfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) oinfo.size = 0
                        if (oinfo.size > 0 && muxStarted) {
                            ob.position(oinfo.offset)
                            ob.limit(oinfo.offset + oinfo.size)
                            m.writeSampleData(muxTrack, ob, oinfo)
                        }
                        e.releaseOutputBuffer(oi, false)
                        if (oinfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
            return muxStarted
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return false
        } finally {
            try { dec?.release() } catch (_: Exception) {}
            try { enc?.stop() } catch (_: Exception) {}
            try { enc?.release() } catch (_: Exception) {}
            try { if (muxStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { ex.release() } catch (_: Exception) {}
            pcm.delete()
        }
    }
}
