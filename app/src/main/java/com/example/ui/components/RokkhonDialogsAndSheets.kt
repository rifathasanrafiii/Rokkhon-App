package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.model.NotificationKind
import com.example.data.model.RokkhonNotificationItem
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.RokkhonPalette

/**
 * Notifications, full screen: unread first, a small filter, and one compact row per item. Tapping a
 * row opens what it is about. Grey and black; red only for an SOS.
 */
@Composable
fun RokkhonNotificationCenterDialog(
    language: AppLanguage,
    notifications: List<RokkhonNotificationItem>,
    unreadCount: Int,
    falseSosWarningBanner: String?,
    falseSosPenaltyCount: Int,
    onMarkAllRead: () -> Unit,
    onNotificationClick: (RokkhonNotificationItem) -> Unit,
    onDismiss: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    var filter by rememberSaveable { mutableStateOf(0) }
    val shown = notifications.filter {
        when (filter) {
            1 -> it.kind == NotificationKind.BLOOD_REQUEST || it.kind == NotificationKind.RESPONDER_UPDATE
            2 -> it.kind == NotificationKind.SOS_ALERT || it.kind == NotificationKind.FALSE_SOS_WARNING
            else -> true
        }
    }
    val fresh = shown.filter { !it.isRead }
    val earlier = shown.filter { it.isRead }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("notification_center_dialog")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(icon = UIcons.ArrowBack, onClick = onDismiss, contentDescription = if (bn) "ফিরে যান" else "Back", testTag = "notification_back")
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (bn) "নোটিফিকেশন" else "Notifications", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                    Text(
                        if (unreadCount > 0) (if (bn) "${bnCount(unreadCount)}টি নতুন" else "$unreadCount new") else (if (bn) "সব দেখা হয়েছে" else "All caught up"),
                        fontSize = 12.5.sp, color = RokkhonPalette.Muted
                    )
                }
                if (unreadCount > 0) {
                    Text(
                        if (bn) "সব পড়া" else "Mark read",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RokkhonPalette.Subtle)
                            .springClick(onClick = onMarkAllRead)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("notification_mark_all_read")
                    )
                }
            }
            SegmentedControl(
                segments = listOf(
                    Segment(if (bn) "সব" else "All"),
                    Segment(if (bn) "রক্ত" else "Blood"),
                    Segment("SOS")
                ),
                selectedIndex = filter,
                onSelect = { filter = it },
                height = 38.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f).navigationBarsPadding()
            ) {
                if (falseSosPenaltyCount > 0 || falseSosWarningBanner != null) {
                    item(key = "warning") {
                        FalseSosAccountWarningBanner(
                            language = language,
                            warningMessage = falseSosWarningBanner,
                            penaltyCount = falseSosPenaltyCount,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(RokkhonPalette.Subtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(UIcons.NotificationsNone, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(if (bn) "এখনও কিছু নেই" else "Nothing yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (bn) "আপনার এলাকায় রক্তের আবেদন বা আপনার রক্তের গ্রুপ মিললে এখানে জানাব।"
                                else "We'll let you know here when someone near you needs your blood group.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (fresh.isNotEmpty()) {
                    item(key = "fresh_label") { NotificationGroupLabel(if (bn) "নতুন" else "New") }
                    items(fresh, key = { "n_${it.id}" }) { NotificationRow(it, bn) { onNotificationClick(it) } }
                }
                if (earlier.isNotEmpty()) {
                    item(key = "earlier_label") { NotificationGroupLabel(if (bn) "আগের" else "Earlier") }
                    items(earlier, key = { "e_${it.id}" }) { NotificationRow(it, bn) { onNotificationClick(it) } }
                }
            }
        }
    }
}

private fun bnCount(n: Int): String = n.toString().map { if (it.isDigit()) '০' + (it - '0') else it }.joinToString("")

@Composable
private fun NotificationGroupLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun NotificationRow(item: RokkhonNotificationItem, bn: Boolean, onClick: () -> Unit) {
    val sos = item.kind == NotificationKind.SOS_ALERT
    val icon = when (item.kind) {
        NotificationKind.SOS_ALERT -> UIcons.Shield
        NotificationKind.BLOOD_REQUEST -> UIcons.Bloodtype
        NotificationKind.RESPONDER_UPDATE -> UIcons.People
        NotificationKind.FALSE_SOS_WARNING -> UIcons.ReportProblem
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (!item.isRead) RokkhonPalette.Subtle else Color.White)
            .springClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("notification_item_${item.id}"),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (sos) RokkhonPalette.Sos else if (!item.isRead) Color.White else RokkhonPalette.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (sos) Color.White else RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (bn) item.titleBn else item.titleEn,
                    fontSize = 14.5.sp,
                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                    color = RokkhonPalette.Ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (!item.isRead) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RokkhonPalette.Primary))
                }
            }
            Text(
                if (bn) item.bodyBn else item.bodyEn,
                fontSize = 13.sp, lineHeight = 18.sp, color = RokkhonPalette.Muted, maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                listOf(item.timestampLabel, item.areaName).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.5.sp, color = RokkhonPalette.Faint, maxLines = 1
            )
        }
    }
}

/**
 * Prominent Account Warning Banner for False SOS Reports & Penalties.
 */
@Composable
fun FalseSosAccountWarningBanner(
    language: AppLanguage,
    warningMessage: String?,
    penaltyCount: Int,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFF3E0),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, IosSystemOrange, RoundedCornerShape(18.dp))
            .testTag("false_sos_warning_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(IosSystemOrange.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UIcons.ReportProblem,
                    contentDescription = "False SOS Warning",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (language == AppLanguage.BENGALI) {
                        if (penaltyCount > 0) "আপনার $penaltyCount টি SOS 'বিতর্কিত' হয়েছে" else "ভুয়া রিপোর্ট নীতি"
                    } else {
                        if (penaltyCount > 0) "$penaltyCount of your SOS alerts were disputed" else "False-report policy"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = warningMessage ?: if (language == AppLanguage.BENGALI) {
                        "প্রতি অ্যাকাউন্ট একটি SOS-এ একবারই 'ভুয়া' রিপোর্ট করতে পারে। রিপোর্ট বেশি হলেও SOS মুছে যায় না (২৪ ঘণ্টা পর অটো-রিমুভ)। অডিও প্রমাণ বা অন্তত ১ জন রেসপন্ডার থাকলে প্রেরক সুরক্ষিত থাকেন। শুধু সত্যিকারের বিপদে SOS পাঠান।"
                    } else {
                        "Each account can report an SOS as false once. Reports never delete an SOS (it expires after 24h). Audio evidence or at least one responder protects the sender. Only send SOS in a real emergency."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF78350F)
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = UIcons.Close,
                        contentDescription = "Dismiss Warning",
                        tint = Color(0xFF92400E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
