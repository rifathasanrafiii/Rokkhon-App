package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.SosAlert
import com.example.data.model.emergencyTypeLabel
import com.example.ui.components.AreaChip
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.BloodBadge
import com.example.ui.components.BloodGroupChipRow
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.LifelineResponderCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonMap
import com.example.ui.components.bluetoothOnlySosAlerts
import com.example.ui.components.RokkhonMapController
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SegmentedControl
import com.example.ui.components.SelectedMapPin
import com.example.ui.components.SosDetailsDialog
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.donationStatusText
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Map: real OpenStreetMap with donors, blood requests and SOS as pins, and a draggable sheet
 * listing the same items. Layers are separate so the map never gets crowded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMapHubScreen(
    viewModel: RokkhonViewModel,
    onOpenBloodRequestDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val hasGps by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val layerFilter by viewModel.mapLayerFilter.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val respondedSosIds by viewModel.respondedSosIds.collectAsStateWithLifecycle()
    val reportedFalseSosIds by viewModel.reportedFalseSosIds.collectAsStateWithLifecycle()
    val activePlaybackAlertId by viewModel.audioVerifier.activePlaybackAlertId.collectAsStateWithLifecycle()
    val playbackSeconds by viewModel.audioVerifier.playbackProgressSeconds.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val sosToInspect by viewModel.sosToInspect.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()

    val layer = if (layerFilter == MapLayerFilter.ALL) MapLayerFilter.DONORS_ONLY else layerFilter
    val areaMatches: (String) -> Boolean = { selectedDistrict.matchesArea(it) }
    val cloudSosAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    val allSosAlerts = cloudSosAlerts + bluetoothOnlySosAlerts(bleBeacons, cloudSosAlerts)

    val donors = filterMatchingDonors(
        (donorsState as? UiState.Success)?.data.orEmpty(),
        viewModel.currentUserId,
        selectedBloodGroup,
        onlyAvailable = true,
        areaMatches = areaMatches
    )
    val requests = (requestsState as? UiState.Success)?.data.orEmpty().filter {
        it.status == "OPEN" && areaMatches(it.district) && (selectedBloodGroup == null || it.bloodGroup == selectedBloodGroup)
    }
    // SOS are shown by distance, not by area name: what matters is how close they are.
    val sosAlerts = allSosAlerts
        .filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
        .sortedBy { it.distanceFromKm(userLat, userLng) }

    var selectedPin by remember { mutableStateOf<SelectedMapPin?>(null) }
    var detailsSosId by remember { mutableStateOf<String?>(null) }
    var showAreaPicker by remember { mutableStateOf(false) }
    val mapController = remember { RokkhonMapController() }
    val sheetState = rememberBottomSheetScaffoldState()

    LaunchedEffect(sosToInspect) {
        sosToInspect?.let {
            viewModel.markSosEvidenceInspected(it.id)
            detailsSosId = it.id
            viewModel.consumeSosToInspect()
        }
    }
    LaunchedEffect(layer) { selectedPin = null }

    fun openDetails(alert: SosAlert) {
        viewModel.markSosEvidenceInspected(alert.id)
        detailsSosId = alert.id
    }

    val centerLat = if (hasGps && isAreaAutoDetected) userLat else selectedDistrict.latitude
    val centerLng = if (hasGps && isAreaAutoDetected) userLng else selectedDistrict.longitude

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 210.dp,
        sheetContainerColor = Rk.Bg,
        sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        sheetShadowElevation = 12.dp,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(color = Rk.Line) },
        containerColor = Rk.Bg,
        modifier = Modifier.testTag("find_screen"),
        sheetContent = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "sheet_header") {
                    Row(
                        modifier = Modifier.padding(horizontal = Rk.Gutter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (layer) {
                                    MapLayerFilter.BLOOD_REQUESTS_ONLY -> if (bn) "${requests.size}টি রক্তের আবেদন" else "${requests.size} blood requests"
                                    MapLayerFilter.SOS_ALERTS_ONLY -> if (bn) "${sosAlerts.size}টি সক্রিয় SOS" else "${sosAlerts.size} active SOS"
                                    else -> if (bn) "${donors.size} জন ডোনার পাওয়া গেছে" else "${donors.size} donors found"
                                },
                                color = Rk.Ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (layer == MapLayerFilter.SOS_ALERTS_ONLY) {
                                    if (bn) "কাছেরগুলো আগে" else "Nearest first"
                                } else if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                                color = Rk.Muted,
                                fontSize = 13.sp
                            )
                        }
                        if (layer == MapLayerFilter.BLOOD_REQUESTS_ONLY) {
                            RoundIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = if (bn) "নতুন আবেদন" else "New request",
                                onClick = onOpenBloodRequestDialog,
                                tag = "map_new_request",
                                tint = Color.White,
                                background = Rk.Crimson
                            )
                        }
                    }
                }
                if (layer != MapLayerFilter.SOS_ALERTS_ONLY) {
                    item(key = "groups") {
                        BloodGroupChipRow(
                            language = language,
                            selected = selectedBloodGroup,
                            onSelect = { viewModel.setBloodFilter(it ?: selectedBloodGroup) },
                            allLabel = if (layer == MapLayerFilter.DONORS_ONLY) {
                                if (bn) "রোগীর গ্রুপ: সব" else "Patient: all"
                            } else {
                                null
                            }
                        )
                    }
                }
                when (layer) {
                    MapLayerFilter.BLOOD_REQUESTS_ONLY -> {
                        if (requests.isEmpty()) {
                            item(key = "empty_req") {
                                EmptyStateCard(if (bn) "এই এলাকায় এখন কোনো খোলা আবেদন নেই।" else "No open requests in this area.", Modifier.padding(horizontal = Rk.Gutter))
                            }
                        }
                        items(requests, key = { "map_req_${it.id}" }) { req ->
                            Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
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
                    MapLayerFilter.SOS_ALERTS_ONLY -> {
                        if (falseSosWarningBanner != null) {
                            item(key = "false_banner") {
                                FalseSosAccountWarningBanner(
                                    language = language,
                                    warningMessage = falseSosWarningBanner,
                                    penaltyCount = falseSosPenaltyCount,
                                    onDismiss = { viewModel.dismissFalseSosWarningBanner() },
                                    modifier = Modifier.padding(horizontal = Rk.Gutter)
                                )
                            }
                        }
                        if (sosAlerts.isEmpty()) {
                            item(key = "empty_sos") {
                                EmptyStateCard(if (bn) "এখন কোনো সক্রিয় SOS নেই।" else "No active SOS right now.", Modifier.padding(horizontal = Rk.Gutter))
                            }
                        }
                        items(sosAlerts, key = { "map_sos_${it.id}" }) { alert ->
                            Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
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
                    }
                    else -> {
                        if (donors.isEmpty()) {
                            item(key = "empty_donors") {
                                EmptyStateCard(
                                    if (bn) "এই এলাকায় এখন কোনো উপলব্ধ ডোনার নেই। এলাকা বদলে দেখুন বা আবেদন শেয়ার করুন।" else "No available donors here. Try another area or share a request.",
                                    Modifier.padding(horizontal = Rk.Gutter)
                                )
                            }
                        }
                        items(donors, key = { "map_donor_${it.userId.ifBlank { it.id }}" }) { donor ->
                            Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
                                LifelineResponderCard(
                                    donor = donor,
                                    language = language,
                                    patientGroup = selectedBloodGroup,
                                    distanceKm = if (hasGps) com.example.data.model.calculateDistanceKm(userLat, userLng, donor.latitude, donor.longitude) else null,
                                    onCallClick = { RokkhonIntents.dial(context, donor.contactPhone, language) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            RokkhonMap(
                centerLat = centerLat,
                centerLng = centerLng,
                donors = if (layer == MapLayerFilter.DONORS_ONLY) donors else emptyList(),
                requests = if (layer == MapLayerFilter.BLOOD_REQUESTS_ONLY) requests else emptyList(),
                // SOS pins are always shown: danger nearby matters on every layer.
                sosAlerts = sosAlerts,
                userLat = userLat,
                userLng = userLng,
                showUser = hasGps,
                onPinSelected = { selectedPin = it },
                controller = mapController,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, shadowElevation = 4.dp, color = Rk.Surface) {
                        AreaChip(
                            areaName = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                            onClick = { showAreaPicker = true }
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp, color = Rk.Surface) {
                    SegmentedControl(
                        options = listOf(
                            MapLayerFilter.DONORS_ONLY.name to (if (bn) "ডোনার ${donors.size}" else "Donors ${donors.size}"),
                            MapLayerFilter.BLOOD_REQUESTS_ONLY.name to (if (bn) "রোগী ${requests.size}" else "Patients ${requests.size}"),
                            MapLayerFilter.SOS_ALERTS_ONLY.name to "SOS ${sosAlerts.size}"
                        ),
                        selectedKey = layer.name,
                        onSelect = { viewModel.setMapLayerFilter(MapLayerFilter.valueOf(it)) },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // OpenStreetMap's licence requires visible credit; the map's own credit sits under the sheet.
            Text(
                text = "© OpenStreetMap contributors",
                color = Rk.InkSoft,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 218.dp)
                    .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MapButton(Icons.Default.Add, if (bn) "জুম ইন" else "Zoom in", "map_zoom_in_button") { mapController.zoomIn() }
                MapButton(Icons.Default.Remove, if (bn) "জুম আউট" else "Zoom out", "map_zoom_out_button") { mapController.zoomOut() }
                MapButton(Icons.Default.MyLocation, if (bn) "আমার লোকেশন" else "My location", "map_area_auto_gps_chip", tint = Rk.Blue) {
                    if (hasGps) mapController.animateTo(userLat, userLng, 16.0)
                    viewModel.autoSelectAreaFromPermission(notifyUser = true)
                }
            }

            selectedPin?.let { pin ->
                MapPinCard(
                    pin = pin,
                    language = language,
                    currentUserId = viewModel.currentUserId,
                    hasPledged = (pin as? SelectedMapPin.RequestPin)?.let { it.request.id in pledgedIds || viewModel.hasUserPledged(it.request) } ?: false,
                    hasRespondedSos = (pin as? SelectedMapPin.SosPin)?.let { it.alert.id in respondedSosIds || viewModel.hasUserRespondedSos(it.alert) } ?: false,
                    onDismiss = { selectedPin = null },
                    onCall = { phone -> RokkhonIntents.dial(context, phone, language) },
                    onDirections = { lat, lng -> RokkhonIntents.directions(context, lat, lng, language) },
                    onPledgeBlood = { viewModel.pledgeToDonate(it) },
                    onRespondSos = { viewModel.respondToSos(it) },
                    onOpenSosDetails = { openDetails(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 14.dp, end = 14.dp, bottom = 226.dp)
                )
            }
        }
    }

    if (showAreaPicker) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selectedDistrict,
            isAutoGpsActive = isAreaAutoDetected,
            onSelectAutoGps = { viewModel.autoSelectAreaFromPermission(notifyUser = true) },
            onSelectDistrict = { dist -> viewModel.selectDistrict(dist, isManual = true) },
            onDismiss = { showAreaPicker = false }
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
private fun MapButton(icon: ImageVector, description: String, tag: String, tint: Color = Rk.Ink, onClick: () -> Unit) {
    Surface(
        color = Rk.Surface,
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MapPinCard(
    pin: SelectedMapPin,
    language: AppLanguage,
    currentUserId: String,
    hasPledged: Boolean,
    hasRespondedSos: Boolean,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onDirections: (Double, Double) -> Unit,
    onPledgeBlood: (BloodRequest) -> Unit,
    onRespondSos: (SosAlert) -> Unit,
    onOpenSosDetails: (SosAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Rk.Surface,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Rk.Line),
        modifier = modifier
            .fillMaxWidth()
            .testTag("map_pin_detail_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (pin) {
                    is SelectedMapPin.DonorPin -> BloodBadge(pin.donor.bloodGroup, size = 44)
                    is SelectedMapPin.RequestPin -> BloodBadge(pin.request.bloodGroup, size = 44, filled = true)
                    is SelectedMapPin.SosPin -> Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Rk.Maroon),
                        contentAlignment = Alignment.Center
                    ) { Text("SOS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (pin) {
                            is SelectedMapPin.DonorPin -> pin.donor.displayName
                            is SelectedMapPin.RequestPin -> pin.request.hospitalName
                            is SelectedMapPin.SosPin -> pin.alert.senderName
                        },
                        color = Rk.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (pin) {
                            is SelectedMapPin.DonorPin -> donationStatusText(pin.donor, language).first
                            is SelectedMapPin.RequestPin -> (if (bn) "${pin.request.unitsNeeded} ব্যাগ • " else "${pin.request.unitsNeeded} bags • ") + pin.request.district
                            is SelectedMapPin.SosPin -> emergencyTypeLabel(pin.alert.emergencyType, language)
                        },
                        color = Rk.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = if (bn) "বন্ধ" else "Close", tint = Rk.Muted)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (pin) {
                    is SelectedMapPin.DonorPin -> {
                        PrimaryButton(
                            text = if (bn) "কল করুন" else "Call donor",
                            onClick = { onCall(pin.donor.contactPhone) },
                            icon = Icons.Default.Call,
                            color = Rk.Green,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is SelectedMapPin.RequestPin -> {
                        if (pin.request.userId != currentUserId) {
                            PrimaryButton(
                                text = if (hasPledged) (if (bn) "সাড়া দিয়েছেন" else "Pledged") else (if (bn) "আমি রক্ত দেব" else "I can donate"),
                                onClick = { onPledgeBlood(pin.request) },
                                enabled = !hasPledged,
                                modifier = Modifier.weight(1f)
                            )
                            RoundIconButton(Icons.Default.Call, if (bn) "কল" else "Call", { onCall(pin.request.contactPhone) }, "pin_call", tint = Rk.Green, background = Rk.GreenSoft, size = 48)
                        }
                        RoundIconButton(Icons.Default.Directions, if (bn) "পথ" else "Directions", { onDirections(pin.request.latitude, pin.request.longitude) }, "pin_directions", tint = Rk.Blue, background = Rk.BlueSoft, size = 48)
                    }
                    is SelectedMapPin.SosPin -> {
                        if (pin.alert.userId != currentUserId) {
                            PrimaryButton(
                                text = if (hasRespondedSos) (if (bn) "যাচ্ছি ✓" else "Going ✓") else (if (bn) "আমি যাচ্ছি" else "I'm going"),
                                onClick = { onRespondSos(pin.alert) },
                                enabled = !hasRespondedSos,
                                color = Rk.Blue,
                                modifier = Modifier.weight(1f)
                            )
                            RoundIconButton(Icons.Default.Directions, if (bn) "পথ" else "Directions", { onDirections(pin.alert.latitude, pin.alert.longitude) }, "pin_directions", tint = Rk.Blue, background = Rk.BlueSoft, size = 48)
                        }
                        PrimaryButton(
                            text = if (bn) "বিস্তারিত" else "Details",
                            onClick = { onOpenSosDetails(pin.alert) },
                            color = Rk.Ink,
                            modifier = if (pin.alert.userId == currentUserId) Modifier.weight(1f) else Modifier
                        )
                    }
                }
            }
        }
    }
}
