package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.calculateDistanceKm
import com.example.data.model.canDonateTo
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.BloodBadge
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.StatusChip
import com.example.ui.components.donationStatusText
import com.example.ui.components.initialsOf
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/** Donors who can give to [patientGroup] in the chosen area, best matches first. */
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

private const val HOME_FEED_LIMIT = 5

/**
 * Home: crimson hero (request blood / SOS), your donor status, area, blood-group match,
 * nearby SOS and the request / donor feed for the chosen area.
 */
@Composable
fun HomeMatchHubScreen(
    viewModel: RokkhonViewModel,
    onOpenBloodRequestDialog: () -> Unit,
    onOpenSosVerification: () -> Unit
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
    val respondedSosIds by viewModel.respondedSosIds.collectAsStateWithLifecycle()
    val reportedFalseSosIds by viewModel.reportedFalseSosIds.collectAsStateWithLifecycle()
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val hasGps by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()

    var homeFeedTab by rememberSaveable { mutableStateOf(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }

    val areaName = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn
    val areaMatches: (String) -> Boolean = { !isAreaFilterEnabled || selectedDistrict.matchesArea(it) }
    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    val me = allDonors.firstOrNull { it.userId == viewModel.currentUserId }
    val matchingDonors = filterMatchingDonors(allDonors, viewModel.currentUserId, selectedBloodGroup, onlyAvailableFilter, areaMatches)

    val allRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val openRequests = allRequests
        .filter { it.status == "OPEN" && (selectedBloodGroup == null || it.bloodGroup == selectedBloodGroup) && areaMatches(it.district) }
        .sortedWith(
            compareByDescending<com.example.data.model.BloodRequest> { me != null && canDonateTo(me.bloodGroup, it.bloodGroup) }
                .thenBy { when (it.urgencyLevel) { "CRITICAL" -> 0; "URGENT" -> 1; else -> 2 } }
        )

    val allSos = (sosState as? UiState.Success)?.data.orEmpty()
    val activeMySos = allSos.firstOrNull {
        it.status == "ACTIVE" && !it.isExpiredAfter24Hours() &&
            (it.id == activeUserSosId || it.userId == viewModel.currentUserId)
    }
    val nearbySos = allSos
        .filter {
            it.status == "ACTIVE" && !it.isExpiredAfter24Hours() && it.userId != viewModel.currentUserId &&
                it.distanceFromKm(userLat, userLng) <= SOS_NOTIFICATION_RADIUS_KM
        }
        .sortedBy { it.distanceFromKm(userLat, userLng) }
    val isLoading = donorsState is UiState.Loading || requestsState is UiState.Loading

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Rk.Bg)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            HomeHero(
                language = language,
                userInitial = viewModel.currentUserName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "R",
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

        item(key = "donor_status") {
            DonorStatusCard(
                language = language,
                me = me,
                onToggleAvailable = { viewModel.toggleDonorActiveStatus(me, it) },
                onOpenProfile = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "area") {
            AreaSelectorCard(
                language = language,
                areaName = areaName,
                isAreaFilterEnabled = isAreaFilterEnabled,
                onToggleAreaFilter = { viewModel.toggleAreaFilter(!isAreaFilterEnabled) },
                onOpenPicker = { showAreaPickerDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "match") {
            HomeCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bn) "রোগীর রক্তের গ্রুপ" else "Find your match",
                            fontSize = 18.sp,
                            color = Rk.Ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                selectedBloodGroup == null -> if (bn) "গ্রুপ বাছুন • সব গ্রুপ দেখানো হচ্ছে" else "Select blood group • showing all"
                                bn -> "$selectedBloodGroup রোগীকে যারা দিতে পারবেন"
                                else -> "Donors who can give to $selectedBloodGroup"
                            },
                            fontSize = 13.sp,
                            color = Rk.Muted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(
                            checked = onlyAvailableFilter,
                            onCheckedChange = { viewModel.setOnlyAvailableDonorsFilter(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Rk.Crimson),
                            modifier = Modifier.testTag("available_only_switch")
                        )
                        Text(
                            text = if (onlyAvailableFilter) (if (bn) "শুধু উপলব্ধ" else "Available only") else (if (bn) "সব ডোনার" else "All donors"),
                            fontSize = 11.5.sp,
                            color = if (onlyAvailableFilter) Rk.Green else Rk.Muted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                BloodGroupPillGrid2x4(
                    selectedBloodGroup = selectedBloodGroup,
                    onSelectBloodGroup = { bg -> viewModel.setBloodFilter(bg) }
                )
            }
        }

        item(key = "summary") {
            DonorSummaryCard(
                language = language,
                donors = matchingDonors,
                areaName = if (isAreaFilterEnabled) areaName else (if (bn) "সারা বাংলাদেশ" else "All Bangladesh"),
                onOpenMap = {
                    viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                    viewModel.selectTab(RokkhonTab.RADAR_MAP)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (nearbySos.isNotEmpty()) {
            item(key = "sos_header") {
                SectionHeader(
                    title = if (bn) "কাছে SOS (১ কি.মি.)" else "SOS nearby (1 km)",
                    actionLabel = if (bn) "ম্যাপে দেখুন" else "View on map",
                    onAction = {
                        viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                        viewModel.selectTab(RokkhonTab.RADAR_MAP)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            items(nearbySos.take(2), key = { "home_sos_${it.id}" }) { alert ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SosPostCardItem(
                        alert = alert,
                        language = language,
                        isMySos = false,
                        isHistoryItem = false,
                        hasRespondedNearby = alert.id in respondedSosIds || viewModel.hasUserRespondedSos(alert),
                        hasReportedFalse = alert.id in reportedFalseSosIds || viewModel.hasUserReportedFalseSos(alert),
                        distanceKm = alert.distanceFromKm(userLat, userLng),
                        onOpenDetails = { viewModel.openSosOnMap(alert.id) },
                        onRespond = { viewModel.respondToSos(alert) },
                        onDirections = { RokkhonIntents.directions(context, alert.latitude, alert.longitude, language) },
                        onReportFalseAlarm = { viewModel.flagFalseAlarm(alert) },
                        onMarkSafeOwnerOnly = {}
                    )
                }
            }
        }

        item(key = "feed_tabs") {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedTabCard(
                    label = if (bn) "রোগীর আবেদন" else "Patient posts",
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
                EmptyStateCard(text = if (bn) "লোড হচ্ছে…" else "Loading…", modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else if (homeFeedTab == MapLayerFilter.DONORS_ONLY) {
            if (matchingDonors.isEmpty()) {
                item(key = "no_donors") {
                    EmptyStateCard(
                        text = if (bn) {
                            "এই এলাকায় এখন কোনো উপলব্ধ ডোনার নেই। 'সকল এলাকা' চালু করুন বা আবেদন পোস্ট করে শেয়ার করুন।"
                        } else {
                            "No available donors here right now. Switch to all areas, or post a request and share it."
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(matchingDonors.take(HOME_FEED_LIMIT), key = { "home_donor_${it.userId.ifBlank { it.id }}" }) { donor ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LifelineResponderCard(
                        donor = donor,
                        language = language,
                        patientGroup = selectedBloodGroup,
                        distanceKm = if (hasGps) calculateDistanceKm(userLat, userLng, donor.latitude, donor.longitude) else null,
                        onCallClick = { RokkhonIntents.dial(context, donor.contactPhone, language) }
                    )
                }
            }
            if (matchingDonors.size > HOME_FEED_LIMIT) {
                item(key = "more_donors") {
                    SeeAllRow(
                        text = if (bn) "সব ${matchingDonors.size} জন ডোনার ম্যাপে দেখুন" else "See all ${matchingDonors.size} donors on the map",
                        tag = "home_see_all_donors",
                        onClick = {
                            viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                            viewModel.selectTab(RokkhonTab.RADAR_MAP)
                        }
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
            items(openRequests.take(HOME_FEED_LIMIT), key = { "home_req_${it.id}" }) { req ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ClinicalBloodRequestCard(
                        req = req,
                        language = language,
                        isOwner = req.userId == viewModel.currentUserId,
                        hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req),
                        distanceKm = if (hasGps) calculateDistanceKm(userLat, userLng, req.latitude, req.longitude) else null,
                        onDonateClick = { viewModel.pledgeToDonate(req) },
                        onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                        onShareClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                        onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                    )
                }
            }
            item(key = "all_requests") {
                SeeAllRow(
                    text = if (bn) "সব রক্তের আবেদন দেখুন" else "See all blood requests",
                    tag = "home_see_all_requests",
                    onClick = { viewModel.selectTab(RokkhonTab.REQUESTS) }
                )
            }
        }

        item(key = "bottom_space") { Spacer(modifier = Modifier.height(12.dp)) }
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
                    NotificationKind.SOS_ALERT, NotificationKind.FALSE_SOS_WARNING -> viewModel.openSosOnMap(item.relatedSosId)
                    NotificationKind.BLOOD_REQUEST, NotificationKind.RESPONDER_UPDATE -> viewModel.selectTab(RokkhonTab.REQUESTS)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFFA3132B), Color(0xFF7A0C1E), Color(0xFF450712))))
    ) {
        // Soft glow and rings: depth without noise.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF4D63).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.30f),
                    radius = size.width * 0.62f
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.92f, size.height * 0.30f)
            )
            listOf(0.30f, 0.46f, 0.62f).forEach { r ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.width * r,
                    center = Offset(size.width * 0.98f, size.height * 0.32f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFFF3B52), Color(0xFFD0102B)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bn) "রক্ষণ" else "rokkhon",
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    HeroPill(onClick = onToggleLanguage, tag = "language_toggle_button") {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        // Shows the language you will switch TO.
                        Text(if (bn) "EN" else "বাংলা", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    HeroCircle(onClick = onOpenNotifications, tag = "notification_bell_button") {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = if (bn) "নোটিফিকেশন" else "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(17.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF2B45))
                                    .border(1.5.dp, Color(0xFF8E1024), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (unreadCount > 9) "9+" else "$unreadCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    HeroCircle(onClick = onOpenProfile, tag = "open_profile_avatar") {
                        Text(userInitial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = if (bn) "রক্ত দিন  •  জীবন বাঁচান  •  পাশে থাকুন" else "DONATE  •  SAVE  •  TOGETHER",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (bn) 0.5.sp else 2.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (bn) "প্রতিটি ফোঁটা\nএকটি জীবন বাঁচাতে পারে।" else "Every drop\ncan save a life.",
                    fontSize = if (bn) 30.sp else 34.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (bn) 40.sp else 38.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (bn) {
                        "এলাকার রক্তদাতা খুঁজুন, আর বিপদে এক ট্যাপে ১ কি.মি.-এর সবাইকে SOS পাঠান।"
                    } else {
                        "Find donors in your area, and alert everyone within 1 km with one tap when in danger."
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .shadow(10.dp, CircleShape, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(role = Role.Button) { onRequestBlood() }
                            .padding(start = 16.dp, end = 7.dp)
                            .testTag("request_blood_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Rk.Crimson, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (bn) "রক্তের আবেদন" else "Request blood",
                            fontSize = 15.5.sp,
                            color = Rk.Ink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFE5283F), Rk.CrimsonDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .height(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.22f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable(role = Role.Button) { onSos() }
                            .padding(horizontal = 20.dp)
                            .testTag("home_sos_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFB4BE), modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("SOS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }

                if (hasActiveSos) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (bn) "আপনার SOS চালু • লাইভ লোকেশন শেয়ার হচ্ছে" else "Your SOS is live • sharing location",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (bn) "আমি নিরাপদ" else "I'm safe",
                            color = Rk.Green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(role = Role.Button) { onMarkSafe() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("home_mark_safe_button")
                        )
                    }
                }
            }
            // Rounded lip so the content starts like a sheet pulled over the hero.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Rk.Bg)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Rk.Line)
                )
            }
        }
    }
}

@Composable
private fun HeroPill(onClick: () -> Unit, tag: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun HeroCircle(onClick: () -> Unit, tag: String, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** White card with a hairline border and a soft shadow, the base of every Home block. */
@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    borderColor: Color = Rk.Line,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F7A0C1E))
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        content = content
    )
}

/** Where the old streak card sat: your own donor status with a one-tap availability switch. */
@Composable
private fun DonorStatusCard(
    language: AppLanguage,
    me: DonorNode?,
    onToggleAvailable: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    val hasProfile = me != null && me.contactPhone.isNotBlank()
    HomeCard(
        modifier = modifier.testTag(if (hasProfile) "donor_status_card" else "profile_setup_card"),
        color = Color(0xFFFFF5F6),
        borderColor = Rk.RoseLine,
        onClick = if (hasProfile) null else onOpenProfile
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasProfile) {
                BloodBadge(me!!.bloodGroup, size = 50, filled = true)
            } else {
                Box(
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(Rk.Rose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Rk.Crimson, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (hasProfile) {
                    Text(if (bn) "আপনি রক্তদাতা • ${me!!.bloodGroup}" else "You're a donor • ${me!!.bloodGroup}", color = Rk.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    val (status, color) = donationStatusText(me, language)
                    StatusChip(text = status, color = color)
                } else {
                    Text(if (bn) "রক্তদাতা হিসেবে যোগ দিন" else "Become a donor", color = Rk.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = if (bn) "গ্রুপ ও নম্বর দিন, আপনার এলাকায় দরকার হলে রোগীরা আপনাকে পাবেন।" else "Add your group and number so patients nearby can reach you.",
                        color = Rk.Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            if (hasProfile) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = me!!.isAvailableDonor,
                        onCheckedChange = onToggleAvailable,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Rk.Green),
                        modifier = Modifier.testTag("donor_active_switch")
                    )
                    Text(
                        text = if (me.isAvailableDonor) (if (bn) "উপলব্ধ" else "Available") else (if (bn) "বিরতিতে" else "Paused"),
                        color = if (me.isAvailableDonor) Rk.Green else Rk.Muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Rk.Crimson),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
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
    HomeCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rk.Crimson, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (bn) "এলাকা বাছুন" else "Select area", fontSize = 16.sp, color = Rk.Ink, fontWeight = FontWeight.Bold)
                Text(
                    text = if (bn) "কাছের ডোনার খুঁজতে আপনার এলাকা দিন" else "Choose your area to find nearby donors",
                    fontSize = 12.5.sp,
                    color = Rk.Muted
                )
            }
            Text(
                text = if (isAreaFilterEnabled) (if (bn) "শুধু এই এলাকা" else "This area only") else (if (bn) "সকল এলাকা" else "All areas"),
                color = if (isAreaFilterEnabled) Rk.Crimson else Rk.InkSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isAreaFilterEnabled) Rk.Rose else Rk.SurfaceMuted)
                    .clickable(role = Role.Switch) { onToggleAreaFilter() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("area_filter_mode_toggle")
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(Rk.Bg)
                .border(1.dp, Rk.Line, CircleShape)
                .clickable(role = Role.Button) { onOpenPicker() }
                .padding(horizontal = 16.dp)
                .testTag("area_dropdown_selector"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rk.InkSoft, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = areaName,
                fontSize = 15.5.sp,
                color = Rk.Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Rk.Ink)
        }
    }
}

@Composable
private fun DonorSummaryCard(
    language: AppLanguage,
    donors: List<DonorNode>,
    areaName: String,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    HomeCard(
        modifier = modifier.testTag("explore_compatible_donors_card"),
        color = Color(0xFFFFF5F6),
        borderColor = Rk.RoseLine,
        onClick = onOpenMap
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Rk.Rose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Rk.Crimson, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bn) "এই এলাকায় ${donors.size} জন উপলব্ধ ডোনার" else "${donors.size} available donors in this area",
                    fontSize = 15.5.sp,
                    color = Rk.CrimsonDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = areaName + (if (bn) " • আসল ম্যাপ" else " • Real street map"),
                    fontSize = 12.5.sp,
                    color = Rk.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (bn) "ম্যাপে দেখুন →" else "Open map →",
                    fontSize = 13.5.sp,
                    color = Rk.Crimson,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (donors.isNotEmpty()) {
                DonorAvatarStack(initials = donors.take(3).map { initialsOf(it.displayName) }, extraCount = (donors.size - 3).coerceAtLeast(0))
            }
        }
    }
}

/** Overlapping initials of the first few donors, like a "people nearby" stack. */
@Composable
private fun DonorAvatarStack(initials: List<String>, extraCount: Int) {
    val colors = listOf(Color(0xFFC8102E), Color(0xFF1D4ED8), Color(0xFF0F766E))
    Box {
        initials.forEachIndexed { i, text ->
            Box(
                modifier = Modifier
                    .offset(x = (i * 22).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors[i % colors.size])
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (initials.size * 22).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Rk.Line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$extraCount", color = Rk.Ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Reserve the stack's full width.
        Spacer(modifier = Modifier.width(((initials.size + if (extraCount > 0) 1 else 0) * 22 + 12).dp).height(34.dp))
    }
}

@Composable
private fun FeedTabCard(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(Color(0xFF3B0A12), Color(0xFF5E0D1C))) else Brush.linearGradient(listOf(Color.White, Color.White)))
            .border(1.dp, if (selected) Color.Transparent else Rk.Line, shape)
            .clickable(role = Role.Tab) { onClick() }
            .padding(12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.14f) else Rk.Rose),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else Rk.Crimson, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.5.sp, color = if (selected) Color.White.copy(alpha = 0.8f) else Rk.InkSoft, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("$count", fontSize = 20.sp, color = if (selected) Color.White else Rk.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SeeAllRow(text: String, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onClick() }
            .padding(vertical = 12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Rk.Crimson, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Rk.Crimson, modifier = Modifier.size(16.dp))
    }
}
