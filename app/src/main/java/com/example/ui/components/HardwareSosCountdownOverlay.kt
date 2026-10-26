package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.triggerMethodLabel
import com.example.ui.viewmodel.PendingHardwareSos

/**
 * Full-screen cancel window shown after 3x power/volume presses.
 * The SOS is only sent when the countdown ends (or "Send now"), so an accidental pocket press
 * can be cancelled before anyone is alerted.
 */
@Composable
fun HardwareSosCountdownOverlay(
    language: AppLanguage,
    pending: PendingHardwareSos,
    liveDecibels: Int,
    onCancel: () -> Unit,
    onSendNow: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2200508))
                .testTag("hardware_sos_countdown"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (bn) "SOS পাঠানো হচ্ছে" else "Sending SOS",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = triggerMethodLabel(pending.triggerMethod, language),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE11900)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${pending.secondsLeft}",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (bn) {
                        "মাইক্রোফোন রেকর্ড হচ্ছে (${liveDecibels} dB)। ভুল করে চাপ লাগলে এখনই বাতিল করুন।"
                    } else {
                        "Recording microphone ($liveDecibels dB). Pressed by mistake? Cancel now."
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF000000)),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("hardware_sos_cancel")
                ) {
                    Text(
                        text = if (bn) "বাতিল করুন — আমি নিরাপদ" else "Cancel — I'm safe",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSendNow,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("hardware_sos_send_now")
                ) {
                    Text(
                        text = if (bn) "এখনই পাঠান" else "Send now",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
