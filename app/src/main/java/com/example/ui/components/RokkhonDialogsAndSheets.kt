package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.NotificationKind
import com.example.data.model.RokkhonNotificationItem
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed

/**
 * Live Notification Center Dialog/Sheet:
 * Opens when tapping the top-right Notification Bell on the Home screen.
 * Displays Live SOS alerts, nearby blood requests, responder updates, and False-SOS warnings.
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 14.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f)
                    .testTag("notification_center_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(LifelineBlush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = UIcons.NotificationsActive,
                                    contentDescription = null,
                                    tint = IosSystemRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "নোটিফিকেশন"
                                    } else {
                                        "Notifications"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF000000),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        if (unreadCount > 0) "$unreadCount টি নতুন" else "সব পড়া হয়েছে"
                                    } else {
                                        if (unreadCount > 0) "$unreadCount unread" else "All caught up"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5E5E5E)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFF3F3F3),
                                shape = CircleShape,
                                modifier = Modifier
                                    .clickable { onMarkAllRead() }
                                    .testTag("notifications_mark_all_read_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = UIcons.DoneAll,
                                        contentDescription = "Mark All Read",
                                        tint = IosSystemRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) "সব পড়া হয়েছে" else "Read All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IosSystemRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF3F3F3))
                            ) {
                                Icon(
                                    imageVector = UIcons.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF000000),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (falseSosWarningBanner != null || falseSosPenaltyCount > 0) {
                        FalseSosAccountWarningBanner(
                            language = language,
                            warningMessage = falseSosWarningBanner,
                            penaltyCount = falseSosPenaltyCount
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (notifications.isEmpty()) {
                            item {
                                RokkhonEmptyState(
                                    icon = UIcons.NotificationsActive,
                                    title = if (language == AppLanguage.BENGALI) "কোনো নোটিফিকেশন নেই" else "No notifications yet",
                                    body = if (language == AppLanguage.BENGALI) {
                                        "এলাকায় নতুন রক্তের আবেদন, ১ কি.মি.-এর মধ্যে SOS বা কেউ সাড়া দিলে এখানে জানানো হবে।"
                                    } else {
                                        "New blood requests in your area, SOS within 1 km and responder updates show up here."
                                    },
                                    testTag = "notifications_empty"
                                )
                            }
                        }
                        items(notifications, key = { it.id }) { item ->
                            val (accentColor, iconVec) = when (item.kind) {
                                NotificationKind.SOS_ALERT -> PinCrimsonRed to UIcons.Shield
                                NotificationKind.BLOOD_REQUEST -> IosSystemRed to UIcons.Bloodtype
                                NotificationKind.RESPONDER_UPDATE -> IosSystemGreen to UIcons.VerifiedUser
                                NotificationKind.FALSE_SOS_WARNING -> IosSystemOrange to UIcons.ReportProblem
                            }

                            Surface(
                                color = if (item.isRead) Color(0xFFFFFFFF) else accentColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (item.isRead) 1.dp else 1.3.dp,
                                        color = if (item.isRead) Color(0xFFE8E8E8) else accentColor.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { onNotificationClick(item) }
                                    .testTag("notification_item_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVec,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) item.titleBn else item.titleEn,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF000000),
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item.timestampLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) item.bodyBn else item.bodyEn,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF545454)
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.areaName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF5E5E5E),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = when (item.kind) {
                                                    NotificationKind.SOS_ALERT -> if (language == AppLanguage.BENGALI) "SOS দেখুন →" else "View SOS →"
                                                    NotificationKind.BLOOD_REQUEST -> if (language == AppLanguage.BENGALI) "আবেদন দেখুন →" else "View request →"
                                                    else -> if (language == AppLanguage.BENGALI) "বিস্তারিত →" else "Details →"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IosSystemBlue,
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
