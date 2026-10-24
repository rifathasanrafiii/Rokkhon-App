package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.ble.BleSosMeshManager
import com.example.data.model.AppLanguage
import com.example.data.model.BleSosBeacon
import com.google.firebase.Timestamp
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.daysUntilEligible
import com.example.data.model.emergencyTypeLabel
import com.example.data.model.triggerMethodLabel
import com.example.ui.theme.Rk

val InkText = Rk.Ink
val MutedText = Rk.Muted
val CardBorder = Rk.Line
val SoftRose = Rk.Rose

/** Small rounded status chip used across cards. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(color = color.copy(alpha = 0.11f), shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun EmptyStateCard(text: String, modifier: Modifier = Modifier) {
    RkCard(modifier = modifier, color = Rk.SurfaceMuted, border = Rk.SurfaceMuted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Rk.Muted, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, color = Rk.Muted, fontSize = 14.sp)
        }
    }
}

/** Slim reminder that the lists show sample data, so nobody calls a demo donor in a real emergency. */
@Composable
fun DemoModeBanner(language: AppLanguage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Rk.AmberSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("demo_mode_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Rk.Amber, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (language == AppLanguage.BENGALI) {
                "ডেমো মোড • নমুনা ডেটা দেখানো হচ্ছে"
            } else {
                "Demo mode • showing sample data"
            },
            color = Rk.Amber,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun initialsOf(name: String): String {
    val latin = Regex("""\(([^)]*)\)""").find(name)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: name
    val parts = latin.split(" ", "•").map { it.trim() }.filter { it.isNotEmpty() && it.first().isLetter() }
    return parts.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }
}

fun donationStatusText(donor: DonorNode, language: AppLanguage): Pair<String, Color> {
    val bn = language == AppLanguage.BENGALI
    if (!donor.isAvailableDonor) return (if (bn) "এখন উপলব্ধ নন" else "Not available") to Rk.Muted
    val days = daysUntilEligible(donor.lastDonationDate)
    return if (days > 0) {
        (if (bn) "$days দিন পর দিতে পারবেন" else "Can donate in $days days") to Rk.Amber
    } else {
        (if (bn) "এখন দিতে পারবেন" else "Can donate now") to Rk.Green
    }
}

/** Rounded-square blood group badge. */
@Composable
fun BloodBadge(group: String, size: Int = 48, filled: Boolean = false, subtitle: String? = null) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(if (filled) Rk.Crimson else Rk.Rose),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = group,
                color = if (filled) Color.White else Rk.Crimson,
                fontSize = (size * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (size * 0.38f).sp
            )
            if (subtitle != null) {
                Text(subtitle, color = if (filled) Color.White.copy(alpha = 0.85f) else Rk.Crimson, fontSize = 10.sp, lineHeight = 12.sp)
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
    tint: Color = Rk.Crimson,
    background: Color = Rk.Rose,
    size: Int = 44
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((size * 0.45f).dp))
    }
}

@Composable
fun LifelineResponderCard(
    donor: DonorNode,
    language: AppLanguage,
    patientGroup: String? = null,
    distanceKm: Double? = null,
    onCallClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val (statusText, statusColor) = donationStatusText(donor, language)
    RkCard(modifier = Modifier.testTag("donor_card_${donor.userId}"), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BloodBadge(donor.bloodGroup)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = donor.displayName,
                        color = Rk.Ink,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (donor.verifiedSosCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Rk.Blue, modifier = Modifier.size(15.dp))
                    }
                }
                Text(
                    text = listOfNotNull(
                        donor.district.substringBefore(" •").takeIf { it.isNotBlank() },
                        distanceKm?.let { "${"%.1f".format(it)} km" }
                    ).joinToString(" • "),
                    color = Rk.Muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = statusText, color = statusColor)
                    if (patientGroup != null && patientGroup != donor.bloodGroup) {
                        StatusChip(text = if (bn) "কম্প্যাটিবল" else "Compatible", color = Rk.Blue)
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            RoundIconButton(
                icon = Icons.Default.Call,
                contentDescription = if (bn) "কল করুন" else "Call",
                onClick = { if (donor.contactPhone.isNotBlank()) onCallClick() },
                tag = "call_donor_${donor.userId}",
                tint = Rk.Green,
                background = Rk.GreenSoft
            )
        }
    }
}

@Composable
fun ClinicalBloodRequestCard(
    req: BloodRequest,
    language: AppLanguage,
    isOwner: Boolean,
    hasPledged: Boolean = false,
    distanceKm: Double? = null,
    onDonateClick: () -> Unit,
    onCallClick: () -> Unit,
    onShareClick: () -> Unit,
    onFulfillClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val (urgencyText, urgencyColor) = when (req.urgencyLevel) {
        "CRITICAL" -> (if (bn) "এখনই দরকার" else "Needed now") to Rk.Crimson
        "URGENT" -> (if (bn) "জরুরি" else "Urgent") to Rk.Amber
        else -> (if (bn) "আজকের মধ্যে" else "Today") to Rk.Blue
    }
    RkCard(modifier = Modifier.testTag("blood_request_card_${req.id}")) {
        Row(verticalAlignment = Alignment.Top) {
            BloodBadge(req.bloodGroup, size = 52, filled = true, subtitle = if (bn) "${req.unitsNeeded} ব্যাগ" else "${req.unitsNeeded} bag")
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = req.hospitalName,
                        color = Rk.Ink,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = listOfNotNull(
                        req.district,
                        distanceKm?.let { "${"%.1f".format(it)} km" },
                        relativeTimeLabel(req.createdAt?.toDate()?.time, language)
                    ).joinToString(" • "),
                    color = Rk.Muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(text = urgencyText, color = urgencyColor)
                    if (req.respondersCount > 0) {
                        StatusChip(
                            text = if (bn) "${req.respondersCount} জন সাড়া দিয়েছেন" else "${req.respondersCount} responded",
                            color = Rk.Green
                        )
                    }
                }
            }
        }

        val details = listOfNotNull(
            req.patientName.takeIf { it.isNotBlank() && it != "Patient" }?.let { (if (bn) "রোগী: " else "Patient: ") + it },
            req.patientProblem.takeIf { it.isNotBlank() && it != "-" },
            req.hemoglobinLevel.takeIf { it.isNotBlank() && it != "-" }?.let { "Hb $it" },
            req.wardCabinNumber.takeIf { it.isNotBlank() && it != "-" }
        )
        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = details.joinToString(" • "),
                color = Rk.InkSoft,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isOwner) {
                PrimaryButton(
                    text = if (bn) "রক্ত পেয়েছি" else "Blood arranged",
                    onClick = onFulfillClick,
                    icon = Icons.Default.CheckCircle,
                    color = Rk.Green,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fulfill_request_button_${req.id}")
                )
            } else {
                PrimaryButton(
                    text = if (hasPledged) (if (bn) "সাড়া দিয়েছেন" else "Pledged") else (if (bn) "আমি রক্ত দেব" else "I can donate"),
                    onClick = onDonateClick,
                    enabled = !hasPledged,
                    icon = if (hasPledged) Icons.Default.CheckCircle else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("donate_blood_button_${req.id}")
                )
                RoundIconButton(
                    icon = Icons.Default.Call,
                    contentDescription = if (bn) "কল করুন" else "Call",
                    onClick = onCallClick,
                    tag = "call_request_${req.id}",
                    tint = Rk.Green,
                    background = Rk.GreenSoft,
                    size = 48
                )
            }
            RoundIconButton(
                icon = Icons.Default.Share,
                contentDescription = if (bn) "শেয়ার করুন" else "Share",
                onClick = onShareClick,
                tag = "share_request_${req.id}",
                tint = Rk.InkSoft,
                background = Rk.SurfaceMuted,
                size = 48
            )
        }
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

/** SOS alerts that arrived over Bluetooth only (no internet) carry this id prefix. */
const val BLE_SOS_ID_PREFIX = "ble_"

fun SosAlert.isBluetoothOnly(): Boolean = id.startsWith(BLE_SOS_ID_PREFIX)

/**
 * SOS heard over the Bluetooth mesh that the cloud doesn't have (sender or receiver offline),
 * shown next to the cloud alerts. A cloud alert carries the same BLE code, so it isn't listed twice.
 */
fun bluetoothOnlySosAlerts(beacons: List<BleSosBeacon>, cloudAlerts: List<SosAlert>): List<SosAlert> {
    val cloudCodes = cloudAlerts.mapNotNull { it.bleBeaconHash.takeIf { code -> code.isNotBlank() } }.toSet()
    return beacons
        .filter { it.packetKind == BleSosMeshManager.PACKET_KIND_SOS_1KM && it.senderCode !in cloudCodes }
        .filter { it.distanceFromOriginKm == null || it.distanceFromOriginKm <= SOS_NOTIFICATION_RADIUS_KM }
        .map { b ->
            SosAlert(
                id = "$BLE_SOS_ID_PREFIX${b.packetId}",
                senderName = b.senderCode,
                bloodGroup = b.bloodGroup,
                emergencyType = b.emergencyType,
                latitude = b.latitude,
                longitude = b.longitude,
                hasLiveAudioVerification = b.hasCameraAudioProof,
                triggerMethod = "BLE_MESH",
                bleBeaconHash = b.senderCode,
                status = "ACTIVE",
                createdAt = Timestamp(java.util.Date(b.timestampMillis))
            )
        }
}

@Composable
fun SosEvidenceChip(alert: SosAlert, language: AppLanguage) {
    val bn = language == AppLanguage.BENGALI
    if (alert.hasLiveAudioVerification) {
        StatusChip(
            text = if (bn) "অডিও প্রমাণ" else "Audio evidence",
            color = Rk.Green,
            icon = Icons.Default.Mic
        )
    } else {
        StatusChip(
            text = if (bn) "প্রমাণ নেই" else "No evidence",
            color = Rk.Muted,
            icon = Icons.Default.MicOff
        )
    }
}

/**
 * SOS card used in Map → SOS and in the user's own SOS history.
 * Responders get "I'm going", "Directions" and "False" (one vote each per account); the sender gets "I'm safe".
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
    val viaBle = alert.isBluetoothOnly()
    RkCard(
        modifier = Modifier.testTag("sos_post_card_${alert.id}"),
        onClick = onOpenDetails,
        border = if (isHistoryItem) Rk.Line else Rk.RoseLine
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isHistoryItem) Rk.SurfaceMuted else Rk.Maroon),
                contentAlignment = Alignment.Center
            ) {
                Text("SOS", color = if (isHistoryItem) Rk.Muted else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isMySos -> if (bn) "আপনার SOS" else "Your SOS"
                        viaBle -> if (bn) "কাছের রক্ষণ ইউজার" else "Nearby Rokkhon user"
                        else -> alert.senderName
                    },
                    color = Rk.Ink,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        emergencyTypeLabel(alert.emergencyType, language),
                        relativeTimeLabel(alert.createdAt?.toDate()?.time, language),
                        distanceKm?.takeIf { !isMySos }?.let { "${"%.1f".format(it)} km" }
                    ).joinToString(" • "),
                    color = Rk.Muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!isHistoryItem && distanceKm != null && distanceKm <= SOS_NOTIFICATION_RADIUS_KM && !isMySos) {
                StatusChip(text = if (bn) "১ কি.মি.-এর মধ্যে" else "Within 1 km", color = Rk.Crimson)
            }
            if (viaBle) {
                StatusChip(text = if (bn) "ব্লুটুথ" else "Bluetooth", color = Rk.Blue, icon = Icons.Default.Bluetooth)
            } else {
                SosEvidenceChip(alert, language)
            }
            if (alert.responderCount > 0) {
                StatusChip(
                    text = if (bn) "${alert.responderCount} জন যাচ্ছেন" else "${alert.responderCount} going",
                    color = Rk.Blue
                )
            }
        }

        if (isHistoryItem) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${alert.district} • ${triggerMethodLabel(alert.triggerMethod, language)} • " +
                    if (alert.status == "RESOLVED") (if (bn) "নিরাপদ" else "Marked safe") else (if (bn) "মেয়াদ শেষ" else "Expired"),
                color = Rk.Muted,
                fontSize = 12.sp
            )
        }

        if (alert.isCommunityDisputed && !isHistoryItem) {
            Spacer(modifier = Modifier.height(10.dp))
            val shielded = alert.isShieldedByEvidenceOrResponders
            Text(
                text = if (shielded) {
                    if (bn) "কয়েকজন ভুয়া বলেছেন, তবে প্রমাণ/রেসপন্ডার থাকায় এটি সুরক্ষিত।" else "Some reported this as false, but evidence/responders protect it."
                } else {
                    if (bn) "বিতর্কিত: একাধিক ভুয়া রিপোর্ট, কোনো রেসপন্ডার নেই।" else "Disputed: several false reports and no responders."
                },
                color = if (shielded) Rk.Green else Rk.Amber,
                fontSize = 12.5.sp
            )
        }

        if (!isHistoryItem) {
            Spacer(modifier = Modifier.height(12.dp))
            if (isMySos) {
                PrimaryButton(
                    text = if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS",
                    onClick = onMarkSafeOwnerOnly,
                    icon = Icons.Default.CheckCircle,
                    color = Rk.Green,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resolve_sos_safe_button_${alert.id}")
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(
                        text = if (hasRespondedNearby) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"),
                        onClick = onRespond,
                        enabled = !hasRespondedNearby && !viaBle,
                        icon = if (hasRespondedNearby) null else Icons.AutoMirrored.Filled.DirectionsRun,
                        color = Rk.Blue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sos_nearby_respond_button_${alert.id}")
                    )
                    RoundIconButton(
                        icon = Icons.Default.Directions,
                        contentDescription = if (bn) "পথ দেখুন" else "Directions",
                        onClick = onDirections,
                        tag = "sos_directions_${alert.id}",
                        tint = Rk.Blue,
                        background = Rk.BlueSoft,
                        size = 48
                    )
                    if (!viaBle) {
                        RoundIconButton(
                            icon = Icons.Default.ReportProblem,
                            contentDescription = if (bn) "ভুয়া রিপোর্ট" else "Report false",
                            onClick = { if (!hasReportedFalse) onReportFalseAlarm() },
                            tag = "sos_false_report_button_${alert.id}",
                            tint = if (hasReportedFalse) Rk.Faint else Rk.Amber,
                            background = if (hasReportedFalse) Rk.SurfaceMuted else Rk.AmberSoft,
                            size = 48
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
    val viaBle = alert.isBluetoothOnly()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Rk.Surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                            text = when {
                                isOwner -> if (bn) "আপনার SOS" else "Your SOS"
                                viaBle -> if (bn) "কাছের রক্ষণ ইউজার" else "Nearby Rokkhon user"
                                else -> alert.senderName
                            },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Rk.Ink
                        )
                        Text(
                            text = "${emergencyTypeLabel(alert.emergencyType, language)} • ${relativeTimeLabel(alert.createdAt?.toDate()?.time, language)}",
                            color = Rk.Muted,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = if (bn) "বন্ধ" else "Close", tint = Rk.Ink)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (!viaBle) DetailRow(if (bn) "এলাকা" else "Area", alert.district)
                if (distanceKm != null && !isOwner) DetailRow(if (bn) "দূরত্ব" else "Distance", "${"%.2f".format(distanceKm)} km")
                DetailRow(
                    if (bn) "কীভাবে এসেছে" else "Received via",
                    if (viaBle) (if (bn) "ব্লুটুথ (ইন্টারনেট ছাড়া)" else "Bluetooth (no internet)") else triggerMethodLabel(alert.triggerMethod, language)
                )
                DetailRow(if (bn) "রক্তের গ্রুপ" else "Blood group", alert.bloodGroup)
                if (!viaBle) {
                    DetailRow(if (bn) "যাচ্ছেন" else "Going", "${alert.responderCount}")
                    DetailRow(if (bn) "ভুয়া রিপোর্ট" else "False reports", "${alert.falseAlarmReports}")
                }
                if (alert.status == "ACTIVE" && !viaBle) {
                    DetailRow(
                        if (bn) "অটো-রিমুভ" else "Auto-expires",
                        if (bn) "${alert.hoursRemainingUntilAutoRemove()} ঘণ্টা পর" else "in ${alert.hoursRemainingUntilAutoRemove()} h"
                    )
                }

                if (!viaBle) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RkCard(color = Rk.SurfaceMuted, border = Rk.SurfaceMuted, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        SosEvidenceChip(alert, language)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (hasLocalClip) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundIconButton(
                                    icon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (bn) "অডিও চালান" else "Play audio",
                                    onClick = onTogglePlay,
                                    tag = "play_sos_audio_${alert.id}",
                                    tint = Rk.Green,
                                    background = Rk.GreenSoft
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { (progressSeconds / 5f).coerceIn(0f, 1f) },
                                        color = Rk.Green,
                                        trackColor = Rk.Line,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${"%.1f".format(progressSeconds)}s / 5.0s", color = Rk.Muted, fontSize = 12.sp)
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
                                color = Rk.InkSoft,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (alert.status == "ACTIVE") {
                    if (isOwner) {
                        PrimaryButton(
                            text = if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS",
                            onClick = onMarkSafeOwnerOnly,
                            color = Rk.Green,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!viaBle) {
                                PrimaryButton(
                                    text = if (hasResponded) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"),
                                    onClick = onRespond,
                                    enabled = !hasResponded,
                                    color = Rk.Blue,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SecondaryButton(
                                text = if (bn) "পথ দেখুন" else "Directions",
                                onClick = onDirections,
                                icon = Icons.Default.Directions,
                                color = Rk.Blue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SecondaryButton(
                    text = if (bn) "লোকেশন শেয়ার করুন" else "Share location",
                    onClick = onShare,
                    icon = Icons.Default.Share,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isOwner && alert.status == "ACTIVE") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (bn) "নিজের নিরাপত্তা আগে। বিপদ গুরুতর হলে ৯৯৯-এ কল করুন।" else "Your own safety first. If it is serious, call 999.",
                        color = Rk.Muted,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = Rk.Muted, fontSize = 13.5.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = value, color = Rk.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(thickness = 0.5.dp, color = Rk.Line)
    }
}
