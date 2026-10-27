package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.NotificationKind
import com.example.data.model.canDonateTo
import com.example.data.model.daysUntilEligible
import com.example.data.model.parseDonationDateMillis
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.components.QuickAction
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RokkhonNotificationCenterDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.SheetGridItem
import com.example.ui.components.enterOnce
import com.example.ui.components.riseIn
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
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
    onOpenTutorial: () -> Unit = {},
    onOpenPrivateSos: (() -> Unit)? = null,
    sosAlertActive: Boolean = false
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedBloodGroup by viewModel.selectedBloodFilter.collectAsStateWithLifecycle()
    val onlyAvailableFilter by viewModel.onlyAvailableDonorsFilter.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val isAreaFilterEnabled by viewModel.isAreaFilterEnabled.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val requestsState by viewModel.bloodRequestsState.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val dailyStreakCount by viewModel.dailyStreakCount.collectAsStateWithLifecycle()
    val guardianPoints by viewModel.guardianPoints.collectAsStateWithLifecycle()
    val isLocationDisabledByAdmin by viewModel.isLocationDisabledByAdmin.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()

    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }

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

    val bn = language == AppLanguage.BENGALI
    val isOwnDonorRegistered = allDonors.any { it.userId == viewModel.currentUserId && it.contactPhone.isNotBlank() }
    val firstName = viewModel.currentUserName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() && it != "Guest" }
    var selectedDonor by remember { mutableStateOf<DonorNode?>(null) }
    var selectedRequest by remember { mutableStateOf<BloodRequest?>(null) }

    // White status-bar icons while the red header is behind them, dark again once it scrolls away.
    val listState = rememberLazyListState()
    val headerUnderStatusBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 260 }
    }
    val hostView = LocalView.current
    DisposableEffect(headerUnderStatusBar) {
        val window = generateSequence(hostView.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        controller?.isAppearanceLightStatusBars = !headerUnderStatusBar
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }

    // A people-first home: who can give blood near you, and who needs it.
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("home_screen")
    ) {
        item(key = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
            // Red gradient behind the top bar, greeting and search, like the first design.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE11900), Color(0xFFA8150C))))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp)
                    .testTag("home_red_header")
            ) {
                HomeTopBar(
                    onDark = true,
                    language = language,
                    userInitial = viewModel.currentUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "R",
                    streakDays = dailyStreakCount,
                    points = guardianPoints,
                    hasUnread = unreadNotificationCount > 0,
                    onStreakClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                    onLanguageClick = { viewModel.toggleLanguage() },
                    onBellClick = { showNotificationCenterDialog = true },
                    onAvatarClick = { viewModel.selectTab(RokkhonTab.DONOR_PROFILE) },
                    onSosClick = onOpenPrivateSos,
                    sosAlert = sosAlertActive
                )

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = greeting(bn) + (firstName?.let { ", $it" } ?: ""),
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (bn) "আজ কারো জন্য রক্ত খুঁজছেন?" else "Looking for blood for someone today?",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .springClick(pressedScale = 0.98f) { showGroupSheet = true }
                        .padding(start = 18.dp, end = 8.dp)
                        .testTag("home_search_bar"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(UIcons.Search, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(21.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedBloodGroup?.let { if (bn) "$it গ্রুপের ডোনার" else "$it donors" }
                            ?: if (bn) "কোন গ্রুপের রক্ত লাগবে?" else "Which blood group?",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RokkhonPalette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RokkhonPalette.Subtle)
                            .springClick { showAreaPickerDialog = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("area_dropdown_selector"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(UIcons.LocationOn, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isAreaFilterEnabled) (if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn).substringBefore(",") else (if (bn) "সব জেলা" else "All"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RokkhonPalette.Ink,
                            maxLines = 1
                        )
                        Icon(UIcons.KeyboardArrowDown, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(16.dp))
                    }
                }

            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                if (isLocationDisabledByAdmin) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("admin_location_off_banner"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(UIcons.LocationOff, contentDescription = null, tint = RokkhonPalette.Warning, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bn) "লাইভ লোকেশন অ্যাডমিন বন্ধ রেখেছেন" else "Live location turned off by admin",
                            color = RokkhonPalette.Warning,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = UIcons.Bloodtype,
                        label = if (bn) "রক্ত চাই" else "Request",
                        onClick = onOpenBloodRequestDialog,
                        modifier = Modifier.weight(1f).riseIn(0),
                        testTag = "request_blood_button"
                    )
                    QuickAction(
                        icon = UIcons.Search,
                        label = if (bn) "খুঁজুন" else "Find",
                        onClick = {
                            viewModel.setMapLayerFilter(MapLayerFilter.DONORS_ONLY)
                            viewModel.selectTab(RokkhonTab.RADAR_MAP)
                        },
                        modifier = Modifier.weight(1f).riseIn(1),
                        testTag = "explore_compatible_donors_card"
                    )
                    QuickAction(
                        icon = if (isOwnDonorRegistered) UIcons.CardGiftcard else UIcons.Person,
                        label = if (isOwnDonorRegistered) (if (bn) "রিওয়ার্ড" else "Rewards") else (if (bn) "ডোনার হোন" else "Join"),
                        onClick = { if (isOwnDonorRegistered) viewModel.selectTab(RokkhonTab.REWARDS) else viewModel.openDonorEditor() },
                        modifier = Modifier.weight(1f).riseIn(2),
                        testTag = "home_profile_shortcut"
                    )
                    QuickAction(
                        icon = UIcons.GridView,
                        label = if (bn) "আরও" else "More",
                        onClick = { showMoreSheet = true },
                        modifier = Modifier.weight(1f).riseIn(3),
                        testTag = "home_more_button"
                    )
                }


                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(
                    title = if (bn) "রক্তের গ্রুপ" else "Blood group",
                    trailing = {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .springClick { viewModel.setOnlyAvailableDonorsFilter(!onlyAvailableFilter) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("donor_active_switch"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (onlyAvailableFilter) UIcons.CheckCircle else UIcons.People,
                                contentDescription = null,
                                tint = if (onlyAvailableFilter) RokkhonPalette.Positive else RokkhonPalette.Muted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (onlyAvailableFilter) (if (bn) "এখন দিতে পারবেন" else "Can give now") else (if (bn) "সব ডোনার" else "All donors"),
                                fontSize = 13.sp,
                                color = if (onlyAvailableFilter) RokkhonPalette.Positive else RokkhonPalette.Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            }
        }

        item(key = "group_chips") {
            BloodGroupChipRow(
                language = language,
                selected = selectedBloodGroup,
                onSelect = { viewModel.setBloodFilter(it) },
                contentPadding = 16.dp
            )
        }

        // Patients waiting right now, only when there are any
        if (openRequests.isNotEmpty()) {
            item(key = "requests_header") {
                SectionHeader(
                    title = if (bn) "যাদের রক্ত লাগবে" else "Who needs blood",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
                    trailing = {
                        Text(
                            text = if (bn) "সব দেখুন" else "See all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RokkhonPalette.Ink,
                            modifier = Modifier
                                .clip(CircleShape)
                                .springClick {
                                    viewModel.setMapLayerFilter(MapLayerFilter.BLOOD_REQUESTS_ONLY)
                                    viewModel.selectTab(RokkhonTab.RADAR_MAP)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("filter_tab_patient_requests")
                        )
                    }
                )
            }
            item(key = "requests_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(openRequests.take(12), key = { "req_card_${it.id.ifBlank { it.patientName }}" }) { req ->
                        UrgentRequestCard(req = req, language = language, onClick = { selectedRequest = req })
                    }
                }
            }
        }

        if (!isOwnDonorRegistered) {
            item(key = "become_donor") {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .springClick(pressedScale = 0.98f) { viewModel.openDonorEditor() }
                        .padding(18.dp)
                        .testTag("home_become_donor_card"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (bn) "আপনার এক ব্যাগ রক্তে\nএকটি জীবন বাঁচে" else "One bag of your blood\ncan save a life", color = Color.White, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (bn) "ডোনার হিসেবে যোগ দিন" else "Join as a donor",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    Icon(UIcons.Bloodtype, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
                }
            }
        }

        item(key = "donors_header") {
            SectionHeader(
                title = if (bn) "আপনার এলাকার ডোনার" else "Donors near you",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 4.dp),
                trailing = {
                    Text(
                        text = if (bn) "${bnDigits(matchingDonors.size)} জন" else "${matchingDonors.size}",
                        fontSize = 14.sp,
                        color = RokkhonPalette.Muted,
                        modifier = Modifier.testTag("filter_tab_available_donors")
                    )
                }
            )
        }

        if (matchingDonors.isEmpty()) {
            item(key = "empty_donors") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 28.dp)
                        .testTag("home_empty_donors"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(RokkhonPalette.Subtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(UIcons.People, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (bn) "এখানে এখনো কেউ যোগ দেননি" else "No one here yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = RokkhonPalette.Ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (bn) "আপনার এলাকার প্রথম ডোনার হোন, অথবা উপরে অন্য জেলা বেছে দেখুন।" else "Be the first donor in your area, or pick another district above.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = RokkhonPalette.Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(matchingDonors, key = { _, d -> "home_donor_${d.id.ifBlank { d.userId }}" }) { index, donor ->
                DonorRow(
                    donor = donor,
                    language = language,
                    onClick = { selectedDonor = donor },
                    onCall = { RokkhonIntents.dial(context, donor.contactPhone, language) },
                    modifier = Modifier.animateItem().enterOnce(index)
                )
            }
        }

        if (archivedFulfilledCount > 0) {
            item(key = "archived_footer") {
                Text(
                    text = if (bn) "এ পর্যন্ত ${bnDigits(archivedFulfilledCount)} জন রোগী রক্ত পেয়েছেন" else "$archivedFulfilledCount patients got blood so far",
                    fontSize = 13.sp,
                    color = RokkhonPalette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    selectedDonor?.let { donor ->
        RokkhonBottomSheet(onDismiss = { selectedDonor = null }, testTag = "donor_detail_sheet") {
            DonorDetail(
                donor = donor,
                language = language,
                onCall = { RokkhonIntents.dial(context, donor.contactPhone, language) },
                onSms = { RokkhonIntents.sms(context, donor.contactPhone, if (bn) "আসসালামু আলাইকুম, রক্ষণ অ্যাপ থেকে আপনার নম্বর পেয়েছি। ${donor.bloodGroup} রক্তের প্রয়োজন।" else "Hi, I found you on Rokkhon. We need ${donor.bloodGroup} blood.", language) }
            )
        }
    }

    selectedRequest?.let { req ->
        RokkhonBottomSheet(onDismiss = { selectedRequest = null }, testTag = "request_detail_sheet") {
            val hasPledged = req.id in pledgedIds || viewModel.hasUserPledged(req)
            ClinicalBloodRequestCard(
                req = req,
                language = language,
                isOwner = req.userId == viewModel.currentUserId,
                hasPledged = hasPledged,
                onDonateClick = { viewModel.pledgeToDonate(req) },
                onCallClick = { RokkhonIntents.dial(context, req.contactPhone, language) },
                onFulfillClick = {
                    viewModel.fulfillOwnBloodRequest(req.id)
                    selectedRequest = null
                }
            )
        }
    }

    if (showGroupSheet) {
        RokkhonBottomSheet(
            onDismiss = { showGroupSheet = false },
            title = if (bn) "কোন গ্রুপের রক্ত লাগবে?" else "Which blood group?",
            testTag = "home_group_sheet"
        ) {
            Text(
                text = if (bn) "রোগীর গ্রুপ বেছে নিন — যারা দিতে পারবেন তাদের দেখাব।" else "Pick the patient's group — we'll show who can give.",
                fontSize = 14.sp,
                color = RokkhonPalette.Muted
            )
            Spacer(modifier = Modifier.height(14.dp))
            val groups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
            groups.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { g ->
                        val sel = g == selectedBloodGroup
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (sel) Color.Black else RokkhonPalette.Subtle)
                                .springClick {
                                    viewModel.setBloodFilter(g)
                                    showGroupSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(g, color = if (sel) Color.White else RokkhonPalette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = if (bn) "সব গ্রুপ দেখুন" else "Show all groups",
                color = RokkhonPalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .springClick {
                        viewModel.setBloodFilter(null)
                        showGroupSheet = false
                    }
                    .padding(vertical = 12.dp)
            )
        }
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
                    icon = UIcons.LocationOn,
                    label = if (bn) "এলাকা" else "Area",
                    state = if (bn) selectedDistrict.nameBn else selectedDistrict.nameEn,
                    onClick = { close(); showAreaPickerDialog = true },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = UIcons.FilterAlt,
                    label = if (bn) "শুধু এই এলাকা" else "This area only",
                    state = if (isAreaFilterEnabled) (if (bn) "চালু" else "On") else (if (bn) "বন্ধ" else "Off"),
                    active = isAreaFilterEnabled,
                    onClick = { viewModel.toggleAreaFilter(!isAreaFilterEnabled) },
                    modifier = Modifier.weight(1f),
                    testTag = "area_filter_mode_toggle"
                )
                SheetGridItem(
                    icon = UIcons.Translate,
                    label = if (bn) "ভাষা" else "Language",
                    state = if (bn) "বাংলা" else "English",
                    onClick = { viewModel.toggleLanguage() },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SheetGridItem(
                    icon = UIcons.NotificationsNone,
                    label = if (bn) "নোটিফিকেশন" else "Notifications",
                    state = if (unreadNotificationCount > 0) "$unreadNotificationCount" else null,
                    onClick = { close(); showNotificationCenterDialog = true },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = UIcons.Star,
                    label = if (bn) "রিওয়ার্ড" else "Rewards",
                    state = "$guardianPoints",
                    onClick = { close(); viewModel.selectTab(RokkhonTab.REWARDS) },
                    modifier = Modifier.weight(1f)
                )
                SheetGridItem(
                    icon = UIcons.HelpOutline,
                    label = if (bn) "কিভাবে চলে" else "How it works",
                    onClick = { close(); onOpenTutorial() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showAreaPickerDialog) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selectedDistrict,
            onSelectDistrict = { dist -> viewModel.selectDistrict(dist, isManual = true) },
            onDetectLocation = { done -> viewModel.detectNearestArea(done) },
            onAreaDetected = { viewModel.selectDistrict(it, isManual = false) },
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
                        color = if (isSelected) Color(0xFF000000) else Color.White,
                        contentColor = if (isSelected) Color.White else Color(0xFF000000),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(
                                width = if (isSelected) 0.dp else 1.dp,
                                color = Color(0xFFE2E2E2),
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


/** Black wordmark on the left; streak, language, notifications and profile on the right. */
@Composable
private fun HomeTopBar(
    onDark: Boolean = false,
    language: AppLanguage,
    userInitial: String,
    streakDays: Int,
    points: Int,
    hasUnread: Boolean,
    onStreakClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onBellClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSosClick: (() -> Unit)? = null,
    sosAlert: Boolean = false
) {
    val bn = language == AppLanguage.BENGALI
    // On the red header everything turns white; elsewhere it stays black on grey.
    val fg = if (onDark) Color.White else RokkhonPalette.Ink
    val pillBg = if (onDark) Color.White.copy(alpha = 0.16f) else RokkhonPalette.Subtle
    val badge = if (onDark) Color.White else RokkhonPalette.Sos
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(if (onDark) R.drawable.rokkhon_wordmark_white else R.drawable.rokkhon_wordmark_black),
            contentDescription = "রক্ষণ",
            modifier = Modifier.height(30.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pillBg)
                    .springClick(onClick = onStreakClick)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
                    .testTag("header_streak_points_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(UIcons.LocalFireDepartment, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (bn) "${streakDays}দি" else "${streakDays}d", color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(UIcons.Star, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("$points", color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            if (onSosClick != null) {
                // Private SOS: hold-to-start lives behind this, so a stray tap here starts nothing.
                HeaderIconButton(onClick = onSosClick, testTag = "home_private_sos_button", description = "SOS") {
                    Icon(UIcons.Shield, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
                    if (sosAlert) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 9.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badge)
                        )
                    }
                }
            }
            HeaderIconButton(onClick = onLanguageClick, testTag = "language_toggle_button", description = "Switch Language") {
                Icon(UIcons.Translate, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            }
            HeaderIconButton(onClick = onBellClick, testTag = "notification_bell_button", description = "Notifications") {
                Icon(UIcons.NotificationsNone, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                if (hasUnread) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 9.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badge)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(pillBg)
                    .springClick(onClick = onAvatarClick)
                    .semantics { contentDescription = "Profile" }
                    .testTag("open_profile_avatar"),
                contentAlignment = Alignment.Center
            ) {
                Text(userInitial, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    testTag: String,
    description: String,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .springClick(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** "All" + the eight groups as one scrollable row of pills; the selected one is black. */
@Composable
internal fun BloodGroupChipRow(
    language: AppLanguage,
    selected: String?,
    onSelect: (String?) -> Unit,
    includeAll: Boolean = true,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val groups = (if (includeAll) listOf(null) else emptyList()) + listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val isSelected = group == selected
            val bg by animateColorAsState(if (isSelected) Color.Black else RokkhonPalette.Subtle, label = "chip_bg")
            val fg by animateColorAsState(if (isSelected) Color.White else RokkhonPalette.Ink, label = "chip_fg")
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 56.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .springClick(pressedScale = 0.92f) { onSelect(group) }
                    .padding(horizontal = 16.dp)
                    .testTag("blood_group_chip_${group ?: "all"}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group ?: if (language == AppLanguage.BENGALI) "সব" else "All",
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** "শুভ সকাল" / "Good morning" by the phone's clock. */
private fun greeting(bn: Boolean): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (bn) {
        when (hour) {
            in 5..11 -> "শুভ সকাল"
            in 12..15 -> "শুভ দুপুর"
            in 16..17 -> "শুভ বিকেল"
            in 18..20 -> "শুভ সন্ধ্যা"
            else -> "শুভ রাত্রি"
        }
    } else {
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}

private val BANGLA_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/** 12 -> "১২" for Bangla sentences. */
internal fun bnDigits(n: Int): String = n.toString().map { c -> if (c.isDigit()) BANGLA_DIGITS[c - '0'] else c }.joinToString("")

/** "৩ মাস আগে দিয়েছেন" / "Gave 3 months ago", or that they haven't given yet. */
private fun lastDonationLabel(lastDonationDate: String, bn: Boolean): String {
    val last = parseDonationDateMillis(lastDonationDate)
        ?: return if (bn) "আগে রক্ত দেননি" else "Hasn't donated yet"
    val days = ((System.currentTimeMillis() - last) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)
    return when {
        days < 1 -> if (bn) "আজ রক্ত দিয়েছেন" else "Gave today"
        days < 30 -> if (bn) "${bnDigits(days)} দিন আগে দিয়েছেন" else "Gave $days days ago"
        days < 365 -> if (bn) "${bnDigits(days / 30)} মাস আগে দিয়েছেন" else "Gave ${days / 30} months ago"
        else -> if (bn) "${bnDigits(days / 365)} বছর আগে দিয়েছেন" else "Gave ${days / 365} years ago"
    }
}

/** One donor in the people list: initials, name, where and when they last gave, group, call. */
@Composable
internal fun DonorRow(
    donor: DonorNode,
    language: AppLanguage,
    onClick: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    val area = donor.district.substringBefore(",").ifBlank { if (bn) "এলাকা জানা নেই" else "Area unknown" }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .springClick(pressedScale = 0.98f, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("donor_row_${donor.id.ifBlank { donor.userId }}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(50.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(RokkhonPalette.Subtle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(donorInitials(donor.displayName), color = RokkhonPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (donor.isEligibleNow) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(RokkhonPalette.Positive)
                            .border(2.5.dp, Color.White, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = donor.displayName.ifBlank { if (bn) "নাম নেই" else "No name" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RokkhonPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$area · " + if (donor.isEligibleNow || donor.lastDonationDate.isBlank()) lastDonationLabel(donor.lastDonationDate, bn)
                    else (if (bn) "${bnDigits(daysUntilEligible(donor.lastDonationDate))} দিন পর দিতে পারবেন" else "Can give in ${daysUntilEligible(donor.lastDonationDate)} days"),
                    fontSize = 13.5.sp,
                    color = RokkhonPalette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = donor.bloodGroup,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RokkhonPalette.Ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RokkhonPalette.Subtle)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .springClick(onClick = onCall)
                    .semantics { contentDescription = if (bn) "কল করুন" else "Call" },
                contentAlignment = Alignment.Center
            ) {
                Icon(UIcons.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
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

/** Donor details in a sheet: who they are, when they can give, and ways to reach them. */
@Composable
internal fun DonorDetail(donor: DonorNode, language: AppLanguage, onCall: () -> Unit, onSms: () -> Unit) {
    val bn = language == AppLanguage.BENGALI
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(RokkhonPalette.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Text(donorInitials(donor.displayName), color = RokkhonPalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(donor.displayName.ifBlank { if (bn) "নাম নেই" else "No name" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
            Text(donor.district.ifBlank { "—" }, fontSize = 14.sp, color = RokkhonPalette.Muted)
        }
        Text(
            text = donor.bloodGroup,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clip(CircleShape).background(Color.Black).padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
    Spacer(modifier = Modifier.height(18.dp))
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RokkhonPalette.Subtle)) {
        DetailLine(
            icon = UIcons.CheckCircle,
            text = if (donor.isEligibleNow) (if (bn) "এখন রক্ত দিতে পারবেন" else "Can give blood now")
            else if (!donor.isAvailableDonor) (if (bn) "এখন রক্ত দিচ্ছেন না" else "Not giving right now")
            else (if (bn) "${bnDigits(daysUntilEligible(donor.lastDonationDate))} দিন পর দিতে পারবেন" else "Can give in ${daysUntilEligible(donor.lastDonationDate)} days"),
            tint = if (donor.isEligibleNow) RokkhonPalette.Positive else RokkhonPalette.Muted
        )
        DetailLine(icon = UIcons.Event, text = lastDonationLabel(donor.lastDonationDate, bn))
    }
    Spacer(modifier = Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .springClick(onClick = onCall)
                .testTag("donor_detail_call"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(UIcons.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (bn) "কল করুন" else "Call", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RokkhonPalette.Subtle)
                .springClick(onClick = onSms),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(UIcons.Sms, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (bn) "মেসেজ" else "Message", color = RokkhonPalette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String, tint: Color = RokkhonPalette.Ink) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 15.sp, color = RokkhonPalette.Ink)
    }
}

/** A patient waiting for blood, as a compact card in the horizontal row. */
@Composable
internal fun UrgentRequestCard(req: BloodRequest, language: AppLanguage, onClick: () -> Unit) {
    val bn = language == AppLanguage.BENGALI
    val critical = req.urgencyLevel == "CRITICAL"
    Column(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RokkhonPalette.Subtle)
            .springClick(pressedScale = 0.97f, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(req.bloodGroup, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (critical) {
                Text(
                    text = if (bn) "জরুরি" else "Urgent",
                    color = RokkhonPalette.Sos,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = req.patientName.ifBlank { req.requesterName },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = RokkhonPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = req.hospitalName,
            fontSize = 13.5.sp,
            color = RokkhonPalette.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (bn) "${bnDigits(req.unitsNeeded.toInt())} ব্যাগ · ${req.neededDateTime}" else "${req.unitsNeeded} bag(s) · ${req.neededDateTime}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RokkhonPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
