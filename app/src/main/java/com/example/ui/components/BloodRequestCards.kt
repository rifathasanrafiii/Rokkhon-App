package com.example.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SosAlert
import com.example.data.model.daysUntilEligible
import com.example.data.model.triggerMethodLabel
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SelectedMapPin
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush

/**
 * Available Donor Card using clean vector icons (Zero Emojis).
 */
@Composable
fun LifelineResponderCard(
    donor: DonorNode,
    language: AppLanguage = AppLanguage.ENGLISH,
    onCallClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    // The 90-day rule, in words (the raw saved date meant nothing to a patient's family).
    val daysLeft = daysUntilEligible(donor.lastDonationDate)
    val statusText = when {
        !donor.isAvailableDonor -> if (bn) "এখন উপলব্ধ নন" else "Not available now"
        daysLeft > 0 -> if (bn) "$daysLeft দিন পর রক্ত দিতে পারবেন" else "Can donate again in $daysLeft days"
        else -> if (bn) "এখন রক্ত দিতে পারবেন" else "Available now"
    }
    val statusColor = if (donor.isEligibleNow) IosSystemGreen else IosSystemOrange
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(54.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(LifelineBlush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = donor.bloodGroup,
                        style = MaterialTheme.typography.titleLarge,
                        color = IosSystemRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(IosSystemGreen)
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = donor.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = UIcons.Verified,
                        contentDescription = "Verified Donor",
                        tint = IosSystemRed,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${donor.bloodGroup} Blood Group • Trust ${donor.trustScore}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF545454),
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = UIcons.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF5E5E5E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = donor.district,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5E5E5E)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                color = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .border(1.2.dp, IosSystemRed.copy(alpha = 0.45f), CircleShape)
                    .clickable { onCallClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = UIcons.Call,
                        contentDescription = "Contact",
                        tint = IosSystemRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Contact",
                        style = MaterialTheme.typography.labelLarge,
                        color = IosSystemRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Clinical Bangladesh Blood Request Card:
 * - Enforces 1-tap-per-user-account lock on "রক্ত দিন / Donate Blood" (`hasPledged`)
 * - Allows the requester (`isOwner`) to remove/archive the post when blood is managed (`onFulfillClick`).
 */
@Composable
fun ClinicalBloodRequestCard(
    req: BloodRequest,
    language: AppLanguage,
    isOwner: Boolean,
    hasPledged: Boolean = false,
    onDonateClick: () -> Unit,
    onCallClick: () -> Unit,
    onFulfillClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(IosSystemRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = req.bloodGroup,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${req.unitsNeeded} ব্যাগ",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "রোগী: ${req.patientName}"
                            } else {
                                "Patient: ${req.patientName}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${req.hospitalName} • ${req.district}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF545454),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    color = LifelineBlush,
                    shape = CircleShape
                ) {
                    Text(
                        text = req.neededDateTime.ifBlank { req.urgencyLevel },
                        color = IosSystemRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clinical Metrics Box (Hemoglobin, Bags, Problem, Ward/Cabin)
            Surface(
                color = Color(0xFFF6F6F6),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.8.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "হিমোগ্লোবিন (Hb)" else "Hemoglobin (Hb)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5E5E5E)
                            )
                            Text(
                                text = req.hemoglobinLevel.ifBlank { "6.5 g/dL" },
                                style = MaterialTheme.typography.titleSmall,
                                color = IosSystemRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "রক্তের পরিমাণ" else "Blood Bags",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5E5E5E)
                            )
                            Text(
                                text = "${req.unitsNeeded} ব্যাগ (Bags)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF000000),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "ওয়ার্ড / কেবিন" else "Ward / Cabin",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5E5E5E)
                            )
                            Text(
                                text = req.wardCabinNumber.ifBlank { "Emergency Ward" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF000000),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE8E8E8))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = UIcons.MedicalServices,
                            contentDescription = null,
                            tint = IosSystemRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "সমস্যা: ${req.patientProblem}"
                            } else {
                                "Diagnosis: ${req.patientProblem}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Your own request: no "Donate" (you can't pledge to yourself), only Call + Managed.
                if (!isOwner) Button(
                    onClick = onDonateClick,
                    enabled = !hasPledged,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosSystemRed,
                        disabledContainerColor = IosSystemGreen.copy(alpha = 0.16f),
                        disabledContentColor = IosSystemGreen
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("donate_blood_button_${req.id}")
                ) {
                    Icon(
                        imageVector = if (hasPledged) UIcons.CheckCircle else UIcons.Bloodtype,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasPledged) {
                            if (language == AppLanguage.BENGALI) {
                                "সাড়া দিয়েছেন (${req.respondersCount})"
                            } else {
                                "Pledged (${req.respondersCount})"
                            }
                        } else {
                            if (language == AppLanguage.BENGALI) {
                                "রক্ত দিন (${req.respondersCount} সাড়া)"
                            } else {
                                "Donate Blood (${req.respondersCount})"
                            }
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                val context = LocalContext.current
                OutlinedButton(
                    onClick = onCallClick,
                    shape = CircleShape
                ) {
                    Icon(UIcons.Call, contentDescription = "Call", modifier = Modifier.size(16.dp), tint = IosSystemRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (language == AppLanguage.BENGALI) "কল" else "Call", color = IosSystemRed, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(LifelineBlush)
                        .testTag("share_blood_request_button_${req.id}")
                ) {
                    Icon(
                        imageVector = UIcons.Share,
                        contentDescription = if (language == AppLanguage.BENGALI) "শেয়ার করুন" else "Share",
                        tint = IosSystemRed,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isOwner) {
                    OutlinedButton(
                        onClick = onFulfillClick,
                        shape = CircleShape,
                        modifier = Modifier.testTag("delete_fulfilled_request_button_${req.id}")
                    ) {
                        Icon(
                            imageVector = UIcons.DeleteOutline,
                            contentDescription = "Remove Post",
                            tint = Color(0xFF1F1F1F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) "ম্যানেজড" else "Managed",
                            color = Color(0xFF1F1F1F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapPinDetailOverlayCard(
    pin: SelectedMapPin,
    language: AppLanguage,
    currentUserId: String,
    hasPledged: Boolean,
    hasRespondedSos: Boolean = false,
    hasReportedFalseSos: Boolean = false,
    onDismiss: () -> Unit,
    onPledgeBlood: (BloodRequest) -> Unit,
    onRespondSos: (SosAlert) -> Unit,
    onReportFalseSos: (SosAlert) -> Unit = {},
    onResolveOwnSos: (SosAlert) -> Unit,
    onOpenSosVideoAudio: (SosAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1217)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                    val iconVec = when (pin) {
                        is SelectedMapPin.DonorPin -> UIcons.Bloodtype
                        is SelectedMapPin.RequestPin -> UIcons.LocalHospital
                        is SelectedMapPin.SosPin -> UIcons.Shield
                        is SelectedMapPin.BlePin -> UIcons.BluetoothConnected
                    }
                    Icon(
                        imageVector = iconVec,
                        contentDescription = null,
                        tint = Color(0xFFDDDDDD),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    when (pin) {
                        is SelectedMapPin.DonorPin -> {
                            Text(
                                text = "${pin.donor.displayName} (${pin.donor.bloodGroup})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is SelectedMapPin.RequestPin -> {
                            Text(
                                text = "${pin.request.bloodGroup} • ${pin.request.unitsNeeded} ব্যাগ (Hb: ${pin.request.hemoglobinLevel})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFDDDDDD),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is SelectedMapPin.SosPin -> {
                            Text(
                                text = "SOS: ${pin.alert.senderName} (${triggerMethodLabel(pin.alert.triggerMethod, language)})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFDDDDDD),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is SelectedMapPin.BlePin -> {
                            Text(
                                text = "BLE Offline Peer: ${pin.beacon.beaconId}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(UIcons.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            when (pin) {
                is SelectedMapPin.DonorPin -> {
                    Text(
                        text = "${pin.donor.district} • Trust ${pin.donor.trustScore}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            RokkhonIntents.dial(context, pin.donor.contactPhone, language)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(UIcons.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call Donor (${pin.donor.contactPhone})")
                    }
                }
                is SelectedMapPin.RequestPin -> {
                    Text(
                        text = "রোগী: ${pin.request.patientName} • সমস্যা: ${pin.request.patientProblem}\n${pin.request.hospitalName} (${pin.request.wardCabinNumber})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onPledgeBlood(pin.request) },
                        enabled = !hasPledged,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            disabledContainerColor = IosSystemGreen.copy(alpha = 0.25f),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (hasPledged) {
                                if (language == AppLanguage.BENGALI) "আপনি সাড়া দিয়েছেন" else "Already Pledged"
                            } else {
                                if (language == AppLanguage.BENGALI) "রক্ত দিন (১ বার)" else "Donate Blood (1x)"
                            }
                        )
                    }
                }
                is SelectedMapPin.SosPin -> {
                    val isOwner = pin.alert.userId == currentUserId
                    Text(
                        text = "${pin.alert.district} • ${pin.alert.verificationNote}\nNearby: ${pin.alert.responderCount} • False Reports: ${pin.alert.falseAlarmReports} (24h Window)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenSosVideoAudio(pin.alert) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(UIcons.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == AppLanguage.BENGALI) "অডিও" else "5s Audio")
                        }

                        if (isOwner) {
                            Button(
                                onClick = { onResolveOwnSos(pin.alert) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(UIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == AppLanguage.BENGALI) "নিরাপদ" else "Mark Safe")
                            }
                        } else {
                            Button(
                                onClick = { onRespondSos(pin.alert) },
                                enabled = !hasRespondedSos,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IosSystemGreen,
                                    disabledContainerColor = IosSystemGreen.copy(alpha = 0.35f),
                                    disabledContentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (language == AppLanguage.BENGALI) {
                                        "যাচ্ছি (${pin.alert.responderCount})"
                                    } else {
                                        "Nearby (${pin.alert.responderCount})"
                                    }
                                )
                            }

                            Button(
                                onClick = { onReportFalseSos(pin.alert) },
                                enabled = !hasReportedFalseSos,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IosSystemOrange,
                                    disabledContainerColor = IosSystemOrange.copy(alpha = 0.35f),
                                    disabledContentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (hasReportedFalseSos) {
                                        if (language == AppLanguage.BENGALI) {
                                            "রিপোর্টেড (${pin.alert.falseAlarmReports})"
                                        } else {
                                            "Reported (${pin.alert.falseAlarmReports})"
                                        }
                                    } else {
                                        if (language == AppLanguage.BENGALI) {
                                            "ভুয়া (${pin.alert.falseAlarmReports})"
                                        } else {
                                            "False (${pin.alert.falseAlarmReports})"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is SelectedMapPin.BlePin -> {
                    Text(
                        text = "Distance: ~${"%.1f".format(pin.beacon.estimatedDistanceMeters)}m (RSSI ${pin.beacon.rssi} dBm) • Blood: ${pin.beacon.bloodGroup}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
