package com.example.data.sos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GpsProblem { NONE, NO_PERMISSION, LOCATION_OFF, WAITING_FOR_FIX }

enum class MicStatus { OFF, NO_PERMISSION, READY, LIVE }

sealed interface PushStatus {
    data object NotSent : PushStatus
    data object Sending : PushStatus
    data class Sent(val delivered: Int, val failed: Int, val noDevice: Int) : PushStatus
    /** The trusted notification server is not set up or not reachable; see README. */
    data class Unavailable(val reason: String) : PushStatus
}

/** The automatic SMS to the numbers the user set (separate from the push to Rokkhon accounts). */
sealed interface SmsStatus {
    data object None : SmsStatus
    data object Sending : SmsStatus
    /** [ok] texts left the phone; that is not a delivery receipt. */
    data class Sent(val ok: Int, val failed: Int) : SmsStatus
    data object NeedsPermission : SmsStatus
    data object NoTelephony : SmsStatus
}

/** What the SOS sender's phone is doing right now. Written by [PrivateSosService], read by the UI. */
data class OwnerSosState(
    val sessionId: String? = null,
    val startedAtMs: Long = 0L,
    val lastFix: LiveFix? = null,
    val lastPublishMs: Long = 0L,
    val intervalMs: Long = AdaptiveInterval.FAST_MS,
    val gpsProblem: GpsProblem = GpsProblem.WAITING_FOR_FIX,
    val locationOn: Boolean = true,
    val mic: MicStatus = MicStatus.OFF,
    val listening: Set<String> = emptySet(),
    val online: Boolean = true,
    val push: PushStatus = PushStatus.NotSent,
    val sms: SmsStatus = SmsStatus.None
) {
    val active: Boolean get() = sessionId != null
}

object SosRuntime {
    /** Outlives screens: the alert to trusted people must finish even if the user navigates away. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _owner = MutableStateFlow(OwnerSosState())
    val owner: StateFlow<OwnerSosState> = _owner.asStateFlow()

    fun update(block: (OwnerSosState) -> OwnerSosState) = _owner.update(block)

    fun reset() {
        _owner.value = OwnerSosState()
    }
}

/**
 * GPS update pace: every 5 s while moving, every 10 s when standing still or when the battery is
 * low, so a long SOS does not drain the phone it depends on.
 */
class AdaptiveInterval {
    private var stillFixes = 0

    var current: Long = FAST_MS
        private set

    /** Returns the interval to use from now on. */
    fun onFix(speedMps: Float?, movedMeters: Float?, batteryPercent: Int?, charging: Boolean): Long {
        val moving = (speedMps ?: 0f) >= 1.0f || (movedMeters ?: 0f) >= 15f
        stillFixes = if (moving) 0 else stillFixes + 1
        val lowBattery = batteryPercent != null && batteryPercent <= 20 && !charging
        current = if (lowBattery || stillFixes >= 3) SLOW_MS else FAST_MS
        return current
    }

    companion object {
        const val FAST_MS = 5_000L
        const val SLOW_MS = 10_000L
    }
}
