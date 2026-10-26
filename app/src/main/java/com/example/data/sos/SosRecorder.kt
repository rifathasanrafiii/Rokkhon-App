package com.example.data.sos

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import org.webrtc.AudioTrackSink

/**
 * Records what a listener hears into an AAC .m4a file on this phone only. Gets 16-bit PCM from the
 * remote WebRTC track (AudioTrackSink, 10 ms chunks) and encodes it on its own thread with the
 * platform AAC encoder. Nothing is uploaded anywhere.
 */
class SosRecorder(val file: File) : AudioTrackSink {
    private val thread = HandlerThread("sos-recorder").apply { start() }
    private val handler = Handler(thread.looper)

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var sampleRate = 0
    private var channels = 0
    private var framesQueued = 0L
    @Volatile private var stopped = false

    /** Audio frames written so far, for the duration shown while recording. */
    @Volatile var writtenMs: Long = 0L
        private set

    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long
    ) {
        if (stopped || bitsPerSample != 16) return
        val bytes = ByteArray(numberOfFrames * numberOfChannels * 2)
        val dup = audioData.duplicate()
        dup.rewind()
        dup.get(bytes, 0, minOf(bytes.size, dup.remaining()))
        handler.post { encode(bytes, sampleRate, numberOfChannels, numberOfFrames) }
    }

    private fun encode(pcm: ByteArray, rate: Int, ch: Int, frames: Int) {
        if (stopped) return
        try {
            if (codec == null) {
                sampleRate = rate
                channels = ch
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 64_000 * ch)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                }
                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            if (rate != sampleRate || ch != channels) return
            val c = codec ?: return
            var offset = 0
            while (offset < pcm.size) {
                val index = c.dequeueInputBuffer(10_000)
                if (index < 0) break
                val buf = c.getInputBuffer(index) ?: break
                buf.clear()
                val n = minOf(buf.remaining(), pcm.size - offset)
                buf.put(pcm, offset, n)
                val pts = framesQueued * 1_000_000L / sampleRate
                c.queueInputBuffer(index, 0, n, pts, 0)
                framesQueued += n / (2 * channels)
                offset += n
            }
            writtenMs = framesQueued * 1000L / sampleRate
            drain(endOfStream = false)
        } catch (e: Exception) {
            Log.w(TAG, "encode failed", e)
        }
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var idle = 0
        while (true) {
            val index = c.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                // At the end, wait up to ~1 s for the encoder to flush, never forever.
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++idle > 100) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer!!.addTrack(c.outputFormat)
                    muxer!!.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val out = c.getOutputBuffer(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted && out != null) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer!!.writeSampleData(trackIndex, out, info)
                    }
                    c.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Finishes the file. Returns it, or null if nothing was heard (the empty file is deleted). */
    fun stop(onDone: (File?) -> Unit) {
        handler.post {
            stopped = true
            var ok = false
            try {
                codec?.let { c ->
                    val index = c.dequeueInputBuffer(10_000)
                    if (index >= 0) c.queueInputBuffer(index, 0, 0, framesQueued * 1_000_000L / maxOf(sampleRate, 1), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    drain(endOfStream = true)
                }
                ok = muxerStarted && framesQueued > 0
            } catch (e: Exception) {
                Log.w(TAG, "finish failed", e)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                if (muxerStarted) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
                codec = null
                muxer = null
            }
            if (!ok) file.delete()
            thread.quitSafely()
            onDone(if (ok) file else null)
        }
    }

    private companion object {
        const val TAG = "SosRecorder"
    }
}
