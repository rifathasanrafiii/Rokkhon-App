package com.example.data.media

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 5-second microphone "blackbox" for Rokkhon SOS.
 * - Records real microphone PCM while an SOS is being prepared and reports the measured level.
 * - Never invents evidence: when the mic is unavailable or permission is denied,
 *   [isAudioVerified] stays false and the level stays 0.
 * - Clips live only on the sender's device (they are not uploaded), so playback is only
 *   possible for alerts recorded on this phone; [hasLocalClip] tells the UI which ones.
 */
class LiveAudioVerifier {

    private val _currentDecibels = MutableStateFlow(0)
    val currentDecibels: StateFlow<Int> = _currentDecibels.asStateFlow()

    private val _peakDecibels = MutableStateFlow(0)
    val peakDecibels: StateFlow<Int> = _peakDecibels.asStateFlow()

    private val _isAudioVerified = MutableStateFlow(false)
    val isAudioVerified: StateFlow<Boolean> = _isAudioVerified.asStateFlow()

    private val _activePlaybackAlertId = MutableStateFlow<String?>(null)
    val activePlaybackAlertId: StateFlow<String?> = _activePlaybackAlertId.asStateFlow()

    private val _playbackProgressSeconds = MutableStateFlow(0f)
    val playbackProgressSeconds: StateFlow<Float> = _playbackProgressSeconds.asStateFlow()

    private val _waveformSamples = MutableStateFlow<List<Int>>(emptyList())
    val waveformSamples: StateFlow<List<Int>> = _waveformSamples.asStateFlow()

    private val recordedPcmByAlertId = ConcurrentHashMap<String, ShortArray>()
    @Volatile
    private var latestFiveSecondBuffer: ShortArray? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @SuppressLint("MissingPermission")
    fun startLiveMonitoring(scope: CoroutineScope) {
        stopLiveMonitoring()
        _peakDecibels.value = 0
        _currentDecibels.value = 0
        _isAudioVerified.value = false
        _waveformSamples.value = emptyList()
        latestFiveSecondBuffer = null

        recordingJob = scope.launch(Dispatchers.IO) {
            val totalFiveSecSamples = SAMPLE_RATE * 5
            val fiveSecCapture = ShortArray(totalFiveSecSamples)
            var writeCursor = 0
            val dynamicBars = ArrayDeque<Int>()

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE / 10)

            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@launch
                }
                audioRecord = recorder
                recorder.startRecording()
                // ~100 ms per read; read() blocks, so no extra delay is needed (a delay would drop audio).
                val buffer = ShortArray(SAMPLE_RATE / 10)

                while (isActive) {
                    val readCount = recorder.read(buffer, 0, buffer.size)
                    if (readCount <= 0) continue
                    var sum = 0.0
                    for (i in 0 until readCount) {
                        val sample = buffer[i]
                        sum += sample.toDouble() * sample
                        if (writeCursor < totalFiveSecSamples) {
                            fiveSecCapture[writeCursor++] = sample
                        }
                    }
                    val rms = sqrt(sum / readCount)
                    // dB relative to 1 LSB of 16-bit PCM (0..~90). Not calibrated SPL, but real and comparable.
                    val db = if (rms > 1.0) (20 * log10(rms)).toInt().coerceIn(0, 120) else 0
                    _currentDecibels.value = db
                    if (db > _peakDecibels.value) _peakDecibels.value = db
                    _isAudioVerified.value = true

                    dynamicBars.addLast(db)
                    if (dynamicBars.size > 20) dynamicBars.removeFirst()
                    _waveformSamples.value = dynamicBars.toList()
                    latestFiveSecondBuffer = fiveSecCapture.copyOf(writeCursor)
                }
            } catch (_: SecurityException) {
                // Microphone permission denied: leave evidence flags false.
            } catch (_: Exception) {
                // Microphone busy/unavailable: leave evidence flags false.
            }
        }
    }

    /** True when real microphone samples with audible content were captured. */
    fun hasCapturedAudio(): Boolean {
        val buf = latestFiveSecondBuffer ?: return false
        return buf.count { abs(it.toInt()) > 200 } > 200
    }

    fun attachLatestFiveSecondClipToAlert(alertId: String) {
        val buf = latestFiveSecondBuffer
        if (buf != null && buf.any { it.toInt() != 0 }) {
            recordedPcmByAlertId[alertId] = buf.copyOf()
        }
    }

    fun moveClip(fromAlertId: String, toAlertId: String) {
        recordedPcmByAlertId.remove(fromAlertId)?.let { recordedPcmByAlertId[toAlertId] = it }
    }

    fun hasLocalClip(alertId: String): Boolean = recordedPcmByAlertId.containsKey(alertId)

    /**
     * Plays the real 5-second clip for [alertId] if it was recorded on this device.
     * Returns false (and plays nothing) when no clip exists.
     */
    fun togglePlayFiveSecondClip(
        scope: CoroutineScope,
        alertId: String
    ): Boolean {
        if (_activePlaybackAlertId.value == alertId) {
            stopPlayback()
            return true
        }
        val pcmToPlay = recordedPcmByAlertId[alertId] ?: return false
        stopPlayback()
        _activePlaybackAlertId.value = alertId
        _playbackProgressSeconds.value = 0f

        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val chunkSize = SAMPLE_RATE / 10
                var offset = 0
                while (isActive && offset < pcmToPlay.size) {
                    val count = minOf(chunkSize, pcmToPlay.size - offset)
                    track.write(pcmToPlay, offset, count)
                    offset += count
                    _playbackProgressSeconds.value = (offset.toFloat() / SAMPLE_RATE).coerceAtMost(5.0f)
                }
            } catch (_: Exception) {
                // Audio output unavailable; nothing to play.
            } finally {
                stopPlayback()
            }
        }
        return true
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        _activePlaybackAlertId.value = null
        _playbackProgressSeconds.value = 0f
    }

    fun stopLiveMonitoring() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}
