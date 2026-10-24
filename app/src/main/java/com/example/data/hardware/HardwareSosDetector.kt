package com.example.data.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pocket-Safe HardwareSosDetector:
 * - Shake detection is intentionally REMOVED so walking or running with the phone in a pocket
 *   can never accidentally trigger a false SOS alarm.
 * - Listens exclusively for intentional 3x hardware button presses:
 *   1. 3x Power Button Screen On/Off Toggles (ACTION_SCREEN_OFF / ACTION_SCREEN_ON within 3.4s)
 *   2. 3x Rapid Volume Up / Volume Down Key Presses (within 2.6s)
 *   3. In-App 3x Power Tap Simulator (for quick emulator testing)
 */
class HardwareSosDetector(
    context: Context,
    private val onHardwareSosTriggered: (triggerMethod: String) -> Unit
) {
    private val appContext = context.applicationContext

    private val powerToggleTimestamps = ArrayDeque<Long>()
    private val volumePressTimestamps = ArrayDeque<Long>()
    private val simulatedPowerTimestamps = ArrayDeque<Long>()

    private var lastTriggerElapsedRealtime = 0L
    private var receiverRegistered = false

    private val _isHardwareGuardArmed = MutableStateFlow(true)
    val isHardwareGuardArmed: StateFlow<Boolean> = _isHardwareGuardArmed.asStateFlow()

    private val _recentPowerTapCount = MutableStateFlow(0)
    val recentPowerTapCount: StateFlow<Int> = _recentPowerTapCount.asStateFlow()

    private val _lastTriggerSource = MutableStateFlow("ARMED_3X_POWER_AND_VOLUME")
    val lastTriggerSource: StateFlow<String> = _lastTriggerSource.asStateFlow()

    private val screenPowerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!_isHardwareGuardArmed.value) return
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                recordHardwarePress(
                    queue = powerToggleTimestamps,
                    windowMs = 3400L,
                    triggerMethod = "3X_POWER_BUTTON"
                )
            }
        }
    }

    fun startListening() {
        if (!receiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                appContext.registerReceiver(screenPowerReceiver, filter)
                receiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    fun stopListening() {
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(screenPowerReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    fun setHardwareGuardArmed(armed: Boolean) {
        _isHardwareGuardArmed.value = armed
    }

    /**
     * Handles physical Volume Up / Volume Down key presses from Activity.onKeyDown().
     * Returns true if 3x rapid presses triggered the SOS flow.
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!_isHardwareGuardArmed.value) return false
        if ((event?.repeatCount ?: 0) > 0) return false
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return recordHardwarePress(
                queue = volumePressTimestamps,
                windowMs = 2600L,
                triggerMethod = "3X_VOLUME_BUTTON"
            )
        }
        return false
    }

    /**
     * Allows testing the exact 3x rapid Power Button cadence directly inside the UI or Emulator.
     */
    fun recordSimulatedPowerButtonTap(): Int {
        val now = SystemClock.elapsedRealtime()
        while (simulatedPowerTimestamps.isNotEmpty() && now - simulatedPowerTimestamps.first() > 2600L) {
            simulatedPowerTimestamps.removeFirst()
        }
        simulatedPowerTimestamps.addLast(now)
        val count = simulatedPowerTimestamps.size
        _recentPowerTapCount.value = count

        if (count >= 3) {
            simulatedPowerTimestamps.clear()
            _recentPowerTapCount.value = 0
            fireTrigger("3X_POWER_BUTTON")
            return 3
        }
        return count
    }

    private fun recordHardwarePress(
        queue: ArrayDeque<Long>,
        windowMs: Long,
        triggerMethod: String
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        while (queue.isNotEmpty() && now - queue.first() > windowMs) {
            queue.removeFirst()
        }
        queue.addLast(now)
        _recentPowerTapCount.value = queue.size

        if (queue.size >= 3) {
            queue.clear()
            _recentPowerTapCount.value = 0
            fireTrigger(triggerMethod)
            return true
        }
        return false
    }

    private fun fireTrigger(method: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerElapsedRealtime < 4000L) return
        lastTriggerElapsedRealtime = now
        _lastTriggerSource.value = method
        onHardwareSosTriggered(method)
    }
}
