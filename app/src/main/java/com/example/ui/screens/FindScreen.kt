package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.canDonateTo
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.Segment
import com.example.ui.components.SegmentedControl
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Find: search every donor and every open blood request by name, area, hospital and blood group.
 * A plain native list (the street map was removed); details open in a bottom sheet.
 */
@Composable
fun FindScreen(
    viewModel: RokkhonViewModel,
    onOpenBloodRequestDialog: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaFilterEnabled by viewModel.isAreaFilterEnabled.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val onlyAvailable by viewModel.onlyAvailableDonorsFilter.collectAsStateWithLifecycle()
    val layerFilter by viewModel.mapLayerFilter.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()

    val bn = language == AppLanguage.BENGALI
    val showRequests = layerFilter == MapLayerFilter.BLOOD_REQUESTS_ONLY
    var query by remember { mutableStateOf("") }
    var showAreaPicker by remember { mutableStateOf(false) }
    var selectedDonor by remember { mutableStateOf<DonorNode?>(null) }
    var selectedRequest by remember { mutableStateOf<BloodRequest?>(null) }

    val q = query.trim().lowercase()
    val donors = (donorsState as? UiState.Success)?.data.orEmpty().filter { d ->
        d.userId != viewModel.currentUserId &&
            (!onlyAvailable || d.isEligibleNow) &&
            (selectedBloodGroup == null || canDonateTo(d.bloodGroup, selectedBloodGroup!!)) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(d.district)) &&
            (q.isEmpty() || d.displayName.lowercase().contains(q) || d.district.lowercase().contains(q))
    }.sortedWith(compareByDescending<DonorNode> { it.isEligibleNow }.thenByDescending { it.bloodGroup == selectedBloodGroup })

    val requests = (requestsState as? UiState.Success)?.data.orEmpty().filter { r ->
        r.status == "OPEN" &&
            (selectedBloodGroup == null || r.bloodGroup == selectedBloodGroup) &&
            (!isAreaFilterEnabled || selectedDistrict.matchesArea(r.district)) &&
            (q.isEmpty() || r.patientName.lowercase().contains(q) || r.hospitalName.lowercase().contains(q) || r.district.lowercase().contains(q))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("find_screen")
    ) {
        item(key = "find_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (bn) "খুঁজুন" else "Find",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = RokkhonPalette.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .springClick(onClick = onOpenBloodRequestDialog)
                            .testTag("new_blood_request_fab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(UIcons.Add, contentDescription = if (bn) "রক্তের আবেদন" else "Request blood", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Where: tap to pick any district or upazila
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .springClick(pressedScale = 0.97f) { showAreaPicker = true }
                        .padding(vertical = 6.dp)
                        .testTag("find_area_title"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(UIcons.LocationOn, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAreaFilterEnabled) (if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn) else (if (bn) "সারা বাংলাদেশ" else "All Bangladesh"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RokkhonPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(UIcons.KeyboardArrowDown, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
                    if (isAreaAutoDetected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bn) "জিপিএস" else "GPS",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RokkhonPalette.Muted,
                            modifier = Modifier.clip(CircleShape).background(RokkhonPalette.Subtle).padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(RokkhonPalette.Subtle)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(UIcons.Search, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (showRequests) (if (bn) "রোগী, হাসপাতাল বা এলাকা" else "Patient, hospital or area") else (if (bn) "নাম বা এলাকা" else "Name or area"),
                                fontSize = 16.sp,
                                color = RokkhonPalette.Muted
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = RokkhonPalette.Ink),
                            cursorBrush = SolidColor(Color.Black),
                            modifier = Modifier.fillMaxWidth().testTag("find_search_input")
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            UIcons.Close,
                            contentDescription = if (bn) "মুছুন" else "Clear",
                            tint = RokkhonPalette.Muted,
                            modifier = Modifier.size(20.dp).springClick { query = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        item(key = "find_groups") {
            BloodGroupChipRow(
                language = language,
                selected = selectedBloodGroup,
                onSelect = { viewModel.setBloodFilter(it) },
                contentPadding = 16.dp
            )
        }

        item(key = "find_tabs") {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
                SegmentedControl(
                    segments = listOf(
                        Segment("${if (bn) "ডোনার" else "Donors"} ${donors.size}", UIcons.People, "map_filter_tab_available_donors"),
                        Segment("${if (bn) "রোগী" else "Patients"} ${requests.size}", UIcons.LocalHospital, "map_filter_tab_patient_requests")
                    ),
                    selectedIndex = if (showRequests) 1 else 0,
                    onSelect = { viewModel.setMapLayerFilter(if (it == 1) MapLayerFilter.BLOOD_REQUESTS_ONLY else MapLayerFilter.DONORS_ONLY) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
                if (!showRequests) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(CircleShape)
                            .springClick { viewModel.setOnlyAvailableDonorsFilter(!onlyAvailable) }
                            .padding(vertical = 4.dp)
                            .testTag("find_available_toggle"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = UIcons.CheckCircle,
                            contentDescription = null,
                            tint = if (onlyAvailable) RokkhonPalette.Positive else RokkhonPalette.Faint,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bn) "শুধু যারা এখন রক্ত দিতে পারবেন" else "Only those who can give now",
                            fontSize = 14.sp,
                            color = if (onlyAvailable) RokkhonPalette.Ink else RokkhonPalette.Muted
                        )
                    }
                }
            }
        }

        if (!showRequests) {
            if (donors.isEmpty()) {
                item(key = "find_empty_donors") {
                    FindEmpty(
                        title = if (bn) "কোনো ডোনার পাওয়া যায়নি" else "No donors found",
                        body = if (bn) "অন্য এলাকা বা গ্রুপ বেছে দেখুন।" else "Try another area or blood group.",
                        testTag = "find_empty_donors"
                    )
                }
            } else {
                items(donors, key = { "find_donor_${it.id.ifBlank { it.userId }}" }) { donor ->
                    DonorRow(
                        donor = donor,
                        language = language,
                        onClick = { selectedDonor = donor },
                        onCall = { RokkhonIntents.dial(context, donor.contactPhone, language) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else {
            if (requests.isEmpty()) {
                item(key = "find_empty_requests") {
                    FindEmpty(
                        title = if (bn) "কোনো খোলা আবেদন নেই" else "No open requests",
                        body = if (bn) "এই মুহূর্তে এখানে কেউ রক্ত খুঁজছেন না।" else "Nobody here is looking for blood right now.",
                        testTag = "find_empty_requests"
                    )
                }
            } else {
                items(requests, key = { "find_req_${it.id.ifBlank { it.patientName }}" }) { req ->
                    RequestRow(req = req, language = language, onClick = { selectedRequest = req }, modifier = Modifier.animateItem())
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showAreaPicker) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selectedDistrict,
            isAutoGpsActive = isAreaAutoDetected,
            onSelectAutoGps = { viewModel.autoSelectAreaFromPermission(notifyUser = true) },
            onSelectDistrict = { viewModel.selectDistrict(it, isManual = true) },
            onDismiss = { showAreaPicker = false }
        )
    }

    selectedDonor?.let { donor ->
        RokkhonBottomSheet(onDismiss = { selectedDonor = null }, testTag = "find_donor_sheet") {
            DonorDetail(
                donor = donor,
                language = language,
                onCall = { RokkhonIntents.dial(context, donor.contactPhone, language) },
                onSms = {
                    RokkhonIntents.sms(
                        context,
                        donor.contactPhone,
                        if (bn) "আসসালামু আলাইকুম, রক্ষণ অ্যাপ থেকে আপনার নম্বর পেয়েছি। ${donor.bloodGroup} রক্তের প্রয়োজন।" else "Hi, I found you on Rokkhon. We need ${donor.bloodGroup} blood.",
                        language
                    )
                }
            )
        }
    }

    selectedRequest?.let { req ->
        RokkhonBottomSheet(onDismiss = { selectedRequest = null }, testTag = "find_request_sheet") {
            ClinicalBloodRequestCard(
                req = req,
                language = language,
                isOwner = req.userId == viewModel.currentUserId,
                hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req),
                onDonateClick = { viewModel.pledgeToDonate(req) },
                onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                onFulfillClick = {
                    viewModel.fulfillOwnBloodRequest(req.id)
                    selectedRequest = null
                }
            )
        }
    }
}

/** A patient's request as a list row: group, patient, hospital, how much and when. */
@Composable
private fun RequestRow(req: BloodRequest, language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bn = language == AppLanguage.BENGALI
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .springClick(pressedScale = 0.98f, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(req.bloodGroup, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = req.patientName.ifBlank { req.requesterName },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RokkhonPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (req.urgencyLevel == "CRITICAL") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (bn) "জরুরি" else "Urgent", color = RokkhonPalette.Sos, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = req.hospitalName + (if (req.district.isNotBlank()) " · " + req.district.substringBefore(",") else ""),
                    fontSize = 13.5.sp,
                    color = RokkhonPalette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (bn) "${bnDigits(req.unitsNeeded.toInt())} ব্যাগ · ${req.neededDateTime}" else "${req.unitsNeeded} bag(s) · ${req.neededDateTime}",
                    fontSize = 13.sp,
                    color = RokkhonPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(UIcons.KeyboardArrowRight, contentDescription = null, tint = RokkhonPalette.Faint, modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .padding(start = 80.dp, end = 16.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(RokkhonPalette.Divider)
        )
    }
}

@Composable
private fun FindEmpty(title: String, body: String, testTag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(RokkhonPalette.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(UIcons.Search, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, fontSize = 14.sp, color = RokkhonPalette.Muted, textAlign = TextAlign.Center)
    }
}
