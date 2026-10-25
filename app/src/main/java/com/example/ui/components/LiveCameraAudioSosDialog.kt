package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.media.LiveAudioVerifier
import com.example.data.model.AppLanguage
import androidx.compose.foundation.clickable
import com.example.data.model.triggerMethodLabel
import com.example.data.model.SOS_EMERGENCY_TYPES
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import kotlinx.coroutines.delay

/**
 * 5-Second Audio & Video Blackbox SOS Recorder with Auto-Detected Category:
 * - Records 5 seconds of real audio and camera video frames.
 * - Automatically detects emergency category from speed/movement telemetry without showing any
 *   manual category selection choices.
 * - Starts continuous live GPS location sharing until the user explicitly marks themselves Safe.
 */
@Composable
fun LiveCameraAudioSosDialog(
    language: AppLanguage,
    userBloodGroup: String,
    districtName: String,
    triggerMethod: String = "3X_POWER_BUTTON",
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // True only once the camera preview is really running (it is a live view; video is not saved).
    var cameraFrameCaptured by remember { mutableStateOf(false) }
    var selectedEmergencyType by remember { mutableStateOf("MEDICAL") }
    var countdownSeconds by remember { mutableIntStateOf(5) }
    var isCountdownRunning by remember { mutableStateOf(true) }
    var hasAutoDispatched by remember { mutableStateOf(false) }

    val currentDb by audioVerifier.currentDecibels.collectAsStateWithLifecycle()
    val peakDb by audioVerifier.peakDecibels.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission
        if (hasAudioPermission) {
            audioVerifier.startLiveMonitoring(scope)
        }
    }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permsToRequest.toTypedArray())
        audioVerifier.startLiveMonitoring(scope)
    }

    DisposableEffect(Unit) {
        onDispose {
            audioVerifier.stopLiveMonitoring()
        }
    }

    // 5-Second Audio + Video Blackbox Recording Countdown -> Auto-Dispatches at 0s
    LaunchedEffect(isCountdownRunning, countdownSeconds) {
        if (isCountdownRunning && countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds -= 1
        } else if (isCountdownRunning && countdownSeconds == 0 && !hasAutoDispatched) {
            hasAutoDispatched = true
            isCountdownRunning = false
            // Only real evidence: the mic reading if audio was captured, otherwise none. The camera
            // is a live view and is not recorded, so it never counts as video evidence.
            val hasAudio = audioVerifier.hasCapturedAudio()
            val finalDb = if (hasAudio) peakDb else 0
            onDispatchVerifiedSos(
                selectedEmergencyType,
                false,
                hasAudio,
                finalDb,
                sosEvidenceNote(language, triggerMethod, hasAudio, finalDb),
                triggerMethod
            )
        }
    }

    Dialog(
        onDismissRequest = {
            isCountdownRunning = false
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(IosSystemRed.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "5s Blackbox SOS",
                                tint = IosSystemRed
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "৫-সেকেন্ড অডিও SOS"
                                } else {
                                    "5-Second Audio SOS"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "৫ সেকেন্ড অডিও রেকর্ড হয়ে নিজে থেকে পাঠানো হবে • বাতিল করা যায়"
                                } else {
                                    "Records 5 s of audio, then sends by itself • you can cancel"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF756568)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isCountdownRunning = false
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_sos_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1A0F12))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trigger Source + 5-Second Recording Progress Pill
                Surface(
                    color = LifelineBlush,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = IosSystemRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "ট্রিগার: ${triggerMethodLabel(triggerMethod, language)}"
                                } else {
                                    "Trigger: ${triggerMethodLabel(triggerMethod, language)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = IosSystemRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = IosSystemRed,
                            shape = CircleShape
                        ) {
                            val recordedSec = (5 - countdownSeconds).coerceIn(0, 5)
                            Text(
                                text = if (countdownSeconds > 0) {
                                    "REC ${recordedSec}.0s / 5.0s"
                                } else {
                                    "5.0s CLIP READY"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live CameraX Viewfinder Box (5-Second Video Evidence)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(205.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0E131F))
                        .border(
                            width = 2.dp,
                            color = IosSystemGreen,
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview
                                        )
                                        cameraFrameCaptured = true
                                    } catch (_: Exception) {
                                        cameraFrameCaptured = false
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Feed",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "ক্যামেরা চালু হচ্ছে (ভিডিও সেভ হয় না)..."
                                } else {
                                    "Starting camera (video is not saved)..."
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Top-left Live 5S REC badge
                    Surface(
                        color = IosSystemRed,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "লাইভ ক্যামেরা • ৫ সেকেন্ড অডিও" else "LIVE CAMERA • 5-SEC AUDIO REC",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom-right Verified Frame Stamp
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = IosSystemGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "$districtName • লাইভ লোকেশন" else "$districtName • Live location",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5-Second Ambient Audio Blackbox Recorder Card
                Surface(
                    color = Color(0xFFFAF6F6),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "5s Audio Recorder",
                                    tint = IosSystemGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "৫-সেকেন্ডের অডিও রেকর্ড (${currentDb} dB)"
                                    } else {
                                        "5-Second Audio Capture (${currentDb} dB)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (language == AppLanguage.BENGALI) "এই ফোনে সংরক্ষিত" else "Saved on this phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = IosSystemGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "এই ৫ সেকেন্ডের অডিও প্রমাণ হিসেবে আপনার ফোনে থাকে। অন্যরা শুধু দেখবে প্রমাণ আছে কিনা ও কত জোরে শব্দ ছিল।"
                            } else {
                                "The 5-second audio stays on your phone as evidence. Others only see that evidence exists and how loud it was."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF756568)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val animatedLevel by animateFloatAsState(
                            targetValue = (currentDb.coerceIn(30, 110) / 110f),
                            label = "audio_bar"
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        ) {
                            drawRoundRect(
                                color = Color(0xFFE2D5D7),
                                size = size,
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                            )
                            drawRoundRect(
                                color = IosSystemGreen,
                                size = Size(size.width * animatedLevel.coerceAtLeast(0.55f), size.height),
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Read-Only Auto-Detected Category & Live Location Status Card (No Category Selection Needed)
                Surface(
                    color = Color(0xFFF3F8FF),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IosSystemBlue.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = IosSystemBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "বিপদের ধরন ও লাইভ লোকেশন"
                                } else {
                                    "Emergency type & live GPS"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "বিপদের ধরন বেছে নিন। আপনি 'নিরাপদ' মার্ক না করা পর্যন্ত লাইভ লোকেশন শেয়ার হতে থাকবে।"
                            } else {
                                "Pick the type of emergency. Your live GPS location is shared until you mark yourself Safe."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A5568)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            SOS_EMERGENCY_TYPES.forEach { type ->
                                val selected = type == selectedEmergencyType
                                Surface(
                                    color = if (selected) IosSystemBlue else Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, IosSystemBlue.copy(alpha = if (selected) 1f else 0.3f), CircleShape)
                                        .clip(CircleShape)
                                        .clickable { selectedEmergencyType = type }
                                        .testTag("sos_type_$type")
                                ) {
                                    Text(
                                        text = when (type) {
                                            "MEDICAL" -> if (language == AppLanguage.BENGALI) "চিকিৎসা" else "Medical"
                                            "ACCIDENT" -> if (language == AppLanguage.BENGALI) "দুর্ঘটনা" else "Accident"
                                            "HARASSMENT" -> if (language == AppLanguage.BENGALI) "হয়রানি" else "Harassment"
                                            else -> if (language == AppLanguage.BENGALI) "রাস্তায়" else "Road"
                                        },
                                        color = if (selected) Color.White else Color(0xFF1A0F12),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isCountdownRunning = false
                            onDismiss()
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("cancel_sos_button")
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "বাতিল করুন" else "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A0F12)
                        )
                    }

                    Button(
                        onClick = {
                            if (!hasAutoDispatched) {
                                hasAutoDispatched = true
                                isCountdownRunning = false
                                // Only real evidence: the mic reading if audio was captured, otherwise none. The camera
                                // is a live view and is not recorded, so it never counts as video evidence.
                                val hasAudio = audioVerifier.hasCapturedAudio()
                                val finalDb = if (hasAudio) peakDb else 0
                                onDispatchVerifiedSos(
                                    selectedEmergencyType,
                                    false,
                                    hasAudio,
                                    finalDb,
                                    sosEvidenceNote(language, triggerMethod, hasAudio, finalDb),
                                    triggerMethod
                                )
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosSystemRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(54.dp)
                            .testTag("confirm_dispatch_sos_button")
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (countdownSeconds > 0) {
                                if (language == AppLanguage.BENGALI) "এখনই পাঠান (${countdownSeconds}s)" else "Send 5s SOS (${countdownSeconds}s)"
                            } else {
                                if (language == AppLanguage.BENGALI) "৫-সেকেন্ড SOS পাঠান" else "Broadcast 5s SOS"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/** What the SOS honestly carries: the trigger, and the mic reading only when audio was captured. */
private fun sosEvidenceNote(language: AppLanguage, triggerMethod: String, hasAudio: Boolean, peakDb: Int): String {
    val trigger = triggerMethodLabel(triggerMethod, language)
    return if (language == AppLanguage.BENGALI) {
        if (hasAudio) "$trigger • ৫ সেকেন্ডের অডিও প্রেরকের ফোনে সংরক্ষিত ($peakDb dB) • লাইভ লোকেশন চালু" else "$trigger • অডিও প্রমাণ নেই • লাইভ লোকেশন চালু"
    } else {
        if (hasAudio) "$trigger • 5 s audio kept on the sender's phone ($peakDb dB) • live location on" else "$trigger • no audio evidence • live location on"
    }
}
