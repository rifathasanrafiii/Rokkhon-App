package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.components.RokkhonIntents
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush

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

