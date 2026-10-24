package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.FALSE_SOS_REMOVAL_THRESHOLD
import com.example.data.model.NotificationKind
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.InteractiveBangladeshRadarMap
import com.example.ui.components.OverlappingDonorAvatarRow
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.components.SelectedMapPin
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
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
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()

    var homeFeedTab by remember { mutableStateOf(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }

    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    val matchingDonors = allDonors.filter { donor ->
        (!onlyAvailableFilter || donor.isAvailableDonor) &&
            (selectedBloodGroup == null || donor.bloodGroup == selectedBloodGroup) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(donor.district))
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF280509))
    ) {
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(210.dp)
                    .align(Alignment.CenterEnd)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD91E36).copy(alpha = 0.36f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.75f, size.height * 0.45f),
                        radius = size.minDimension * 0.75f
                    )
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Brand Bar: "[Drop] rokkhon" on left, "[5d 345] [EN] [Bell] [R]" on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE01E37)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "rokkhon",
                            fontSize = 21.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Streak & Points Pill: [Flame 5d  Star 345]
                        Surface(
                            color = Color.Black.copy(alpha = 0.22f),
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) }
                                .testTag("header_streak_points_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = "Streak",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${dailyStreakCount}d",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = "Points",
                                    tint = Color(0xFFEAB308),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$guardianPoints",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Language Toggle Pill: [Translate EN]
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

                        // FIXED Notification Bell Circle: Opens Notification Center (NOT SOS!)
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

                        // User Initial Avatar Circle ("R")
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                .clickable { onOpenTutorial() }
                                .testTag("open_tutorial_button"),
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

                // Eyebrow: DONATE • SAVE • TOGETHER
                Text(
                    text = "DONATE  •  SAVE  •  TOGETHER",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.0.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Main Headline
                Text(
                    text = "Every drop\ncan save a life.",
                    fontSize = 33.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 37.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = if (language == AppLanguage.BENGALI) {
                        "এলাকাভিত্তিক উপলব্ধ রক্তদাতা এবং ৫-সেকেন্ডের\nঅডিও/ভিডিও জরুরি SOS।"
                    } else {
                        "Area-based available blood donors & 5-second\naudio/video emergency SOS."
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Hero CTA Row: White "Request Blood (->)" Capsule + Dark "Power (1/3)" Capsule
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

                    // Power (1/3) Hardware Auto-SOS Pill
                    Surface(
                        color = Color(0xFF3A1016).copy(alpha = 0.72f),
                        shape = CircleShape,
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                            .clickable { onSimulatePowerTap() }
                            .testTag("power_3x_test_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color(0xFFFF8FA3),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val displayedTaps = if (recentPowerTaps > 0) recentPowerTaps else 1
                            Text(
                                text = "Power ($displayedTaps/3)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
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
                                        "আপনার লাইভ SOS শেয়ার হচ্ছে • নিরাপদ হলে বন্ধ করুন"
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
            }
        }

        // 2. Rounded Pure White / Warm Alabaster Bottom Sheet
        Surface(
            color = Color(0xFFFAF8F8),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
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
                        onOpenRewards = { viewModel.performDailySafeCheckIn() }
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
                                        fontSize = 18.sp,
                                        color = Color(0xFF1A1113),
                                        fontWeight = FontWeight.Bold
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
                                        text = if (onlyAvailableFilter) "Available only" else "All donors",
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
                }

                // Card 4: Available Donors in This Area Summary Card with Overlapping Avatars
                item {
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
                                        text = "${matchingDonors.size} available donors in this area",
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
                                            text = "${selectedDistrict.nameEn} • Real Street Map",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF756A6C)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Open Real Map",
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

                            Column(horizontalAlignment = Alignment.End) {
                                OverlappingDonorAvatarRow(
                                    initials = listOf("TA", "FT", "MH"),
                                    extraCount = (matchingDonors.size + 1).coerceAtLeast(4)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recent donors nearby",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF7B6F72)
                                )
                            }
                        }
                    }
                }

                // Row 5: Two Side-by-Side Summary Cards ("Available Donors" & "Patient Posts")
                item {
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

                // Row 6: Footer Status Strip ("Managed & Retained in DB: 0" + "+ New Request")
                item {
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

                if (homeFeedTab == MapLayerFilter.DONORS_ONLY) {
                    items(matchingDonors, key = { "home_donor_${it.id.ifBlank { it.userId }}" }) { donor ->
                        LifelineResponderCard(
                            donor = donor,
                            onCallClick = {
                                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.contactPhone}"))
                                context.startActivity(dial)
                            }
                        )
                    }
                } else {
                    items(openRequests, key = { it.id.ifBlank { it.patientName } }) { req ->
                        val hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req)
                        ClinicalBloodRequestCard(
                            req = req,
                            language = language,
                            isOwner = req.userId == viewModel.currentUserId,
                            hasPledged = hasPledged,
                            onDonateClick = { viewModel.pledgeToDonate(req) },
                            onCallClick = {
                                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${req.contactPhone}"))
                                context.startActivity(dial)
                            },
                            onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
        }
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
                            "$streakDays দিনের সেফটি স্ট্রিক • $guardianPoints পয়েন্ট"
                        } else {
                            "$streakDays Day Safety Streak • $guardianPoints Pts"
                        },
                        fontSize = 15.sp,
                        color = Color(0xFF1A1113),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "প্রতিদিনের চেক-ইন স্ট্রিক বাড়ায় ও রিওয়ার্ড আনলক করে।"
                        } else {
                            "Daily check-in builds your streak\n& unlocks rewards."
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

/**
 * Screen 2 (Find — Real Interactive Bangladesh Map + 3 Dedicated Tabs:
 * 1. Available Donors
 * 2. Patient Blood Requests
 * 3. Active SOS Alerts (with "Nearby / যাচ্ছি" & "False / ভুয়া (X/3)" buttons, auto-removing at 3 false reports)
 */
@Composable
fun RadarMapHubScreen(
    viewModel: RokkhonViewModel,
    recentPowerTaps: Int = 0,
    onSimulatePowerTap: () -> Unit = {},
    onOpenSosVerification: () -> Unit,
    onOpenBloodRequestDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaFilterEnabled by viewModel.isAreaFilterEnabled.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val layerFilter by viewModel.mapLayerFilter.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val respondedSosIds by viewModel.respondedSosIds.collectAsStateWithLifecycle()
    val reportedFalseSosIds by viewModel.reportedFalseSosIds.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val activePlaybackAlertId by viewModel.audioVerifier.activePlaybackAlertId.collectAsStateWithLifecycle()
    val playbackSeconds by viewModel.audioVerifier.playbackProgressSeconds.collectAsStateWithLifecycle()
    val waveformBars by viewModel.audioVerifier.waveformSamples.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()

    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()

    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    val allRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val allSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()

    val areaDonors = allDonors.filter { donor ->
        donor.isAvailableDonor &&
            (selectedBloodGroup == null || donor.bloodGroup == selectedBloodGroup) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(donor.district))
    }

    val areaOpenRequests = allRequests.filter { req ->
        req.status == "OPEN" &&
            (selectedBloodGroup == null || req.bloodGroup == selectedBloodGroup) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(req.district))
    }

    // Active SOS alerts stay visible for 24 hours even if falseAlarmReports >= 3 (no immediate auto-removal)
    val activeSosAlerts = allSosAlerts.filter { alert ->
        alert.status == "ACTIVE" &&
            !alert.isExpiredAfter24Hours() &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(alert.district))
    }.ifEmpty {
        // Fallback so active SOS alerts across Bangladesh remain visible if none in current sub-area
        allSosAlerts.filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
    }

    var selectedPin by remember { mutableStateOf<SelectedMapPin?>(null) }
    var selectedSosForMediaModal by remember { mutableStateOf<SosAlert?>(null) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarObsidianBg)
    ) {
        // 1. Top Real Bangladesh Map Area (50% of screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.50f)
        ) {
            InteractiveBangladeshRadarMap(
                centerLat = userLat,
                centerLng = userLng,
                donors = areaDonors,
                bloodRequests = areaOpenRequests,
                sosAlerts = activeSosAlerts,
                bleBeacons = bleBeacons,
                selectedBloodGroup = selectedBloodGroup,
                layerFilter = layerFilter,
                onPinSelected = { selectedPin = it },
                showLiveFloatingCard = (selectedPin == null),
                isLiveLocationActive = isLiveLocationActive,
                selectedAreaName = selectedDistrict.nameEn,
                modifier = Modifier.fillMaxSize()
            )

            // Top-Left Auto GPS + Search All 64 Districts + Quick Area Filter Strip inside Map
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 10.dp, end = 64.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = PinCrimsonRed,
                    shape = CircleShape,
                    modifier = Modifier
                        .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .clickable { showAreaPickerDialog = true }
                        .testTag("map_open_area_search_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search All 64 Districts",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "সব জেলা (${selectedDistrict.nameBn})"
                            } else {
                                "All 64 Areas: ${selectedDistrict.nameEn}"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = if (isAreaAutoDetected) IosSystemGreen else Color(0xFF1A0F12).copy(alpha = 0.9f),
                    shape = CircleShape,
                    modifier = Modifier
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable { viewModel.autoSelectAreaFromPermission(notifyUser = true) }
                        .testTag("map_area_auto_gps_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Auto GPS",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) "অটো জিপিএস" else "Auto GPS",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = Color(0xFF1A0F12).copy(alpha = 0.9f),
                    shape = CircleShape,
                    modifier = Modifier
                        .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { viewModel.setBloodFilter(null) }
                        .testTag("blood_filter_all")
                ) {
                    Text(
                        text = if (selectedBloodGroup == null) "All Blood Groups" else "Group: $selectedBloodGroup (Reset)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                BANGLADESH_DISTRICTS.take(12).forEach { dist ->
                    val isSel = dist.areaKey == selectedDistrict.areaKey
                    Surface(
                        color = if (isSel) PinCrimsonRed else Color(0xFF1A0F12).copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.clickable { viewModel.selectDistrict(dist, isManual = true) }
                    ) {
                        Text(
                            text = dist.nameEn,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (selectedPin != null) {
                MapPinDetailOverlayCard(
                    pin = selectedPin!!,
                    language = language,
                    currentUserId = viewModel.currentUserId,
                    hasPledged = (selectedPin as? SelectedMapPin.RequestPin)?.let {
                        it.request.id in pledgedIds || viewModel.hasUserPledged(it.request)
                    } ?: false,
                    hasRespondedSos = (selectedPin as? SelectedMapPin.SosPin)?.let {
                        it.alert.id in respondedSosIds || viewModel.hasUserRespondedSos(it.alert)
                    } ?: false,
                    hasReportedFalseSos = (selectedPin as? SelectedMapPin.SosPin)?.let {
                        it.alert.id in reportedFalseSosIds || viewModel.hasUserReportedFalseSos(it.alert)
                    } ?: false,
                    onDismiss = { selectedPin = null },
                    onPledgeBlood = { viewModel.pledgeToDonate(it) },
                    onRespondSos = { viewModel.respondToSos(it) },
                    onReportFalseSos = { alert ->
                        viewModel.flagFalseAlarm(alert)
                    },
                    onResolveOwnSos = { alert ->
                        viewModel.resolveOwnSos(alert.id)
                        selectedPin = null
                    },
                    onOpenSosVideoAudio = { alert ->
                        viewModel.markSosEvidenceInspected(alert.id)
                        selectedSosForMediaModal = alert
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .fillMaxWidth()
                )
            }
        }

        // 2. Bottom Rounded White Sheet with 3 Tabs: Donors | Patients | Active SOS
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.50f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2D5D7))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Area Header (Tap to open all 64 districts picker) + Post Blood Request Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showAreaPickerDialog = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        selectedDistrict.nameBn
                                    } else {
                                        selectedDistrict.nameEn
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change Area",
                                    tint = IosSystemRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isAreaAutoDetected) IosSystemGreen.copy(alpha = 0.14f) else LifelineBlush,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = if (isAreaAutoDetected) {
                                            if (language == AppLanguage.BENGALI) "অটো জিপিএস" else "Auto GPS"
                                        } else {
                                            if (language == AppLanguage.BENGALI) "ম্যানুয়াল" else "Manual"
                                        },
                                        color = if (isAreaAutoDetected) IosSystemGreen else IosSystemRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "ডোনার, রোগীর রক্তের পোস্ট এবং জরুরি SOS আলাদা ট্যাবে দেখুন"
                                } else {
                                    "Donors, Patient Posts & Active SOS alerts separated by tab"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF756568)
                            )
                        }

                        Surface(
                            color = LifelineBlush,
                            shape = CircleShape,
                            modifier = Modifier
                                .clickable { onOpenBloodRequestDialog() }
                                .testTag("new_blood_request_fab")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = IosSystemRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "রক্তের আবেদন" else "Post Request",
                                    color = IosSystemRed,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Tab Segmented Switcher: Available Donors | Patient Posts | Active SOS
                    SeparateDonorRequestAndSosFilterTabs(
                        language = language,
                        selectedLayer = when (layerFilter) {
                            MapLayerFilter.BLOOD_REQUESTS_ONLY -> MapLayerFilter.BLOOD_REQUESTS_ONLY
                            MapLayerFilter.SOS_ALERTS_ONLY -> MapLayerFilter.SOS_ALERTS_ONLY
                            else -> MapLayerFilter.DONORS_ONLY
                        },
                        donorsCount = areaDonors.size,
                        requestsCount = areaOpenRequests.size,
                        sosCount = activeSosAlerts.size,
                        onSelectLayer = { viewModel.setMapLayerFilter(it) }
                    )
                }

                when (layerFilter) {
                    MapLayerFilter.BLOOD_REQUESTS_ONLY -> {
                        if (areaOpenRequests.isEmpty()) {
                            item {
                                Surface(
                                    color = Color(0xFFFAF6F6),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "এই এলাকায় বর্তমানে কোনো খোলা রক্তের আবেদন নেই।"
                                        } else {
                                            "No active patient blood requests in this area."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF756568),
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(areaOpenRequests, key = { "map_req_${it.id}" }) { req ->
                                val hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req)
                                ClinicalBloodRequestCard(
                                    req = req,
                                    language = language,
                                    isOwner = req.userId == viewModel.currentUserId,
                                    hasPledged = hasPledged,
                                    onDonateClick = { viewModel.pledgeToDonate(req) },
                                    onCallClick = {
                                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${req.contactPhone}"))
                                        context.startActivity(dial)
                                    },
                                    onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                                )
                            }
                        }
                    }

                    MapLayerFilter.SOS_ALERTS_ONLY -> {
                        if (falseSosWarningBanner != null || falseSosPenaltyCount > 0) {
                            item {
                                FalseSosAccountWarningBanner(
                                    language = language,
                                    warningMessage = falseSosWarningBanner,
                                    penaltyCount = falseSosPenaltyCount,
                                    onDismiss = { viewModel.dismissFalseSosWarningBanner() }
                                )
                            }
                        }

                        if (activeSosAlerts.isEmpty()) {
                            item {
                                Surface(
                                    color = Color(0xFFFAF6F6),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "এই এলাকায় বর্তমানে কোনো সক্রিয় SOS বিপদ সংকেত নেই।"
                                        } else {
                                            "No active SOS alerts in this area right now."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF756568),
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(activeSosAlerts, key = { "find_sos_${it.id}" }) { alert ->
                                val isPlayingThisAlert = activePlaybackAlertId == alert.id
                                val isMySos = alert.userId == viewModel.currentUserId
                                val hasResponded = alert.id in respondedSosIds || viewModel.hasUserRespondedSos(alert)
                                val hasReportedFalse = alert.id in reportedFalseSosIds || viewModel.hasUserReportedFalseSos(alert)
                                val distanceKm = alert.distanceFromKm(userLat, userLng)

                                SosPostCardItem(
                                    alert = alert,
                                    language = language,
                                    isMySos = isMySos,
                                    isHistoryItem = false,
                                    hasRespondedNearby = hasResponded,
                                    hasReportedFalse = hasReportedFalse,
                                    distanceKm = distanceKm,
                                    isPlaying = isPlayingThisAlert,
                                    progressSeconds = if (isPlayingThisAlert) playbackSeconds else 0f,
                                    waveformBars = waveformBars,
                                    onCardTapOpenVideoAudio = {
                                        viewModel.markSosEvidenceInspected(alert.id)
                                        selectedSosForMediaModal = alert
                                    },
                                    onToggleInlinePlay = {
                                        viewModel.markSosEvidenceInspected(alert.id)
                                        viewModel.audioVerifier.togglePlayFiveSecondClip(
                                            scope = scope,
                                            alertId = alert.id,
                                            peakDb = alert.peakAudioDecibels.toInt()
                                        )
                                    },
                                    onRespond = { viewModel.respondToSos(alert) },
                                    onReportFalseAlarm = { viewModel.flagFalseAlarm(alert) },
                                    onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(alert.id) }
                                )
                            }
                        }
                    }

                    else -> {
                        items(areaDonors, key = { it.id.ifBlank { it.userId } }) { donor ->
                            LifelineResponderCard(
                                donor = donor,
                                onCallClick = {
                                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.contactPhone}"))
                                    context.startActivity(dial)
                                }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
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

    if (selectedSosForMediaModal != null) {
        val alert = selectedSosForMediaModal!!
        val isPlayingThisAlert = activePlaybackAlertId == alert.id
        SosVideoAudioInspectorDialog(
            language = language,
            alert = alert,
            isOwner = alert.userId == viewModel.currentUserId,
            isPlaying = isPlayingThisAlert,
            progressSeconds = if (isPlayingThisAlert) playbackSeconds else 0f,
            waveformBars = waveformBars,
            onTogglePlay = {
                viewModel.markSosEvidenceInspected(alert.id)
                viewModel.audioVerifier.togglePlayFiveSecondClip(
                    scope = scope,
                    alertId = alert.id,
                    peakDb = alert.peakAudioDecibels.toInt()
                )
            },
            onRespond = { viewModel.respondToSos(alert) },
            onMarkSafeOwnerOnly = {
                viewModel.resolveOwnSos(alert.id)
                selectedSosForMediaModal = null
            },
            onDismiss = {
                viewModel.audioVerifier.stopPlayback()
                selectedSosForMediaModal = null
            }
        )
    }
}

/**
 * Available Donor Card using clean vector icons (Zero Emojis).
 */
@Composable
fun LifelineResponderCard(
    donor: DonorNode,
    onCallClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
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
                        color = Color(0xFF1A0F12),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified Donor",
                        tint = IosSystemRed,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${donor.bloodGroup} Blood Group • Trust ${donor.trustScore}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A4A4E),
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF756568),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = donor.district,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF756568)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(IosSystemGreen)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = donor.lastDonationDate.ifBlank { "Available now" },
                        style = MaterialTheme.typography.labelSmall,
                        color = IosSystemGreen,
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
                        imageVector = Icons.Default.Call,
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
            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
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
                            color = Color(0xFF1A0F12),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${req.hospitalName} • ${req.district}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5A4A4E),
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
                color = Color(0xFFFAF6F6),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.8.dp, Color(0xFFEDE2E4), RoundedCornerShape(16.dp))
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
                                color = Color(0xFF756568)
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
                                color = Color(0xFF756568)
                            )
                            Text(
                                text = "${req.unitsNeeded} ব্যাগ (Bags)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "ওয়ার্ড / কেবিন" else "Ward / Cabin",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF756568)
                            )
                            Text(
                                text = req.wardCabinNumber.ifBlank { "Emergency Ward" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEDE2E4))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
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
                            color = Color(0xFF1A0F12),
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
                Button(
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
                        imageVector = if (hasPledged) Icons.Default.CheckCircle else Icons.Default.Bloodtype,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasPledged) {
                            if (language == AppLanguage.BENGALI) {
                                "সাড়া দিয়েছেন (${req.respondersCount})"
                            } else {
                                "Pledged (${req.respondersCount})"
                            }
                        } else {
                            if (language == AppLanguage.BENGALI) {
                                "রক্ত দিন (${req.respondersCount} সাড়া)"
                            } else {
                                "Donate Blood (${req.respondersCount})"
                            }
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onCallClick,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp), tint = IosSystemRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", color = IosSystemRed, fontWeight = FontWeight.Bold)
                }

                if (isOwner) {
                    OutlinedButton(
                        onClick = onFulfillClick,
                        shape = CircleShape,
                        modifier = Modifier.testTag("delete_fulfilled_request_button_${req.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove Post",
                            tint = Color(0xFF4A0E17),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) "ম্যানেজড" else "Managed",
                            color = Color(0xFF4A0E17),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapPinDetailOverlayCard(
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
                        is SelectedMapPin.DonorPin -> Icons.Default.Bloodtype
                        is SelectedMapPin.RequestPin -> Icons.Default.LocalHospital
                        is SelectedMapPin.SosPin -> Icons.Default.Shield
                        is SelectedMapPin.BlePin -> Icons.Default.BluetoothConnected
                    }
                    Icon(
                        imageVector = iconVec,
                        contentDescription = null,
                        tint = Color(0xFFFFB3C0),
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
                                color = Color(0xFFFFB3C0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is SelectedMapPin.SosPin -> {
                            Text(
                                text = "SOS: ${pin.alert.senderName} (${pin.alert.triggerMethod})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFB3C0),
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
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(18.dp))
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
                            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${pin.donor.contactPhone}"))
                            context.startActivity(dial)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
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
                            containerColor = PinCrimsonRed,
                            disabledContainerColor = IosSystemGreen.copy(alpha = 0.25f),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (hasPledged) {
                                if (language == AppLanguage.BENGALI) "আপনি সাড়া দিয়েছেন" else "Already Pledged"
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
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == AppLanguage.BENGALI) "ভিডিও" else "5s Clip")
                        }

                        if (isOwner) {
                            Button(
                                onClick = { onResolveOwnSos(pin.alert) },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
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
                                            "ভুয়া (${pin.alert.falseAlarmReports})"
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

/**
 * Screen 3 (SOS + BLE Screen):
 * Streamlined per user instructions:
 * - Dedicated exclusively to Sending SOS (5s Camera/Audio + 3x Power Button + BLE Mesh) and Turning Off / Marking Safe the user's own active SOS.
 * - Shows a clear False-SOS Warning Banner if the user has any false SOS reports/penalties.
 * - Shows ONLY whether THIS user (`alert.userId == viewModel.currentUserId`) has previously sent any SOS alerts ("আমার পূর্বের SOS হিস্ট্রি / My Past SOS History").
 * - Active SOS alerts from other users are viewed & responded to inside the "Find -> SOS" tab!
 */
@Composable
fun SosGuardianAndBleScreen(
    viewModel: RokkhonViewModel,
    recentPowerTaps: Int = 0,
    onSimulatePowerTap: () -> Unit = {},
    onOpenSosVerification: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val isAdvertising by viewModel.bleMeshManager.isAdvertising.collectAsStateWithLifecycle()
    val bleStatus by viewModel.bleMeshManager.statusMessage.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val activePlaybackAlertId by viewModel.audioVerifier.activePlaybackAlertId.collectAsStateWithLifecycle()
    val playbackSeconds by viewModel.audioVerifier.playbackProgressSeconds.collectAsStateWithLifecycle()
    val waveformBars by viewModel.audioVerifier.waveformSamples.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()

    var selectedSosForMediaModal by remember { mutableStateOf<SosAlert?>(null) }

    val allAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    // ONLY this user's own SOS alerts (Active + Past History)
    val mySosAlerts = allAlerts.filter { it.userId == viewModel.currentUserId }
    val myActiveSos = mySosAlerts.firstOrNull {
        it.status == "ACTIVE" && !it.isExpiredAfter24Hours()
    }
    val myHistorySosList = mySosAlerts.filter { it.id != myActiveSos?.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarObsidianBg)
    ) {
        // 1. Dark Maroon SOS Transmitter & Turn-Off Hero Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Surface(
                color = PinCrimsonRed.copy(alpha = 0.22f),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color(0xFFFFB3C0),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "SOS + BLE ট্রান্সমিটার • SOS পাঠান ও বন্ধ করুন • আপনার নিজস্ব SOS হিস্ট্রি"
                        } else {
                            "SOS + BLE TRANSMITTER • SEND OR TURN OFF SOS • YOUR PERSONAL SOS HISTORY"
                        },
                        color = Color(0xFFFFB3C0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (myActiveSos != null || isLiveLocationActive) {
                    if (language == AppLanguage.BENGALI) {
                        "আপনার SOS এখন সক্রিয় আছে!"
                    } else {
                        "Your Emergency SOS is LIVE!"
                    }
                } else {
                    if (language == AppLanguage.BENGALI) {
                        "জরুরি SOS পাঠান বা বন্ধ করুন"
                    } else {
                        "Send Emergency SOS\nor Turn Off Anytime."
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (language == AppLanguage.BENGALI) {
                    "এখান থেকে ৫-সেকেন্ড অডিও-ভিডিওসহ SOS পাঠাতে ও বন্ধ (নিরাপদ) করতে পারবেন এবং আপনি আগে কোনো SOS দিয়েছেন কিনা তার হিস্ট্রি দেখতে পারবেন। অন্যদের SOS দেখতে Find ট্যাবে যান।"
                } else {
                    "Send a 5s Audio/Video + BLE SOS or turn off your active SOS here, and review your own past SOS history below. To view or respond to others' SOS alerts, open the Find tab."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .clickable { onSimulatePowerTap() }
                        .testTag("simulate_3x_power_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recentPowerTaps > 0) "Tap ($recentPowerTaps/3)" else "3x Power Auto",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onOpenSosVerification,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("sos_guardian_main_button")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) "৫-সেকেন্ড SOS পাঠান" else "Send 5s SOS",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Prominent Turn Off / Mark Safe Button when the current user has an active SOS
            if (myActiveSos != null || isLiveLocationActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("turn_off_my_sos_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "আমি এখন নিরাপদ • আমার সক্রিয় SOS বন্ধ করুন"
                        } else {
                            "I Am Safe Now • Turn Off My Active SOS"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp
                    )
                }
            }
        }

        // 2. White Rounded Bottom Sheet: My Active SOS + False Warning + BLE Transmitter + My Past SOS History
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))

                    // False SOS Warning Banner (Always informs user of anti-false-SOS policy & any penalties)
                    FalseSosAccountWarningBanner(
                        language = language,
                        warningMessage = falseSosWarningBanner,
                        penaltyCount = falseSosPenaltyCount,
                        onDismiss = if (falseSosWarningBanner != null) {
                            { viewModel.dismissFalseSosWarningBanner() }
                        } else {
                            null
                        }
                    )
                }

                // If the user currently has an active SOS broadcasting, show it with Turn Off button
                if (myActiveSos != null) {
                    item {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "আপনার বর্তমান সক্রিয় SOS (চালু আছে)"
                            } else {
                                "Your Currently Active SOS Broadcast"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PinCrimsonRed,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    item {
                        val isPlayingThisAlert = activePlaybackAlertId == myActiveSos.id
                        SosPostCardItem(
                            alert = myActiveSos,
                            language = language,
                            isMySos = true,
                            isHistoryItem = false,
                            hasRespondedNearby = false,
                            isPlaying = isPlayingThisAlert,
                            progressSeconds = if (isPlayingThisAlert) playbackSeconds else 0f,
                            waveformBars = waveformBars,
                            onCardTapOpenVideoAudio = {
                                selectedSosForMediaModal = myActiveSos
                            },
                            onToggleInlinePlay = {
                                viewModel.audioVerifier.togglePlayFiveSecondClip(
                                    scope = scope,
                                    alertId = myActiveSos.id,
                                    peakDb = myActiveSos.peakAudioDecibels.toInt()
                                )
                            },
                            onRespond = {},
                            onReportFalseAlarm = {},
                            onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(myActiveSos.id) }
                        )
                    }
                }

                // Offline BLE Mesh & Quick Link to Find -> Active SOS Tab
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6F6)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
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
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(LifelineBlush),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BluetoothConnected,
                                            contentDescription = "BLE Mesh",
                                            tint = IosSystemRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) {
                                                "অফলাইন ব্লুটুথ (BLE) ও লাইভ জিপিএস স্ট্যাটাস"
                                            } else {
                                                "Offline BLE Mesh & Live GPS Status"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF1A0F12),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isLiveLocationActive) {
                                                "LIVE GPS SHARING ACTIVE • $bleStatus"
                                            } else {
                                                "$bleStatus • ${bleBeacons.size} nearby BLE nodes"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLiveLocationActive) IosSystemGreen else IosSystemRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                if (isAdvertising || isLiveLocationActive) {
                                    OutlinedButton(
                                        onClick = { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") },
                                        shape = CircleShape
                                    ) {
                                        Text(if (language == AppLanguage.BENGALI) "বন্ধ করুন" else "Turn Off")
                                    }
                                } else {
                                    Surface(
                                        color = LifelineBlush,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                                                viewModel.selectTab(RokkhonTab.RADAR_MAP)
                                            }
                                            .testTag("open_find_sos_tab_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) "Find-এ সবার SOS দেখুন" else "Find SOS Tab",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = IosSystemRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = IosSystemRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // My Past SOS History Section (Shows whether THIS user has previously sent any SOS)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = IosSystemRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "আমার পূর্বের SOS হিস্ট্রি (${myHistorySosList.size}টি রেকর্ড)"
                                    } else {
                                        "My Past SOS History (${myHistorySosList.size} Records)"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        if (mySosAlerts.isEmpty()) {
                                            "আপনি আগে কখনো কোনো SOS পাঠাননি"
                                        } else {
                                            "আপনি আগে মোট ${mySosAlerts.size} বার SOS পাঠিয়েছেন (৫-সেকেন্ড অডিও-ভিডিওসহ সংরক্ষিত)"
                                        }
                                    } else {
                                        if (mySosAlerts.isEmpty()) {
                                            "You have not sent any SOS alerts previously"
                                        } else {
                                            "You have sent ${mySosAlerts.size} SOS alert(s) from this account (5s clips archived)"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF756568)
                                )
                            }
                        }
                    }
                }

                if (myHistorySosList.isEmpty()) {
                    item {
                        Surface(
                            color = Color(0xFFFAF6F6),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "আপনার অ্যাকাউন্ট থেকে পূর্বে পাঠানো কোনো SOS হিস্ট্রি নেই।"
                                } else {
                                    "No past SOS history found for your account."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF756568),
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(myHistorySosList, key = { "my_history_sos_${it.id}" }) { alert ->
                        val isPlayingThisAlert = activePlaybackAlertId == alert.id
                        SosPostCardItem(
                            alert = alert,
                            language = language,
                            isMySos = true,
                            isHistoryItem = true,
                            hasRespondedNearby = false,
                            isPlaying = isPlayingThisAlert,
                            progressSeconds = if (isPlayingThisAlert) playbackSeconds else 0f,
                            waveformBars = waveformBars,
                            onCardTapOpenVideoAudio = {
                                selectedSosForMediaModal = alert
                            },
                            onToggleInlinePlay = {
                                viewModel.audioVerifier.togglePlayFiveSecondClip(
                                    scope = scope,
                                    alertId = alert.id,
                                    peakDb = alert.peakAudioDecibels.toInt()
                                )
                            },
                            onRespond = {},
                            onReportFalseAlarm = {},
                            onMarkSafeOwnerOnly = {}
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    if (selectedSosForMediaModal != null) {
        val alert = selectedSosForMediaModal!!
        val isPlayingThisAlert = activePlaybackAlertId == alert.id
        SosVideoAudioInspectorDialog(
            language = language,
            alert = alert,
            isOwner = alert.userId == viewModel.currentUserId,
            isPlaying = isPlayingThisAlert,
            progressSeconds = if (isPlayingThisAlert) playbackSeconds else 0f,
            waveformBars = waveformBars,
            onTogglePlay = {
                viewModel.audioVerifier.togglePlayFiveSecondClip(
                    scope = scope,
                    alertId = alert.id,
                    peakDb = alert.peakAudioDecibels.toInt()
                )
            },
            onRespond = { viewModel.respondToSos(alert) },
            onMarkSafeOwnerOnly = {
                viewModel.resolveOwnSos(alert.id)
                selectedSosForMediaModal = null
            },
            onDismiss = {
                viewModel.audioVerifier.stopPlayback()
                selectedSosForMediaModal = null
            }
        )
    }
}

/**
 * Reusable SOS Post Card (used in Find -> Active SOS and in SOS + BLE -> My Past SOS History).
 * - In Find -> Active SOS:
 *   - Shows the "Nearby / যাচ্ছি (Count)" button (`sos_nearby_respond_button_${alert.id}`)
 *   - Shows the "False / ভুয়া (X/3)" button (`sos_false_report_button_${alert.id}`) with live false count
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
                            text = "${alert.senderName} • ${alert.emergencyType}",
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
                            text = "${alert.district} • ${alert.triggerMethod}$distLabel",
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
                                else -> "5s Video+Audio"
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
                            "ভুয়া (False) কাউন্ট: ${alert.falseAlarmReports}"
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
                                "প্রমাণ শিল্ড সক্রিয়: ${alert.falseAlarmReports}টি ভুয়া রিপোর্ট থাকা সত্ত্বেও ৫-সেকেন্ড অডিও/ভিডিও প্রমাণ ও সাহায্যকারীর কারণে ভিক্টিমের ট্রাস্ট পয়েন্ট কাটা হয়নি। অ্যালার্টটি ২৪ ঘণ্টা সক্রিয় থাকবে।"
                            } else {
                                "Evidence & Responder Shield Active: Despite ${alert.falseAlarmReports} False reports, verified 5s media/responder presence protects the sender's Trust Points (0 pts deducted). Remains active for 24h."
                            }
                        } else {
                            if (language == AppLanguage.BENGALI) {
                                "কমিউনিটি ডিসপিউটেড (${alert.falseAlarmReports}টি False রিপোর্ট): অ্যালার্টটি সাথে সাথে মুছে ফেলা হয়নি; ২৪ ঘণ্টা পর অটো-রিমুভ হবে।"
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

            // Interactive 5-Second Audio & Video Evidence Player Box
            FiveSecondEvidencePlayerCard(
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
                    // 2. "False / ভুয়া (Count)" Button (Strictly 1 vote per account; never auto-deletes at 3)
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
                                imageVector = if (hasRespondedNearby) Icons.Default.CheckCircle else Icons.Default.DirectionsRun,
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
                                        "ভুয়া দেওয়া হয়েছে (${alert.falseAlarmReports})"
                                    } else {
                                        "Reported False (${alert.falseAlarmReports})"
                                    }
                                } else {
                                    if (language == AppLanguage.BENGALI) {
                                        "ভুয়া / False (${alert.falseAlarmReports})"
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "নোট: ১টি অ্যাকাউন্ট থেকে ১ বারই False দেওয়া যাবে। ৩+ False হলেও অ্যালার্ট মুছে যাবে না (২৪ ঘণ্টা পর অটো-রিমুভ হবে) এবং প্রমাণ থাকলে ট্রাস্ট পয়েন্ট সুরক্ষিত থাকবে।"
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
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = IosSystemGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "৫-সেকেন্ড ভিডিও ও অডিও (${"%.1f".format(progressSeconds)}s / 5.0s)"
                        } else {
                            "5s Video & Audio (${"%.1f".format(progressSeconds)}s / 5.0s)"
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
                            contentDescription = "Play 5s Clip",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) {
                                if (language == AppLanguage.BENGALI) "থামান" else "Stop"
                            } else {
                                if (language == AppLanguage.BENGALI) "৫-সেকেন্ড শুনুন ও দেখুন" else "Play 5s Clip"
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
                            text = "CAM 5S",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (language == AppLanguage.BENGALI) "ট্যাপ করুন" else "Tap Video",
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
                        "PLAYING 5S VIDEO & AUDIO..."
                    } else if (language == AppLanguage.BENGALI) {
                        "পূর্ণ ভিডিও ও অডিও দেখতে ট্যাপ করুন"
                    } else {
                        "TAP POST TO WATCH VIDEO & AUDIO"
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
                                        "SOS ৫-সেকেন্ড ভিডিও ও অডিও প্রমাণ"
                                    } else {
                                        "SOS 5-Second Video & Audio Evidence"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${alert.senderName} • ${alert.district} (${alert.triggerMethod})",
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
                                        if (language == AppLanguage.BENGALI) "ভিডিও ও অডিও চলছে..." else "Playing 5s Clip..."
                                    } else {
                                        if (language == AppLanguage.BENGALI) "৫-সেকেন্ড ভিডিও ও অডিও চালান" else "Play 5s Video & Audio"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FiveSecondEvidencePlayerCard(
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
                                Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "কাছাকাছি / যাচ্ছি মার্ক করুন (+৫০ পয়েন্ট)"
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

/**
 * Full-Screen Emergency Blood Request Sheet with Searchable All-Bangladesh Area Selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostBloodRequestDialog(
    language: AppLanguage,
    defaultDistrict: String,
    defaultHospital: String,
    onDismiss: () -> Unit,
    onSubmit: (
        patientName: String,
        bloodGroup: String,
        unitsNeeded: Int,
        hemoglobinLevel: String,
        patientProblem: String,
        hospitalName: String,
        wardCabinNumber: String,
        neededDateTime: String,
        district: String,
        contactPhone: String,
        urgencyLevel: String
    ) -> Unit
) {
    var bloodGroup by remember { mutableStateOf("O+") }
    var patientName by remember { mutableStateOf("") }
    var unitsNeeded by remember { mutableIntStateOf(2) }
    var hemoglobinLevel by remember { mutableStateOf("6.5 g/dL") }
    var patientProblem by remember { mutableStateOf("ডেঙ্গু / জরুরি অপারেশন (Dengue / Surgery)") }
    var hospitalName by remember { mutableStateOf(defaultHospital) }
    var wardCabinNumber by remember { mutableStateOf("Ward 14, Cabin 302") }
    var district by remember { mutableStateOf(defaultDistrict) }
    var contactPhone by remember { mutableStateOf("+8801711") }
    var selectedUrgencySlot by remember { mutableStateOf("NOW") }
    var showAreaPickerDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RadarObsidianBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (language == AppLanguage.BENGALI) "Emergency Request • রক্তের আবেদন" else "Emergency Request",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Let's find the\nright donor.",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 34.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (language == AppLanguage.BENGALI) {
                        "সারা বাংলাদেশের যেকোনো জেলা বা এলাকা সিলেক্ট করে রোগীর তথ্য দিন।"
                    } else {
                        "Triggers real-time alert to available donors in your selected Bangladesh area."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2D5D7))
                        )
                    }

                    Text(
                        text = if (language == AppLanguage.BENGALI) "Blood group needed • রক্তের গ্রুপ" else "Blood group needed",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1A0F12),
                        fontWeight = FontWeight.ExtraBold
                    )

                    BloodGroupPillGrid2x4(
                        selectedBloodGroup = bloodGroup,
                        onSelectBloodGroup = { bloodGroup = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "রোগীর এলাকা সিলেক্ট করুন (৬৪ জেলা)" else "Select Hospital Area (All 64 Districts)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF1A0F12),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            color = LifelineBlush,
                            shape = CircleShape,
                            modifier = Modifier.clickable { showAreaPickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "সব জেলা খুঁজুন" else "Search All 64",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IosSystemRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BANGLADESH_DISTRICTS.take(16).forEach { dist ->
                            val isSelected = district.contains(dist.nameEn.substringBefore(","))
                            Surface(
                                color = if (isSelected) IosSystemRed else Color(0xFFFAF6F6),
                                contentColor = if (isSelected) Color.White else Color(0xFF1A0F12),
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFEDE2E4), CircleShape)
                                    .clickable {
                                        district = dist.nameEn
                                        hospitalName = dist.defaultHospitalEn
                                    }
                            ) {
                                Text(
                                    text = dist.nameEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (language == AppLanguage.BENGALI) "Hospital & Clinical Details • রোগীর তথ্য" else "Hospital & Clinical Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1A0F12),
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = hospitalName,
                        onValueChange = { hospitalName = it },
                        leadingIcon = {
                            Icon(Icons.Default.LocalHospital, contentDescription = null, tint = IosSystemRed)
                        },
                        label = { Text(if (language == AppLanguage.BENGALI) "হাসপাতালের নাম (Hospital)" else "Hospital Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_hospital_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = IosSystemRed)
                        },
                        label = { Text(if (language == AppLanguage.BENGALI) "এলাকা / জেলা (Location)" else "Location / Area") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text(if (language == AppLanguage.BENGALI) "রোগীর নাম (Patient Name)" else "Patient Name") },
                        placeholder = { Text("রাবেয়া খাতুন / আব্দুল করিম") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_patient_name_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hemoglobinLevel,
                            onValueChange = { hemoglobinLevel = it },
                            label = { Text(if (language == AppLanguage.BENGALI) "হিমোগ্লোবিন (Hb)" else "Hemoglobin (g/dL)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("blood_request_hemoglobin_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wardCabinNumber,
                            onValueChange = { wardCabinNumber = it },
                            label = { Text(if (language == AppLanguage.BENGALI) "ওয়ার্ড / কেবিন" else "Ward / Cabin") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("blood_request_ward_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "রক্তের ব্যাগ:" else "Bags Needed:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1A0F12),
                            fontWeight = FontWeight.Bold
                        )
                        listOf(1, 2, 3, 4).forEach { count ->
                            val isSelected = unitsNeeded == count
                            Surface(
                                color = if (isSelected) IosSystemRed else Color(0xFFFAF6F6),
                                contentColor = if (isSelected) Color.White else Color(0xFF1A0F12),
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFEDE2E4), CircleShape)
                                    .clickable { unitsNeeded = count }
                            ) {
                                Text(
                                    text = "$count ব্যাগ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = patientProblem,
                        onValueChange = { patientProblem = it },
                        label = { Text(if (language == AppLanguage.BENGALI) "রোগীর সমস্যা (Problem / Diagnosis)" else "Problem / Diagnosis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_problem_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text(if (language == AppLanguage.BENGALI) "জরুরি মোবাইল নম্বর (Phone)" else "Contact Phone") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_phone_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Text(
                        text = if (language == AppLanguage.BENGALI) "How urgent is it? • কখন রক্ত লাগবে?" else "How urgent is it?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1A0F12),
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val slots = listOf(
                            Triple("NOW", "Now", "Emergency"),
                            Triple("SIX_HRS", "6 hrs", "Urgent"),
                            Triple("TODAY", "Today", "Flexible")
                        )
                        slots.forEach { (key, title, sub) ->
                            val isSel = selectedUrgencySlot == key
                            val iconVec = when (key) {
                                "NOW" -> Icons.Default.FlashOn
                                "SIX_HRS" -> Icons.Default.Schedule
                                else -> Icons.Default.WbSunny
                            }
                            Surface(
                                color = if (isSel) IosSystemRed else Color.White,
                                contentColor = if (isSel) Color.White else Color(0xFF1A0F12),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isSel) 0.dp else 1.dp,
                                        color = Color(0xFFEDE2E4),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedUrgencySlot = key }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = title,
                                        tint = if (isSel) Color.White else IosSystemRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) Color.White.copy(alpha = 0.8f) else Color(0xFF756568)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val mappedUrgency = when (selectedUrgencySlot) {
                                "NOW" -> "CRITICAL"
                                "SIX_HRS" -> "URGENT"
                                else -> "STANDARD"
                            }
                            val mappedTimeText = when (selectedUrgencySlot) {
                                "NOW" -> "Now • Emergency"
                                "SIX_HRS" -> "Within 6 hrs • Urgent"
                                else -> "Today • Flexible"
                            }
                            onSubmit(
                                patientName.ifBlank { "Emergency Patient" },
                                bloodGroup,
                                unitsNeeded,
                                hemoglobinLevel.ifBlank { "6.5 g/dL" },
                                patientProblem.ifBlank { "Emergency Transfusion" },
                                hospitalName.ifBlank { "Dhaka Medical College Hospital" },
                                wardCabinNumber.ifBlank { "Emergency Ward" },
                                mappedTimeText,
                                district.ifBlank { "Mirpur 10, Dhaka" },
                                contactPhone.ifBlank { "+8801711000000" },
                                mappedUrgency
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_blood_request_button")
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "জরুরি রক্তের আবেদন পোস্ট করুন"
                            } else {
                                "Post Emergency Blood Request"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }

    if (showAreaPickerDialog) {
        val currentDistObj = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: BANGLADESH_DISTRICTS.first()
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = currentDistObj,
            isAutoGpsActive = false,
            onSelectAutoGps = {
                district = currentDistObj.nameEn
                hospitalName = currentDistObj.defaultHospitalEn
            },
            onSelectDistrict = { dist ->
                district = dist.nameEn
                hospitalName = dist.defaultHospitalEn
            },
            onDismiss = { showAreaPickerDialog = false }
        )
    }
}
