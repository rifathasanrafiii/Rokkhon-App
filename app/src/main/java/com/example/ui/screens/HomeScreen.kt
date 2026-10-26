package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ble.BleSosMeshManager
import com.example.data.model.AppLanguage
import com.example.data.model.BleSosBeacon
import com.example.data.model.NotificationKind
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.canDonateTo
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.OverlappingDonorAvatarRow
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Screen 1 (Home Match, All-Bangladesh Area Filter & Daily Safety Streak Hub) — Pixel-for-pixel match with reference UI:
 * - Notification Bell on top-right opens the Live Notification Center (never triggers SOS by mistake).
 * - Select Area card opens the Searchable All-Bangladesh Area Picker (all 64 districts + Dhaka/Chattogram thanas).
 * - Find your match card filters available donors cleanly.
 */
@Composable
fun HomeMatchHubScreen(
    viewModel: RokkhonViewModel,
    recentPowerTaps: Int = 0,
    onSimulatePowerTap: () -> Unit = {},
    onOpenBloodRequestDialog: () -> Unit,
    onOpenSosVerification: () -> Unit,
    onOpenTutorial: () -> Unit = {}
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val onlyAvailableFilter by viewModel.onlyAvailableDonorsFilter.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaFilterEnabled by viewModel.isAreaFilterEnabled.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val dailyStreakCount by viewModel.dailyStreakCount.collectAsStateWithLifecycle()
    val checkedInToday by viewModel.checkedInToday.collectAsStateWithLifecycle()
    val guardianPoints by viewModel.guardianPoints.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val isLocationDisabledByAdmin by viewModel.isLocationDisabledByAdmin.collectAsStateWithLifecycle()
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()

    var homeFeedTab by remember { mutableStateOf(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }

    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    // Donors who can give to the patient's group (O- can give to A+ too), not inside the
    // 90-day gap after a donation, and never the user themself. Exact group matches first.
    val matchingDonors = allDonors.filter { donor ->
        donor.userId != viewModel.currentUserId &&
            (!onlyAvailableFilter || donor.isEligibleNow) &&
            (selectedBloodGroup == null || canDonateTo(donor.bloodGroup, selectedBloodGroup!!)) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(donor.district))
    }.sortedByDescending { it.bloodGroup == selectedBloodGroup }

    val allRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val openRequests = allRequests.filter { req ->
        req.status == "OPEN" &&
            (selectedBloodGroup == null || req.bloodGroup == selectedBloodGroup) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(req.district))
    }
    val archivedFulfilledCount = allRequests.count { it.status == "FULFILLED" }

    val allSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    val activeMySos = allSosAlerts.firstOrNull {
        it.status == "ACTIVE" &&
            !it.isExpiredAfter24Hours() &&
            (it.id == activeUserSosId || it.userId == viewModel.currentUserId)
    }

    // The hero scrolls away with the cards (the old fixed hero left only a small scroll area on short phones).
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F8))
            .testTag("home_screen")
    ) {
        item(key = "hero") {
        // 1. Rich Crimson-to-Dark-Burgundy Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF7B0C1A),
                            Color(0xFF5A0913),
                            Color(0xFF2B060B)
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Brand Bar: রক্ষণ wordmark on left, "[5d 345] [EN] [Bell] [R]" on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.rokkhon_wordmark_white),
                        contentDescription = "রক্ষণ",
                        modifier = Modifier.height(30.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Language Toggle Pill
                        Surface(
                            color = Color.Black.copy(alpha = 0.22f),
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { viewModel.toggleLanguage() }
                                .testTag("language_toggle_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Switch Language",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "বাংলা" else "EN",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Notification Bell Circle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .clickable { showNotificationCenterDialog = true }
                                .testTag("notification_bell_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            if (unreadNotificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF2B45))
                                )
                            }
                        }

                        // User Initial Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                .clickable { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) }
                                .testTag("open_profile_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.currentUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "R",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clean, Minimal Headline (Decluttered per user request)
                Text(
                    text = if (language == AppLanguage.BENGALI) "প্রতিটি পদক্ষেপ একটি জীবন বাঁচাতে পারে" else "Every step can save a life",
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 29.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Hero CTA Row: White "Request Blood (->)" Capsule + Prominent Home "SOS" Capsule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { onOpenBloodRequestDialog() }
                            .testTag("request_blood_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFFC8102E),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "রক্তের আবেদন" else "Request Blood",
                                fontSize = 14.5.sp,
                                color = Color(0xFF1A1113),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFB80F24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Home SOS Button (replaces the Power 1/3 tab on Home screen)
                    Surface(
                        color = Color(0xFFE01E37),
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { onOpenSosVerification() }
                            .testTag("power_3x_test_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "SOS",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "জরুরি SOS" else "SOS",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // If Live SOS Location Sharing is Active, show 1-Tap "Mark Safe" Stop Banner (Owner Only)
                if (isLiveLocationActive || activeMySos != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = PinCrimsonRed,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "আপনার লাইভ SOS শেয়ার হচ্ছে • নিরাপদ হলে বন্ধ করুন"
                                    } else {
                                        "Your Live SOS GPS Active • Tap Safe to Stop"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.resolveOwnSos(activeMySos?.id ?: activeUserSosId ?: "")
                                    }
                                    .testTag("home_mark_safe_button")
                            ) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "নিরাপদ" else "Mark Safe",
                                    color = IosSystemGreen,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // If Live Location Tracking is remotely disabled
                if (isLocationDisabledByAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF1F1218),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .testTag("admin_location_off_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "লাইভ লোকেশন বর্তমানে অফ রাখা হয়েছে"
                                } else {
                                    "Live GPS location is currently turned OFF"
                                },
                                color = Color(0xFFFDE68A),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }

                item(key = "sheet_top") {
                    // Rounded sheet edge over the hero colour, as in the original layout.
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF2B060B))) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Color(0xFFFAF8F8))
                            .padding(horizontal = 16.dp)
                    ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDAD2D4))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Card 1: 5 Day Safety Streak • 345 Pts Card
                    DailySafetyStreakCheckInCard(
                        language = language,
                        streakDays = dailyStreakCount,
                        checkedInToday = checkedInToday,
                        guardianPoints = guardianPoints,
                        onCheckInClick = { viewModel.performDailySafeCheckIn() },
                        onOpenRewards = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Card 2: Select Area Card with "This Area Only" Badge & Searchable All-Bangladesh Area Selector
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(15.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        tint = Color(0xFFB80F24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "এলাকা নির্বাচন করুন" else "Select Area",
                                            fontSize = 15.5.sp,
                                            color = Color(0xFF1A1113),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) {
                                                "সারা বাংলাদেশের ৬৪ জেলা ও থানা থেকে এলাকা বেছে নিন"
                                            } else {
                                                "Choose your area to find nearby donors"
                                            },
                                            fontSize = 12.sp,
                                            color = Color(0xFF7B6F72)
                                        )
                                    }
                                }

                                Surface(
                                    color = if (isAreaFilterEnabled) Color(0xFFFDF0F1) else Color(0xFFF2ECEE),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            if (isAreaFilterEnabled) Color(0xFFF5D8DC) else Color(0xFFE2D8DA),
                                            CircleShape
                                        )
                                        .clickable { viewModel.toggleAreaFilter(!isAreaFilterEnabled) }
                                        .testTag("area_filter_mode_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = null,
                                            tint = if (isAreaFilterEnabled) Color(0xFFB80F24) else Color(0xFF5A4A4E),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isAreaFilterEnabled) {
                                                if (language == AppLanguage.BENGALI) "শুধু এই এলাকা" else "This Area Only"
                                            } else {
                                                if (language == AppLanguage.BENGALI) "সকল এলাকা" else "All Areas"
                                            },
                                            color = if (isAreaFilterEnabled) Color(0xFFB80F24) else Color(0xFF5A4A4E),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Area Selector Pill Box -> Opens Searchable All-Bangladesh Area Picker Dialog
                            Surface(
                                color = Color(0xFFFAF8F8),
                                shape = RoundedCornerShape(26.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .border(1.dp, Color(0xFFE6DFE0), RoundedCornerShape(26.dp))
                                    .clickable { showAreaPickerDialog = true }
                                    .testTag("area_dropdown_selector")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF2B1E20),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) {
                                                selectedDistrict.nameBn
                                            } else {
                                                selectedDistrict.nameEn
                                            },
                                            fontSize = 15.sp,
                                            color = Color(0xFF1A1113),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select Area",
                                        tint = Color(0xFF2B1E20),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Card 3: "Find your match" + "Available only" Switch + 2x4 Blood Group Pill Grid
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) "রক্তের গ্রুপ খুঁজুন" else "Find your match",
                                        fontSize = 19.sp,
                                        color = Color(0xFF1A1113),
                                        fontWeight = FontWeight.Normal
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "রক্তের গ্রুপ সিলেক্ট করুন (${selectedBloodGroup ?: "সব গ্রুপ"})"
                                        } else {
                                            "Select blood group (${selectedBloodGroup ?: "All"})"
                                        },
                                        fontSize = 13.sp,
                                        color = Color(0xFF7B6F72)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (onlyAvailableFilter) {
                                            if (language == AppLanguage.BENGALI) "শুধু উপলব্ধ" else "Available only"
                                        } else {
                                            if (language == AppLanguage.BENGALI) "সব ডোনার" else "All donors"
                                        },
                                        fontSize = 12.5.sp,
                                        color = if (onlyAvailableFilter) Color(0xFF159957) else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = onlyAvailableFilter,
                                        onCheckedChange = { viewModel.setOnlyAvailableDonorsFilter(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFE31B32)
                                        ),
                                        modifier = Modifier.testTag("donor_active_switch")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            BloodGroupPillGrid2x4(
                                selectedBloodGroup = selectedBloodGroup,
                                onSelectBloodGroup = { bg -> viewModel.setBloodFilter(bg) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    }
                    }
                }

                // Card 4: Available Donors in This Area Summary Card with Overlapping Avatars
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF2F3)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFF2E2E4), RoundedCornerShape(22.dp))
                            .clickable {
                                viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                                viewModel.selectTab(RokkhonTab.RADAR_MAP)
                            }
                            .testTag("explore_compatible_donors_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF5DDE0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WaterDrop,
                                        contentDescription = null,
                                        tint = Color(0xFFB80F24),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) "এই এলাকায় ${matchingDonors.size} জন উপলব্ধ ডোনার" else "${matchingDonors.size} available donors in this area",
                                        fontSize = 14.5.sp,
                                        color = Color(0xFFA80E20),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF756A6C),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "${selectedDistrict.nameBn} • আসল স্ট্রিট ম্যাপ" else "${selectedDistrict.nameEn} • Real Street Map",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF756A6C)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "আসল ম্যাপ খুলুন" else "Open Real Map",
                                            fontSize = 13.sp,
                                            color = Color(0xFFB80F24),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color(0xFFB80F24),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Real donors from the list above (the old row always showed the same 3 sample faces).
                            if (matchingDonors.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                                OverlappingDonorAvatarRow(
                                    initials = matchingDonors.take(3).map { donorInitials(it.displayName) },
                                    extraCount = (matchingDonors.size - 3).coerceAtLeast(0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "কাছের ডোনার" else "Recent donors nearby",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF7B6F72)
                                )
                            }
                        }
                    }
                    }
                }

                // Row 5: Two Side-by-Side Summary Cards ("Available Donors" & "Patient Posts")
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Card: Available Donors
                        Surface(
                            color = Color(0xFFFAF2F3),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (homeFeedTab == MapLayerFilter.DONORS_ONLY) 1.4.dp else 1.dp,
                                    color = if (homeFeedTab == MapLayerFilter.DONORS_ONLY) Color(0xFFD99AA3) else Color(0xFFF2E2E4),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { homeFeedTab = MapLayerFilter.DONORS_ONLY }
                                .testTag("filter_tab_available_donors")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF5DDE0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.People,
                                            contentDescription = null,
                                            tint = Color(0xFFB80F24),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "উপলব্ধ ডোনার" else "Available Donors",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF2B1E20),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${matchingDonors.size}",
                                            fontSize = 19.sp,
                                            color = Color(0xFF1A1113),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF5DDE0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFFB80F24),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Right Card: Patient Posts
                        Surface(
                            color = Color(0xFFFAF2F3),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (homeFeedTab == MapLayerFilter.BLOOD_REQUESTS_ONLY) 1.4.dp else 1.dp,
                                    color = if (homeFeedTab == MapLayerFilter.BLOOD_REQUESTS_ONLY) Color(0xFFD99AA3) else Color(0xFFF2E2E4),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { homeFeedTab = MapLayerFilter.BLOOD_REQUESTS_ONLY }
                                .testTag("filter_tab_patient_requests")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF5DDE0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalHospital,
                                            contentDescription = null,
                                            tint = Color(0xFFB80F24),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) "রোগীর পোস্ট" else "Patient Posts",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF2B1E20),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${openRequests.size}",
                                            fontSize = 19.sp,
                                            color = Color(0xFF1A1113),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF5DDE0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFFB80F24),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                // Row 6: Footer Status Strip ("Managed & Retained in DB: 0" + "+ New Request")
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF6E6265),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "ডাটাবেসে সংরক্ষিত সম্পন্ন পোস্ট: $archivedFulfilledCount"
                                } else {
                                    "Managed & Retained in DB: $archivedFulfilledCount"
                                },
                                fontSize = 12.5.sp,
                                color = Color(0xFF6E6265),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = if (language == AppLanguage.BENGALI) "+ নতুন আবেদন" else "+ New Request",
                            fontSize = 13.5.sp,
                            color = Color(0xFFC8102E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenBloodRequestDialog() }
                        )
                    }
                    }
                }

                if (homeFeedTab == MapLayerFilter.DONORS_ONLY) {
                    items(matchingDonors, key = { "home_donor_${it.id.ifBlank { it.userId }}" }) { donor ->
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                        LifelineResponderCard(
                            donor = donor,
                            language = language,
                            onCallClick = {
                                RokkhonIntents.dial(context, donor.contactPhone, language)
                            }
                        )
                    }
                    }
                } else {
                    items(openRequests, key = { it.id.ifBlank { it.patientName } }) { req ->
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                        val hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req)
                        ClinicalBloodRequestCard(
                            req = req,
                            language = language,
                            isOwner = req.userId == viewModel.currentUserId,
                            hasPledged = hasPledged,
                            onDonateClick = { viewModel.pledgeToDonate(req) },
                            onCallClick = {
                                RokkhonIntents.dial(context, req.contactPhone, language)
                            },
                            onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                        )
                    }
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
    }

    if (showAreaPickerDialog) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selectedDistrict,
            isAutoGpsActive = isAreaAutoDetected,
            onSelectAutoGps = { viewModel.autoSelectAreaFromPermission(notifyUser = true) },
            onSelectDistrict = { dist -> viewModel.selectDistrict(dist, isManual = true) },
            onDismiss = { showAreaPickerDialog = false }
        )
    }

    if (showNotificationCenterDialog) {
        RokkhonNotificationCenterDialog(
            language = language,
            notifications = notifications,
            unreadCount = unreadNotificationCount,
            falseSosWarningBanner = falseSosWarningBanner,
            falseSosPenaltyCount = falseSosPenaltyCount,
            onMarkAllRead = { viewModel.markAllNotificationsRead() },
            onNotificationClick = { item ->
                viewModel.markNotificationRead(item.id)
                showNotificationCenterDialog = false
                when (item.kind) {
                    NotificationKind.SOS_ALERT, NotificationKind.FALSE_SOS_WARNING -> {
                        viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                        viewModel.selectTab(RokkhonTab.RADAR_MAP)
                    }
                    NotificationKind.BLOOD_REQUEST, NotificationKind.RESPONDER_UPDATE -> {
                        viewModel.setMapLayerFilter(MapLayerFilter.BLOOD_REQUESTS_ONLY)
                        viewModel.selectTab(RokkhonTab.RADAR_MAP)
                    }
                }
            },
            onDismiss = { showNotificationCenterDialog = false }
        )
    }
}

/**
 * SOS heard over the Bluetooth mesh that the cloud doesn't have (sender or receiver offline),
 * shown next to the cloud alerts. A cloud alert carries the same BLE code, so it isn't listed twice.
 */
internal fun bluetoothOnlySosAlerts(beacons: List<BleSosBeacon>, cloudAlerts: List<SosAlert>): List<SosAlert> {
    val cloudCodes = cloudAlerts.mapNotNull { it.bleBeaconHash.takeIf { code -> code.isNotBlank() } }.toSet()
    return beacons
        .filter { it.packetKind == BleSosMeshManager.PACKET_KIND_SOS_1KM && it.senderCode !in cloudCodes }
        .filter { it.distanceFromOriginKm == null || it.distanceFromOriginKm <= SOS_NOTIFICATION_RADIUS_KM }
        .map { b ->
            SosAlert(
                id = "ble_${b.packetId}",
                senderName = "Bluetooth SOS (${b.senderCode})",
                bloodGroup = b.bloodGroup,
                emergencyType = b.emergencyType,
                district = "",
                latitude = b.latitude,
                longitude = b.longitude,
                hasLiveAudioVerification = b.hasCameraAudioProof,
                triggerMethod = "BLE_MESH",
                verificationNote = "Received over Bluetooth from a nearby Rokkhon phone",
                bleBeaconHash = b.senderCode,
                status = "ACTIVE",
                createdAt = com.google.firebase.Timestamp(java.util.Date(b.timestampMillis))
            )
        }
}

/** "Rifat Hasan (Rifat R.)" -> "RR": Latin initials for the avatar circles. */
internal fun donorInitials(name: String): String {
    val latin = Regex("""\(([^)]*)\)""").find(name)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: name
    val parts = latin.split(" ", "•").map { it.trim() }.filter { it.isNotEmpty() && it.first().isLetter() }
    return parts.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }
}

/**
 * Snapchat-style Daily Safety Streak & Guardian Points Card matching image_fdddfe18.png.
 */
@Composable
fun DailySafetyStreakCheckInCard(
    language: AppLanguage,
    streakDays: Int,
    checkedInToday: Boolean,
    guardianPoints: Int,
    onCheckInClick: () -> Unit,
    onOpenRewards: () -> Unit
) {
    Surface(
        color = Color(0xFFFDFBFB),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFCEAE9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = "Daily Streak",
                        tint = Color(0xFFE01E37),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "$streakDays দিনের সেফটি স্ট্রিক • $guardianPoints পয়েন্ট"
                        } else {
                            "$streakDays Day Safety Streak • $guardianPoints\u00A0Pts"
                        },
                        fontSize = 15.sp,
                        color = Color(0xFF1A1113),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "প্রতিদিনের চেক-ইন স্ট্রিক বাড়ায় ও রিওয়ার্ড আনলক করে।"
                        } else {
                            "Daily check-in builds your streak\n& unlocks rewards."
                        },
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFF7B6F72)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = if (checkedInToday) Color(0xFFE5F3EB) else Color(0xFFE01E37),
                shape = CircleShape,
                modifier = Modifier
                    .clickable {
                        if (checkedInToday) onOpenRewards() else onCheckInClick()
                    }
                    .testTag("home_daily_streak_checkin_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (checkedInToday) Icons.Default.CheckCircle else Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = if (checkedInToday) Color(0xFF159957) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (checkedInToday) {
                            if (language == AppLanguage.BENGALI) "সম্পন্ন" else "Done"
                        } else {
                            if (language == AppLanguage.BENGALI) "নিরাপদ (+২৫)" else "Safe (+25)"
                        },
                        color = if (checkedInToday) Color(0xFF159957) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 3-Tab Segmented Filter Switcher inside Find (`RadarMapHubScreen`):
 * 1. Available Donors (`DONORS_ONLY`)
 * 2. Patient Blood Requests (`BLOOD_REQUESTS_ONLY`)
 * 3. Active SOS Alerts (`SOS_ALERTS_ONLY`)
 */
@Composable
fun SeparateDonorRequestAndSosFilterTabs(
    language: AppLanguage,
    selectedLayer: MapLayerFilter,
    donorsCount: Int,
    requestsCount: Int,
    sosCount: Int,
    onSelectLayer: (MapLayerFilter) -> Unit
) {
    Surface(
        color = Color(0xFFF5ECEE),
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isDonorsSelected = selectedLayer == MapLayerFilter.DONORS_ONLY
            Surface(
                color = if (isDonorsSelected) IosSystemRed else Color.Transparent,
                contentColor = if (isDonorsSelected) Color.White else Color(0xFF4A3A3D),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { onSelectLayer(MapLayerFilter.DONORS_ONLY) }
                    .testTag("map_filter_tab_available_donors")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "ডোনার ($donorsCount)"
                        } else {
                            "Donors ($donorsCount)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val isRequestsSelected = selectedLayer == MapLayerFilter.BLOOD_REQUESTS_ONLY
            Surface(
                color = if (isRequestsSelected) IosSystemRed else Color.Transparent,
                contentColor = if (isRequestsSelected) Color.White else Color(0xFF4A3A3D),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { onSelectLayer(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
                    .testTag("map_filter_tab_patient_requests")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "রোগী ($requestsCount)"
                        } else {
                            "Patients ($requestsCount)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val isSosSelected = selectedLayer == MapLayerFilter.SOS_ALERTS_ONLY
            Surface(
                color = if (isSosSelected) PinCrimsonRed else Color.Transparent,
                contentColor = if (isSosSelected) Color.White else Color(0xFF4A3A3D),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { onSelectLayer(MapLayerFilter.SOS_ALERTS_ONLY) }
                    .testTag("map_filter_tab_sos_alerts")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "SOS ($sosCount)"
                        } else {
                            "SOS ($sosCount)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 2x4 Blood Group Pill Grid (A+, A-, B+, B-, AB+, AB-, O+, O-) matching image_fdddfe18.png.
 */
@Composable
fun BloodGroupPillGrid2x4(
    selectedBloodGroup: String?,
    onSelectBloodGroup: (String) -> Unit
) {
    val rows = listOf(
        listOf("A+", "A-", "B+", "B-"),
        listOf("AB+", "AB-", "O+", "O-")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { bg ->
                    val isSelected = selectedBloodGroup == bg
                    Surface(
                        color = if (isSelected) Color(0xFFB80F24) else Color.White,
                        contentColor = if (isSelected) Color.White else Color(0xFF1A1113),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(
                                width = if (isSelected) 0.dp else 1.dp,
                                color = Color(0xFFE6DFE0),
                                shape = CircleShape
                            )
                            .clickable { onSelectBloodGroup(bg) }
                            .testTag("blood_filter_$bg")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = bg,
                                fontSize = 15.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
