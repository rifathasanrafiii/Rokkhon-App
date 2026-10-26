package com.example.data.sos

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaRecorder
import com.example.R
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * One PeerConnectionFactory per process, shared by the SOS sender (mic) and the listener (playback).
 * Audio is DTLS-SRTP encrypted end to end between the two phones; nothing passes through Firebase
 * except the connection set-up messages.
 */
object WebRtcSupport {
    @Volatile
    private var factory: PeerConnectionFactory? = null

    fun factory(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        synchronized(this) {
            factory?.let { return it }
            val app = context.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions()
            )
            val adm = JavaAudioDeviceModule.builder(app)
                // Raw microphone: room sound matters in an emergency, not just a voice close to the phone.
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                // Listener plays through the loudspeaker like media, not the earpiece like a call.
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .createAudioDeviceModule()
            val f = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()
            factory = f
            return f
        }
    }

    /**
     * Free public STUN finds a direct path for most mobile and home networks. Where both phones sit
     * behind strict NAT (some carrier networks) a TURN relay is needed; none is bundled because every
     * hosted one costs money. A self-hosted coturn can be set in res/values/sos_config.xml.
     */
    fun iceServers(context: Context): List<PeerConnection.IceServer> {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder(
                listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302", "stun:stun.cloudflare.com:3478")
            ).createIceServer()
        )
        val turn = context.getString(R.string.sos_turn_url).trim()
        if (turn.isNotEmpty()) {
            servers += PeerConnection.IceServer.builder(turn.split(',').map { it.trim() })
                .setUsername(context.getString(R.string.sos_turn_username))
                .setPassword(context.getString(R.string.sos_turn_credential))
                .createIceServer()
        }
        return servers
    }

    fun hasTurn(context: Context): Boolean = context.getString(R.string.sos_turn_url).isNotBlank()

    fun rtcConfig(context: Context): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers(context)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

    fun micConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory += MediaConstraints.KeyValuePair("googEchoCancellation", "false")
        mandatory += MediaConstraints.KeyValuePair("googAutoGainControl", "true")
        mandatory += MediaConstraints.KeyValuePair("googNoiseSuppression", "true")
        mandatory += MediaConstraints.KeyValuePair("googHighpassFilter", "true")
    }

    /** SdpObserver with only the callbacks a caller needs. */
    open class Sdp(
        private val onCreated: (SessionDescription) -> Unit = {},
        private val onSet: () -> Unit = {},
        private val onError: (String) -> Unit = {}
    ) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = onCreated(desc)
        override fun onSetSuccess() = onSet()
        override fun onCreateFailure(error: String?) = onError(error ?: "create failed")
        override fun onSetFailure(error: String?) = onError(error ?: "set failed")
    }
}
