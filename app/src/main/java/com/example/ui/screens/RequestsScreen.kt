package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.canDonateTo
import com.example.ui.components.AreaChip
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.BloodGroupChipRow
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.SegmentedControl
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * Requests: every open blood request (this area or all Bangladesh), filterable by group,
 * plus the user's own requests at the top so they can close them once blood is arranged.
 */
@Composable
fun RequestsScreen(
    viewModel: RokkhonViewModel,
    onOpenBloodRequestDialog: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaAutoDetected by viewModel.isAreaAutoDetected.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val hasGps by viewModel.hasRealGpsFix.collectAsStateWithLifecycle()

    var scope by rememberSaveable { mutableStateOf("area") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyCanGive by rememberSaveable { mutableStateOf(false) }
    var showAreaPicker by remember { mutableStateOf(false) }

    val me = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == viewModel.currentUserId }
    val all = (requestsState as? UiState.Success)?.data.orEmpty()
    val mine = all.filter { it.userId == viewModel.currentUserId && it.status == "OPEN" }
    val others = all
        .filter { it.status == "OPEN" && it.userId != viewModel.currentUserId }
        .filter { scope == "all" || selectedDistrict.matchesArea(it.district) }
        .filter { group == null || it.bloodGroup == group }
        .filter { !onlyCanGive || (me != null && canDonateTo(me.bloodGroup, it.bloodGroup)) }
        .sortedWith(
            compareBy<com.example.data.model.BloodRequest> {
                when (it.urgencyLevel) { "CRITICAL" -> 0; "URGENT" -> 1; else -> 2 }
            }.thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
        )

    Box(modifier = Modifier.fillMaxSize().background(Rk.Bg).testTag("requests_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = if (bn) "রক্তের আবেদন" else "Blood requests",
                    subtitle = if (bn) "${others.size}টি খোলা আবেদন" else "${others.size} open requests",
                    onBack = { viewModel.selectTab(com.example.ui.viewmodel.RokkhonTab.HOME_MATCH) }
                ) {
                    AreaChip(
                        dark = true,
                        areaName = if (scope == "all") (if (bn) "সারা দেশ" else "Bangladesh") else (if (bn) selectedDistrict.nameBn.substringBefore(",") else selectedDistrict.nameEn.substringBefore(",")),
                        onClick = { showAreaPicker = true }
                    )
                }
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SegmentedControl(
                        options = listOf(
                            "area" to (if (bn) "এই এলাকা" else "This area"),
                            "all" to (if (bn) "সারা বাংলাদেশ" else "All Bangladesh")
                        ),
                        selectedKey = scope,
                        onSelect = { scope = it },
                        modifier = Modifier.padding(horizontal = Rk.Gutter)
                    )
                    BloodGroupChipRow(language = language, selected = group, onSelect = { group = it })
                    if (me != null) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = Rk.Gutter)
                                .clip(CircleShape)
                                .background(if (onlyCanGive) Rk.Rose else Color.Transparent)
                                .border(1.dp, if (onlyCanGive) Rk.RoseLine else Rk.Line, CircleShape)
                                .clickable(role = Role.Checkbox) { onlyCanGive = !onlyCanGive }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("filter_can_give"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (onlyCanGive) "✓ " else "") + (if (bn) "শুধু যাদের আমি (${me.bloodGroup}) দিতে পারব" else "Only ones I (${me.bloodGroup}) can give to"),
                                color = if (onlyCanGive) Rk.Crimson else Rk.InkSoft,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            if (viewModel.isDemoMode) {
                item(key = "demo") { DemoModeBanner(language, Modifier.padding(horizontal = Rk.Gutter)) }
            }

            if (mine.isNotEmpty()) {
                item(key = "mine_header") {
                    SectionHeader(title = if (bn) "আমার আবেদন" else "My requests", modifier = Modifier.padding(horizontal = Rk.Gutter))
                }
                items(mine, key = { "mine_${it.id}" }) { req ->
                    Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
                        ClinicalBloodRequestCard(
                            req = req,
                            language = language,
                            isOwner = true,
                            onDonateClick = {},
                            onCallClick = {},
                            onShareClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                            onFulfillClick = { viewModel.fulfillOwnBloodRequest(req.id) }
                        )
                    }
                }
                item(key = "others_header") {
                    SectionHeader(title = if (bn) "অন্যদের আবেদন" else "Other requests", modifier = Modifier.padding(horizontal = Rk.Gutter))
                }
            }

            if (others.isEmpty()) {
                item(key = "empty") {
                    EmptyStateCard(
                        text = if (bn) "এই ফিল্টারে কোনো খোলা আবেদন নেই।" else "No open requests match these filters.",
                        modifier = Modifier.padding(horizontal = Rk.Gutter)
                    )
                }
            }
            items(others, key = { "req_${it.id}" }) { req ->
                Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
                    ClinicalBloodRequestCard(
                        req = req,
                        language = language,
                        isOwner = false,
                        hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req),
                        distanceKm = if (hasGps) com.example.data.model.calculateDistanceKm(userLat, userLng, req.latitude, req.longitude) else null,
                        onDonateClick = { viewModel.pledgeToDonate(req) },
                        onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                        onShareClick = { RokkhonIntents.shareBloodRequest(context, req, language) },
                        onFulfillClick = {}
                    )
                }
            }
            item(key = "bottom") { Spacer(modifier = Modifier.height(88.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenBloodRequestDialog,
            containerColor = Rk.Crimson,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(if (bn) "নতুন আবেদন" else "New request", fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Rk.Gutter)
                .testTag("new_blood_request_fab")
        )
    }

    if (showAreaPicker) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selectedDistrict,
            isAutoGpsActive = isAreaAutoDetected,
            onSelectAutoGps = { viewModel.autoSelectAreaFromPermission(notifyUser = true) },
            onSelectDistrict = { dist ->
                viewModel.selectDistrict(dist, isManual = true)
                scope = "area"
            },
            onDismiss = { showAreaPicker = false }
        )
    }
}

/** 2x4 blood group grid used in forms (A+ … O-). */
@Composable
fun BloodGroupPillGrid2x4(
    selectedBloodGroup: String?,
    onSelectBloodGroup: (String) -> Unit
) {
    val rows = listOf(listOf("A+", "A-", "B+", "B-"), listOf("AB+", "AB-", "O+", "O-"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { bg ->
                    val isSelected = selectedBloodGroup == bg
                    Surface(
                        color = if (isSelected) Rk.Crimson else Rk.Surface,
                        contentColor = if (isSelected) Color.White else Rk.Ink,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(width = if (isSelected) 0.dp else 1.dp, color = Rk.Line, shape = RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { onSelectBloodGroup(bg) }
                            .testTag("blood_pick_$bg")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = bg, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
