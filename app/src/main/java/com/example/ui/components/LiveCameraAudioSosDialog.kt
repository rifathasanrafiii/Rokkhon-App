package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.media.LiveAudioVerifier
import com.example.data.model.AppLanguage
import com.example.data.model.SOS_EMERGENCY_TYPES
import com.example.data.model.emergencyTypeLabel
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import kotlinx.coroutines.delay

/**
 * In-app SOS: records 5 seconds of real microphone audio while counting down, lets the user
 * pick what kind of emergency it is, then sends automatically (or immediately on "Send now").
 * Evidence flags reflect what was actually captured — nothing is filled in when the mic is
 * unavailable or permission is denied.
 */
@Composable
fun LiveCameraAudioSosDialog(
    language: AppLanguage,
    userBloodGroup: String,
    districtName: String,
    triggerMethod: String = "ONE_TAP_SOS",
    audioVerifier: LiveAudioVerifier,
    onDismiss: () -> Unit,
    onDispatchVerifiedSos: (
        emergencyType: String,
        hasLiveCamera: Boolean,
        hasLiveAudio: Boolean,
        peakDecibels: Int,
        verificationNote: String,
        triggerMethod: String
    ) -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var emergencyType by remember { mutableStateOf("ROAD_DANGER") }
    var countdownSeconds by remember { mutableIntStateOf(SOS_COUNTDOWN_SECONDS) }
    var dispatched by remember { mutableStateOf(false) }

    val currentDb by audioVerifier.currentDecibels.collectAsStateWithLifecycle()
    val peakDb by audioVerifier.peakDecibels.collectAsStateWithLifecycle()
    val isAudioLive by audioVerifier.isAudioVerified.collectAsStateWithLifecycle()
    val waveform by audioVerifier.waveformSamples.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission
        if (hasAudioPermission) audioVerifier.startLiveMonitoring(scope)
    }

    fun dispatch() {
        if (dispatched) return
        dispatched = true
        val hasAudio = audioVerifier.hasCapturedAudio()
        val note = if (hasAudio) "Mic audio attached ($peakDb dB peak)" else "No audio evidence"
        onDispatchVerifiedSos(emergencyType, false, hasAudio, if (hasAudio) peakDb else 0, note, triggerMethod)
    }

    LaunchedEffect(Unit) {
        if (hasAudioPermission) {
            audioVerifier.startLiveMonitoring(scope)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    DisposableEffect(Unit) {
        onDispose { audioVerifier.stopLiveMonitoring() }
    }

    LaunchedEffect(Unit) {
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds -= 1
        }
        dispatch()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PinCrimsonRed.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = PinCrimsonRed)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bn) "জরুরি SOS পাঠানো হচ্ছে" else "Sending emergency SOS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF1A0F12),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (bn) {
                                "$countdownSeconds সেকেন্ডে অটো পাঠানো হবে • $districtName"
                            } else {
                                "Sends automatically in ${countdownSeconds}s • $districtName"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF756568)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sos_dialog_button")) {
                        Icon(Icons.Default.Close, contentDescription = if (bn) "বাতিল" else "Cancel", tint = Color(0xFF1A0F12))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (bn) "কী ধরনের বিপদ? (ঐচ্ছিক)" else "What's happening? (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A0F12)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SOS_EMERGENCY_TYPES.chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { type ->
                            val selected = type == emergencyType
                            Surface(
                                color = if (selected) PinCrimsonRed else Color.White,
                                contentColor = if (selected) Color.White else Color(0xFF1A0F12),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, if (selected) PinCrimsonRed else Color(0xFFE6DFE0), RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { emergencyType = type }
                                    .testTag("sos_type_$type")
                            ) {
                                Text(
                                    text = emergencyTypeLabel(type, language),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Live microphone level (real readings only)
                Surface(
                    color = if (hasAudioPermission) Color(0xFFF2FBF5) else IosSystemOrange.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasAudioPermission) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (hasAudioPermission) IosSystemGreen else IosSystemOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !hasAudioPermission -> if (bn) "মাইক্রোফোন পারমিশন নেই" else "No microphone permission"
                                    isAudioLive -> if (bn) "অডিও রেকর্ড হচ্ছে • $currentDb dB" else "Recording audio • $currentDb dB"
                                    else -> if (bn) "মাইক্রোফোন চালু হচ্ছে…" else "Starting microphone…"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (!hasAudioPermission) {
                                if (bn) "SOS তবুও যাবে, তবে অডিও প্রমাণ থাকবে না।" else "The SOS still goes out, just without audio evidence."
                            } else if (bn) {
                                "৫ সেকেন্ডের অডিও শুধু আপনার ফোনে থাকে; কেউ 'ভুয়া' রিপোর্ট করলে এটি আপনাকে সুরক্ষা দেয়।"
                            } else {
                                "The 5s clip stays on your phone; it protects you if someone reports your SOS as false."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5E5153)
                        )
                        if (hasAudioPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            ) {
                                val bars = 20
                                val gap = 3.dp.toPx()
                                val barWidth = (size.width - gap * (bars - 1)) / bars
                                val padded = List(bars - waveform.size.coerceAtMost(bars)) { 0 } + waveform.takeLast(bars)
                                padded.forEachIndexed { i, db ->
                                    val h = (db.coerceIn(0, 90) / 90f).coerceAtLeast(0.06f) * size.height
                                    drawRoundRect(
                                        color = IosSystemGreen,
                                        topLeft = Offset(i * (barWidth + gap), size.height - h),
                                        size = Size(barWidth, h),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = LifelineBlush,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PinCrimsonRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (bn) {
                                "পাঠানোর পর ১ কি.মি.-এর মধ্যে রক্ষণ ইউজাররা জানবেন (ইন্টারনেট + ব্লুটুথ) এবং 'নিরাপদ' মার্ক না করা পর্যন্ত আপনার লাইভ লোকেশন শেয়ার হবে। রক্তের গ্রুপ: $userBloodGroup"
                            } else {
                                "Rokkhon users within 1 km are alerted (internet + Bluetooth) and your live location is shared until you mark yourself safe. Blood group: $userBloodGroup"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A0E17)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_sos_button")
                    ) {
                        Text(if (bn) "বাতিল" else "Cancel", fontWeight = FontWeight.Bold, color = Color(0xFF1A0F12))
                    }
                    Button(
                        onClick = { dispatch() },
                        colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("dispatch_verified_sos_button")
                    ) {
                        Text(
                            text = if (bn) "এখনই পাঠান ($countdownSeconds)" else "Send now ($countdownSeconds)",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

private const val SOS_COUNTDOWN_SECONDS = 5
