package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ble.BleSosMeshManager
import com.example.data.model.AppLanguage
import com.example.data.model.BD_NATIONAL_EMERGENCY_NUMBER
import com.example.data.model.SosAlert
import com.example.data.model.TrustedContact
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.QuickAction
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonEmptyState
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.RollingNumber
import com.example.ui.components.SectionHeader
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.SosVideoAudioInspectorDialog
import com.example.ui.components.StatTile
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

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
    val isScanning by viewModel.bleMeshManager.isScanning.collectAsStateWithLifecycle()
    val relayedCount by viewModel.bleRelayedCount.collectAsStateWithLifecycle()
    val guardEnabled by viewModel.guardNetworkEnabled.collectAsStateWithLifecycle()
    val trustedContacts by viewModel.trustedContacts.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()
    val userBearing by viewModel.userBearing.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedSosForMediaModal by remember { mutableStateOf<SosAlert?>(null) }
    var showContactsEditor by remember { mutableStateOf(false) }

    val allAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    // ONLY this user's own SOS alerts (Active + Past History)
    val mySosAlerts = allAlerts.filter { it.userId == viewModel.currentUserId }
    val myActiveSos = mySosAlerts.firstOrNull {
        it.status == "ACTIVE" && !it.isExpiredAfter24Hours()
    }
    val myHistorySosList = mySosAlerts.filter { it.id != myActiveSos?.id }

    // Right after an SOS goes out, open the SMS to family once (the user only has to press send).
    LaunchedEffect(myActiveSos != null) {
        val created = myActiveSos?.createdAt?.toDate()?.time ?: return@LaunchedEffect
        if (trustedContacts.isNotEmpty() && System.currentTimeMillis() - created < 60_000L &&
            viewModel.shouldPromptTrustedSms()
        ) {
            RokkhonIntents.smsTrustedContacts(context, trustedContacts, viewModel.currentUserName, userLat, userLng, language)
        }
    }

    val bn = language == AppLanguage.BENGALI
    val sosLive = myActiveSos != null || isLiveLocationActive
    val dirText = compassDirectionLabel(userBearing, bn)
    val block = viewModel.bleMeshManager.blockReason()
    val networkStatus = when {
        block == BleSosMeshManager.BlockReason.NO_PERMISSION -> if (bn) "ব্লুটুথ পারমিশন দিন" else "Permission needed"
        block == BleSosMeshManager.BlockReason.BLUETOOTH_OFF -> if (bn) "ব্লুটুথ বন্ধ আছে" else "Bluetooth is off"
        block == BleSosMeshManager.BlockReason.NO_HARDWARE -> if (bn) "ব্লুটুথ অনুপলব্ধ" else "No Bluetooth hardware"
        isAdvertising -> if (bn) "আপনার SOS সম্প্রচার হচ্ছে" else "Broadcasting your SOS"
        isScanning -> if (bn) "সক্রিয় • রিলে $relayedCount" else "Active • relayed $relayedCount"
        else -> bleStatus
    }
    val networkOk = isScanning && block == null
    var showNetworkSheet by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showPolicySheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("sos_screen")
    ) {
        item(key = "sos_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (bn) "নিরাপত্তা" else "Safety",
                        color = RokkhonPalette.Ink,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(RokkhonPalette.Subtle)
                            .springClick {
                                viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                                viewModel.selectTab(RokkhonTab.RADAR_MAP)
                            }
                            .testTag("open_find_sos_tab_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(UIcons.Map, contentDescription = if (bn) "ম্যাপে সবার SOS" else "All SOS on map", tint = RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(ok = guardEnabled && networkOk)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = (if (guardEnabled) (if (bn) "১ কি.মি. নেটওয়ার্ক চালু" else "1 km network on") else (if (bn) "নেটওয়ার্ক বন্ধ" else "Network off")) + "  ·  " + dirText,
                        color = RokkhonPalette.Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.springClick(pressedScale = 0.98f) { showNetworkSheet = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // The one red thing on the page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RokkhonPalette.Sos)
                        .springClick(pressedScale = 0.97f, onClick = onOpenSosVerification)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                        .testTag("sos_guardian_main_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(UIcons.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (sosLive) (if (bn) "SOS চালু আছে" else "SOS is live") else (if (bn) "SOS পাঠান" else "Send SOS"),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (bn) "৫ সেকেন্ডের অডিও প্রমাণসহ" else "With 5-second audio proof",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                    Icon(UIcons.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }

                AnimatedVisibility(visible = sosLive) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .springClick { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") }
                            .testTag("turn_off_my_sos_button"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(UIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (bn) "আমি নিরাপদ — SOS বন্ধ করুন" else "I'm safe — stop SOS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = UIcons.Call,
                        label = if (bn) "৯৯৯" else "999",
                        onClick = { RokkhonIntents.dial(context, BD_NATIONAL_EMERGENCY_NUMBER, language) },
                        modifier = Modifier.weight(1f),
                        testTag = "call_999_button"
                    )
                    QuickAction(
                        icon = UIcons.Sms,
                        label = if (bn) "পরিবারকে SMS" else "SMS family",
                        onClick = {
                            if (trustedContacts.isEmpty()) {
                                showContactsEditor = true
                            } else {
                                RokkhonIntents.smsTrustedContacts(context, trustedContacts, viewModel.currentUserName, userLat, userLng, language)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "sms_trusted_contacts_button"
                    )
                    QuickAction(
                        icon = UIcons.Share,
                        label = if (bn) "লোকেশন" else "Location",
                        onClick = { RokkhonIntents.shareLocation(context, viewModel.currentUserName, userLat, userLng, language) },
                        modifier = Modifier.weight(1f),
                        testTag = "share_location_button"
                    )
                    QuickAction(
                        icon = UIcons.People,
                        label = if (bn) "কন্টাক্ট" else "Contacts",
                        onClick = { showContactsEditor = true },
                        modifier = Modifier.weight(1f),
                        testTag = "sos_contacts_quick_button"
                    )
                }
            }
        }

        item(key = "sos_sheet") {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            icon = UIcons.Shield,
                            value = bleBeacons.size,
                            label = if (bn) "১ কি.মি. নেটওয়ার্ক" else "1 km network",
                            accent = RokkhonPalette.Ink,
                            onClick = { showNetworkSheet = true },
                            modifier = Modifier.weight(1f).enterOnce(0),
                            testTag = "sos_network_tile",
                            trailing = {
                                Switch(
                                    checked = guardEnabled,
                                    onCheckedChange = { viewModel.setGuardNetworkEnabled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Black),
                                    modifier = Modifier
                                        .scale(0.78f)
                                        .height(30.dp)
                                        .testTag("guard_network_switch")
                                )
                            }
                        )
                        StatTile(
                            icon = UIcons.People,
                            value = trustedContacts.size,
                            valueSuffix = "/3",
                            label = if (bn) "বিশ্বস্ত কন্টাক্ট" else "Trusted contacts",
                            accent = RokkhonPalette.Primary,
                            onClick = { showContactsEditor = true },
                            modifier = Modifier.weight(1f).enterOnce(1),
                            testTag = "trusted_contacts_card"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            icon = UIcons.MyLocation,
                            value = 0,
                            valueText = dirText,
                            label = if (sosLive) (if (bn) "লাইভ শেয়ার হচ্ছে" else "Sharing live") else (if (bn) "লাইভ লোকেশন" else "Live location"),
                            accent = if (sosLive) RokkhonPalette.Sos else RokkhonPalette.Ink,
                            onClick = { showLocationSheet = true },
                            modifier = Modifier.weight(1f).enterOnce(2),
                            testTag = "sos_location_tile"
                        )
                        StatTile(
                            icon = UIcons.Gavel,
                            value = falseSosPenaltyCount,
                            label = if (bn) "ভুয়া রিপোর্ট নীতি" else "False report policy",
                            accent = RokkhonPalette.Ink,
                            onClick = { showPolicySheet = true },
                            modifier = Modifier.weight(1f).enterOnce(3),
                            testTag = "sos_policy_tile"
                        )
                    }

                    if (falseSosWarningBanner != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FalseSosAccountWarningBanner(
                            language = language,
                            warningMessage = falseSosWarningBanner,
                            penaltyCount = falseSosPenaltyCount,
                            onDismiss = { viewModel.dismissFalseSosWarningBanner() }
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                }
            }
        }

        if (myActiveSos != null) {
            item(key = "my_active_sos_header") {
                SectionHeader(
                    title = if (bn) "আপনার সক্রিয় SOS" else "Your active SOS",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
            }
            item(key = "my_active_sos") {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
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
                        onCardTapOpenVideoAudio = { selectedSosForMediaModal = myActiveSos },
                        onToggleInlinePlay = { viewModel.audioVerifier.togglePlayFiveSecondClip(scope = scope, alertId = myActiveSos.id) },
                        onRespond = {},
                        onReportFalseAlarm = {},
                        onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(myActiveSos.id) }
                    )
                }
            }
        }

        item(key = "history_header") {
            SectionHeader(
                title = if (bn) "আমার SOS (${myHistorySosList.size})" else "My SOS (${myHistorySosList.size})",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }

        if (myHistorySosList.isEmpty()) {
            item(key = "history_empty") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    RokkhonEmptyState(
                        icon = UIcons.History,
                        title = if (bn) "এখনো কোনো SOS পাঠাননি" else "No SOS sent yet",
                        body = if (bn) "আপনার পাঠানো সব SOS এখানে থাকবে।" else "Every SOS you send is kept here.",
                        testTag = "sos_empty_history"
                    )
                }
            }
        } else {
            items(myHistorySosList, key = { "my_history_sos_${it.id}" }) { alert ->
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).animateItem()) {
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
                        onCardTapOpenVideoAudio = { selectedSosForMediaModal = alert },
                        onToggleInlinePlay = { viewModel.audioVerifier.togglePlayFiveSecondClip(scope = scope, alertId = alert.id) },
                        onRespond = {},
                        onReportFalseAlarm = {},
                        onMarkSafeOwnerOnly = {}
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showNetworkSheet) {
        RokkhonBottomSheet(
            onDismiss = { showNetworkSheet = false },
            title = if (bn) "১ কি.মি. সুরক্ষা নেটওয়ার্ক" else "1 km safety network",
            testTag = "sos_network_sheet"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape)
                        .background(if (guardEnabled && networkOk) IosSystemGreen else RokkhonPalette.Primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (if (isLiveLocationActive) (if (bn) "লাইভ জিপিএস চালু • " else "Live GPS on • ") else "") + networkStatus,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RokkhonPalette.Ink,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = guardEnabled,
                    onCheckedChange = { viewModel.setGuardNetworkEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Black)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetStat(value = bleBeacons.size, label = if (bn) "কাছের সংকেত" else "Nearby signals", modifier = Modifier.weight(1f))
                SheetStat(value = relayedCount, label = if (bn) "রিলে করা" else "Relayed", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (bn) {
                    "অ্যাপ বন্ধ থাকলেও ১ কি.মি.-এর মধ্যে কেউ SOS দিলে আপনি তাৎক্ষণিক অ্যালার্ট পাবেন, আর আপনার ফোন সংকেতটি সামনে পৌঁছে দেবে — ইন্টারনেট ছাড়াই।"
                } else {
                    "Get an instant alert when someone within 1 km sends an SOS, even with the app closed. Your phone also relays it onward, no internet needed."
                },
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = RokkhonPalette.Muted
            )
        }
    }

    if (showLocationSheet) {
        RokkhonBottomSheet(
            onDismiss = { showLocationSheet = false },
            title = if (sosLive) (if (bn) "লাইভ SOS লোকেশন" else "Live SOS location") else (if (bn) "আপনার লোকেশন" else "Your location"),
            testTag = "sos_location_sheet"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetStatText(value = dirText, label = if (bn) "দিক" else "Heading", modifier = Modifier.weight(1f))
                SheetStatText(value = "%.4f, %.4f".format(java.util.Locale.US, userLat, userLng), label = if (bn) "স্থানাঙ্ক" else "Coordinates", modifier = Modifier.weight(1.6f))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetAction(UIcons.Refresh, if (bn) "রিফ্রেশ" else "Refresh", Modifier.weight(1f)) { viewModel.refreshGpsLocation() }
                SheetAction(UIcons.Share, if (bn) "শেয়ার" else "Share", Modifier.weight(1f)) {
                    RokkhonIntents.shareLocation(context, viewModel.currentUserName, userLat, userLng, language)
                }
            }
        }
    }

    if (showPolicySheet) {
        RokkhonBottomSheet(
            onDismiss = { showPolicySheet = false },
            testTag = "sos_policy_sheet"
        ) {
            FalseSosAccountWarningBanner(
                language = language,
                warningMessage = falseSosWarningBanner,
                penaltyCount = falseSosPenaltyCount,
                onDismiss = null
            )
        }
    }

    if (showContactsEditor) {
        TrustedContactsEditorDialog(
            language = language,
            initial = trustedContacts,
            onSave = { if (viewModel.saveTrustedContacts(it)) showContactsEditor = false },
            onDismiss = { showContactsEditor = false }
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

@Composable
private fun TrustedContactsEditorDialog(
    language: AppLanguage,
    initial: List<TrustedContact>,
    onSave: (List<TrustedContact>) -> Unit,
    onDismiss: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val names = remember { mutableStateListOf(*Array(RokkhonViewModel.MAX_TRUSTED_CONTACTS) { initial.getOrNull(it)?.name ?: "" }) }
    val phones = remember { mutableStateListOf(*Array(RokkhonViewModel.MAX_TRUSTED_CONTACTS) { initial.getOrNull(it)?.phone ?: "" }) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(if (bn) "বিশ্বস্ত কন্টাক্ট (সর্বোচ্চ ৩)" else "Trusted contacts (up to 3)", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF000000))
                Text(
                    text = if (bn) "এই নম্বরগুলো শুধু আপনার ফোনে থাকে, কোথাও আপলোড হয় না।" else "Stored only on this phone, never uploaded.",
                    color = Color(0xFF5E5E5E),
                    fontSize = 12.5.sp
                )
                for (i in 0 until RokkhonViewModel.MAX_TRUSTED_CONTACTS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = names[i],
                        onValueChange = { names[i] = it.take(40) },
                        label = { Text(if (bn) "নাম ${i + 1}" else "Name ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trusted_name_$i")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = phones[i],
                        onValueChange = { phones[i] = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                        label = { Text(if (bn) "মোবাইল নম্বর ${i + 1}" else "Mobile ${i + 1}") },
                        placeholder = { Text("01XXXXXXXXX") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trusted_phone_$i")
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, shape = CircleShape, modifier = Modifier.weight(1f)) {
                        Text(if (bn) "বাতিল" else "Cancel", color = Color(0xFF000000))
                    }
                    Button(
                        onClick = {
                            onSave((0 until RokkhonViewModel.MAX_TRUSTED_CONTACTS).map { TrustedContact(names[it], phones[it]) })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_trusted_contacts")
                    ) {
                        Text(if (bn) "সেভ করুন" else "Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (ok) RokkhonPalette.Positive else RokkhonPalette.Faint)
    )
}

@Composable
private fun SheetStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(RokkhonPalette.Subtle)
            .padding(14.dp)
    ) {
        RollingNumber(value = value, fontSize = 24.sp, color = RokkhonPalette.Ink)
        Text(label, fontSize = 12.sp, color = RokkhonPalette.Muted)
    }
}

@Composable
private fun SheetStatText(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(RokkhonPalette.Subtle)
            .padding(14.dp)
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RokkhonPalette.Ink, maxLines = 1)
        Text(label, fontSize = 12.sp, color = RokkhonPalette.Muted)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(RokkhonPalette.Subtle)
            .springClick(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RokkhonPalette.Primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = RokkhonPalette.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** "উত্তর-পূর্ব 45°" / "NE 45°" from a compass bearing. */
private fun compassDirectionLabel(bearingDegrees: Float, bengali: Boolean): String {
    val deg = ((bearingDegrees % 360f) + 360f) % 360f
    val sector = ((deg + 22.5f) / 45f).toInt() % 8
    val dir = if (bengali) {
        when (sector) {
            0 -> "উত্তর"
            1 -> "উত্তর-পূর্ব"
            2 -> "পূর্ব"
            3 -> "দক্ষিণ-পূর্ব"
            4 -> "দক্ষিণ"
            5 -> "দক্ষিণ-পশ্চিম"
            6 -> "পশ্চিম"
            else -> "উত্তর-পশ্চিম"
        }
    } else {
        when (sector) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            else -> "NW"
        }
    }
    return "$dir ${deg.toInt()}°"
}
