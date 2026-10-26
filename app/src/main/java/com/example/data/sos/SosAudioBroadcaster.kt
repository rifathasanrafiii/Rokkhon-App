package com.example.data.sos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver

/**
 * Sends this phone's microphone, one way, to each authorised listener (one peer connection each).
 *
 * A listener writes sosSignaling/{sid}/{uid}/request; this side answers with an offer only if the
 * listener's grant is accepted and includes audio. Revoking the grant or turning audio off closes
 * that listener's connection at once. The phone never plays anything back.
 */
class SosAudioBroadcaster(
    private val context: Context,
    private val repo: SosRepository,
    private val sessionId: String,
    private val ownerUid: String
) {
    private val main = Handler(Looper.getMainLooper())
    private val factory = WebRtcSupport.factory(context)
    private var source: AudioSource? = null
    private var track: AudioTrack? = null

    private class Peer(val viewerUid: String, val generation: Long, val pc: PeerConnection) {
        val appliedIce = mutableSetOf<String>()
        var answered = false
    }

    private val peers = mutableMapOf<String, Peer>()
    private val allowed = mutableMapOf<String, Boolean>()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    /** Listeners whose audio connection is up right now. */
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private var signalingRef: DatabaseReference? = null
    private var signalingListener: ChildEventListener? = null
    private var accessRef: DatabaseReference? = null
    private var accessListener: ValueEventListener? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        source = factory.createAudioSource(WebRtcSupport.micConstraints())
        track = factory.createAudioTrack("rokkhon-sos-mic", source).apply { setEnabled(true) }

        // Who may listen, kept live so a revoke takes effect without waiting for the next request.
        accessRef = repo.ref("sosAccess/$ownerUid").also { ref ->
            accessListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allowed.clear()
                    snapshot.children.forEach { g ->
                        val uid = g.key ?: return@forEach
                        allowed[uid] = g.child("status").getValue(String::class.java) == "accepted" &&
                            g.child("audio").getValue(Boolean::class.java) == true
                    }
                    peers.keys.filter { allowed[it] != true }.forEach { closePeer(it, removeSignal = true) }
                }
                override fun onCancelled(error: DatabaseError) {}
            }.also { ref.addValueEventListener(it) }
        }

        signalingRef = repo.ref("sosSignaling/$sessionId").also { ref ->
            signalingListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = onSignal(snapshot)
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = onSignal(snapshot)
                override fun onChildRemoved(snapshot: DataSnapshot) {
                    snapshot.key?.let { closePeer(it, removeSignal = false) }
                }
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            }.also { ref.addChildEventListener(it) }
        }
    }

    private fun onSignal(node: DataSnapshot) {
        val viewer = node.key ?: return
        val request = node.child("request").getValue(Long::class.java) ?: return
        if (allowed[viewer] != true) return
        var peer = peers[viewer]
        if (peer == null || peer.generation != request) {
            closePeer(viewer, removeSignal = false)
            peer = createPeer(viewer, request) ?: return
        }
        val answer = node.child("answer")
        if (!peer.answered && answer.child("n").getValue(Long::class.java) == request) {
            val sdp = answer.child("sdp").getValue(String::class.java) ?: return
            peer.answered = true
            peer.pc.setRemoteDescription(
                WebRtcSupport.Sdp(onError = { Log.w(TAG, "answer rejected: $it") }),
                org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.ANSWER, sdp)
            )
        }
        if (peer.answered) {
            node.child("viewerIce").children.forEach { c ->
                val key = c.key ?: return@forEach
                if (c.child("n").getValue(Long::class.java) != request || !peer.appliedIce.add(key)) return@forEach
                peer.pc.addIceCandidate(
                    IceCandidate(
                        c.child("sdpMid").getValue(String::class.java),
                        c.child("sdpMLineIndex").getValue(Int::class.java) ?: 0,
                        c.child("candidate").getValue(String::class.java) ?: return@forEach
                    )
                )
            }
        }
    }

    private fun createPeer(viewer: String, generation: Long): Peer? {
        val signal = repo.ref("sosSignaling/$sessionId/$viewer")
        val pc = factory.createPeerConnection(WebRtcSupport.rtcConfig(context), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                main.post {
                    if (peers[viewer]?.generation != generation) return@post
                    signal.child("ownerIce").push().setValue(
                        mapOf(
                            "candidate" to candidate.sdp,
                            "sdpMid" to (candidate.sdpMid ?: ""),
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "n" to generation
                        )
                    )
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                main.post {
                    if (peers[viewer]?.generation != generation) return@post
                    val up = state == PeerConnection.IceConnectionState.CONNECTED ||
                        state == PeerConnection.IceConnectionState.COMPLETED
                    _connected.value = if (up) _connected.value + viewer else _connected.value - viewer
                    if (state == PeerConnection.IceConnectionState.CONNECTED) repo.logEventAsync(sessionId, "listening")
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
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        }) ?: return null

        pc.addTransceiver(
            track,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf("rokkhon-sos"))
        )
        val peer = Peer(viewer, generation, pc)
        peers[viewer] = peer
        pc.createOffer(WebRtcSupport.Sdp(onCreated = { offer ->
            pc.setLocalDescription(WebRtcSupport.Sdp(onSet = {
                main.post {
                    if (peers[viewer]?.generation != generation) return@post
                    signal.child("offer").setValue(mapOf("type" to "offer", "sdp" to offer.description, "n" to generation))
                }
            }), offer)
        }, onError = { Log.w(TAG, "offer failed: $it") }), MediaConstraints())
        return peer
    }

    private fun closePeer(viewer: String, removeSignal: Boolean) {
        peers.remove(viewer)?.let { p -> runCatching { p.pc.dispose() } }
        _connected.value = _connected.value - viewer
        if (removeSignal) repo.ref("sosSignaling/$sessionId/$viewer").removeValue()
    }

    fun stop() {
        if (!running) return
        running = false
        signalingListener?.let { signalingRef?.removeEventListener(it) }
        accessListener?.let { accessRef?.removeEventListener(it) }
        peers.keys.toList().forEach { closePeer(it, removeSignal = false) }
        runCatching { track?.dispose() }
        runCatching { source?.dispose() }
        track = null
        source = null
        _connected.value = emptySet()
    }

    private companion object {
        const val TAG = "SosAudioBroadcaster"
    }
}
