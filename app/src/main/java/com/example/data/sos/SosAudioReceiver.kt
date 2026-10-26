package com.example.data.sos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription

enum class AudioLink { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, STOPPED }

enum class LinkQuality { UNKNOWN, GOOD, FAIR, POOR }

/**
 * Receives the SOS sender's microphone on a listener's phone and plays it on the loudspeaker.
 * Reconnects with back-off when the network drops; gives up honestly (FAILED) when no route exists,
 * which is what happens on strict NATs without a TURN relay.
 */
class SosAudioReceiver(
    private val context: Context,
    private val repo: SosRepository,
    private val sessionId: String
) {
    private val main = Handler(Looper.getMainLooper())
    private val factory = WebRtcSupport.factory(context)
    private val me = repo.myUid ?: ""
    private val signal = repo.ref("sosSignaling/$sessionId/$me")

    private val _link = MutableStateFlow(AudioLink.IDLE)
    val link: StateFlow<AudioLink> = _link.asStateFlow()

    private val _quality = MutableStateFlow(LinkQuality.UNKNOWN)
    val quality: StateFlow<LinkQuality> = _quality.asStateFlow()

    private var pc: PeerConnection? = null
    private var remoteTrack: AudioTrack? = null
    private var generation: Long? = null
    private var answeredGeneration: Long? = null
    private val appliedIce = mutableSetOf<String>()
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false
    private var listener: ValueEventListener? = null
    private var attempts = 0
    private var running = false
    private var muted = false
    private var sink: AudioTrackSink? = null
    private var hadSignal = false

    private val watchdog = Runnable { if (running && _link.value != AudioLink.CONNECTED) retry() }
    private val statsTick = object : Runnable {
        override fun run() {
            if (!running) return
            pc?.getStats { report -> main.post { _quality.value = qualityOf(report) } }
            main.postDelayed(this, 4000)
        }
    }

    fun start() {
        if (running) return
        running = true
        attempts = 0
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onSignal(snapshot)
            override fun onCancelled(error: DatabaseError) {
                // Access revoked, audio turned off or the session ended.
                stopInternal(AudioLink.STOPPED, clearSignal = false)
            }
        }.also { signal.addValueEventListener(it) }
        request()
        main.post(statsTick)
    }

    private fun request() {
        closePeer()
        _link.value = if (attempts == 0) AudioLink.CONNECTING else AudioLink.RECONNECTING
        // A fresh request with a new server timestamp: the sender answers it with a fresh offer.
        signal.setValue(mapOf("request" to ServerValue.TIMESTAMP))
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, 20_000)
    }

    private fun retry() {
        if (!running) return
        attempts++
        if (attempts > 6) {
            _link.value = AudioLink.FAILED
            return
        }
        val delayMs = min(30_000L, 2_000L shl (attempts - 1))
        _link.value = AudioLink.RECONNECTING
        main.postDelayed({ if (running) request() }, delayMs)
    }

    /** Try again after FAILED (for example once the phone is back on a better network). */
    fun retryNow() {
        if (!running) return
        attempts = 0
        request()
    }

    private fun onSignal(node: DataSnapshot) {
        if (!running) return
        if (!node.exists()) {
            // Our node was deleted: by the sender (audio off / revoked) or by our own reset.
            if (hadSignal && generation != null) stopInternal(AudioLink.STOPPED, clearSignal = false)
            return
        }
        hadSignal = true
        val n = node.child("request").getValue(Long::class.java) ?: return
        if (generation != n) {
            generation = n
            answeredGeneration = null
            appliedIce.clear()
            pendingIce.clear()
            remoteSet = false
        }
        val offer = node.child("offer")
        if (answeredGeneration != n && offer.child("n").getValue(Long::class.java) == n) {
            val sdp = offer.child("sdp").getValue(String::class.java) ?: return
            answeredGeneration = n
            answer(n, sdp)
        }
        node.child("ownerIce").children.forEach { c ->
            val key = c.key ?: return@forEach
            if (c.child("n").getValue(Long::class.java) != n || !appliedIce.add(key)) return@forEach
            val cand = IceCandidate(
                c.child("sdpMid").getValue(String::class.java),
                c.child("sdpMLineIndex").getValue(Int::class.java) ?: 0,
                c.child("candidate").getValue(String::class.java) ?: return@forEach
            )
            if (remoteSet) pc?.addIceCandidate(cand) else pendingIce += cand
        }
    }

    private fun answer(n: Long, offerSdp: String) {
        closePeer()
        val peer = factory.createPeerConnection(WebRtcSupport.rtcConfig(context), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                main.post {
                    if (generation != n || !running) return@post
                    signal.child("viewerIce").push().setValue(
                        mapOf(
                            "candidate" to candidate.sdp,
                            "sdpMid" to (candidate.sdpMid ?: ""),
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "n" to n
                        )
                    )
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                main.post {
                    if (generation != n || !running) return@post
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            attempts = 0
                            main.removeCallbacks(watchdog)
                            _link.value = AudioLink.CONNECTED
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _link.value = AudioLink.RECONNECTING
                            main.removeCallbacks(watchdog)
                            main.postDelayed(watchdog, 8_000)
                        }
                        PeerConnection.IceConnectionState.FAILED -> retry()
                        else -> Unit
                    }
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                val t = receiver.track() as? AudioTrack ?: return
                main.post {
                    remoteTrack = t
                    t.setEnabled(true)
                    t.setVolume(if (muted) 0.0 else 1.0)
                    sink?.let { t.addSink(it) }
                }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }) ?: run { retry(); return }
        pc = peer
        peer.setRemoteDescription(WebRtcSupport.Sdp(onSet = {
            main.post {
                if (pc !== peer) return@post
                remoteSet = true
                pendingIce.forEach { peer.addIceCandidate(it) }
                pendingIce.clear()
                peer.createAnswer(WebRtcSupport.Sdp(onCreated = { ans ->
                    peer.setLocalDescription(WebRtcSupport.Sdp(onSet = {
                        main.post {
                            if (generation != n || !running) return@post
                            signal.child("answer").setValue(mapOf("type" to "answer", "sdp" to ans.description, "n" to n))
                        }
                    }), ans)
                }, onError = { Log.w(TAG, "answer failed: $it"); main.post { retry() } }), MediaConstraints())
            }
        }, onError = { Log.w(TAG, "offer rejected: $it"); main.post { retry() } }),
            SessionDescription(SessionDescription.Type.OFFER, offerSdp))
    }

    fun setMuted(value: Boolean) {
        muted = value
        remoteTrack?.setVolume(if (value) 0.0 else 1.0)
    }

    /** Feeds what is heard into [s] (the local recorder); null detaches it. */
    fun setSink(s: AudioTrackSink?) {
        sink?.let { old -> remoteTrack?.removeSink(old) }
        sink = s
        s?.let { remoteTrack?.addSink(it) }
    }

    private fun closePeer() {
        sink?.let { s -> remoteTrack?.removeSink(s) }
        remoteTrack = null
        pc?.let { runCatching { it.dispose() } }
        pc = null
        remoteSet = false
    }

    fun stop() = stopInternal(AudioLink.STOPPED, clearSignal = true)

    private fun stopInternal(finalState: AudioLink, clearSignal: Boolean) {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        listener?.let { signal.removeEventListener(it) }
        closePeer()
        if (clearSignal) signal.removeValue()
        _link.value = finalState
        _quality.value = LinkQuality.UNKNOWN
    }

    private fun qualityOf(report: RTCStatsReport): LinkQuality {
        var rtt: Double? = null
        var lost = 0L
        var received = 0L
        var jitter: Double? = null
        report.statsMap.values.forEach { s ->
            when (s.type) {
                "candidate-pair" -> if (s.members["state"] == "succeeded" || s.members["nominated"] == true) {
                    (s.members["currentRoundTripTime"] as? Double)?.let { rtt = it }
                }
                "inbound-rtp" -> if (s.members["kind"] == "audio") {
                    lost = (s.members["packetsLost"] as? Number)?.toLong() ?: 0L
                    received = (s.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                    jitter = s.members["jitter"] as? Double
                }
            }
        }
        if (received == 0L && rtt == null) return LinkQuality.UNKNOWN
        val lossPct = if (received + lost > 0) lost * 100.0 / (received + lost) else 0.0
        val r = rtt ?: 0.0
        val j = jitter ?: 0.0
        return when {
            lossPct > 8 || r > 0.8 || j > 0.1 -> LinkQuality.POOR
            lossPct > 2 || r > 0.35 || j > 0.05 -> LinkQuality.FAIR
            else -> LinkQuality.GOOD
        }
    }

    private companion object {
        const val TAG = "SosAudioReceiver"
    }
}
