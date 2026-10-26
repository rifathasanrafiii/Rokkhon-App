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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.InteractiveBangladeshRadarMap
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SosPostCardItem
import com.example.ui.components.SosVideoAudioInspectorDialog
import com.example.ui.components.compassDirectionLabel
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarObsidianBg
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarObsidianBg)
    ) {
        // 1. Pure Gradient SOS Command Header (No 3x Power Test button, clean full-width SOS CTA)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8B1124),
                            Color(0xFF4B0A15),
                            RadarObsidianBg
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFFFB3C0),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "SOS কমান্ড সেন্টার"
                            } else {
                                "SOS COMMAND CENTER"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable {
                            viewModel.setMapLayerFilter(MapLayerFilter.SOS_ALERTS_ONLY)
                            viewModel.selectTab(RokkhonTab.RADAR_MAP)
                        }
                        .testTag("open_find_sos_tab_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "ম্যাপে সবার SOS" else "All SOS on Map",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (myActiveSos != null || isLiveLocationActive) {
                    if (language == AppLanguage.BENGALI) {
                        "আপনার জরুরি SOS ও লাইভ লোকেশন চালু আছে"
                    } else {
                        "Your Emergency SOS & Live Location are ON"
                    }
                } else {
                    if (language == AppLanguage.BENGALI) {
                        "এক ট্যাপে জরুরি SOS পাঠান"
                    } else {
                        "Send Emergency SOS in One Tap"
                    }
                },
                fontSize = 23.sp,
                lineHeight = 30.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Full-Width SOS Button (3x Power Test removed from UI per user request)
            Button(
                onClick = onOpenSosVerification,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PinCrimsonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sos_guardian_main_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == AppLanguage.BENGALI) "৫-সেকেন্ড SOS পাঠান" else "Send 5s SOS",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp
                )
            }

            // Prominent Turn Off / Mark Safe Button when the current user has an active SOS
            if (myActiveSos != null || isLiveLocationActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.resolveOwnSos(myActiveSos?.id ?: activeUserSosId ?: "") },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("turn_off_my_sos_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) {
                            "আমি এখন নিরাপদ • সক্রিয় SOS বন্ধ করুন"
                        } else {
                            "I Am Safe Now • Turn Off Active SOS"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp
                    )
                }
            }
        }

        // 2. Clean, Well-Organized White Rounded Sheet
        Surface(
            color = Color(0xFFFAF8F8),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2D5D7))
                        )
                    }
                }

                // Section A: Quick Emergency Actions (999 Call, Family SMS, Share Location)
                item {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "দ্রুত জরুরি সহায়তা"
                                } else {
                                    "Quick Emergency Actions"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(
                                    Triple(if (language == AppLanguage.BENGALI) "৯৯৯ কল" else "Call 999", Icons.Default.Call, "call_999_button"),
                                    Triple(if (language == AppLanguage.BENGALI) "পরিবারকে SMS" else "SMS Family", Icons.Default.Sms, "sms_trusted_contacts_button"),
                                    Triple(if (language == AppLanguage.BENGALI) "লোকেশন শেয়ার" else "Share GPS", Icons.Default.Share, "share_location_button")
                                ).forEach { (label, icon, tag) ->
                                    Surface(
                                        color = Color(0xFFFAF5F6),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(16.dp))
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                when (tag) {
                                                    "call_999_button" -> RokkhonIntents.dial(context, BD_NATIONAL_EMERGENCY_NUMBER, language)
                                                    "sms_trusted_contacts_button" -> if (trustedContacts.isEmpty()) {
                                                        showContactsEditor = true
                                                    } else {
                                                        RokkhonIntents.smsTrustedContacts(context, trustedContacts, viewModel.currentUserName, userLat, userLng, language)
                                                    }
                                                    else -> RokkhonIntents.shareLocation(context, viewModel.currentUserName, userLat, userLng, language)
                                                }
                                            }
                                            .testTag(tag)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(LifelineBlush),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(icon, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(17.dp))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = label,
                                                color = Color(0xFF1A0F12),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section B: Live Real-Time Directional Location & Active SOS Broadcast
                item {
                    val dirText = com.example.ui.components.compassDirectionLabel(
                        userBearing,
                        language == AppLanguage.BENGALI
                    )
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isLiveLocationActive || myActiveSos != null) PinCrimsonRed.copy(alpha = 0.45f) else Color(0xFFEDE2E4),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isLiveLocationActive || myActiveSos != null) PinCrimsonRed else IosSystemGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isLiveLocationActive || myActiveSos != null) {
                                            if (language == AppLanguage.BENGALI) "লাইভ SOS লোকেশন ট্র্যাকিং চালু" else "Live SOS Location Streaming"
                                        } else {
                                            if (language == AppLanguage.BENGALI) "রিয়েল-টাইম জিপিএস ও দিকনির্দেশনা" else "Real-Time GPS & Direction"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF1A0F12),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Surface(
                                    color = LifelineBlush,
                                    shape = CircleShape,
                                    modifier = Modifier.clickable { viewModel.refreshGpsLocation() }
                                ) {
                                    Text(
                                        text = dirText,
                                        color = IosSystemRed,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "স্থানাঙ্ক: ${"%.5f".format(userLat)}, ${"%.5f".format(userLng)} • আপনি যেদিকে মুভ করবেন ম্যাপে পিন সেদিকেই যাবে"
                                } else {
                                    "Coords: ${"%.5f".format(userLat)}, ${"%.5f".format(userLng)} • Moves in real time in your exact direction"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A4A4E),
                                fontWeight = FontWeight.Medium
                            )

                            if (isLiveLocationActive || myActiveSos != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(16.dp))
                                ) {
                                    InteractiveBangladeshRadarMap(
                                        centerLat = userLat,
                                        centerLng = userLng,
                                        donors = emptyList(),
                                        bloodRequests = emptyList(),
                                        sosAlerts = listOfNotNull(myActiveSos),
                                        bleBeacons = bleBeacons,
                                        selectedBloodGroup = null,
                                        layerFilter = MapLayerFilter.SOS_ALERTS_ONLY,
                                        onPinSelected = {},
                                        showLiveFloatingCard = false,
                                        isLiveLocationActive = true,
                                        selectedAreaName = if (language == AppLanguage.BENGALI) selectedDistrict.nameBn else selectedDistrict.nameEn,
                                        userLat = userLat,
                                        userLng = userLng,
                                        userBearing = userBearing,
                                        onRefreshLiveGps = { viewModel.refreshGpsLocation() },
                                        initialUseOsmTiles = false,
                                        bengali = language == AppLanguage.BENGALI,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Active SOS Post Card (if any)
                if (myActiveSos != null) {
                    item {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "আপনার বর্তমান সক্রিয় SOS"
                            } else {
                                "Your Active SOS Broadcast"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PinCrimsonRed,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    item {
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
                            onCardTapOpenVideoAudio = {
                                selectedSosForMediaModal = myActiveSos
                            },
                            onToggleInlinePlay = {
                                viewModel.audioVerifier.togglePlayFiveSecondClip(
                                    scope = scope,
                                    alertId = myActiveSos.id
                                )
                            },
                            onRespond = {},
                            onReportFalseAlarm = {},
                            onMarkSafeOwnerOnly = { viewModel.resolveOwnSos(myActiveSos.id) }
                        )
                    }
                }

                // Section C: 1 km Safety Network & Live Radar Status Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(LifelineBlush),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "SOS Network",
                                            tint = IosSystemRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (language == AppLanguage.BENGALI) {
                                                "১ কি.মি. সুরক্ষা নেটওয়ার্ক"
                                            } else {
                                                "1 km Safety Network"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF1A0F12),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        val bn = language == AppLanguage.BENGALI
                                        val block = viewModel.bleMeshManager.blockReason()
                                        val statusLine = when {
                                            block == BleSosMeshManager.BlockReason.NO_PERMISSION -> if (bn) "ব্লুটুথ পারমিশন দিন" else "Permission needed"
                                            block == BleSosMeshManager.BlockReason.BLUETOOTH_OFF -> if (bn) "ব্লুটুথ বন্ধ আছে" else "Bluetooth is off"
                                            block == BleSosMeshManager.BlockReason.NO_HARDWARE -> if (bn) "ব্লুটুথ অনুপলব্ধ" else "No Bluetooth hardware"
                                            isAdvertising -> if (bn) "আপনার SOS সিগন্যাল সম্প্রচারিত হচ্ছে" else "Broadcasting your SOS"
                                            isScanning -> if (bn) "সক্রিয় • কাছের SOS: ${bleBeacons.size} • রিলে: $relayedCount" else "Active • nearby: ${bleBeacons.size} • relayed: $relayedCount"
                                            else -> bleStatus
                                        }
                                        Text(
                                            text = if (isLiveLocationActive) {
                                                (if (bn) "লাইভ জিপিএস চালু • " else "Live GPS on • ") + statusLine
                                            } else {
                                                statusLine
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLiveLocationActive || (isScanning && block == null)) IosSystemGreen else IosSystemRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Switch(
                                    checked = guardEnabled,
                                    onCheckedChange = { viewModel.setGuardNetworkEnabled(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosSystemGreen),
                                    modifier = Modifier.testTag("guard_network_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "অ্যাপ বন্ধ থাকলেও ১ কি.মি.-এর মধ্যে কেউ SOS দিলে আপনি তাৎক্ষণিক অ্যালার্ট পাবেন।"
                                } else {
                                    "Receive instant alerts when someone within 1 km triggers an SOS, even when the app is closed."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF756568),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                // Section D: Trusted Contacts Card
                item {
                    TrustedContactsCard(
                        language = language,
                        contacts = trustedContacts,
                        onEdit = { showContactsEditor = true }
                    )
                }

                // Section E: Anti-False-SOS Policy Banner
                item {
                    FalseSosAccountWarningBanner(
                        language = language,
                        warningMessage = falseSosWarningBanner,
                        penaltyCount = falseSosPenaltyCount,
                        onDismiss = if (falseSosWarningBanner != null) {
                            { viewModel.dismissFalseSosWarningBanner() }
                        } else {
                            null
                        }
                    )
                }

                // Section F: My Past SOS History Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = IosSystemRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        "আমার SOS হিস্ট্রি (${myHistorySosList.size})"
                                    } else {
                                        "My SOS History (${myHistorySosList.size})"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF1A0F12),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (language == AppLanguage.BENGALI) {
                                        if (mySosAlerts.isEmpty()) {
                                            "আপনার পাঠানো সব SOS রেকর্ড এখানে সংরক্ষিত থাকে"
                                        } else {
                                            "মোট ${mySosAlerts.size} বার SOS পাঠানো হয়েছে"
                                        }
                                    } else {
                                        if (mySosAlerts.isEmpty()) {
                                            "All SOS alerts sent from your account are saved here"
                                        } else {
                                            "Total ${mySosAlerts.size} SOS alert(s) recorded"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF756568)
                                )
                            }
                        }
                    }
                }

                if (myHistorySosList.isEmpty()) {
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(18.dp))
                        ) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "আপনার অ্যাকাউন্ট থেকে পূর্বে পাঠানো কোনো SOS হিস্ট্রি নেই।"
                                } else {
                                    "No past SOS history found for your account."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF756568),
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(myHistorySosList, key = { "my_history_sos_${it.id}" }) { alert ->
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
                            onCardTapOpenVideoAudio = {
                                selectedSosForMediaModal = alert
                            },
                            onToggleInlinePlay = {
                                viewModel.audioVerifier.togglePlayFiveSecondClip(
                                    scope = scope,
                                    alertId = alert.id
                                )
                            },
                            onRespond = {},
                            onReportFalseAlarm = {},
                            onMarkSafeOwnerOnly = {}
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
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
private fun TrustedContactsCard(
    language: AppLanguage,
    contacts: List<TrustedContact>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bn = language == AppLanguage.BENGALI
    Surface(
        color = Color(0xFFFAF6F6),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(22.dp))
            .testTag("trusted_contacts_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (bn) "বিশ্বস্ত কন্টাক্ট" else "Trusted contacts", color = Color(0xFF1A0F12), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (bn) "SOS পাঠালে এদের লোকেশন লিংকসহ SMS যাবে" else "They get an SMS with your location when you send SOS",
                        color = Color(0xFF756568),
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
                        Text(c.name.ifBlank { c.phone }, color = Color(0xFF1A0F12), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(c.phone, color = Color(0xFF756568), fontSize = 13.sp)
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(if (bn) "বিশ্বস্ত কন্টাক্ট (সর্বোচ্চ ৩)" else "Trusted contacts (up to 3)", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A0F12))
                Text(
                    text = if (bn) "এই নম্বরগুলো শুধু আপনার ফোনে থাকে, কোথাও আপলোড হয় না।" else "Stored only on this phone, never uploaded.",
                    color = Color(0xFF756568),
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
                        Text(if (bn) "বাতিল" else "Cancel", color = Color(0xFF1A0F12))
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
