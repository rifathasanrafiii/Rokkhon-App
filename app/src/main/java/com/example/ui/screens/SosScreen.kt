package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
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
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import com.example.data.model.BD_NATIONAL_EMERGENCY_NUMBER
import com.example.data.model.SosAlert
import com.example.data.model.TrustedContact
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FalseSosAccountWarningBanner
import com.example.ui.components.InkText
import com.example.ui.components.MutedText
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SosDetailsDialog
import com.example.ui.components.SosPostCardItem
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
import com.example.ui.viewmodel.MapLayerFilter
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

/**
 * SOS: send / stop your own SOS, call 999, SMS trusted contacts, Bluetooth status and your SOS history.
 * Other people's SOS alerts live in Find → SOS.
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
    val bleStatus by viewModel.bleMeshManager.statusMessage.collectAsStateWithLifecycle()
    val bleBeacons by viewModel.bleBeacons.collectAsStateWithLifecycle()
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
            .background(Color(0xFFFAF8F8))
            .testTag("sos_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)))
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
                ) {
                    Text(
                        text = if (isLive) {
                            if (bn) "আপনার SOS চালু আছে" else "Your SOS is live"
                        } else {
                            if (bn) "বিপদে আছেন?" else "In danger?"
                        },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isLive) {
                            if (bn) {
                                "১ কি.মি.-এর মধ্যে রক্ষণ ইউজাররা জানেন এবং আপনার লাইভ লোকেশন শেয়ার হচ্ছে। নিরাপদ হলে বন্ধ করুন।"
                            } else {
                                "Rokkhon users within 1 km are alerted and your live location is being shared. Turn it off when you're safe."
                            }
                        } else if (bn) {
                            "SOS চাপলে ৫ সেকেন্ড পর কাছের রক্ষণ ইউজাররা জানবেন। অ্যাপ খোলা থাকলে পাওয়ার/ভলিউম বাটন ৩ বার চাপলেও হবে।"
                        } else {
                            "Press SOS and nearby Rokkhon users are alerted after 5 seconds. With the app open, pressing power/volume 3x works too."
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLive) {
                        Button(
                            onClick = { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") },
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("turn_off_my_sos_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (bn) "আমি নিরাপদ • SOS বন্ধ করুন" else "I'm safe • Turn off SOS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    } else {
                        Button(
                            onClick = onOpenSosVerification,
                            colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("sos_guardian_main_button")
                        ) {
                            Text(if (bn) "SOS পাঠান" else "Send SOS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
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
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = CircleShape,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSimulatePowerTap() }
                                .testTag("simulate_3x_power_button")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFF8FA3), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = (if (bn) "টেস্ট: ৩x পাওয়ার চাপ " else "Test: 3x power press ") + "($recentPowerTaps/3)",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(Color(0xFFFAF8F8))
                )
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
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (myActiveSos != null) {
            item(key = "active_sos") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

        item(key = "contacts") {
            TrustedContactsCard(
                language = language,
                contacts = trustedContacts,
                onEdit = { showContactsEditor = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "ble") {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bleOk = isScanning || isAdvertising
                        Icon(
                            imageVector = if (bleOk) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (bleOk) IosSystemBlue else IosSystemOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (bn) "অফলাইন ব্লুটুথ (BLE)" else "Offline Bluetooth (BLE)",
                                color = InkText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            val blockReason = viewModel.bleMeshManager.blockReason()
                            Text(
                                text = when {
                                    isAdvertising -> if (bn) "আপনার SOS ব্লুটুথে সম্প্রচার হচ্ছে" else "Broadcasting your SOS over Bluetooth"
                                    isScanning -> if (bn) "চালু • কাছের SOS ও রক্তের অ্যালার্ট শুনছে" else "On • listening for nearby SOS and blood alerts"
                                    blockReason == com.example.data.ble.BleSosMeshManager.BlockReason.NO_PERMISSION ->
                                        if (bn) "ব্লুটুথ পারমিশন দিন (সেটিংস → অ্যাপ → রক্ষণ)" else "Allow Bluetooth permission (Settings → Apps → Rokkhon)"
                                    blockReason == com.example.data.ble.BleSosMeshManager.BlockReason.BLUETOOTH_OFF ->
                                        if (bn) "ব্লুটুথ বন্ধ আছে, চালু করুন" else "Bluetooth is off, turn it on"
                                    blockReason == com.example.data.ble.BleSosMeshManager.BlockReason.NO_HARDWARE ->
                                        if (bn) "এই ফোনে ব্লুটুথ নেই" else "This phone has no Bluetooth"
                                    else -> bleStatus
                                },
                                color = MutedText,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (bn) {
                            "ইন্টারনেট না থাকলেও ৫০-১০০ মিটারের মধ্যে থাকা রক্ষণ ইউজারদের ফোনে আপনার SOS পৌঁছায়। কাছাকাছি সংকেত: ${bleBeacons.size}টি।"
                        } else {
                            "Without internet, your SOS still reaches Rokkhon phones within ~50-100 m. Nearby signals: ${bleBeacons.size}."
                        },
                        color = Color(0xFF4A3A3D),
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = LifelineBlush,
                        shape = CircleShape,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                                viewModel.selectTab(RokkhonTab.RADAR_MAP)
                            }
                            .testTag("open_find_sos_tab_button")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (bn) "অন্যদের SOS দেখুন" else "See others' SOS", color = IosSystemRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        item(key = "history_header") {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (bn) "আমার আগের SOS (${myHistory.size})" else "My past SOS (${myHistory.size})",
                    color = InkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
        if (myHistory.isEmpty()) {
            item(key = "history_empty") {
                EmptyStateCard(
                    text = if (bn) "আপনি এখনো কোনো SOS পাঠাননি।" else "You haven't sent any SOS yet.",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        items(myHistory, key = { "my_history_sos_${it.id}" }) { alert ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

@Composable
private fun HeroAction(label: String, icon: ImageVector, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
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
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
            .testTag("trusted_contacts_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (bn) "বিশ্বস্ত কন্টাক্ট" else "Trusted contacts", color = InkText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (bn) "SOS পাঠালে এদের লোকেশন লিংকসহ SMS যাবে" else "They get an SMS with your location when you send SOS",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = LifelineBlush,
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onEdit() }
                        .testTag("edit_trusted_contacts")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (contacts.isEmpty()) (if (bn) "যোগ করুন" else "Add") else (if (bn) "এডিট" else "Edit"), color = IosSystemRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (contacts.isEmpty()) {
                Text(
                    text = if (bn) "এখনো কাউকে যোগ করা হয়নি। পরিবারের ১-৩ জনকে যোগ করুন।" else "No one added yet. Add 1-3 family members.",
                    color = IosSystemOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                contacts.forEach { c ->
                    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF5DDE0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.name.firstOrNull()?.uppercase() ?: "?", color = IosSystemRed, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(c.name.ifBlank { c.phone }, color = InkText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(c.phone, color = MutedText, fontSize = 13.sp)
                    }
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
