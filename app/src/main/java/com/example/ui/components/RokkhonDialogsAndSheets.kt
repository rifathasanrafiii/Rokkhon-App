package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.BANGLADESH_DIVISIONS
import com.example.data.model.BangladeshDistrict
import com.example.data.model.NotificationKind
import com.example.data.model.RokkhonNotificationItem
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed

/**
 * Searchable All-Bangladesh Area Selector Sheet/Dialog:
 * Covers all 64 districts of Bangladesh across all 8 divisions + major Dhaka & Chattogram areas.
 */
@Composable
fun BangladeshAreaPickerDialog(
    language: AppLanguage,
    selectedDistrict: BangladeshDistrict,
    isAutoGpsActive: Boolean,
    onSelectAutoGps: () -> Unit,
    onSelectDistrict: (BangladeshDistrict) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDivisionKey by remember { mutableStateOf("ALL") }

    val filteredDistricts = remember(searchQuery, selectedDivisionKey) {
        BANGLADESH_DISTRICTS.filter { dist ->
            val matchesDivision = selectedDivisionKey == "ALL" ||
                dist.divisionEn.equals(selectedDivisionKey, ignoreCase = true)
            matchesDivision && dist.matchesSearch(searchQuery)
        }
    }

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
                    .fillMaxHeight(0.88f)
                    .testTag("bangladesh_area_picker_dialog")
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
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = IosSystemRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "সারা বাংলাদেশের এলাকা নির্বাচন করুন"
                                    } else {
                                        "Select Area (All 64 Districts)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "৮টি বিভাগ, ৬৪ জেলা এবং ঢাকা ও চট্টগ্রামের প্রধান থানা (${BANGLADESH_DISTRICTS.size}টি এলাকা)"
                                    } else {
                                        "All 64 districts + Dhaka & Chattogram areas (${BANGLADESH_DISTRICTS.size} locations)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF756568)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF5ECEE))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF1A0F12),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = IosSystemRed
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "জেলা বা এলাকার নাম লিখুন (যেমন: মিরপুর, বগুড়া, কক্সবাজার)..."
                                } else {
                                    "Search district or area (e.g. Mirpur, Bogura, Sylhet, Cox's Bazar)..."
                                },
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("area_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto-Detect GPS Button
                    Surface(
                        color = if (isAutoGpsActive) IosSystemGreen.copy(alpha = 0.14f) else Color(0xFFFAF6F6),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isAutoGpsActive) IosSystemGreen else Color(0xFFEDE2E4),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSelectAutoGps()
                                onDismiss()
                            }
                            .testTag("area_picker_auto_gps_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = IosSystemGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "জিপিএস দিয়ে কাছের এলাকা বেছে নিন"
                                        } else {
                                            "Auto-Detect Nearest Area via GPS"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = IosSystemGreen,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "বর্তমান নির্বাচিত: ${selectedDistrict.nameBn}"
                                        } else {
                                            "Currently selected: ${selectedDistrict.nameEn}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF5A4A4E)
                                    )
                                }
                            }
                            if (isAutoGpsActive) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IosSystemGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Division Filter Chips (8 Divisions of Bangladesh)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BANGLADESH_DIVISIONS.forEach { (divKey, labels) ->
                            val isSelected = selectedDivisionKey == divKey
                            Surface(
                                color = if (isSelected) IosSystemRed else Color(0xFFF5ECEE),
                                contentColor = if (isSelected) Color.White else Color(0xFF3D2E31),
                                shape = CircleShape,
                                modifier = Modifier
                                    .clickable { selectedDivisionKey = divKey }
                                    .testTag("division_chip_$divKey")
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) labels.second else labels.first,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable List of Districts & Areas
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDistricts, key = { it.areaKey }) { dist ->
                            val isCurrent = dist.areaKey == selectedDistrict.areaKey
                            Surface(
                                color = if (isCurrent) LifelineBlush else Color(0xFFFAF8F8),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isCurrent) 1.4.dp else 1.dp,
                                        color = if (isCurrent) IosSystemRed else Color(0xFFEEE6E8),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        onSelectDistrict(dist)
                                        onDismiss()
                                    }
                                    .testTag("area_option_${dist.areaKey}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = if (isCurrent) IosSystemRed else Color(0xFF5A4A4E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "${dist.nameEn} • ${dist.nameBn}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isCurrent) IosSystemRed else Color(0xFF1A0F12),
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) {
                                                    "${dist.divisionBn} বিভাগ • ${dist.defaultHospitalBn}"
                                                } else {
                                                    "${dist.divisionEn} Division • ${dist.defaultHospitalEn}"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF756568)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (isCurrent) IosSystemRed else Color.White,
                                        shape = CircleShape,
                                        modifier = Modifier.border(
                                            0.8.dp,
                                            if (isCurrent) IosSystemRed else Color(0xFFE2D5D7),
                                            CircleShape
                                        )
                                    ) {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) dist.divisionBn else dist.divisionEn,
                                            color = if (isCurrent) Color.White else Color(0xFF5A4A4E),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    imageVector = Icons.Default.NotificationsActive,
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
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        if (unreadCount > 0) "$unreadCount টি নতুন" else "সব পড়া হয়েছে"
                                    } else {
                                        if (unreadCount > 0) "$unreadCount unread" else "All caught up"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF756568)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFF5ECEE),
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
                                        imageVector = Icons.Default.DoneAll,
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
                                    .background(Color(0xFFF5ECEE))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF1A0F12),
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
                        items(notifications, key = { it.id }) { item ->
                            val (accentColor, iconVec) = when (item.kind) {
                                NotificationKind.SOS_ALERT -> PinCrimsonRed to Icons.Default.Shield
                                NotificationKind.BLOOD_REQUEST -> IosSystemRed to Icons.Default.Bloodtype
                                NotificationKind.RESPONDER_UPDATE -> IosSystemGreen to Icons.Default.VerifiedUser
                                NotificationKind.FALSE_SOS_WARNING -> IosSystemOrange to Icons.Default.ReportProblem
                            }

                            Surface(
                                color = if (item.isRead) Color(0xFFFAF8F8) else accentColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (item.isRead) 1.dp else 1.3.dp,
                                        color = if (item.isRead) Color(0xFFEEE6E8) else accentColor.copy(alpha = 0.4f),
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
                                                color = Color(0xFF1A0F12),
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
                                            color = Color(0xFF5A4A4E)
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
                                                color = Color(0xFF756568),
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
                    imageVector = Icons.Default.ReportProblem,
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
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Warning",
                        tint = Color(0xFF92400E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
