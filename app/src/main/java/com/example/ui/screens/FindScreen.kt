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
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.SosAlert
import com.example.data.model.emergencyTypeLabel
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.InkText
import com.example.ui.components.InteractiveBangladeshRadarMap
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.MutedText
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SelectedMapPin
import com.example.ui.components.SosDetailsDialog
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.donationStatusText
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarGlassCard
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Find: Bangladesh map on top, and a sheet with three tabs (Donors | Patients | SOS).
 */
@Composable
fun RadarMapHubScreen(
    viewModel: RokkhonViewModel,
    onOpenSosVerification: () -> Unit,
    onOpenBloodRequestDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaFilterEnabled by viewModel.isAreaFilterEnabled.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val hasGps by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val layerFilter by viewModel.mapLayerFilter.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val respondedSosIds by viewModel.respondedSosIds.collectAsStateWithLifecycle()
    val reportedFalseSosIds by viewModel.reportedFalseSosIds.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val activePlaybackAlertId by viewModel.audioVerifier.activePlaybackAlertId.collectAsStateWithLifecycle()
    val playbackSeconds by viewModel.audioVerifier.playbackProgressSeconds.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()
    val sosToInspect by viewModel.sosToInspect.collectAsStateWithLifecycle()

    val areaMatches: (String) -> Boolean = { !isAreaFilterEnabled || selectedDistrict.matchesArea(it) }
    val allDonors = (donorsState as? UiState.Success)?.data.orEmpty()
    val allRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val allSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()

    val areaDonors = filterMatchingDonors(allDonors, viewModel.currentUserId, selectedBloodGroup, onlyAvailable = true, areaMatches = areaMatches)
    val areaOpenRequests = allRequests.filter { req ->
        req.status == "OPEN" &&
            (selectedBloodGroup == null || req.bloodGroup == selectedBloodGroup) &&
            areaMatches(req.district)
    }
    // SOS stay listed for 24h (or until marked safe), sorted by distance from the user.
    val allActiveSos = allSosAlerts.filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
    val areaSos = allActiveSos.filter { areaMatches(it.district) }
    val showingAllBangladeshSos = areaSos.isEmpty() && allActiveSos.isNotEmpty()
    val activeSosAlerts = (if (showingAllBangladeshSos) allActiveSos else areaSos)
        .sortedBy { it.distanceFromKm(userLat, userLng) }

    var selectedPin by remember { mutableStateOf<SelectedMapPin?>(null) }
    var detailsSosId by remember { mutableStateOf<String?>(null) }
    var showAreaPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sosToInspect) {
        sosToInspect?.let {
            viewModel.markSosEvidenceInspected(it.id)
            detailsSosId = it.id
            viewModel.consumeSosToInspect()
        }
    }
    LaunchedEffect(layerFilter) { selectedPin = null }

    fun openDetails(alert: SosAlert) {
        viewModel.markSosEvidenceInspected(alert.id)
        detailsSosId = alert.id
    }

    // Centre on the user when we have GPS and the area came from GPS, otherwise on the chosen area.
    val centerLat = if (hasGps && isAreaAutoDetected) userLat else selectedDistrict.latitude
    val centerLng = if (hasGps && isAreaAutoDetected) userLng else selectedDistrict.longitude

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F8))
            .testTag("find_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
        ) {
            InteractiveBangladeshRadarMap(
                centerLat = centerLat,
                centerLng = centerLng,
                donors = areaDonors,
                bloodRequests = areaOpenRequests,
                sosAlerts = activeSosAlerts,
                bleBeacons = bleBeacons,
                selectedBloodGroup = null,
                layerFilter = layerFilter,
                onPinSelected = { selectedPin = it },
                showLiveFloatingCard = selectedPin == null,
                isLiveLocationActive = isLiveLocationActive,
                selectedAreaName = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                language = language,
                userLat = userLat,
                userLng = userLng,
                showUserDot = hasGps,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 60.dp, top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapChip(
                    text = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                    icon = Icons.Default.Search,
                    background = PinCrimsonRed,
                    onClick = { showAreaPickerDialog = true },
                    tag = "map_open_area_search_chip"
                )
                MapChip(
                    text = if (bn) "আমার লোকেশন" else "My location",
                    icon = Icons.Default.MyLocation,
                    background = if (isAreaAutoDetected) IosSystemGreen else Color(0xFF1A0F12).copy(alpha = 0.88f),
                    onClick = { viewModel.autoSelectAreaFromPermission(notifyUser = true) },
                    tag = "map_area_auto_gps_chip"
                )
                MapChip(
                    text = when {
                        selectedBloodGroup == null -> if (bn) "সব গ্রুপ" else "All groups"
                        bn -> "রোগী: $selectedBloodGroup ✕"
                        else -> "Patient: $selectedBloodGroup ✕"
                    },
                    icon = null,
                    background = Color(0xFF1A0F12).copy(alpha = 0.88f),
                    onClick = { if (selectedBloodGroup != null) viewModel.setBloodFilter(selectedBloodGroup) },
                    tag = "blood_filter_all"
                )
            }

            selectedPin?.let { pin ->
                MapPinDetailOverlayCard(
                    pin = pin,
                    language = language,
                    currentUserId = viewModel.currentUserId,
                    hasPledged = (pin as? SelectedMapPin.RequestPin)?.let {
                        it.request.id in pledgedIds || viewModel.hasUserPledged(it.request)
                    } ?: false,
                    hasRespondedSos = (pin as? SelectedMapPin.SosPin)?.let {
                        it.alert.id in respondedSosIds || viewModel.hasUserRespondedSos(it.alert)
                    } ?: false,
                    onDismiss = { selectedPin = null },
                    onCall = { phone -> RokkhonIntents.dial(context, phone, language) },
                    onDirections = { lat, lng -> RokkhonIntents.directions(context, lat, lng, language) },
                    onPledgeBlood = { viewModel.pledgeToDonate(it) },
                    onRespondSos = { viewModel.respondToSos(it) },
                    onOpenSosDetails = { openDetails(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }

        Surface(
            color = Color(0xFFFAF8F8),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAreaPickerDialog = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = InkText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = InkText)
                            }
                            Text(
                                text = if (isAreaFilterEnabled) {
                                    if (bn) "শুধু এই এলাকা • ট্যাপ করে এলাকা বদলান" else "This area only • tap to change"
                                } else {
                                    if (bn) "সারা বাংলাদেশ দেখানো হচ্ছে" else "Showing all of Bangladesh"
                                },
                                fontSize = 12.sp,
                                color = MutedText
                            )
                        }
                        Surface(
                            color = LifelineBlush,
                            shape = CircleShape,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onOpenBloodRequestDialog() }
                                .testTag("new_blood_request_fab")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (bn) "আবেদন" else "Request", color = IosSystemRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SeparateDonorRequestAndSosFilterTabs(
                        language = language,
                        selectedLayer = if (layerFilter == MapLayerFilter.ALL) MapLayerFilter.DONORS_ONLY else layerFilter,
                        donorsCount = areaDonors.size,
                        requestsCount = areaOpenRequests.size,
                        sosCount = activeSosAlerts.size,
                        onSelectLayer = { viewModel.setMapLayerFilter(it) }
                    )
                }

                if (viewModel.isDemoMode) {
                    item(key = "demo") { DemoModeBanner(language) }
                }

                when (layerFilter) {
                    MapLayerFilter.BLOOD_REQUESTS_ONLY -> {
                        if (areaOpenRequests.isEmpty()) {
                            item(key = "empty_req") {
                                EmptyStateCard(if (bn) "এই এলাকায় এখন কোনো খোলা রক্তের আবেদন নেই।" else "No open blood requests in this area.")
                            }
                        }
                        items(areaOpenRequests, key = { "map_req_${it.id}" }) { req ->
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
                    MapLayerFilter.SOS_ALERTS_ONLY -> {
                        if (falseSosWarningBanner != null) {
                            item(key = "false_banner") {
                                FalseSosAccountWarningBanner(
                                    language = language,
                                    warningMessage = falseSosWarningBanner,
                                    penaltyCount = falseSosPenaltyCount,
                                    onDismiss = { viewModel.dismissFalseSosWarningBanner() }
                                )
                            }
                        }
                        if (activeSosAlerts.isEmpty()) {
                            item(key = "empty_sos") {
                                EmptyStateCard(if (bn) "এখন কোনো সক্রিয় SOS নেই।" else "No active SOS alerts right now.")
                            }
                        } else if (showingAllBangladeshSos) {
                            item(key = "sos_all_bd") {
                                Text(
                                    text = if (bn) "এই এলাকায় SOS নেই • সারা দেশের সক্রিয় SOS (কাছেরগুলো আগে)" else "No SOS in this area • showing all of Bangladesh (nearest first)",
                                    color = MutedText,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        items(activeSosAlerts, key = { "find_sos_${it.id}" }) { alert ->
                            SosPostCardItem(
                                alert = alert,
                                language = language,
                                isMySos = alert.userId == viewModel.currentUserId,
                                isHistoryItem = false,
                                hasRespondedNearby = alert.id in respondedSosIds || viewModel.hasUserRespondedSos(alert),
                                hasReportedFalse = alert.id in reportedFalseSosIds || viewModel.hasUserReportedFalseSos(alert),
                                distanceKm = alert.distanceFromKm(userLat, userLng),
                                onOpenDetails = { openDetails(alert) },
                                onRespond = { viewModel.respondToSos(alert) },
                                onDirections = { RokkhonIntents.directions(context, alert.latitude, alert.longitude, language) },
                                onReportFalseAlarm = { viewModel.flagFalseAlarm(alert) },
                                onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(alert.id) }
                            )
                        }
                    }
                    else -> {
                        if (areaDonors.isEmpty()) {
                            item(key = "empty_donors") {
                                EmptyStateCard(
                                    if (bn) "এই এলাকায় এখন কোনো উপলব্ধ ডোনার নেই। এলাকা বদলান বা আবেদন শেয়ার করুন।" else "No available donors in this area. Change area or share a request."
                                )
                            }
                        }
                        items(areaDonors, key = { "find_donor_${it.userId.ifBlank { it.id }}" }) { donor ->
                            LifelineResponderCard(
                                donor = donor,
                                language = language,
                                patientGroup = selectedBloodGroup,
                                onCallClick = { RokkhonIntents.dial(context, donor.contactPhone, language) }
                            )
                        }
                    }
                }

                item(key = "bottom") { Spacer(modifier = Modifier.height(16.dp)) }
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

    val detailsAlert = detailsSosId?.let { id -> allSosAlerts.firstOrNull { it.id == id } }
    if (detailsAlert != null) {
        SosDetailsDialog(
            language = language,
            alert = detailsAlert,
            isOwner = detailsAlert.userId == viewModel.currentUserId,
            hasLocalClip = viewModel.audioVerifier.hasLocalClip(detailsAlert.id),
            isPlaying = activePlaybackAlertId == detailsAlert.id,
            progressSeconds = if (activePlaybackAlertId == detailsAlert.id) playbackSeconds else 0f,
            hasResponded = viewModel.hasUserRespondedSos(detailsAlert),
            distanceKm = detailsAlert.distanceFromKm(userLat, userLng),
            onTogglePlay = { viewModel.audioVerifier.togglePlayFiveSecondClip(scope, detailsAlert.id) },
            onRespond = { viewModel.respondToSos(detailsAlert) },
            onDirections = { RokkhonIntents.directions(context, detailsAlert.latitude, detailsAlert.longitude, language) },
            onShare = { RokkhonIntents.shareSos(context, detailsAlert, language) },
            onMarkSafeOwnerOnly = {
                viewModel.resolveOwnSos(detailsAlert.id)
                detailsSosId = null
            },
            onDismiss = {
                viewModel.audioVerifier.stopPlayback()
                detailsSosId = null
            }
        )
    }
}

@Composable
private fun MapChip(text: String, icon: ImageVector?, background: Color, onClick: () -> Unit, tag: String) {
    Surface(
        color = background,
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(text, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Segmented control: Donors | Patients | SOS. */
@Composable
fun SeparateDonorRequestAndSosFilterTabs(
    language: AppLanguage,
    selectedLayer: MapLayerFilter,
    donorsCount: Int,
    requestsCount: Int,
    sosCount: Int,
    onSelectLayer: (MapLayerFilter) -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Color(0xFFF2E9EB),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SegmentTab(
                label = if (bn) "ডোনার $donorsCount" else "Donors $donorsCount",
                icon = Icons.Default.People,
                selected = selectedLayer == MapLayerFilter.DONORS_ONLY,
                selectedColor = IosSystemRed,
                onClick = { onSelectLayer(MapLayerFilter.DONORS_ONLY) },
                tag = "map_filter_tab_available_donors",
                modifier = Modifier.weight(1f)
            )
            SegmentTab(
                label = if (bn) "রোগী $requestsCount" else "Patients $requestsCount",
                icon = Icons.Default.LocalHospital,
                selected = selectedLayer == MapLayerFilter.BLOOD_REQUESTS_ONLY,
                selectedColor = IosSystemRed,
                onClick = { onSelectLayer(MapLayerFilter.BLOOD_REQUESTS_ONLY) },
                tag = "map_filter_tab_patient_requests",
                modifier = Modifier.weight(1f)
            )
            SegmentTab(
                label = "SOS $sosCount",
                icon = Icons.Default.Shield,
                selected = selectedLayer == MapLayerFilter.SOS_ALERTS_ONLY,
                selectedColor = PinCrimsonRed,
                onClick = { onSelectLayer(MapLayerFilter.SOS_ALERTS_ONLY) },
                tag = "map_filter_tab_sos_alerts",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) Color.White else Color(0xFF4A3A3D),
        shape = CircleShape,
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Tab) { onClick() }
            .testTag(tag)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun MapPinDetailOverlayCard(
    pin: SelectedMapPin,
    language: AppLanguage,
    currentUserId: String,
    hasPledged: Boolean,
    hasRespondedSos: Boolean,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onDirections: (Double, Double) -> Unit,
    onPledgeBlood: (com.example.data.model.BloodRequest) -> Unit,
    onRespondSos: (SosAlert) -> Unit,
    onOpenSosDetails: (SosAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = RadarGlassCard,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .testTag("map_pin_detail_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (pin) {
                    is SelectedMapPin.DonorPin -> Icons.Default.Bloodtype
                    is SelectedMapPin.RequestPin -> Icons.Default.LocalHospital
                    is SelectedMapPin.SosPin -> Icons.Default.Shield
                    is SelectedMapPin.BlePin -> Icons.Default.BluetoothConnected
                }
                Icon(icon, contentDescription = null, tint = PinCrimsonRed, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (pin) {
                        is SelectedMapPin.DonorPin -> "${pin.donor.displayName} • ${pin.donor.bloodGroup}"
                        is SelectedMapPin.RequestPin -> "${pin.request.bloodGroup} • ${pin.request.unitsNeeded} ${if (bn) "ব্যাগ" else "bags"} • ${pin.request.patientName}"
                        is SelectedMapPin.SosPin -> "SOS • ${pin.alert.senderName}"
                        is SelectedMapPin.BlePin -> if (bn) "ব্লুটুথ সংকেত" else "Bluetooth signal"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = if (bn) "বন্ধ" else "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val subtitle = when (pin) {
                is SelectedMapPin.DonorPin -> "${pin.donor.district} • ${donationStatusText(pin.donor, language).first}"
                is SelectedMapPin.RequestPin -> "${pin.request.hospitalName} • ${pin.request.district}"
                is SelectedMapPin.SosPin -> "${emergencyTypeLabel(pin.alert.emergencyType, language)} • ${pin.alert.district} • " +
                    (if (bn) "যাচ্ছেন ${pin.alert.responderCount}" else "Going ${pin.alert.responderCount}")
                is SelectedMapPin.BlePin -> (if (bn) "প্রায় " else "About ") + "${"%.0f".format(pin.beacon.estimatedDistanceMeters)} m • ${pin.beacon.bloodGroup}"
            }
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (pin) {
                    is SelectedMapPin.DonorPin -> {
                        Button(
                            onClick = { onCall(pin.donor.contactPhone) },
                            colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (bn) "কল করুন" else "Call donor", fontWeight = FontWeight.Bold)
                        }
                    }
                    is SelectedMapPin.RequestPin -> {
                        val isOwner = pin.request.userId == currentUserId
                        if (!isOwner) {
                            Button(
                                onClick = { onPledgeBlood(pin.request) },
                                enabled = !hasPledged,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PinCrimsonRed,
                                    disabledContainerColor = IosSystemGreen.copy(alpha = 0.3f),
                                    disabledContentColor = Color.White
                                ),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (hasPledged) (if (bn) "সাড়া দিয়েছেন" else "Pledged") else (if (bn) "আমি রক্ত দেব" else "I can donate"), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onCall(pin.request.contactPhone) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f)),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Call, contentDescription = if (bn) "কল" else "Call", modifier = Modifier.size(16.dp))
                            }
                        }
                        Button(
                            onClick = { onDirections(pin.request.latitude, pin.request.longitude) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f)),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = if (bn) "পথ" else "Directions", modifier = Modifier.size(16.dp))
                        }
                    }
                    is SelectedMapPin.SosPin -> {
                        val isOwner = pin.alert.userId == currentUserId
                        if (!isOwner) {
                            Button(
                                onClick = { onRespondSos(pin.alert) },
                                enabled = !hasRespondedSos,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IosSystemBlue,
                                    disabledContainerColor = IosSystemBlue.copy(alpha = 0.35f),
                                    disabledContentColor = Color.White
                                ),
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (hasRespondedSos) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onDirections(pin.alert.latitude, pin.alert.longitude) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f)),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = if (bn) "পথ" else "Directions", modifier = Modifier.size(16.dp))
                            }
                        }
                        Button(
                            onClick = { onOpenSosDetails(pin.alert) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f)),
                            shape = CircleShape,
                            modifier = if (isOwner) Modifier.weight(1f) else Modifier
                        ) {
                            Text(if (bn) "বিস্তারিত" else "Details", fontWeight = FontWeight.Bold)
                        }
                    }
                    is SelectedMapPin.BlePin -> Unit
                }
            }
        }
    }
}
