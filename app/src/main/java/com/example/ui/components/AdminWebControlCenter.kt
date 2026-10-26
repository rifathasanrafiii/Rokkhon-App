package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.viewmodel.RokkhonViewModel

/**
 * User-facing enforcement lock screen displayed when the user's account is suspended
 * or their hardware device ID is banned remotely from the standalone Admin Web Control Center.
 * Contains zero admin access or override controls inside the user app.
 */
@Composable
fun AdminEnforcementLockScreen(
    viewModel: RokkhonViewModel,
    onSignOut: () -> Unit
) {
    val isDeviceBanned by viewModel.isCurrentDeviceBanned.collectAsStateWithLifecycle()
    val banReason by viewModel.currentDeviceBanReason.collectAsStateWithLifecycle()
    val suspendReason by viewModel.currentAccountSuspendReason.collectAsStateWithLifecycle()

    val title = if (isDeviceBanned) {
        "এই ডিভাইসটি ব্যান করা হয়েছে"
    } else {
        "আপনার অ্যাকাউন্ট সাসপেন্ড করা হয়েছে"
    }

    val subtitle = if (isDeviceBanned) {
        "নিরাপত্তা নীতিমালা লঙ্ঘনের কারণে আপনার ডিভাইস আইডি ব্লক করা হয়েছে।"
    } else {
        "নীতিমালা লঙ্ঘনের কারণে আপনার অ্যাকাউন্টের অ্যাক্সেস সাময়িকভাবে স্থগিত করা হয়েছে।"
    }

    val reasonText = if (isDeviceBanned) banReason else suspendReason

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0508),
                        Color(0xFF090C15),
                        Color(0xFF05070D)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp)
            .testTag("admin_enforcement_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121726)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                        .border(2.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDeviceBanned) Icons.Default.Block else Icons.Default.GppBad,
                        contentDescription = "Enforcement Alert",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isDeviceBanned) "HARDWARE DEVICE BANNED" else "ACCOUNT SUSPENDED",
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Details Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B0F1A))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ইউজার:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(
                            text = "${viewModel.currentUserName} (${viewModel.currentUserId})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ডিভাইস আইডি:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(
                            text = viewModel.currentDeviceId,
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    Text(
                        text = "কারণ: $reasonText",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("enforcement_sign_out_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed)
                ) {
                    Text(
                        text = "লগআউট করুন",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
