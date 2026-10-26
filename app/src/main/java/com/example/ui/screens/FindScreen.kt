package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.SosAlert
import com.example.data.model.canDonateTo
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.InteractiveBangladeshRadarMap
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.MapPinDetailOverlayCard
import com.example.ui.components.RokkhonEmptyState
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SelectedMapPin
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.SosVideoAudioInspectorDialog
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarObsidianBg
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Screen 2 (Find — Real Interactive Bangladesh Map + 3 Dedicated Tabs:
 * 1. Available Donors
 * 2. Patient Blood Requests
 * 3. Active SOS Alerts (with "Nearby / যাচ্ছি" & "False / ভুয়া (X/3)" buttons, auto-removing at 3 false reports)
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
    val userBearing by viewModel.userBearing.collectAsStateWithLifecycle()
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
    val cloudSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    // SOS heard only over the Bluetooth mesh (sender or this phone offline) are listed too.
    val allSosAlerts = cloudSosAlerts + bluetoothOnlySosAlerts(bleBeacons, cloudSosAlerts)

    val areaDonors = allDonors.filter { donor ->
        donor.userId != viewModel.currentUserId &&
            donor.isEligibleNow &&
            (selectedBloodGroup == null || canDonateTo(donor.bloodGroup, selectedBloodGroup!!)) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(donor.district))
    }.sortedByDescending { it.bloodGroup == selectedBloodGroup }

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
                selectedAreaName = if (language == AppLanguage.BENGALI) selectedDistrict.nameBn else selectedDistrict.nameEn,
                userLat = userLat,
                userLng = userLng,
                userBearing = userBearing,
                onRefreshLiveGps = { viewModel.refreshGpsLocation() },
                bengali = language == AppLanguage.BENGALI,
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
                                            if (language == AppLanguage.BENGALI) "ম্যানুয়াল" else "Manual"
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
                                RokkhonEmptyState(
                                    icon = Icons.Default.LocalHospital,
                                    title = if (language == AppLanguage.BENGALI) "কোনো খোলা রক্তের আবেদন নেই" else "No open blood requests",
                                    body = if (language == AppLanguage.BENGALI) "এই এলাকায় এখন কেউ রক্ত খুঁজছেন না। কারও রক্ত লাগলে আবেদন করুন, মিলে যাওয়া ডোনারদের জানানো হবে।" else "Nobody is looking for blood here right now. Post a request and matching donors are notified.",
                                    actionLabel = if (language == AppLanguage.BENGALI) "রক্তের আবেদন করুন" else "Request blood",
                                    onAction = { onOpenBloodRequestDialog() },
                                    testTag = "find_empty_requests"
                                )
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
                                        RokkhonIntents.dial(context, req.contactPhone, language)
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
                                RokkhonEmptyState(
                                    icon = Icons.Default.Shield,
                                    title = if (language == AppLanguage.BENGALI) "এখন কোনো সক্রিয় SOS নেই" else "No active SOS right now",
                                    body = if (language == AppLanguage.BENGALI) "এই এলাকাটি এই মুহূর্তে নিরাপদ। বিপদে পড়লে SOS পাঠান, ১ কি.মি.-এর মধ্যে সবাই ব্লুটুথ ও ইন্টারনেটে জানতে পারবে।" else "This area is quiet. If you are in danger, send an SOS and everyone within 1 km is alerted over Bluetooth and internet.",
                                    actionLabel = if (language == AppLanguage.BENGALI) "SOS পাঠান" else "Send SOS",
                                    onAction = { viewModel.selectTab(RokkhonTab.SOS_GUARDIAN) },
                                    testTag = "find_empty_sos"
                                )
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
                                            alertId = alert.id
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
                        if (areaDonors.isEmpty()) {
                            item {
                                RokkhonEmptyState(
                                    icon = Icons.Default.People,
                                    title = if (language == AppLanguage.BENGALI) "এই এলাকায় এখনো কোনো ডোনার নেই" else "No donors in this area yet",
                                    body = if (language == AppLanguage.BENGALI) {
                                        "অন্য এলাকা বেছে দেখুন, অথবা নিজে ডোনার হয়ে প্রোফাইলে রক্তের গ্রুপ ও ফোন নম্বর দিন।"
                                    } else {
                                        "Try another area, or become a donor by adding your blood group and phone in your profile."
                                    },
                                    actionLabel = if (language == AppLanguage.BENGALI) "ডোনার হোন" else "Become a donor",
                                    onAction = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                                    testTag = "find_empty_donors"
                                )
                            }
                        }
                        items(areaDonors, key = { it.id.ifBlank { it.userId } }) { donor ->
                            LifelineResponderCard(
                                donor = donor,
                                language = language,
                                onCallClick = {
                                    RokkhonIntents.dial(context, donor.contactPhone, language)
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
                    alertId = alert.id
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
