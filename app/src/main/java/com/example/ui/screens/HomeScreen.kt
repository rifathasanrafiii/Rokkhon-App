package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.DonorNode
import com.example.data.model.NotificationKind
import com.example.data.model.canDonateTo
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InkText
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.MutedText
import com.example.ui.components.OverlappingDonorAvatarRow
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.components.SoftRose
import com.example.ui.components.initialsOf
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

private val ScreenBg = Color(0xFFFAF8F8)
private val BrandRed = Color(0xFFB80F24)

/**
 * Donors visible for a patient group: compatible donors only (exact group first), never the user themself.
 */
internal fun filterMatchingDonors(
    all: List<DonorNode>,
    currentUserId: String,
    patientGroup: String?,
    onlyAvailable: Boolean,
    areaMatches: (String) -> Boolean
): List<DonorNode> = all
    .filter { donor ->
        donor.userId != currentUserId &&
            (!onlyAvailable || donor.isEligibleNow) &&
            (patientGroup == null || canDonateTo(donor.bloodGroup, patientGroup)) &&
            areaMatches(donor.district)
    }
    .sortedWith(
        compareByDescending<DonorNode> { patientGroup != null && it.bloodGroup == patientGroup }
            .thenByDescending { it.isEligibleNow }
    )

/**
 * Home: whole page scrolls (the old fixed hero left only a sliver of list on small phones),
 * quick Request Blood / SOS actions, area + blood group filter and the matching feed.
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
    val bn = language == AppLanguage.BENGALI
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
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()
    val needsProfileSetup by viewModel.needsProfileSetup.collectAsStateWithLifecycle()

    var homeFeedTab by rememberSaveable { mutableStateOf(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }

    val areaMatches: (String) -> Boolean = { !isAreaFilterEnabled || selectedDistrict.matchesArea(it) }
    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    val matchingDonors = filterMatchingDonors(allDonors, viewModel.currentUserId, selectedBloodGroup, onlyAvailableFilter, areaMatches)

    val allRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val openRequests = allRequests.filter { req ->
        req.status == "OPEN" &&
            (selectedBloodGroup == null || req.bloodGroup == selectedBloodGroup) &&
            areaMatches(req.district)
    }

    val allSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    val activeMySos = allSosAlerts.firstOrNull {
        it.status == "ACTIVE" && !it.isExpiredAfter24Hours() &&
            (it.id == activeUserSosId || it.userId == viewModel.currentUserId)
    }
    val isLoading = donorsState is UiState.Loading || requestsState is UiState.Loading

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") {
            HomeHero(
                language = language,
                userInitial = viewModel.currentUserName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "R",
                streakDays = dailyStreakCount,
                guardianPoints = guardianPoints,
                unreadCount = unreadNotificationCount,
                hasActiveSos = activeMySos != null,
                onOpenProfile = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                onToggleLanguage = { viewModel.toggleLanguage() },
                onOpenNotifications = { showNotificationCenterDialog = true },
                onRequestBlood = onOpenBloodRequestDialog,
                onSos = onOpenSosVerification,
                onMarkSafe = { viewModel.resolveOwnSos(activeMySos?.id ?: activeUserSosId ?: "") }
            )
        }

        if (viewModel.isDemoMode) {
            item(key = "demo") { DemoModeBanner(language, Modifier.padding(horizontal = 16.dp)) }
        }

        if (needsProfileSetup) {
            item(key = "setup") {
                ActionCard(
                    title = if (bn) "আপনার ডোনার প্রোফাইল তৈরি করুন" else "Create your donor profile",
                    subtitle = if (bn) "রক্তের গ্রুপ ও নম্বর দিলে কাছের রোগীরা আপনাকে খুঁজে পাবেন" else "Add your blood group and number so nearby patients can find you",
                    onClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item(key = "streak") {
            DailySafetyStreakCheckInCard(
                language = language,
                streakDays = dailyStreakCount,
                checkedInToday = checkedInToday,
                guardianPoints = guardianPoints,
                onCheckInClick = { viewModel.performDailySafeCheckIn() },
                onOpenRewards = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "area") {
            AreaSelectorCard(
                language = language,
                areaName = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                isAreaFilterEnabled = isAreaFilterEnabled,
                onToggleAreaFilter = { viewModel.toggleAreaFilter(!isAreaFilterEnabled) },
                onOpenPicker = { showAreaPickerDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "match") {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (bn) "রোগীর রক্তের গ্রুপ" else "Patient's blood group",
                                fontSize = 18.sp,
                                color = InkText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    selectedBloodGroup == null -> if (bn) "সব গ্রুপ দেখানো হচ্ছে" else "Showing all groups"
                                    bn -> "$selectedBloodGroup রোগীকে যারা দিতে পারবেন"
                                    else -> "Donors who can give to $selectedBloodGroup"
                                },
                                fontSize = 13.sp,
                                color = MutedText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = onlyAvailableFilter,
                                onCheckedChange = { viewModel.setOnlyAvailableDonorsFilter(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFE31B32)
                                ),
                                modifier = Modifier.testTag("donor_active_switch")
                            )
                            Text(
                                text = if (onlyAvailableFilter) (if (bn) "শুধু উপলব্ধ" else "Available only") else (if (bn) "সব ডোনার" else "All donors"),
                                fontSize = 11.5.sp,
                                color = if (onlyAvailableFilter) IosSystemGreen else MutedText,
                                fontWeight = FontWeight.Bold
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

        item(key = "summary") {
            DonorSummaryCard(
                language = language,
                donors = matchingDonors,
                areaName = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                isAreaFilterEnabled = isAreaFilterEnabled,
                onOpenMap = {
                    viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                    viewModel.selectTab(RokkhonTab.RADAR_MAP)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "feed_tabs") {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedTabCard(
                    label = if (bn) "রোগীর আবেদন" else "Patient requests",
                    count = openRequests.size,
                    icon = Icons.Default.LocalHospital,
                    selected = homeFeedTab == MapLayerFilter.BLOOD_REQUESTS_ONLY,
                    onClick = { homeFeedTab = MapLayerFilter.BLOOD_REQUESTS_ONLY },
                    tag = "filter_tab_patient_requests",
                    modifier = Modifier.weight(1f)
                )
                FeedTabCard(
                    label = if (bn) "উপলব্ধ ডোনার" else "Available donors",
                    count = matchingDonors.size,
                    icon = Icons.Default.People,
                    selected = homeFeedTab == MapLayerFilter.DONORS_ONLY,
                    onClick = { homeFeedTab = MapLayerFilter.DONORS_ONLY },
                    tag = "filter_tab_available_donors",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isLoading) {
            item(key = "loading") {
                EmptyStateCard(
                    text = if (bn) "লোড হচ্ছে…" else "Loading…",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else if (homeFeedTab == MapLayerFilter.DONORS_ONLY) {
            if (matchingDonors.isEmpty()) {
                item(key = "no_donors") {
                    EmptyStateCard(
                        text = if (bn) {
                            "এই এলাকায় এখন কোনো উপলব্ধ ডোনার নেই। 'সকল এলাকা' চালু করুন বা রক্তের আবেদন পোস্ট করে শেয়ার করুন।"
                        } else {
                            "No available donors here right now. Switch to all areas, or post a request and share it."
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(matchingDonors, key = { "home_donor_${it.userId.ifBlank { it.id }}" }) { donor ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LifelineResponderCard(
                        donor = donor,
                        language = language,
                        patientGroup = selectedBloodGroup,
                        onCallClick = { RokkhonIntents.dial(context, donor.contactPhone, language) }
                    )
                }
            }
        } else {
            if (openRequests.isEmpty()) {
                item(key = "no_requests") {
                    EmptyStateCard(
                        text = if (bn) "এই এলাকায় এখন কোনো খোলা রক্তের আবেদন নেই।" else "No open blood requests in this area right now.",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(openRequests, key = { "home_req_${it.id}" }) { req ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ClinicalBloodRequestCard(
                        req = req,
                        language = language,
                        isOwner = req.userId == viewModel.currentUserId,
                        hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req),
                        onDonateClick = { viewModel.pledgeToDonate(req) },
                        onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                        onShareClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                        onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                    )
                }
            }
        }

        item(key = "bottom_space") { Spacer(modifier = Modifier.height(16.dp)) }
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

@Composable
private fun HomeHero(
    language: AppLanguage,
    userInitial: String,
    streakDays: Int,
    guardianPoints: Int,
    unreadCount: Int,
    hasActiveSos: Boolean,
    onOpenProfile: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRequestBlood: () -> Unit,
    onSos: () -> Unit,
    onMarkSafe: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF7B0C1A), Color(0xFF5A0913), Color(0xFF2B060B))))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)
        ) {
            Canvas(modifier = Modifier.size(200.dp).align(Alignment.CenterEnd)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD91E36).copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.45f),
                        radius = size.minDimension * 0.75f
                    )
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE01E37)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bn) "রক্ষণ" else "rokkhon",
                        fontSize = 21.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    HeroPill(onClick = onOpenProfile, tag = "header_streak_points_pill") {
                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("$streakDays", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("$guardianPoints", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    HeroPill(onClick = onToggleLanguage, tag = "language_toggle_button") {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        // Shows the language you will switch TO.
                        Text(if (bn) "EN" else "বাংলা", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    HeroCircle(onClick = onOpenNotifications, tag = "notification_bell_button") {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = if (bn) "নোটিফিকেশন" else "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 3.dp, end = 3.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF2B45)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    HeroCircle(onClick = onOpenProfile, tag = "open_profile_avatar") {
                        Text(userInitial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (bn) "রক্ত দিন • জীবন বাঁচান • পাশে থাকুন" else "DONATE  •  SAVE  •  TOGETHER",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (bn) 0.5.sp else 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (bn) "প্রতিটি ফোঁটা\nএকটি জীবন বাঁচাতে পারে।" else "Every drop\ncan save a life.",
                    fontSize = if (bn) 28.sp else 31.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = if (bn) 38.sp else 35.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (bn) {
                        "এলাকার রক্তদাতা খুঁজুন, আর বিপদে এক ট্যাপে SOS পাঠান।"
                    } else {
                        "Find donors near you, and send an SOS in one tap when in danger."
                    },
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onRequestBlood() }
                            .testTag("request_blood_button")
                    ) {
                        Row(modifier = Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFFC8102E), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (bn) "রক্তের আবেদন" else "Request blood",
                                fontSize = 15.sp,
                                color = InkText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Surface(
                        color = Color(0xFFE11D48),
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .height(50.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onSos() }
                            .testTag("home_sos_button")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SOS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (hasActiveSos) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = PinCrimsonRed, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (bn) "আপনার SOS চালু • লাইভ লোকেশন শেয়ার হচ্ছে" else "Your SOS is live • sharing location",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onMarkSafe() }
                                    .testTag("home_mark_safe_button")
                            ) {
                                Text(
                                    text = if (bn) "আমি নিরাপদ" else "I'm safe",
                                    color = IosSystemGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // Rounded lip so the light content area starts like a sheet under the hero.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(ScreenBg)
        )
    }
}

@Composable
private fun HeroPill(onClick: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.22f),
        shape = CircleShape,
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun HeroCircle(onClick: () -> Unit, tag: String, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF2B1217),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("profile_setup_card")
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.5.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AreaSelectorCard(
    language: AppLanguage,
    areaName: String,
    isAreaFilterEnabled: Boolean,
    onToggleAreaFilter: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (bn) "এলাকা" else "Area",
                        fontSize = 15.5.sp,
                        color = InkText,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (bn) "৬৪ জেলা ও ঢাকা-চট্টগ্রামের থানা" else "All 64 districts + Dhaka/Ctg areas",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                }
                Surface(
                    color = if (isAreaFilterEnabled) Color(0xFFFDF0F1) else Color(0xFFF2ECEE),
                    shape = CircleShape,
                    modifier = Modifier
                        .border(1.dp, if (isAreaFilterEnabled) Color(0xFFF5D8DC) else Color(0xFFE2D8DA), CircleShape)
                        .clip(CircleShape)
                        .clickable { onToggleAreaFilter() }
                        .testTag("area_filter_mode_toggle")
                ) {
                    Text(
                        text = if (isAreaFilterEnabled) {
                            if (bn) "শুধু এই এলাকা" else "This area only"
                        } else {
                            if (bn) "সকল এলাকা" else "All areas"
                        },
                        color = if (isAreaFilterEnabled) BrandRed else Color(0xFF5A4A4E),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color(0xFFFAF8F8),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Color(0xFFE6DFE0), RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .clickable { onOpenPicker() }
                    .testTag("area_dropdown_selector")
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = areaName,
                        fontSize = 15.sp,
                        color = InkText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF2B1E20))
                }
            }
        }
    }
}

@Composable
private fun DonorSummaryCard(
    language: AppLanguage,
    donors: List<DonorNode>,
    areaName: String,
    isAreaFilterEnabled: Boolean,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Color(0xFFFAF2F3),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF2E2E4), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable { onOpenMap() }
            .testTag("explore_compatible_donors_card")
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SoftRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = BrandRed, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bn) "${donors.size} জন ডোনার পাওয়া গেছে" else "${donors.size} donors found",
                    fontSize = 15.sp,
                    color = Color(0xFFA80E20),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isAreaFilterEnabled) areaName else (if (bn) "সারা বাংলাদেশ" else "All Bangladesh"),
                    fontSize = 12.sp,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (bn) "ম্যাপে দেখুন →" else "View on map →",
                    fontSize = 13.sp,
                    color = BrandRed,
                    fontWeight = FontWeight.Bold
                )
            }
            if (donors.isNotEmpty()) {
                OverlappingDonorAvatarRow(
                    initials = donors.take(3).map { initialsOf(it.displayName) },
                    extraCount = (donors.size - 3).coerceAtLeast(0)
                )
            }
        }
    }
}

@Composable
private fun FeedTabCard(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) Color(0xFF2B1217) else Color(0xFFFAF2F3),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .border(1.dp, if (selected) Color(0xFF2B1217) else Color(0xFFF2E2E4), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab) { onClick() }
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.14f) else SoftRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) Color.White else BrandRed, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (selected) Color.White.copy(alpha = 0.85f) else Color(0xFF2B1E20),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "$count",
                    fontSize = 19.sp,
                    color = if (selected) Color.White else InkText,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** Daily check-in card. Tapping "Done" opens the profile where badges are listed. */
@Composable
fun DailySafetyStreakCheckInCard(
    language: AppLanguage,
    streakDays: Int,
    checkedInToday: Boolean,
    guardianPoints: Int,
    onCheckInClick: () -> Unit,
    onOpenRewards: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Color(0xFFFDFBFB),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF2E2E4), RoundedCornerShape(22.dp))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SoftRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFE01E37), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bn) "$streakDays দিনের স্ট্রিক • $guardianPoints পয়েন্ট" else "$streakDays-day streak • $guardianPoints pts",
                    fontSize = 15.sp,
                    color = InkText,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (bn) "প্রতিদিন জানান আপনি নিরাপদ ও রক্তদানে প্রস্তুত।" else "Check in daily: you're safe and ready to donate.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MutedText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (checkedInToday) Color(0xFFE5F3EB) else Color(0xFFE01E37),
                shape = CircleShape,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { if (checkedInToday) onOpenRewards() else onCheckInClick() }
                    .testTag("home_daily_streak_checkin_button")
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (checkedInToday) Icons.Default.CheckCircle else Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = if (checkedInToday) IosSystemGreen else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (checkedInToday) (if (bn) "সম্পন্ন" else "Done") else (if (bn) "চেক-ইন" else "Check in"),
                        color = if (checkedInToday) IosSystemGreen else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** 2x4 blood group grid (A+, A-, B+, B-, AB+, AB-, O+, O-). Tapping the selected group clears it. */
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { bg ->
                    val isSelected = selectedBloodGroup == bg
                    Surface(
                        color = if (isSelected) BrandRed else Color.White,
                        contentColor = if (isSelected) Color.White else InkText,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(width = if (isSelected) 0.dp else 1.dp, color = Color(0xFFE6DFE0), shape = CircleShape)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onSelectBloodGroup(bg) }
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
