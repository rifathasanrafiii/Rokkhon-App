package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.daysUntilEligible
import com.example.data.model.emergencyTypeLabel
import com.example.data.model.triggerMethodLabel
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed

val InkText = Color(0xFF1A1113)
val MutedText = Color(0xFF6E6265)
val CardBorder = Color(0xFFEEE6E8)
val SoftRose = Color(0xFFF5DDE0)

/** Small rounded status chip used across cards. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun EmptyStateCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, color = MutedText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Visible reminder that the lists contain sample data, so nobody calls a demo donor in a real emergency. */
@Composable
fun DemoModeBanner(language: AppLanguage, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFF7E6),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IosSystemOrange.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("demo_mode_banner")
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = IosSystemOrange, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (language == AppLanguage.BENGALI) {
                    "ডেমো মোড: এখানে দেখানো ডোনার, রোগী ও SOS নমুনা ডেটা। আসল নেটওয়ার্কের জন্য গুগল দিয়ে সাইন-ইন করুন।"
                } else {
                    "Demo mode: donors, patients and SOS shown here are sample data. Sign in with Google for the real network."
                },
                color = Color(0xFF7A4B00),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun initialsOf(name: String): String {
    val latin = Regex("""\(([^)]*)\)""").find(name)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: name
    val parts = latin.split(" ", "•").map { it.trim() }.filter { it.isNotEmpty() && it.first().isLetter() }
    return parts.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }
}

fun donationStatusText(donor: DonorNode, language: AppLanguage): Pair<String, Color> {
    val bn = language == AppLanguage.BENGALI
    if (!donor.isAvailableDonor) return (if (bn) "এখন উপলব্ধ নন" else "Not available") to MutedText
    val days = daysUntilEligible(donor.lastDonationDate)
    return when {
        days > 0 -> (if (bn) "$days দিন পর রক্ত দিতে পারবেন" else "Can donate in $days days") to IosSystemOrange
        else -> (if (bn) "এখন রক্ত দিতে পারবেন" else "Eligible now") to IosSystemGreen
    }
}

@Composable
fun LifelineResponderCard(
    donor: DonorNode,
    language: AppLanguage,
    patientGroup: String? = null,
    onCallClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val (statusText, statusColor) = donationStatusText(donor, language)
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .testTag("donor_card_${donor.userId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SoftRose),
                contentAlignment = Alignment.Center
            ) {
                Text(text = donor.bloodGroup, color = IosSystemRed, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = donor.displayName,
                        color = InkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (donor.verifiedSosCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = IosSystemBlue, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MutedText, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = donor.district,
                        color = MutedText,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = statusText, color = statusColor)
                    if (patientGroup != null && patientGroup != donor.bloodGroup) {
                        StatusChip(text = if (bn) "কম্প্যাটিবল" else "Compatible", color = IosSystemBlue)
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = LifelineBlush,
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = donor.contactPhone.isNotBlank()) { onCallClick() }
                    .testTag("call_donor_${donor.userId}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (bn) "কল" else "Call", color = IosSystemRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = InkText) {
    Column(modifier = modifier) {
        Text(text = label, color = MutedText, fontSize = 11.sp)
        Text(
            text = value.ifBlank { "-" },
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ClinicalBloodRequestCard(
    req: BloodRequest,
    language: AppLanguage,
    isOwner: Boolean,
    hasPledged: Boolean = false,
    onDonateClick: () -> Unit,
    onCallClick: () -> Unit,
    onShareClick: () -> Unit,
    onFulfillClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val urgencyColor = when (req.urgencyLevel) {
        "CRITICAL" -> PinCrimsonRed
        "URGENT" -> IosSystemOrange
        else -> IosSystemBlue
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .testTag("blood_request_card_${req.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SoftRose),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = req.bloodGroup, color = IosSystemRed, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = if (bn) "${req.unitsNeeded} ব্যাগ" else "${req.unitsNeeded} bag${if (req.unitsNeeded > 1) "s" else ""}",
                            color = IosSystemRed,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = req.patientName.ifBlank { if (bn) "রোগী" else "Patient" },
                        color = InkText,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${req.hospitalName} • ${req.district}",
                        color = MutedText,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(
                    text = req.neededDateTime.takeIf { it.isNotBlank() && it != "-" } ?: req.urgencyLevel,
                    color = urgencyColor,
                    icon = Icons.Default.Schedule
                )
                if (req.respondersCount > 0) {
                    StatusChip(
                        text = if (bn) "${req.respondersCount} জন সাড়া দিয়েছেন" else "${req.respondersCount} responded",
                        color = IosSystemGreen,
                        icon = Icons.Default.TaskAlt
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(color = Color(0xFFFAF6F6), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        MetricCell(
                            label = if (bn) "হিমোগ্লোবিন" else "Hemoglobin",
                            value = req.hemoglobinLevel,
                            valueColor = IosSystemRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCell(
                            label = if (bn) "ওয়ার্ড / কেবিন" else "Ward / cabin",
                            value = req.wardCabinNumber,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                    if (req.patientProblem.isNotBlank() && req.patientProblem != "-") {
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEDE2E4))
                        Text(
                            text = (if (bn) "সমস্যা: " else "Diagnosis: ") + req.patientProblem,
                            color = Color(0xFF3B2F31),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isOwner) {
                    Button(
                        onClick = onFulfillClick,
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fulfill_request_button_${req.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (bn) "রক্ত পেয়েছি" else "Blood arranged", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDonateClick,
                        enabled = !hasPledged,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosSystemRed,
                            disabledContainerColor = IosSystemGreen.copy(alpha = 0.16f),
                            disabledContentColor = IosSystemGreen
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("donate_blood_button_${req.id}")
                    ) {
                        Icon(
                            imageVector = if (hasPledged) Icons.Default.CheckCircle else Icons.Default.Bloodtype,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasPledged) {
                                if (bn) "সাড়া দিয়েছেন" else "Pledged"
                            } else {
                                if (bn) "আমি রক্ত দেব" else "I can donate"
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    RoundIconButton(
                        icon = Icons.Default.Call,
                        contentDescription = if (bn) "কল করুন" else "Call",
                        onClick = onCallClick,
                        tag = "call_request_${req.id}"
                    )
                }
                RoundIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = if (bn) "শেয়ার করুন" else "Share",
                    onClick = onShareClick,
                    tag = "share_request_${req.id}"
                )
            }
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
    tint: Color = IosSystemRed,
    background: Color = LifelineBlush
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Human-readable relative time ("12 min ago"). */
fun relativeTimeLabel(createdAtMillis: Long?, language: AppLanguage, nowMillis: Long = System.currentTimeMillis()): String {
    val bn = language == AppLanguage.BENGALI
    if (createdAtMillis == null) return if (bn) "এইমাত্র" else "just now"
    val minutes = ((nowMillis - createdAtMillis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> if (bn) "এইমাত্র" else "just now"
        minutes < 60 -> if (bn) "$minutes মিনিট আগে" else "$minutes min ago"
        minutes < 24 * 60 -> if (bn) "${minutes / 60} ঘণ্টা আগে" else "${minutes / 60} h ago"
        else -> if (bn) "${minutes / (24 * 60)} দিন আগে" else "${minutes / (24 * 60)} d ago"
    }
}

@Composable
fun SosEvidenceChip(alert: SosAlert, language: AppLanguage) {
    val bn = language == AppLanguage.BENGALI
    if (alert.hasLiveAudioVerification) {
        StatusChip(
            text = if (bn) "অডিও প্রমাণ • ${alert.peakAudioDecibels} dB" else "Audio evidence • ${alert.peakAudioDecibels} dB",
            color = IosSystemGreen,
            icon = Icons.Default.Mic
        )
    } else {
        StatusChip(
            text = if (bn) "প্রমাণ নেই" else "No evidence",
            color = MutedText,
            icon = Icons.Default.MicOff
        )
    }
}

/**
 * SOS card used in Find → SOS and in the user's own SOS history.
 * Responders get "Going", "Directions" and "False" (one vote each per account); the sender gets "I'm safe".
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
    onOpenDetails: () -> Unit,
    onRespond: () -> Unit,
    onDirections: () -> Unit,
    onReportFalseAlarm: () -> Unit,
    onMarkSafeOwnerOnly: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val isDisputed = alert.isCommunityDisputed
    val shielded = alert.isShieldedByEvidenceOrResponders
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isHistoryItem) 1.dp else 1.4.dp,
                color = if (isHistoryItem) CardBorder else PinCrimsonRed.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable { onOpenDetails() }
            .testTag("sos_post_card_${alert.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isHistoryItem) Color(0xFFEDE7E8) else PinCrimsonRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SOS", color = if (isHistoryItem) MutedText else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMySos) (if (bn) "আপনার SOS" else "Your SOS") else alert.senderName,
                        color = InkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(emergencyTypeLabel(alert.emergencyType, language))
                            append(" • ")
                            append(relativeTimeLabel(alert.createdAt?.toDate()?.time, language))
                            if (distanceKm != null && !isMySos) append(" • ${"%.1f".format(distanceKm)} km")
                        },
                        color = MutedText,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isHistoryItem && distanceKm != null && distanceKm <= SOS_NOTIFICATION_RADIUS_KM && !isMySos) {
                    StatusChip(text = if (bn) "১ কি.মি.-এর মধ্যে" else "Within 1 km", color = PinCrimsonRed)
                }
                SosEvidenceChip(alert, language)
                StatusChip(
                    text = if (bn) "যাচ্ছেন ${alert.responderCount}" else "Going ${alert.responderCount}",
                    color = IosSystemBlue,
                    icon = Icons.AutoMirrored.Filled.DirectionsRun
                )
                if (alert.falseAlarmReports > 0) {
                    StatusChip(
                        text = if (bn) "ভুয়া ${alert.falseAlarmReports}" else "False ${alert.falseAlarmReports}",
                        color = IosSystemOrange,
                        icon = Icons.Default.ReportProblem
                    )
                }
            }

            if (isHistoryItem) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${alert.district} • ${triggerMethodLabel(alert.triggerMethod, language)} • " +
                        if (alert.status == "RESOLVED") (if (bn) "নিরাপদ" else "Marked safe") else (if (bn) "মেয়াদ শেষ" else "Expired"),
                    color = MutedText,
                    fontSize = 12.sp
                )
            }

            if (isDisputed && !isHistoryItem) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = if (shielded) IosSystemGreen.copy(alpha = 0.10f) else IosSystemOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (shielded) {
                            if (bn) "কয়েকজন ভুয়া বলেছেন, তবে প্রমাণ/রেসপন্ডার থাকায় এটি সুরক্ষিত।" else "Some reported this as false, but evidence/responders protect it."
                        } else {
                            if (bn) "বিতর্কিত: একাধিক ভুয়া রিপোর্ট, কোনো রেসপন্ডার নেই। সাবধানে যাচাই করুন।" else "Disputed: several false reports and no responders. Verify carefully."
                        },
                        color = if (shielded) Color(0xFF065F46) else Color(0xFF92400E),
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (!isHistoryItem) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isMySos) {
                    Button(
                        onClick = onMarkSafeOwnerOnly,
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resolve_sos_safe_button_${alert.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onRespond,
                            enabled = !hasRespondedNearby,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosSystemBlue,
                                disabledContainerColor = IosSystemBlue.copy(alpha = 0.14f),
                                disabledContentColor = IosSystemBlue
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sos_nearby_respond_button_${alert.id}")
                        ) {
                            Icon(
                                imageVector = if (hasRespondedNearby) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasRespondedNearby) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        RoundIconButton(
                            icon = Icons.Default.Directions,
                            contentDescription = if (bn) "পথ দেখুন" else "Directions",
                            onClick = onDirections,
                            tag = "sos_directions_${alert.id}",
                            tint = IosSystemBlue,
                            background = IosSystemBlue.copy(alpha = 0.12f)
                        )
                        RoundIconButton(
                            icon = Icons.Default.ReportProblem,
                            contentDescription = if (bn) "ভুয়া রিপোর্ট" else "Report false",
                            onClick = { if (!hasReportedFalse) onReportFalseAlarm() },
                            tag = "sos_false_report_button_${alert.id}",
                            tint = if (hasReportedFalse) MutedText else IosSystemOrange,
                            background = if (hasReportedFalse) Color(0xFFEDE7E8) else IosSystemOrange.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Details sheet for an SOS. Plays the audio clip only when it was recorded on this phone;
 * other people's clips are not uploaded, so the sheet says so instead of faking playback.
 */
@Composable
fun SosDetailsDialog(
    language: AppLanguage,
    alert: SosAlert,
    isOwner: Boolean,
    hasLocalClip: Boolean,
    isPlaying: Boolean,
    progressSeconds: Float,
    hasResponded: Boolean,
    distanceKm: Double?,
    onTogglePlay: () -> Unit,
    onRespond: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onMarkSafeOwnerOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("sos_details_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOwner) (if (bn) "আপনার SOS" else "Your SOS") else alert.senderName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Text(
                            text = "${emergencyTypeLabel(alert.emergencyType, language)} • ${relativeTimeLabel(alert.createdAt?.toDate()?.time, language)}",
                            color = MutedText,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = if (bn) "বন্ধ" else "Close", tint = InkText)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                DetailRow(if (bn) "এলাকা" else "Area", alert.district)
                if (distanceKm != null && !isOwner) DetailRow(if (bn) "দূরত্ব" else "Distance", "${"%.2f".format(distanceKm)} km")
                DetailRow(if (bn) "কীভাবে পাঠানো" else "Triggered by", triggerMethodLabel(alert.triggerMethod, language))
                DetailRow(if (bn) "রক্তের গ্রুপ" else "Blood group", alert.bloodGroup)
                DetailRow(if (bn) "যাচ্ছেন" else "Going", "${alert.responderCount}")
                DetailRow(if (bn) "ভুয়া রিপোর্ট" else "False reports", "${alert.falseAlarmReports}")
                if (alert.status == "ACTIVE") {
                    DetailRow(
                        if (bn) "অটো-রিমুভ" else "Auto-expires",
                        if (bn) "${alert.hoursRemainingUntilAutoRemove()} ঘণ্টা পর" else "in ${alert.hoursRemainingUntilAutoRemove()} h"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = Color(0xFFF6F3F4), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SosEvidenceChip(alert, language)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (hasLocalClip) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundIconButton(
                                    icon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (bn) "অডিও চালান" else "Play audio",
                                    onClick = onTogglePlay,
                                    tag = "play_sos_audio_${alert.id}",
                                    tint = IosSystemGreen,
                                    background = IosSystemGreen.copy(alpha = 0.12f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { (progressSeconds / 5f).coerceIn(0f, 1f) },
                                        color = IosSystemGreen,
                                        trackColor = Color(0xFFE2D5D7),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${"%.1f".format(progressSeconds)}s / 5.0s", color = MutedText, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                text = if (alert.hasLiveAudioVerification) {
                                    if (bn) {
                                        "প্রেরকের ফোনে ৫ সেকেন্ডের অডিও রেকর্ড হয়েছে (সর্বোচ্চ ${alert.peakAudioDecibels} dB)। গোপনীয়তার জন্য অডিও আপলোড হয় না।"
                                    } else {
                                        "A 5-second clip was recorded on the sender's phone (peak ${alert.peakAudioDecibels} dB). Audio is not uploaded, for privacy."
                                    }
                                } else {
                                    if (bn) "এই SOS-এর সাথে কোনো অডিও প্রমাণ নেই।" else "This SOS has no audio evidence."
                                },
                                color = Color(0xFF3B2F31),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (alert.status == "ACTIVE") {
                    if (isOwner) {
                        Button(
                            onClick = onMarkSafeOwnerOnly,
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRespond,
                                enabled = !hasResponded,
                                colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (hasResponded) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(onClick = onDirections, shape = CircleShape, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = IosSystemBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (bn) "পথ দেখুন" else "Directions", color = IosSystemBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(onClick = onShare, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = InkText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (bn) "লোকেশন শেয়ার করুন" else "Share location", color = InkText, fontWeight = FontWeight.Bold)
                }
                if (!isOwner && alert.status == "ACTIVE") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (bn) "নিজের নিরাপত্তা আগে। বিপদ গুরুতর হলে ৯৯৯-এ কল করুন।" else "Your own safety first. If it is serious, call 999.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MutedText, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = value, color = InkText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
