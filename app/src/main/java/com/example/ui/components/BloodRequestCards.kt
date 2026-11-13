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
import androidx.compose.ui.unit.sp
import com.example.data.model.areaLabel
import com.example.data.model.localDigits
import com.example.ui.theme.RokkhonPalette
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
    val bn = language == AppLanguage.BENGALI
    val context = LocalContext.current
    fun real(v: String) = v.trim().takeIf { it.isNotEmpty() && it != "-" }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                Text(req.bloodGroup, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    req.patientName.ifBlank { req.requesterName }, fontSize = 20.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, maxLines = 2
                )
                Text(
                    listOfNotNull(
                        localDigits(if (bn) "${req.unitsNeeded} ব্যাগ" else "${req.unitsNeeded} bag(s)", bn),
                        real(req.neededDateTime)
                    ).joinToString(" · "),
                    fontSize = 14.sp, color = RokkhonPalette.Muted
                )
            }
            if (req.urgencyLevel == "CRITICAL") {
                Text(
                    if (bn) "জরুরি" else "Urgent", color = RokkhonPalette.Sos, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(RokkhonPalette.Sos.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Only what the requester really entered; nothing is filled in for them.
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RokkhonPalette.Subtle).padding(horizontal = 14.dp, vertical = 6.dp)) {
            InfoLine(UIcons.LocalHospital, req.hospitalName, areaLabel(req.district, bn))
            real(req.wardCabinNumber)?.let { InfoLine(UIcons.LocationOn, if (bn) "ওয়ার্ড / কেবিন" else "Ward / cabin", it) }
            real(req.patientProblem)?.let { InfoLine(UIcons.MedicalServices, if (bn) "সমস্যা" else "Condition", it) }
            real(req.hemoglobinLevel)?.let { InfoLine(UIcons.Bloodtype, if (bn) "হিমোগ্লোবিন" else "Hemoglobin", localDigits(it, bn)) }
            if (req.respondersCount > 0) {
                InfoLine(UIcons.People, localDigits(if (bn) "${req.respondersCount} জন সাড়া দিয়েছেন" else "${req.respondersCount} responded", bn), null)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Your own request: no "Donate" (you can't pledge to yourself).
            if (!isOwner) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (hasPledged) RokkhonPalette.Positive.copy(alpha = 0.12f) else Color.Black)
                        .springClick(enabled = !hasPledged, pressedScale = 0.97f, onClick = onDonateClick)
                        .testTag("donate_blood_button_${req.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fg = if (hasPledged) RokkhonPalette.Positive else Color.White
                    Icon(if (hasPledged) UIcons.CheckCircle else UIcons.Bloodtype, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasPledged) (if (bn) "সাড়া দিয়েছেন" else "You responded") else (if (bn) "রক্ত দেব" else "I'll donate"),
                        color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .springClick(pressedScale = 0.97f, onClick = onFulfillClick)
                        .testTag("delete_fulfilled_request_button_${req.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(UIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (bn) "রক্ত পেয়েছি, বন্ধ করুন" else "Got blood, close", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            RoundIconButton(icon = UIcons.Call, onClick = onCallClick, contentDescription = if (bn) "কল" else "Call")
            RoundIconButton(
                icon = UIcons.Share,
                onClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                contentDescription = if (bn) "শেয়ার করুন" else "Share",
                testTag = "share_blood_request_button_${req.id}"
            )
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            if (!value.isNullOrBlank()) Text(value, fontSize = 13.sp, color = RokkhonPalette.Muted)
        }
    }
}
