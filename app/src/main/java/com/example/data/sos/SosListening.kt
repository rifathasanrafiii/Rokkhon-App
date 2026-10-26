package com.example.data.sos

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ListenState(
    val sessionId: String? = null,
    val link: AudioLink = AudioLink.IDLE,
    val quality: LinkQuality = LinkQuality.UNKNOWN,
    val muted: Boolean = false,
    val recording: Boolean = false,
    val recordingMs: Long = 0L,
    val lastSaved: File? = null
)

/**
 * The one live listening session on this phone (a listener follows one SOS at a time). Lives at
 * process level so audio and a recording keep going when the screen is left or locked.
 */
object SosListenRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var receiver: SosAudioReceiver? = null
    private var repo: SosRepository? = null
    private var recorder: SosRecorder? = null
    private var mirrorJob: Job? = null
    private var tickJob: Job? = null

    fun start(context: Context, repository: SosRepository, sessionId: String) {
        if (_state.value.sessionId == sessionId && receiver != null) return
        stop(context)
        val app = context.applicationContext
        repo = repository
        val r = SosAudioReceiver(app, repository, sessionId)
        receiver = r
        _state.value = ListenState(sessionId = sessionId, link = AudioLink.CONNECTING)
        r.start()
        mirrorJob = scope.launch {
            var lastWire: String? = null
            combine(r.link, r.quality) { l, q -> l to q }.collect { (l, q) ->
                _state.update { it.copy(link = l, quality = q) }
                val wire = when (l) {
                    AudioLink.CONNECTED -> "connected"
                    AudioLink.RECONNECTING -> "reconnecting"
                    AudioLink.FAILED -> "failed"
                    AudioLink.CONNECTING, AudioLink.IDLE -> "connecting"
                    AudioLink.STOPPED -> null
                }
                if (wire != null && wire != lastWire) repository.setListenerState(sessionId, wire, _state.value.recording)
                lastWire = wire
                if (l == AudioLink.STOPPED) {
                    // Ended by the sender, audio turned off or access revoked: also stop any recording.
                    finishRecording(app)
                    SosListenService.stop(app)
                }
            }
        }
        SosListenService.start(app, sessionId)
    }

    fun setMuted(muted: Boolean) {
        receiver?.setMuted(muted)
        _state.update { it.copy(muted = muted) }
    }

    fun retry() = receiver?.retryNow()

    /** Starts recording what is heard into app storage. Only call after the listener confirmed consent. */
    fun startRecording(context: Context, ownerName: String): Boolean {
        val r = receiver ?: return false
        val sid = _state.value.sessionId ?: return false
        if (recorder != null) return true
        val file = SosRecordings.newFile(context, ownerName)
        val rec = SosRecorder(file)
        recorder = rec
        r.setSink(rec)
        _state.update { it.copy(recording = true, recordingMs = 0L) }
        repo?.setListenerState(sid, "connected", true)
        repo?.logEventAsync(sid, "recording_started")
        tickJob = scope.launch {
            while (isActive) {
                _state.update { it.copy(recordingMs = rec.writtenMs) }
                delay(500)
            }
        }
        return true
    }

    fun stopRecording(context: Context) {
        val sid = _state.value.sessionId
        finishRecording(context.applicationContext)
        if (sid != null) {
            repo?.setListenerState(sid, if (_state.value.link == AudioLink.CONNECTED) "connected" else "connecting", false)
            repo?.logEventAsync(sid, "recording_stopped")
        }
    }

    private fun finishRecording(context: Context) {
        val rec = recorder ?: return
        recorder = null
        tickJob?.cancel()
        receiver?.setSink(null)
        rec.stop { file ->
            scope.launch { _state.update { it.copy(recording = false, lastSaved = file ?: it.lastSaved) } }
        }
    }

    fun stop(context: Context) {
        val sid = _state.value.sessionId
        finishRecording(context.applicationContext)
        mirrorJob?.cancel()
        receiver?.stop()
        receiver = null
        if (sid != null) repo?.clearListenerState(sid)
        _state.value = ListenState(lastSaved = _state.value.lastSaved)
        SosListenService.stop(context.applicationContext)
    }
}

/** Keeps the listening process alive (and its recording going) with the screen off. */
class SosListenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sid = intent?.getStringExtra("sid")
        if (sid == null || SosListenRuntime.state.value.sessionId == null) {
            stopSelf(); return START_NOT_STICKY
        }
        val n = SosNotifier.listenOngoing(this, sid, "লাইভ অডিও চলছে। বন্ধ করতে অ্যাপ খুলুন।")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, SosNotifier.ID_LISTEN, n, type) }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context, sessionId: String) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SosListenService::class.java).putExtra("sid", sessionId))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SosListenService::class.java)) }
        }
    }
}

/** Recordings kept in the app's private storage until the listener saves, shares or deletes them. */
object SosRecordings {
    private fun dir(context: Context): File = File(context.filesDir, "sos_recordings").apply { mkdirs() }

    fun newFile(context: Context, ownerName: String): File {
        val safe = ownerName.filter { it.isLetterOrDigit() }.take(20).ifBlank { "sos" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(dir(context), "Rokkhon-SOS-$safe-$stamp.m4a")
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "m4a" && f.length() > 0 }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.sosfiles", file)

    fun shareIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Copies to Downloads/Rokkhon so it survives uninstalling the app. Android 10+ only (no storage permission). */
    fun saveToDownloads(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Rokkhon")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    fun delete(file: File): Boolean = file.delete()
}
