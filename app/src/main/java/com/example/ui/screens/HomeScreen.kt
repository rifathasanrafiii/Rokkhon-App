package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonEmptyState
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.Segment
import com.example.ui.components.SegmentedControl
import com.example.ui.components.SheetGridItem
import com.example.ui.components.StatTile
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState
import kotlin.math.cos
import kotlin.math.sin

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
    val hasGpsFix by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()
    val guardNetworkOn by viewModel.guardNetworkEnabled.collectAsStateWithLifecycle()
    val trustedContacts by viewModel.trustedContacts.collectAsStateWithLifecycle()

    var homeFeedTab by remember { mutableStateOf(MapLayerFilter.BLOOD_REQUESTS_ONLY) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showReadinessSheet by remember { mutableStateOf(false) }
    val checkInReward by viewModel.rewardCheckInPoints.collectAsStateWithLifecycle()

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

    val nearbySosAlerts = allSosAlerts.filter {
        it.status == "ACTIVE" && !it.isExpiredAfter24Hours() &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(it.district))
    }
    val activeSosNearby = nearbySosAlerts.size
    val bn = language == AppLanguage.BENGALI
    val readinessItems = listOf(
        ReadinessItem(
            icon = Icons.Default.MyLocation,
            titleBn = "লাইভ লোকেশন", titleEn = "Live location",
            okBn = "জিপিএস ফিক্স পাওয়া গেছে", okEn = "GPS fix acquired",
            todoBn = if (isLocationDisabledByAdmin) "অ্যাডমিন বন্ধ রেখেছেন" else "জিপিএস চালু করুন",
            todoEn = if (isLocationDisabledByAdmin) "Turned off by admin" else "Turn on GPS",
            ok = hasGpsFix && !isLocationDisabledByAdmin,
            onClick = { viewModel.selectTab(RokkhonTab.RADAR_MAP) }
        ),
        ReadinessItem(
            icon = Icons.Default.Shield,
            titleBn = "১ কি.মি. সুরক্ষা নেটওয়ার্ক", titleEn = "1 km safety network",
            okBn = "ব্লুটুথ রিলে চালু", okEn = "Bluetooth relay on",
            todoBn = "চালু করুন — অ্যাপ বন্ধেও অ্যালার্ট পাবেন", todoEn = "Turn on to get alerts when the app is closed",
            ok = guardNetworkOn,
            onClick = { viewModel.selectTab(RokkhonTab.SOS_GUARDIAN) }
        ),
        ReadinessItem(
            icon = Icons.Default.People,
            titleBn = "বিশ্বস্ত কন্টাক্ট", titleEn = "Trusted contacts",
            okBn = "${trustedContacts.size}/৩ জন যোগ করা আছে", okEn = "${trustedContacts.size}/3 added",
            todoBn = "পরিবারের ১-৩ জনকে যোগ করুন", todoEn = "Add 1-3 family members",
            ok = trustedContacts.isNotEmpty(),
            onClick = { viewModel.selectTab(RokkhonTab.SOS_GUARDIAN) }
        ),
        ReadinessItem(
            icon = Icons.Default.WaterDrop,
            titleBn = "ডোনার প্রোফাইল", titleEn = "Donor profile",
            okBn = "রক্তদাতা হিসেবে নিবন্ধিত", okEn = "Registered as a donor",
            todoBn = "রক্তের গ্রুপ ও ফোন নম্বর দিন", todoEn = "Add blood group and phone",
            ok = allDonors.any { it.userId == viewModel.currentUserId && it.contactPhone.isNotBlank() },
            onClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) }
        )
    )

    // The hero scrolls away with the content, Revolut-style: one big number, round actions, then tiles.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("home_screen")
    ) {
        item(key = "hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(RokkhonPalette.HeroTop, RokkhonPalette.HeroMid, RokkhonPalette.HeroBottom)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeTopBar(
                        language = language,
                        userInitial = viewModel.currentUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "R",
                        streakDays = dailyStreakCount,
                        points = guardianPoints,
                        hasUnread = unreadNotificationCount > 0,
                        onStreakClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                        onLanguageClick = { viewModel.toggleLanguage() },
                        onBellClick = { showNotificationCenterDialog = true },
                        onAvatarClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live radar of the area: green dots are matching donors, amber dots active SOS.
                    DonorRadar(
                        donorKeys = matchingDonors.map { it.id.ifBlank { it.userId } },
                        sosKeys = nearbySosAlerts.map { it.id },
                        onClick = {
                            viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                            viewModel.selectTab(RokkhonTab.RADAR_MAP)
                        },
                        modifier = Modifier.testTag("explore_compatible_donors_card")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .springClick { showAreaPickerDialog = true }
                            .testTag("area_dropdown_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAreaFilterEnabled) (if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn) else (if (bn) "সারা বাংলাদেশ" else "All Bangladesh"),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (bn) "${matchingDonors.size} জন ডোনার" else "${matchingDonors.size} donors",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (bn) "${activeSosNearby}টি SOS" else "$activeSosNearby SOS",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // The two things people open this app for, as in the original design
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .springClick(onClick = onOpenBloodRequestDialog)
                                .padding(start = 16.dp, end = 6.dp)
                                .testTag("request_blood_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFFC8102E), modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (bn) "রক্ত চাই" else "Request blood",
                                fontSize = 15.sp,
                                color = RokkhonPalette.Ink,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(RokkhonPalette.Crimson),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(
                            modifier = Modifier
                                .height(52.dp)
                                .shadow(10.dp, CircleShape, ambientColor = RokkhonPalette.CrimsonBright, spotColor = RokkhonPalette.CrimsonBright)
                                .clip(CircleShape)
                                .background(RokkhonPalette.CrimsonBright)
                                .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .springClick(onClick = onOpenSosVerification)
                                .padding(horizontal = 20.dp)
                                .testTag("power_3x_test_pill"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SOS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .springClick { showMoreSheet = true }
                                .testTag("home_more_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = if (bn) "আরও" else "More", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }

                    // Own live SOS: one tap to stop once safe
                    AnimatedVisibility(visible = isLiveLocationActive || activeMySos != null) {
                        Surface(
                            color = PinCrimsonRed,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (bn) "আপনার লাইভ SOS চালু" else "Your live SOS is on",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .springClick { viewModel.resolveOwnSos(activeMySos?.id ?: activeUserSosId ?: "") }
                                        .testTag("home_mark_safe_button")
                                ) {
                                    Text(
                                        text = if (bn) "নিরাপদ" else "I'm safe",
                                        color = IosSystemGreen,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isLocationDisabledByAdmin) {
                        Row(
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F1218))
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("admin_location_off_banner"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (bn) "লাইভ লোকেশন অ্যাডমিন বন্ধ রেখেছেন" else "Live location turned off by admin",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item(key = "sheet_top") {
            // Rounded sheet edge over the hero colour.
            Box(modifier = Modifier.fillMaxWidth().background(RokkhonPalette.HeroBottom)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(RokkhonPalette.Background)
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                ) {
                    // Blood group: one scrollable row of round chips
                    SectionHeader(
                        title = if (bn) "রক্তের গ্রুপ" else "Blood group",
                        trailing = {
                            Surface(
                                color = if (onlyAvailableFilter) Color(0xFFE8F5EC) else RokkhonPalette.Track,
                                shape = CircleShape,
                                modifier = Modifier
                                    .springClick { viewModel.setOnlyAvailableDonorsFilter(!onlyAvailableFilter) }
                                    .testTag("donor_active_switch")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (onlyAvailableFilter) Icons.Default.CheckCircle else Icons.Default.People,
                                        contentDescription = null,
                                        tint = if (onlyAvailableFilter) IosSystemGreen else RokkhonPalette.Muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (onlyAvailableFilter) (if (bn) "শুধু উপলব্ধ" else "Available") else (if (bn) "সব ডোনার" else "All donors"),
                                        fontSize = 12.sp,
                                        color = if (onlyAvailableFilter) IosSystemGreen else RokkhonPalette.Muted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BloodGroupChipRow(
                        language = language,
                        selected = selectedBloodGroup,
                        onSelect = { viewModel.setBloodFilter(it) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2x2 icon grid: everything that used to be a long card, one tap away
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            icon = Icons.Default.People,
                            value = matchingDonors.size,
                            label = if (bn) "উপলব্ধ ডোনার" else "Available donors",
                            accent = RokkhonPalette.Crimson,
                            selected = homeFeedTab == MapLayerFilter.DONORS_ONLY,
                            onClick = { homeFeedTab = MapLayerFilter.DONORS_ONLY },
                            modifier = Modifier.weight(1f).enterOnce(0),
                            testTag = "filter_tab_available_donors"
                        )
                        StatTile(
                            icon = Icons.Default.LocalHospital,
                            value = openRequests.size,
                            label = if (bn) "রোগীর আবেদন" else "Patient requests",
                            accent = RokkhonPalette.Crimson,
                            selected = homeFeedTab == MapLayerFilter.BLOOD_REQUESTS_ONLY,
                            onClick = { homeFeedTab = MapLayerFilter.BLOOD_REQUESTS_ONLY },
                            modifier = Modifier.weight(1f).enterOnce(1),
                            testTag = "filter_tab_patient_requests"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            icon = Icons.Default.Shield,
                            value = activeSosNearby,
                            label = if (bn) "সক্রিয় SOS" else "Active SOS",
                            accent = RokkhonPalette.Amber,
                            onClick = {
                                viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                                viewModel.selectTab(RokkhonTab.RADAR_MAP)
                            },
                            modifier = Modifier.weight(1f).enterOnce(2),
                            testTag = "home_sos_tile"
                        )
                        StatTile(
                            icon = Icons.Default.LocalFireDepartment,
                            value = dailyStreakCount,
                            valueSuffix = if (bn) " দিন" else "d",
                            label = if (bn) "সেফটি স্ট্রিক" else "Safety streak",
                            accent = Color(0xFFEA580C),
                            onClick = { if (!checkedInToday) viewModel.performDailySafeCheckIn() else viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                            modifier = Modifier.weight(1f).enterOnce(3),
                            testTag = "home_daily_streak_checkin_button",
                            trailing = {
                                if (checkedInToday) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosSystemGreen, modifier = Modifier.size(22.dp))
                                } else {
                                    Surface(color = Color(0xFFEA580C), shape = CircleShape) {
                                        Text(
                                            text = "+$checkInReward",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ReadinessStrip(
                        language = language,
                        items = readinessItems,
                        onClick = { showReadinessSheet = true },
                        modifier = Modifier.enterOnce(4)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    SectionHeader(
                        title = if (bn) "কাছের আবেদন" else "Nearby",
                        trailing = {
                            RoundIconButton(
                                icon = Icons.Default.Add,
                                onClick = onOpenBloodRequestDialog,
                                filled = true,
                                contentDescription = if (bn) "নতুন আবেদন" else "New request",
                                testTag = "home_new_request_button"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SegmentedControl(
                        segments = listOf(
                            Segment(if (bn) "রোগী" else "Patients", Icons.Default.LocalHospital, "home_feed_tab_requests"),
                            Segment(if (bn) "ডোনার" else "Donors", Icons.Default.People, "home_feed_tab_donors")
                        ),
                        selectedIndex = if (homeFeedTab == MapLayerFilter.DONORS_ONLY) 1 else 0,
                        onSelect = { homeFeedTab = if (it == 1) MapLayerFilter.DONORS_ONLY else MapLayerFilter.BLOOD_REQUESTS_ONLY },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        if (homeFeedTab == MapLayerFilter.DONORS_ONLY && matchingDonors.isEmpty()) {
            item(key = "empty_donors") {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    RokkhonEmptyState(
                        icon = Icons.Default.People,
                        title = if (bn) "এখনো কোনো ডোনার নেই" else "No donors here yet",
                        body = if (bn) "প্রোফাইলে রক্তের গ্রুপ ও ফোন দিলে আপনিই প্রথম ডোনার।" else "Add your blood group and phone to be the first donor.",
                        actionLabel = if (bn) "ডোনার হোন" else "Become a donor",
                        onAction = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                        testTag = "home_empty_donors"
                    )
                }
            }
        } else if (homeFeedTab != MapLayerFilter.DONORS_ONLY && openRequests.isEmpty()) {
            item(key = "empty_requests") {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    RokkhonEmptyState(
                        icon = Icons.Default.LocalHospital,
                        title = if (bn) "কোনো খোলা আবেদন নেই" else "No open requests",
                        body = if (bn) "রক্ত লাগলে আবেদন করুন — মিলে যাওয়া ডোনাররা সাথে সাথে জানবেন।" else "Post a request and matching donors are notified right away.",
                        actionLabel = if (bn) "রক্তের আবেদন" else "Request blood",
                        onAction = { onOpenBloodRequestDialog() },
                        testTag = "home_empty_requests"
                    )
                }
            }
        } else if (homeFeedTab == MapLayerFilter.DONORS_ONLY) {
            items(matchingDonors, key = { "home_donor_${it.id.ifBlank { it.userId }}" }) { donor ->
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).animateItem()) {
                    LifelineResponderCard(
                        donor = donor,
                        language = language,
                        onCallClick = { RokkhonIntents.dial(context, donor.contactPhone, language) }
                    )
                }
            }
        } else {
            items(openRequests, key = { it.id.ifBlank { it.patientName } }) { req ->
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).animateItem()) {
                    val hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req)
                    ClinicalBloodRequestCard(
                        req = req,
                        language = language,
                        isOwner = req.userId == viewModel.currentUserId,
                        hasPledged = hasPledged,
                        onDonateClick = { viewModel.pledgeToDonate(req) },
                        onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                        onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                    )
                }
            }
        }

        if (archivedFulfilledCount > 0) {
            item(key = "archived_footer") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = RokkhonPalette.Muted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (bn) "$archivedFulfilledCount টি আবেদন পূরণ হয়েছে" else "$archivedFulfilledCount requests fulfilled",
                        fontSize = 12.sp,
                        color = RokkhonPalette.Muted
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }

    if (showMoreSheet) {
        RokkhonBottomSheet(
            onDismiss = { showMoreSheet = false },
            title = if (bn) "আরও" else "More",
            testTag = "home_more_sheet"
        ) {
            val close = { showMoreSheet = false }
            Row(modifier = Modifier.fillMaxWidth()) {
                SheetGridItem(
                    icon = Icons.Default.LocationOn,
                    label = if (bn) "এলাকা" else "Area",
                    state = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                    onClick = { close(); showAreaPickerDialog = true },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = Icons.Default.FilterAlt,
                    label = if (bn) "শুধু এই এলাকা" else "This area only",
                    state = if (isAreaFilterEnabled) (if (bn) "চালু" else "On") else (if (bn) "বন্ধ" else "Off"),
                    active = isAreaFilterEnabled,
                    onClick = { viewModel.toggleAreaFilter(!isAreaFilterEnabled) },
                    modifier = Modifier.weight(1f),
                    testTag = "area_filter_mode_toggle"
                )
                SheetGridItem(
                    icon = Icons.Default.Translate,
                    label = if (bn) "ভাষা" else "Language",
                    state = if (bn) "বাংলা" else "English",
                    onClick = { viewModel.toggleLanguage() },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SheetGridItem(
                    icon = Icons.Default.NotificationsNone,
                    label = if (bn) "নোটিফিকেশন" else "Notifications",
                    state = if (unreadNotificationCount > 0) "$unreadNotificationCount" else null,
                    onClick = { close(); showNotificationCenterDialog = true },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = Icons.Default.Star,
                    label = if (bn) "রিওয়ার্ড" else "Rewards",
                    state = "$guardianPoints",
                    onClick = { close(); viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    label = if (bn) "কিভাবে চলে" else "How it works",
                    onClick = { close(); onOpenTutorial() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showReadinessSheet) {
        RokkhonBottomSheet(
            onDismiss = { showReadinessSheet = false },
            testTag = "safety_readiness_sheet"
        ) {
            SafetyReadinessCard(
                language = language,
                items = readinessItems.map { item ->
                    ReadinessItem(item.icon, item.titleBn, item.titleEn, item.okBn, item.okEn, item.todoBn, item.todoEn, item.ok) {
                        showReadinessSheet = false
                        item.onClick()
                    }
                }
            )
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


/** One line of the readiness card: a real status from the phone, and where to fix it when it is not OK. */
internal class ReadinessItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val titleBn: String,
    val titleEn: String,
    val okBn: String,
    val okEn: String,
    val todoBn: String,
    val todoEn: String,
    val ok: Boolean,
    val onClick: () -> Unit
)

/**
 * "How ready is this phone to keep you safe": location, Bluetooth safety network, trusted contacts
 * and donor profile. Every row reflects live state, nothing is placeholder.
 */
@Composable
internal fun SafetyReadinessCard(language: AppLanguage, items: List<ReadinessItem>) {
    val bn = language == AppLanguage.BENGALI
    val readyCount = items.count { it.ok }
    val allReady = readyCount == items.size
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
            .testTag("safety_readiness_card")
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (bn) "আপনার সুরক্ষা প্রস্তুতি" else "Your safety readiness",
                        fontSize = 15.5.sp,
                        color = Color(0xFF1A1113),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (allReady) {
                            if (bn) "সব প্রস্তুত — আপনি সুরক্ষিত" else "All set — you are covered"
                        } else {
                            if (bn) "বিপদে পুরো সুরক্ষা পেতে বাকিগুলো চালু করুন" else "Finish the rest to be fully covered"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF7B6F72)
                    )
                }
                Surface(
                    color = if (allReady) Color(0xFFE8F5EC) else Color(0xFFFDF0F1),
                    shape = CircleShape
                ) {
                    Text(
                        text = "$readyCount/${items.size}",
                        fontSize = 12.5.sp,
                        color = if (allReady) IosSystemGreen else Color(0xFFB80F24),
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2E6E8))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(readyCount.toFloat() / items.size)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (allReady) IosSystemGreen else Color(0xFFB80F24))
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { item.onClick() }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (item.ok) Color(0xFFE8F5EC) else Color(0xFFF5DDE0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.ok) IosSystemGreen else Color(0xFFB80F24),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bn) item.titleBn else item.titleEn,
                            fontSize = 13.5.sp,
                            color = Color(0xFF1A1113),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (item.ok) (if (bn) item.okBn else item.okEn) else (if (bn) item.todoBn else item.todoEn),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = if (item.ok) Color(0xFF7B6F72) else Color(0xFF9A4A55)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (item.ok) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = IosSystemGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFB80F24),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp)
                            .height(1.dp)
                            .background(Color(0xFFF4ECEE))
                    )
                }
            }
        }
    }
}


/** Wordmark, streak/points, language, notifications and profile: the hero's top row. */
@Composable
private fun HomeTopBar(
    language: AppLanguage,
    userInitial: String,
    streakDays: Int,
    points: Int,
    hasUnread: Boolean,
    onStreakClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onBellClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.rokkhon_wordmark_white),
            contentDescription = "রক্ষণ",
            modifier = Modifier.height(28.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .springClick(onClick = onStreakClick)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("header_streak_points_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (bn) "${streakDays}দি" else "${streakDays}d", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC94D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("$points", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            HeroIconButton(
                onClick = onLanguageClick,
                testTag = "language_toggle_button",
                contentDescription = "Switch Language"
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            HeroIconButton(onClick = onBellClick, testTag = "notification_bell_button", contentDescription = "Notifications") {
                Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                if (hasUnread) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 7.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2B45))
                    )
                }
            }
            HeroIconButton(onClick = onAvatarClick, testTag = "open_profile_avatar", contentDescription = "Profile") {
                Text(userInitial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroIconButton(
    onClick: () -> Unit,
    testTag: String,
    contentDescription: String,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .springClick(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** "All" + the eight groups as one scrollable row of round chips; the selected one fills crimson. */
@Composable
internal fun BloodGroupChipRow(
    language: AppLanguage,
    selected: String?,
    onSelect: (String?) -> Unit,
    includeAll: Boolean = true
) {
    val groups = (if (includeAll) listOf(null) else emptyList()) + listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val isSelected = group == selected
            val bg by animateColorAsState(if (isSelected) RokkhonPalette.Crimson else Color.White, label = "chip_bg")
            val fg by animateColorAsState(if (isSelected) Color.White else RokkhonPalette.Ink, label = "chip_fg")
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (isSelected) Color.Transparent else RokkhonPalette.Border, CircleShape)
                    .springClick(pressedScale = 0.9f) { onSelect(group) }
                    .testTag("blood_group_chip_${group ?: "all"}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group ?: if (language == AppLanguage.BENGALI) "সব" else "All",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** One-line readiness summary: a progress ring and four status icons; details open in a sheet. */
@Composable
internal fun ReadinessStrip(
    language: AppLanguage,
    items: List<ReadinessItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    val ready = items.count { it.ok }
    val progress by animateFloatAsState(ready.toFloat() / items.size, label = "readiness_progress")
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, RokkhonPalette.Border, RoundedCornerShape(22.dp))
            .springClick(pressedScale = 0.97f, onClick = onClick)
            .testTag("safety_readiness_card")
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (ready == items.size) IosSystemGreen else RokkhonPalette.Crimson,
                    trackColor = RokkhonPalette.Track,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
                Text("$ready/${items.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = RokkhonPalette.Ink)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (bn) "সুরক্ষা প্রস্তুতি" else "Safety readiness", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = RokkhonPalette.Ink)
                Text(
                    text = if (ready == items.size) (if (bn) "সব প্রস্তুত" else "All set") else (if (bn) "${items.size - ready}টি বাকি" else "${items.size - ready} to do"),
                    fontSize = 12.sp,
                    color = RokkhonPalette.Muted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (item.ok) Color(0xFFE8F5EC) else RokkhonPalette.Blush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.ok) IosSystemGreen else RokkhonPalette.Crimson,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * The hero of Home: a slowly sweeping radar of the selected area. Every matching donor is a green
 * dot and every active SOS an amber one, placed by a stable hash of its id so dots don't jump.
 * The sweep is read in the draw phase only, so it never recomposes the screen.
 */
@Composable
private fun DonorRadar(
    donorKeys: List<String>,
    sosKeys: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4200, easing = LinearEasing)),
        label = "radar_sweep"
    )
    val ping = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "radar_ping"
    )
    val donorDots = remember(donorKeys) { donorKeys.take(18).map { radarPosition(it) } }
    val sosDots = remember(sosKeys) { sosKeys.take(8).map { radarPosition("sos$it") } }
    Box(
        modifier = modifier
            .size(212.dp)
            .springClick(pressedScale = 0.97f, onClick = onClick)
            .drawBehind {
                val r = size.minDimension / 2f
                // soft glow + rings
                drawCircle(Brush.radialGradient(listOf(Color(0x33FF4D63), Color.Transparent), center, r), r)
                listOf(1f, 0.68f, 0.36f).forEach { f ->
                    drawCircle(Color.White.copy(alpha = 0.13f), r * f, center, style = Stroke(width = 1.dp.toPx()))
                }
                drawLine(Color.White.copy(alpha = 0.07f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.07f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())
                // sweep
                rotate(sweep.value) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to Color(0x66FF8A9A),
                            center = center
                        ),
                        r
                    )
                }
                donorDots.forEach { (angle, dist) ->
                    val p = Offset(
                        center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * r * dist,
                        center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * r * dist
                    )
                    drawCircle(Color(0x334ADE80), 9.dp.toPx(), p)
                    drawCircle(Color(0xFF4ADE80), 4.5.dp.toPx(), p)
                }
                sosDots.forEach { (angle, dist) ->
                    val p = Offset(
                        center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * r * dist,
                        center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * r * dist
                    )
                    drawCircle(Color(0xFFFBBF24).copy(alpha = (1f - ping.value) * 0.5f), (5f + 12f * ping.value).dp.toPx(), p)
                    drawCircle(Color(0xFFFBBF24), 5.dp.toPx(), p)
                }
                // pulse around the centre
                drawCircle(Color.White.copy(alpha = (1f - ping.value) * 0.25f), 34.dp.toPx() + ping.value * 18.dp.toPx(), center)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .shadow(14.dp, CircleShape, ambientColor = RokkhonPalette.CrimsonBright, spotColor = RokkhonPalette.CrimsonBright)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFF3B52), Color(0xFFC8102E))))
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/** Stable spot on the radar for an id: angle in degrees and distance (0.3..0.92 of the radius). */
private fun radarPosition(key: String): Pair<Float, Float> {
    val h = key.hashCode()
    val angle = ((h and 0x7fffffff) % 360).toFloat()
    val dist = 0.3f + (((h ushr 9) and 0x3ff) / 1023f) * 0.62f
    return angle to dist
}
