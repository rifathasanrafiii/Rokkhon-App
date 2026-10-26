package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.emergencyTypeLabel
import com.example.data.model.triggerMethodLabel
import com.example.ui.components.RokkhonIntents
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed

/**
 * Reusable SOS Post Card (used in Find -> Active SOS and in SOS + BLE -> My Past SOS History).
 * - In Find -> Active SOS:
 *   - Shows the "Nearby / যাচ্ছি (Count)" button (`sos_nearby_respond_button_${alert.id}`)
 *   - Shows the "False / ভুয়া (X/3)" button (`sos_false_report_button_${alert.id}`) with live false count
 *   - Auto-removes when false count reaches 3/3 and warns the sender!
 * - Strictly shows the "নিরাপদ / Mark Safe" button ONLY when `isMySos == true` and `!isHistoryItem`.
 */
@Composable
fun SosPostCardItem(
    alert: SosAlert,
    language: AppLanguage,
    isMySos: Boolean,
    isHistoryItem: Boolean,
    hasRespondedNearby: Boolean = false,
    hasReportedFalse: Boolean = false,
    distanceKm: Double? = null,
    isPlaying: Boolean,
    progressSeconds: Float,
    waveformBars: List<Int>,
    onCardTapOpenVideoAudio: () -> Unit,
    onToggleInlinePlay: () -> Unit,
    onRespond: () -> Unit,
    onReportFalseAlarm: () -> Unit,
    onMarkSafeOwnerOnly: () -> Unit
) {
    val isDisputedSos = alert.isCommunityDisputed
    val isShieldedVictim = alert.isShieldedByEvidenceOrResponders
    val hoursRemaining = alert.hoursRemainingUntilAutoRemove()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisputedSos && !isShieldedVictim -> Color(0xFFFFF3E0)
                isHistoryItem -> Color(0xFFFAF7F8)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when {
                    isDisputedSos && !isShieldedVictim -> IosSystemOrange
                    isMySos && !isHistoryItem -> IosSystemRed.copy(alpha = 0.45f)
                    alert.falseAlarmReports > 0L -> IosSystemOrange.copy(alpha = 0.6f)
                    else -> Color(0xFFEDE2E4)
                },
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onCardTapOpenVideoAudio() }
            .testTag("sos_post_card_${alert.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDisputedSos && !isShieldedVictim -> IosSystemOrange.copy(alpha = 0.2f)
                                    isHistoryItem -> IosSystemGreen.copy(alpha = 0.14f)
                                    else -> LifelineBlush
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isDisputedSos && !isShieldedVictim -> Icons.Default.ReportProblem
                                isHistoryItem -> Icons.Default.CheckCircle
                                else -> Icons.Default.Shield
                            },
                            contentDescription = null,
                            tint = when {
                                isDisputedSos && !isShieldedVictim -> IosSystemOrange
                                isHistoryItem -> IosSystemGreen
                                else -> IosSystemRed
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${alert.senderName} • ${emergencyTypeLabel(alert.emergencyType, language)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isHistoryItem) Color(0xFF1A0F12) else IosSystemRed
                        )
                        val distLabel = if (distanceKm != null) {
                            val zoneTag = if (distanceKm <= SOS_NOTIFICATION_RADIUS_KM) "1km Alert Zone" else "Area Map"
                            " • ${"%.1f".format(distanceKm)} km ($zoneTag)"
                        } else {
                            ""
                        }
                        Text(
                            text = "${alert.district} • ${triggerMethodLabel(alert.triggerMethod, language)}$distLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF756568)
                        )
                    }
                }

                Surface(
                    color = when {
                        isDisputedSos && isShieldedVictim -> IosSystemGreen.copy(alpha = 0.14f)
                        isDisputedSos -> IosSystemOrange.copy(alpha = 0.18f)
                        isHistoryItem -> IosSystemGreen.copy(alpha = 0.14f)
                        else -> LifelineBlush
                    },
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDisputedSos && isShieldedVictim) Icons.Default.Verified else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = when {
                                isDisputedSos && isShieldedVictim -> IosSystemGreen
                                isDisputedSos -> IosSystemOrange
                                isHistoryItem -> IosSystemGreen
                                else -> IosSystemRed
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isDisputedSos && isShieldedVictim -> if (language == AppLanguage.BENGALI) "প্রমাণ দ্বারা সুরক্ষিত" else "Shielded (${hoursRemaining}h)"
                                isDisputedSos -> if (language == AppLanguage.BENGALI) "বিতর্কিত (${hoursRemaining}h বাকি)" else "Disputed (${hoursRemaining}h left)"
                                isHistoryItem -> if (language == AppLanguage.BENGALI) "নিরাপদ (হিস্ট্রি)" else "Safe (History)"
                                alert.hasLiveAudioVerification -> if (language == AppLanguage.BENGALI) "৫ সে. অডিও" else "5s Audio"
                                else -> if (language == AppLanguage.BENGALI) "অডিও প্রমাণ নেই" else "No audio evidence"
                            },
                            color = when {
                                isDisputedSos && isShieldedVictim -> IosSystemGreen
                                isDisputedSos -> Color(0xFFB45309)
                                isHistoryItem -> IosSystemGreen
                                else -> IosSystemRed
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = alert.verificationNote,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1A0F12)
            )

            // Live status strip showing Nearby responders count, False report count & 24h auto-remove status
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = IosSystemGreen.copy(alpha = 0.12f),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "কাছাকাছি / যাচ্ছেন: ${alert.responderCount} জন"
                        } else {
                            "Nearby / Going: ${alert.responderCount}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = IosSystemGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = if (alert.falseAlarmReports > 0L) IosSystemOrange.copy(alpha = 0.18f) else Color(0xFFF5ECEE),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "ভুয়া (False) কাউন্ট: ${alert.falseAlarmReports}"
                        } else {
                            "False Count: ${alert.falseAlarmReports}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alert.falseAlarmReports > 0L) Color(0xFFB45309) else Color(0xFF5A4A4E),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (!isHistoryItem) {
                    Surface(
                        color = Color(0xFFF3EDF7),
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "${hoursRemaining}ঘণ্টা অটো-রিমুভ"
                            } else {
                                "Auto-expires: ${hoursRemaining}h"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4A0E17),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Evidence & Responder Shield banner when 3+ False reports exist
            if (isDisputedSos && !isHistoryItem) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (isShieldedVictim) IosSystemGreen.copy(alpha = 0.10f) else IosSystemOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isShieldedVictim) {
                            if (language == AppLanguage.BENGALI) {
                                "প্রমাণ শিল্ড সক্রিয়: ${alert.falseAlarmReports}টি ভুয়া রিপোর্ট থাকা সত্ত্বেও ৫-সেকেন্ড অডিও প্রমাণ ও সাহায্যকারীর কারণে ভিক্টিমের ট্রাস্ট পয়েন্ট কাটা হয়নি। অ্যালার্টটি ২৪ ঘণ্টা সক্রিয় থাকবে।"
                            } else {
                                "Evidence & Responder Shield Active: Despite ${alert.falseAlarmReports} False reports, verified 5s media/responder presence protects the sender's Trust Points (0 pts deducted). Remains active for 24h."
                            }
                        } else {
                            if (language == AppLanguage.BENGALI) {
                                "কমিউনিটি ডিসপিউটেড (${alert.falseAlarmReports}টি False রিপোর্ট): অ্যালার্টটি সাথে সাথে মুছে ফেলা হয়নি; ২৪ ঘণ্টা পর অটো-রিমুভ হবে।"
                            } else {
                                "Community Disputed (${alert.falseAlarmReports} False reports): Kept visible with live False count and auto-removes after 24 hours."
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isShieldedVictim) Color(0xFF065F46) else Color(0xFF92400E),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5-second audio evidence, only when the sender's phone really recorded it.
            if (alert.hasLiveAudioVerification) FiveSecondEvidencePlayerCard(
                language = language,
                alert = alert,
                isPlaying = isPlaying,
                progressSeconds = progressSeconds,
                waveformBars = waveformBars,
                onTogglePlay = onToggleInlinePlay,
                onOpenFullVideoModal = onCardTapOpenVideoAudio
            )

            if (!isHistoryItem) {
                Spacer(modifier = Modifier.height(12.dp))

                if (isMySos) {
                    // ONLY the user who created the SOS sees the "Mark Safe (Remove from Map)" button
                    Button(
                        onClick = onMarkSafeOwnerOnly,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resolve_sos_safe_button_${alert.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "আমি নিরাপদ • ম্যাপ থেকে অ্যালার্ট সরান"
                            } else {
                                "Mark Myself Safe • Remove from Map"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Find -> SOS Card Action Row:
                    // 1. "Nearby / যাচ্ছি (Count)" Button (1 tap per account)
                    // 2. "False / ভুয়া (Count)" Button (Strictly 1 vote per account; never auto-deletes at 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRespond,
                            enabled = !hasRespondedNearby,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosSystemGreen,
                                disabledContainerColor = IosSystemGreen.copy(alpha = 0.18f),
                                disabledContentColor = IosSystemGreen
                            ),
                            modifier = Modifier
                                .weight(1.15f)
                                .testTag("sos_nearby_respond_button_${alert.id}")
                        ) {
                            Icon(
                                imageVector = if (hasRespondedNearby) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hasRespondedNearby) {
                                    if (language == AppLanguage.BENGALI) {
                                        "যাচ্ছেন (${alert.responderCount})"
                                    } else {
                                        "Going (${alert.responderCount})"
                                    }
                                } else {
                                    if (language == AppLanguage.BENGALI) {
                                        "কাছাকাছি / যাচ্ছি (${alert.responderCount})"
                                    } else {
                                        "Nearby / Going (${alert.responderCount})"
                                    }
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onReportFalseAlarm,
                            enabled = !hasReportedFalse,
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sos_false_report_button_${alert.id}")
                        ) {
                            Icon(
                                imageVector = if (hasReportedFalse) Icons.Default.CheckCircle else Icons.Default.ReportProblem,
                                contentDescription = "Report False SOS",
                                tint = if (hasReportedFalse) Color(0xFF9CA3AF) else IosSystemOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hasReportedFalse) {
                                    if (language == AppLanguage.BENGALI) {
                                        "ভুয়া দেওয়া হয়েছে (${alert.falseAlarmReports})"
                                    } else {
                                        "Reported False (${alert.falseAlarmReports})"
                                    }
                                } else {
                                    if (language == AppLanguage.BENGALI) {
                                        "ভুয়া / False (${alert.falseAlarmReports})"
                                    } else {
                                        "False (${alert.falseAlarmReports})"
                                    }
                                },
                                color = if (hasReportedFalse) Color(0xFF6B7280) else Color(0xFFB45309),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val context = LocalContext.current
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { RokkhonIntents.directions(context, alert.latitude, alert.longitude, language) },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sos_directions_button_${alert.id}")
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "ম্যাপ ডিরেকশন" else "Directions",
                                color = IosSystemRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { RokkhonIntents.shareSos(context, alert, language) },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sos_share_button_${alert.id}")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF1A0F12), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "শেয়ার SOS" else "Share SOS",
                                color = Color(0xFF1A0F12),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "নোট: ১টি অ্যাকাউন্ট থেকে ১ বারই False দেওয়া যাবে। ৩+ False হলেও অ্যালার্ট মুছে যাবে না (২৪ ঘণ্টা পর অটো-রিমুভ হবে) এবং প্রমাণ থাকলে ট্রাস্ট পয়েন্ট সুরক্ষিত থাকবে।"
                        } else {
                            "Note: 1 False vote per account. Alerts stay active for 24h even with 3+ False reports, and verified 5s evidence shields the sender's Trust Points."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF756568)
                    )
                }
            }
        }
    }
}

/**
 * Interactive 5-Second Audio & Video Evidence Player embedded inside each SOS Alert card.
 */
@Composable
fun FiveSecondEvidencePlayerCard(
    language: AppLanguage,
    alert: SosAlert,
    isPlaying: Boolean,
    progressSeconds: Float,
    waveformBars: List<Int>,
    onTogglePlay: () -> Unit,
    onOpenFullVideoModal: () -> Unit = {}
) {
    Surface(
        color = Color(0xFF161014),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3A2026), RoundedCornerShape(18.dp))
            .clickable { onOpenFullVideoModal() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = IosSystemGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "৫-সেকেন্ড অডিও (${"%.1f".format(progressSeconds)}s / 5.0s)"
                        } else {
                            "5s Audio (${"%.1f".format(progressSeconds)}s / 5.0s)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (isPlaying) PinCrimsonRed else IosSystemGreen,
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable { onTogglePlay() }
                        .testTag("play_5s_evidence_button_${alert.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play 5s audio",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) {
                                if (language == AppLanguage.BENGALI) "থামান" else "Stop"
                            } else {
                                if (language == AppLanguage.BENGALI) "৫-সেকেন্ড শুনুন" else "Play 5s audio"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF26161C))
                        .border(1.dp, IosSystemGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .clickable { onOpenFullVideoModal() }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val activeFraction = (progressSeconds / 5.0f).coerceIn(0f, 1f)
                        val scanY = size.height * activeFraction
                        drawCircle(
                            color = PinCrimsonRed.copy(alpha = 0.28f),
                            radius = size.minDimension * 0.36f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                        drawLine(
                            color = IosSystemGreen.copy(alpha = 0.7f),
                            start = Offset(0f, scanY),
                            end = Offset(size.width, scanY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) PinCrimsonRed else IosSystemGreen)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "MIC 5S",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (language == AppLanguage.BENGALI) "ট্যাপ করুন" else "Tap",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 3.dp)
                    )
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    val barCount = waveformBars.size.coerceAtLeast(1)
                    val spacing = 4.dp.toPx()
                    val totalSpacing = spacing * (barCount - 1)
                    val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(3f)
                    val activeFraction = (progressSeconds / 5.0f).coerceIn(0f, 1f)

                    waveformBars.forEachIndexed { idx, db ->
                        val frac = idx.toFloat() / barCount
                        val normalizedHeight = ((db.coerceIn(40, 110) - 35) / 75f).coerceIn(0.2f, 1f)
                        val bh = size.height * normalizedHeight
                        val topY = (size.height - bh) / 2f
                        val barColor = if (isPlaying && frac <= activeFraction) {
                            IosSystemGreen
                        } else {
                            Color(0xFFFFB3C0).copy(alpha = 0.55f)
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(idx * (barWidth + spacing), topY),
                            size = Size(barWidth, bh),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "GPS: ${"%.4f".format(alert.latitude)}, ${"%.4f".format(alert.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Text(
                    text = if (isPlaying) {
                        "PLAYING 5S AUDIO..."
                    } else if (language == AppLanguage.BENGALI) {
                        "বিস্তারিত দেখতে ট্যাপ করুন"
                    } else {
                        "TAP FOR SOS DETAILS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPlaying) IosSystemGreen else Color(0xFFFFB3C0),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Full Interactive SOS Video & Audio Evidence Inspector Modal (`SosVideoAudioInspectorDialog`).
 */
@Composable
fun SosVideoAudioInspectorDialog(
    language: AppLanguage,
    alert: SosAlert,
    isOwner: Boolean,
    isPlaying: Boolean,
    progressSeconds: Float,
    waveformBars: List<Int>,
    onTogglePlay: () -> Unit,
    onRespond: () -> Unit,
    onMarkSafeOwnerOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0F12)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    .testTag("sos_video_audio_inspector_modal")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PinCrimsonRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "SOS ৫-সেকেন্ড অডিও প্রমাণ"
                                    } else {
                                        "SOS 5-Second Audio Evidence"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${alert.senderName} • ${alert.district} (${triggerMethodLabel(alert.triggerMethod, language)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFB3C0)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0E080A))
                            .border(1.2.dp, if (isPlaying) IosSystemGreen else PinCrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .clickable { onTogglePlay() }
                    ) {
                        val activeFraction = (progressSeconds / 5.0f).coerceIn(0f, 1f)
                        val frameNumber = ((activeFraction * 150).toInt() + 1).coerceIn(1, 150)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            drawLine(
                                color = Color(0xFF2E1D24),
                                start = Offset(0f, h * 0.62f),
                                end = Offset(w, h * 0.62f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF3D222B),
                                start = Offset(w * 0.5f, h * 0.62f),
                                end = Offset(w * 0.12f, h),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF3D222B),
                                start = Offset(w * 0.5f, h * 0.62f),
                                end = Offset(w * 0.88f, h),
                                strokeWidth = 2.dp.toPx()
                            )

                            val pulseX = w * (0.35f + 0.3f * activeFraction)
                            val pulseY = h * 0.48f
                            drawCircle(
                                color = PinCrimsonRed.copy(alpha = 0.26f),
                                radius = 44.dp.toPx(),
                                center = Offset(pulseX, pulseY)
                            )
                            drawCircle(
                                color = IosSystemGreen.copy(alpha = 0.65f),
                                radius = 26.dp.toPx(),
                                center = Offset(pulseX, pulseY),
                                style = Stroke(width = 1.8.dp.toPx())
                            )

                            val scanY = h * activeFraction
                            drawLine(
                                color = IosSystemGreen.copy(alpha = if (isPlaying) 0.85f else 0.35f),
                                start = Offset(0f, scanY),
                                end = Offset(w, scanY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) IosSystemGreen else PinCrimsonRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REAR + FRONT CAM • FRAME $frameNumber/150 (${"%.1f".format(progressSeconds)}s / 5.0s)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF231318),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(width = 88.dp, height = 56.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "FRONT CAM\n${alert.bloodGroup}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = if (isPlaying) PinCrimsonRed.copy(alpha = 0.85f) else IosSystemGreen,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clickable { onTogglePlay() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlaying) {
                                        if (language == AppLanguage.BENGALI) "অডিও চলছে..." else "Playing 5s audio..."
                                    } else {
                                        if (language == AppLanguage.BENGALI) "৫-সেকেন্ড অডিও চালান" else "Play 5s audio"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (alert.hasLiveAudioVerification) FiveSecondEvidencePlayerCard(
                        language = language,
                        alert = alert,
                        isPlaying = isPlaying,
                        progressSeconds = progressSeconds,
                        waveformBars = waveformBars,
                        onTogglePlay = onTogglePlay,
                        onOpenFullVideoModal = onTogglePlay
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (alert.status == "ACTIVE") {
                        if (isOwner) {
                            Button(
                                onClick = onMarkSafeOwnerOnly,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "আমি নিরাপদ • ম্যাপ থেকে অ্যালার্ট সরান"
                                    } else {
                                        "Mark Myself Safe • Remove from Map"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = onRespond,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "কাছাকাছি / যাচ্ছি মার্ক করুন (+৫০ পয়েন্ট)"
                                    } else {
                                        "Mark Nearby / Going (+50 Pts)"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
