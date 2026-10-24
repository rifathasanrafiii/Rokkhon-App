package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.ble.BleSosMeshManager
import com.example.data.model.AppLanguage
import com.example.data.model.BD_NATIONAL_EMERGENCY_NUMBER
import com.example.data.model.SosAlert
import com.example.data.model.TrustedContact
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.IconTile
import com.example.ui.components.InkText
import com.example.ui.components.MutedText
import com.example.ui.components.RkCard
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SectionHeader
import com.example.ui.components.SosDetailsDialog
import com.example.ui.components.SosPostCardItem
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/** Bangladesh helplines shown on the SOS screen (number, Bangla label, English label). */
private val BD_HELPLINES = listOf(
    Triple(BD_NATIONAL_EMERGENCY_NUMBER, "জাতীয় জরুরি সেবা (পুলিশ, ফায়ার, অ্যাম্বুলেন্স)", "National emergency (police, fire, ambulance)"),
    Triple("109", "নারী ও শিশু নির্যাতন প্রতিরোধ", "Violence against women & children"),
    Triple("16263", "স্বাস্থ্য বাতায়ন (ডাক্তারের পরামর্শ)", "Shastho Batayon (doctor on call)"),
    Triple("1098", "শিশু সহায়তা", "Child helpline")
)

/**
 * SOS: one big button, quick actions (999 / SMS family / share location), the background safety
 * network, trusted contacts, helplines and your own SOS history. Other people's SOS live on the Map.
 */
@Composable
fun SosGuardianAndBleScreen(
    viewModel: RokkhonViewModel,
    recentPowerTaps: Int = 0,
    onSimulatePowerTap: () -> Unit = {},
    onOpenSosVerification: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI
    val sosState by viewModel.sosAlertsState.collectAsStateWithLifecycle()
    val isAdvertising by viewModel.bleMeshManager.isAdvertising.collectAsStateWithLifecycle()
    val isScanning by viewModel.bleMeshManager.isScanning.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()
    val relayedCount by viewModel.bleRelayedCount.collectAsStateWithLifecycle()
    val guardEnabled by viewModel.guardNetworkEnabled.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val activeUserSosId by viewModel.activeUserSosAlertId.collectAsStateWithLifecycle()
    val activePlaybackAlertId by viewModel.audioVerifier.activePlaybackAlertId.collectAsStateWithLifecycle()
    val playbackSeconds by viewModel.audioVerifier.playbackProgressSeconds.collectAsStateWithLifecycle()
    val falseSosWarningBanner by viewModel.falseSosWarningBanner.collectAsStateWithLifecycle()
    val falseSosPenaltyCount by viewModel.falseSosPenaltyCount.collectAsStateWithLifecycle()
    val trustedContacts by viewModel.trustedContacts.collectAsStateWithLifecycle()
    val userLat by viewModel.userLatitude.collectAsStateWithLifecycle()
    val userLng by viewModel.userLongitude.collectAsStateWithLifecycle()

    var detailsSosId by remember { mutableStateOf<String?>(null) }
    var showContactsEditor by remember { mutableStateOf(false) }

    val allAlerts = (sosState as? UiState.Success)?.data.orEmpty()
    val mySosAlerts = allAlerts.filter { it.userId == viewModel.currentUserId }
    val myActiveSos = mySosAlerts.firstOrNull { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
    val myHistory = mySosAlerts.filter { it.id != myActiveSos?.id }
    val isLive = myActiveSos != null || isLiveLocationActive
    val nearbySosSignals = bleBeacons.count { it.packetKind == BleSosMeshManager.PACKET_KIND_SOS_1KM }

    // Right after an SOS goes out, open the SMS to family once (the user only has to press send).
    LaunchedEffect(myActiveSos != null) {
        val created = myActiveSos?.createdAt?.toDate()?.time ?: return@LaunchedEffect
        if (trustedContacts.isNotEmpty() && System.currentTimeMillis() - created < 60_000L &&
            viewModel.shouldPromptTrustedSms()
        ) {
            RokkhonIntents.smsTrustedContacts(context, trustedContacts, viewModel.currentUserName, userLat, userLng, language)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Rk.Bg)
            .testTag("sos_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Brush.verticalGradient(listOf(Rk.Maroon, Rk.MaroonDeep)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = Rk.Gutter, end = Rk.Gutter, top = 18.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isLive) (if (bn) "আপনার SOS চালু আছে" else "Your SOS is live") else (if (bn) "বিপদে আছেন?" else "In danger?"),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLive) {
                        if (bn) "১ কি.মি.-এর রক্ষণ ইউজাররা জেনেছেন, লাইভ লোকেশন শেয়ার হচ্ছে।" else "Rokkhon users within 1 km know, and your live location is shared."
                    } else {
                        if (bn) "চাপ দিলে ৫ সেকেন্ড পর ১ কি.মি.-এর সব রক্ষণ ইউজার জানবেন — ইন্টারনেট না থাকলেও ব্লুটুথে।" else "Press it and every Rokkhon user within 1 km is alerted after 5 s — over Bluetooth even without internet."
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                if (isLive) {
                    Button(
                        onClick = { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") },
                        colors = ButtonDefaults.buttonColors(containerColor = Rk.Green),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("turn_off_my_sos_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    BigSosButton(label = if (bn) "SOS" else "SOS", hint = if (bn) "চাপুন" else "Press", onClick = onOpenSosVerification)
                }
                Spacer(modifier = Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroAction(
                        label = if (bn) "৯৯৯ কল" else "Call 999",
                        icon = Icons.Default.Call,
                        onClick = { RokkhonIntents.dial(context, BD_NATIONAL_EMERGENCY_NUMBER, language) },
                        tag = "call_999_button",
                        modifier = Modifier.weight(1f)
                    )
                    HeroAction(
                        label = if (bn) "পরিবারকে SMS" else "SMS family",
                        icon = Icons.Default.Sms,
                        onClick = {
                            if (trustedContacts.isEmpty()) {
                                showContactsEditor = true
                            } else {
                                RokkhonIntents.smsTrustedContacts(context, trustedContacts, viewModel.currentUserName, userLat, userLng, language)
                            }
                        },
                        tag = "sms_trusted_contacts_button",
                        modifier = Modifier.weight(1f)
                    )
                    HeroAction(
                        label = if (bn) "লোকেশন" else "Location",
                        icon = Icons.Default.Share,
                        onClick = { RokkhonIntents.shareLocation(context, viewModel.currentUserName, userLat, userLng, language) },
                        tag = "share_location_button",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (BuildConfig.DEBUG && !isLive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { onSimulatePowerTap() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("simulate_3x_power_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFF8FA3), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = (if (bn) "টেস্ট: ৩x পাওয়ার চাপ " else "Test: 3x power press ") + "($recentPowerTaps/3)",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (falseSosPenaltyCount > 0 || falseSosWarningBanner != null) {
            item(key = "false_banner") {
                FalseSosAccountWarningBanner(
                    language = language,
                    warningMessage = falseSosWarningBanner,
                    penaltyCount = falseSosPenaltyCount,
                    onDismiss = if (falseSosWarningBanner != null) {
                        { viewModel.dismissFalseSosWarningBanner() }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(horizontal = Rk.Gutter)
                )
            }
        }

        if (myActiveSos != null) {
            item(key = "active_sos") {
                Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
                    SosPostCardItem(
                        alert = myActiveSos,
                        language = language,
                        isMySos = true,
                        isHistoryItem = false,
                        onOpenDetails = { detailsSosId = myActiveSos.id },
                        onRespond = {},
                        onDirections = {},
                        onReportFalseAlarm = {},
                        onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(myActiveSos.id) }
                    )
                }
            }
        }

        item(key = "network") {
            val blockReason = viewModel.bleMeshManager.blockReason()
            RkCard(modifier = Modifier.padding(horizontal = Rk.Gutter).testTag("safety_network_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        Icons.AutoMirrored.Filled.BluetoothSearching,
                        tint = if (guardEnabled && blockReason == null) Rk.Blue else Rk.Amber,
                        background = if (guardEnabled && blockReason == null) Rk.BlueSoft else Rk.AmberSoft
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (bn) "সুরক্ষা নেটওয়ার্ক" else "Safety network", color = Rk.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = when {
                                blockReason == BleSosMeshManager.BlockReason.NO_PERMISSION ->
                                    if (bn) "ব্লুটুথ পারমিশন দিন (সেটিংস → অ্যাপ → রক্ষণ)" else "Allow Bluetooth (Settings → Apps → Rokkhon)"
                                blockReason == BleSosMeshManager.BlockReason.BLUETOOTH_OFF ->
                                    if (bn) "ব্লুটুথ বন্ধ, চালু করুন" else "Bluetooth is off, turn it on"
                                blockReason == BleSosMeshManager.BlockReason.NO_HARDWARE ->
                                    if (bn) "এই ফোনে ব্লুটুথ নেই, শুধু ইন্টারনেট" else "No Bluetooth on this phone, internet only"
                                isAdvertising -> if (bn) "আপনার SOS ব্লুটুথে যাচ্ছে" else "Your SOS is going out over Bluetooth"
                                guardEnabled -> if (bn) "চালু • অ্যাপ বন্ধ থাকলেও কাজ করে" else "On • works even when the app is closed"
                                isScanning -> if (bn) "শুধু অ্যাপ খোলা থাকলে কাজ করছে" else "Working only while the app is open"
                                else -> if (bn) "বন্ধ" else "Off"
                            },
                            color = Rk.Muted,
                            fontSize = 12.5.sp
                        )
                    }
                    Switch(
                        checked = guardEnabled,
                        onCheckedChange = { viewModel.setGuardNetworkEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Rk.Green),
                        modifier = Modifier.testTag("guard_network_switch")
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                MeshExplainer(bn)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        value = "$nearbySosSignals",
                        label = if (bn) "কাছের ব্লুটুথ SOS" else "Bluetooth SOS nearby",
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        value = "$relayedCount",
                        label = if (bn) "আপনার ফোন পৌঁছে দিয়েছে" else "Relayed by your phone",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                            viewModel.selectTab(RokkhonTab.RADAR_MAP)
                        }
                        .padding(vertical = 10.dp)
                        .testTag("open_find_sos_tab_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (bn) "ম্যাপে কাছের SOS দেখুন" else "See nearby SOS on the map", color = Rk.Crimson, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Rk.Crimson)
                }
            }
        }

        item(key = "contacts") {
            TrustedContactsCard(
                language = language,
                contacts = trustedContacts,
                onEdit = { showContactsEditor = true },
                modifier = Modifier.padding(horizontal = Rk.Gutter)
            )
        }

        item(key = "helplines") {
            RkCard(modifier = Modifier.padding(horizontal = Rk.Gutter).testTag("helplines_card"), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                Text(
                    if (bn) "জরুরি হেল্পলাইন" else "Emergency helplines",
                    color = Rk.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                BD_HELPLINES.forEachIndexed { index, (number, labelBn, labelEn) ->
                    if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = Rk.Line, modifier = Modifier.padding(start = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { RokkhonIntents.dial(context, number, language) }
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                            .testTag("helpline_$number"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(number, color = Rk.Crimson, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(70.dp))
                        Text(if (bn) labelBn else labelEn, color = Rk.InkSoft, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Call, contentDescription = if (bn) "কল" else "Call", tint = Rk.Green, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item(key = "history_header") {
            SectionHeader(
                title = if (bn) "আমার আগের SOS (${myHistory.size})" else "My past SOS (${myHistory.size})",
                modifier = Modifier.padding(horizontal = Rk.Gutter)
            )
        }
        if (myHistory.isEmpty()) {
            item(key = "history_empty") {
                EmptyStateCard(
                    text = if (bn) "আপনি এখনো কোনো SOS পাঠাননি।" else "You haven't sent any SOS yet.",
                    modifier = Modifier.padding(horizontal = Rk.Gutter)
                )
            }
        }
        items(myHistory, key = { "my_history_sos_${it.id}" }) { alert ->
            Box(modifier = Modifier.padding(horizontal = Rk.Gutter)) {
                SosPostCardItem(
                    alert = alert,
                    language = language,
                    isMySos = true,
                    isHistoryItem = true,
                    onOpenDetails = { detailsSosId = alert.id },
                    onRespond = {},
                    onDirections = {},
                    onReportFalseAlarm = {},
                    onMarkSafeOwnerOnly = {}
                )
            }
        }
        item(key = "bottom") { Spacer(modifier = Modifier.height(16.dp)) }
    }

    val detailsAlert: SosAlert? = detailsSosId?.let { id -> allAlerts.firstOrNull { it.id == id } }
    if (detailsAlert != null) {
        SosDetailsDialog(
            language = language,
            alert = detailsAlert,
            isOwner = true,
            hasLocalClip = viewModel.audioVerifier.hasLocalClip(detailsAlert.id),
            isPlaying = activePlaybackAlertId == detailsAlert.id,
            progressSeconds = if (activePlaybackAlertId == detailsAlert.id) playbackSeconds else 0f,
            hasResponded = false,
            distanceKm = null,
            onTogglePlay = { viewModel.audioVerifier.togglePlayFiveSecondClip(scope, detailsAlert.id) },
            onRespond = {},
            onDirections = {},
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

    if (showContactsEditor) {
        TrustedContactsEditorDialog(
            language = language,
            initial = trustedContacts,
            onSave = { if (viewModel.saveTrustedContacts(it)) showContactsEditor = false },
            onDismiss = { showContactsEditor = false }
        )
    }
}

/** Large round SOS button with a soft pulsing ring. */
@Composable
private fun BigSosButton(label: String, hint: String, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "sos_pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ring"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(208.dp)) {
        Box(
            modifier = Modifier
                .size(184.dp)
                .scale(ring)
                .clip(CircleShape)
                .background(Rk.Crimson.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .size(164.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFE5323F), Rk.Crimson, Rk.CrimsonDark)))
                .border(4.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(role = Role.Button) { onClick() }
                .testTag("sos_guardian_main_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(hint, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Three-step picture of how an SOS hops phone to phone up to 1 km. */
@Composable
private fun MeshExplainer(bn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Rk.SurfaceMuted)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeshDot(color = Rk.Crimson, label = if (bn) "আপনি" else "You")
        MeshLink()
        MeshDot(color = Rk.Blue, label = if (bn) "রক্ষণ ফোন" else "Rokkhon phone")
        MeshLink()
        MeshDot(color = Rk.Blue, label = if (bn) "আরও ফোন" else "More phones")
        MeshLink()
        MeshDot(color = Rk.Green, label = if (bn) "১ কি.মি." else "1 km")
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (bn) {
            "প্রতিটি রক্ষণ ফোন ব্লুটুথে SOS-টা পাশের ফোনে পৌঁছে দেয়। যাদের ফোন ১ কি.মি.-এর মধ্যে, তারা সবাই নোটিফিকেশন পান — ইন্টারনেট ছাড়াই।"
        } else {
            "Each Rokkhon phone passes the SOS to the next one over Bluetooth. Everyone within 1 km gets a notification — no internet needed."
        },
        color = Rk.InkSoft,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MeshDot(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Rk.Muted, fontSize = 10.5.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MeshLink() {
    Box(modifier = Modifier.width(14.dp).height(2.dp).background(Rk.Faint))
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Rk.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, color = Rk.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Rk.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun HeroAction(label: String, icon: ImageVector, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onClick() }
            .padding(vertical = 12.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TrustedContactsCard(
    language: AppLanguage,
    contacts: List<TrustedContact>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    RkCard(modifier = modifier.testTag("trusted_contacts_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Group, tint = Rk.Crimson, background = Rk.Rose)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (bn) "বিশ্বস্ত কন্টাক্ট" else "Trusted contacts", color = Rk.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = if (bn) "SOS দিলে এদের কাছে লোকেশনসহ SMS যাবে" else "They get an SMS with your location when you send SOS",
                    color = Rk.Muted,
                    fontSize = 12.5.sp
                )
            }
            Text(
                text = if (contacts.isEmpty()) (if (bn) "যোগ করুন" else "Add") else (if (bn) "এডিট" else "Edit"),
                color = Rk.Crimson,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Rk.Rose)
                    .clickable(role = Role.Button) { onEdit() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("edit_trusted_contacts")
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (contacts.isEmpty()) {
            Text(
                text = if (bn) "এখনো কাউকে যোগ করা হয়নি। পরিবারের ১-৩ জনকে যোগ করুন।" else "No one added yet. Add 1-3 family members.",
                color = Rk.Amber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            contacts.forEach { c ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Rk.Rose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(c.name.firstOrNull()?.uppercase() ?: "?", color = Rk.Crimson, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(c.name.ifBlank { c.phone }, color = Rk.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(c.phone, color = Rk.Muted, fontSize = 13.sp)
                }
            }
        }
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
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(if (bn) "বিশ্বস্ত কন্টাক্ট (সর্বোচ্চ ৩)" else "Trusted contacts (up to 3)", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = InkText)
                Text(
                    text = if (bn) "এই নম্বরগুলো শুধু আপনার ফোনে থাকে, কোথাও আপলোড হয় না।" else "Stored only on this phone, never uploaded.",
                    color = MutedText,
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
                        Text(if (bn) "বাতিল" else "Cancel", color = InkText)
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
