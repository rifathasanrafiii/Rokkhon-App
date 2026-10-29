package com.example.ui.sos

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.TrustedContact
import com.example.data.sos.GpsProblem
import com.example.data.sos.GrantStatus
import com.example.data.sos.Live
import com.example.data.sos.MicStatus
import com.example.data.sos.OwnerSosState
import com.example.data.sos.PushStatus
import com.example.data.sos.SmsStatus
import com.example.data.sos.SosSms
import com.example.data.sos.SosGrant
import com.example.data.sos.SosHistoryItem
import com.example.data.sos.SosSession
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SectionHeader
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.screens.bnDigits
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.PrivateSosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private SOS home. Not running: the 3-second hold button, who will be told, and settings.
 * Running: the sender's dashboard with what is shared, who has seen it, and End SOS.
 */
@Composable
fun PrivateSosScreen(
    vm: PrivateSosViewModel,
    bn: Boolean,
    onBack: () -> Unit,
    onOpenAccess: () -> Unit,
    onOpenSession: (String) -> Unit,
    showBack: Boolean = true
) {
    BackHandler(onBack = onBack)
    val owner by vm.owner.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            SosTopBar(
                title = if (owner.active) t(bn, "SOS চালু আছে", "SOS is on") else t(bn, "জরুরি SOS", "Emergency SOS"),
                onBack = if (showBack) onBack else null
            ) {
                com.example.ui.components.RoundIconButton(icon = UIcons.People, onClick = onOpenAccess, contentDescription = "SOS Access", testTag = "sos_open_access")
            }
            Box(Modifier.weight(1f)) {
                if (owner.active) OwnerDashboard(vm, bn, owner, onOpenAccess)
                else SosHome(vm, bn, onOpenAccess, onOpenSession)
            }
        }
        message?.let { text ->
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(3500)
                vm.consumeMessage()
            }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    text, color = Color.White, fontSize = 13.5.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(14.dp)
                        .testTag("sos_message")
                )
            }
        }
    }
}

@Composable
private fun SosHome(vm: PrivateSosViewModel, bn: Boolean, onOpenAccess: () -> Unit, onOpenSession: (String) -> Unit) {
    val context = LocalContext.current
    val viewers by vm.viewers.collectAsStateWithLifecycle()
    val toMe by vm.grantsToMe.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val stranded by vm.stranded.collectAsStateWithLifecycle()
    val shareAudio by vm.shareAudio.collectAsStateWithLifecycle()
    val allowRecording by vm.allowRecording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val history by vm.myHistory.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showSmsSheet by remember { mutableStateOf(false) }
    val smsContacts by vm.smsContacts.collectAsStateWithLifecycle()

    val trusted = viewers.count { it.status == GrantStatus.ACCEPTED && it.perms.anyLive }
    val pendingOut = viewers.count { it.status == GrantStatus.PENDING }
    val invitesIn = toMe.count { it.status == GrantStatus.PENDING }

    val startLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Starts either way: alerts still go out without GPS; the dashboard then shows what is missing.
        vm.startSos()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setShareAudioDefault(true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        if (!vm.available) {
            UnavailableCard(bn, vm.signedIn)
            Spacer(Modifier.height(16.dp))
        }

        stranded?.let {
            StrandedCard(bn, onResume = { vm.resumeStranded() }, onEnd = { vm.endSos() })
            Spacer(Modifier.height(16.dp))
        }

        watched.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RokkhonPalette.Primary)
                    .springClick(pressedScale = 0.98f) { onOpenSession(w.sessionId) }
                    .padding(16.dp)
                    .testTag("watched_sos_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(w.ownerName, dark = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(bn, "${w.ownerName} SOS চালু করেছেন", "${w.ownerName} started an SOS"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    StatusPill(t(bn, "লাইভ", "LIVE"), Color.White, pulse = true, dotColor = RokkhonPalette.Sos)
                    Text(t(bn, "দেখতে ট্যাপ করুন", "Tap to follow"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                }
                Icon(UIcons.KeyboardArrowRight, null, tint = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HoldToActivateButton(
                label = "SOS",
                hint = if (vm.available) t(bn, "৩ সেকেন্ড চেপে ধরে রাখুন", "Press and hold for 3 seconds")
                else t(bn, "SOS এখন চালু করা যাবে না — উপরে কারণ দেখুন", "SOS can't start right now — see above"),
                enabled = vm.available && !busy,
                onActivated = {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (shareAudio) perms += Manifest.permission.RECORD_AUDIO
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    // The automatic SMS needs its permission before the text can go out by itself.
                    if (smsContacts.isNotEmpty() && !vm.hasSmsPermission && SosSms.hasTelephony(context)) perms += Manifest.permission.SEND_SMS
                    startLauncher.launch(perms.toTypedArray())
                }
            )
        }
        Spacer(Modifier.height(20.dp))

        SosCard {
            NavRow(
                icon = UIcons.People,
                title = if (trusted > 0) t(bn, "${bnDigits(trusted)} জন খবর পাবেন", "$trusted people will be told")
                else t(bn, "এখনও কেউ যোগ করা নেই", "Nobody added yet"),
                subtitle = when {
                    trusted == 0 && pendingOut > 0 -> t(bn, "${bnDigits(pendingOut)} জন এখনও রাজি হননি", "$pendingOut haven't accepted yet")
                    trusted == 0 -> t(bn, "SOS চালু হবে, কিন্তু কেউ জানবে না। আগে কাউকে যোগ করুন।", "SOS would start, but nobody would know. Add someone first.")
                    else -> t(bn, "কে কী দেখবে, এখানে ঠিক করুন", "Choose what each person sees")
                },
                onClick = onOpenAccess,
                testTag = "sos_trusted_row"
            )
            if (invitesIn > 0) {
                HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                NavRow(
                    icon = UIcons.NotificationsActive,
                    title = t(bn, "${bnDigits(invitesIn)} টি নতুন আমন্ত্রণ", "$invitesIn new invitations"),
                    subtitle = t(bn, "কেউ আপনাকে তার SOS-এ যোগ করতে চান", "Someone wants you in their SOS"),
                    onClick = onOpenAccess
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        SosCard {
            NavRow(
                icon = UIcons.Sms,
                title = if (smsContacts.isEmpty()) t(bn, "নম্বরে অটো SMS", "Auto SMS to numbers")
                else t(bn, "${bnDigits(smsContacts.size)} নম্বরে SMS যাবে", "SMS goes to ${smsContacts.size} numbers"),
                subtitle = if (smsContacts.isEmpty()) t(bn, "SOS চালু হলেই লাইভ লিংকসহ SMS নিজে থেকে চলে যাবে — Rokkhon না থাকলেও", "As soon as SOS starts, a text with the live link goes out by itself — even to people without Rokkhon")
                else smsContacts.joinToString(", ") { it.name.ifBlank { it.phone } },
                onClick = { showSmsSheet = true },
                testTag = "sos_sms_row"
            )
        }
        Spacer(Modifier.height(12.dp))

        SosCard {
            SwitchRow(
                icon = UIcons.Mic,
                title = t(bn, "লাইভ অডিও শেয়ার", "Share live audio"),
                subtitle = t(bn, "SOS-এ মাইক্রোফোন চালু থাকবে; শুধু যাদের অনুমতি দিয়েছেন তারা শুনবে। চলাকালীনও বন্ধ করা যায়।",
                    "Your mic is on during SOS; only people you allowed can listen. You can turn it off any time."),
                checked = shareAudio && vm.hasMicPermission,
                onChange = { on ->
                    if (on && !vm.hasMicPermission) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else vm.setShareAudioDefault(on)
                },
                testTag = "sos_default_audio"
            )
            HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SwitchRow(
                icon = UIcons.Videocam,
                title = t(bn, "শ্রোতারা রেকর্ড করতে পারবে", "Listeners may record"),
                subtitle = t(bn, "রেকর্ডিং শুধু তাদের ফোনে থাকে, কোথাও আপলোড হয় না। কেউ রেকর্ড করলে আপনি দেখতে পাবেন।",
                    "Recordings stay on their phone and are never uploaded. You'll see when someone records."),
                checked = allowRecording,
                onChange = { vm.setAllowRecordingDefault(it) },
                testTag = "sos_default_recording"
            )
        }
        Spacer(Modifier.height(12.dp))

        SosCard {
            NavRow(UIcons.History, t(bn, "আগের SOS", "Past SOS"), t(bn, "সময়, শেষ অবস্থান; মুছে ফেলা যায়", "Times and last position; you can delete them"), onClick = { showHistory = true }, testTag = "sos_history_row")
            HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            NavRow(UIcons.Call, t(bn, "৯৯৯-এ কল করুন", "Call 999"), t(bn, "জাতীয় জরুরি সেবা — পুলিশ, ফায়ার, অ্যাম্বুলেন্স", "National emergency — police, fire, ambulance"), onClick = { RokkhonIntents.dial(context, "999", if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH) }, testTag = "sos_call_999")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            t(bn, "Rokkhon ইন্টারনেট আর ফোনের অনুমতির ওপর নির্ভর করে; লোকেশন বা অডিও সবসময় পৌঁছাবে এমন নিশ্চয়তা নেই। জীবন ঝুঁকিতে থাকলে ৯৯৯-এ কল করুন।",
                "Rokkhon depends on the internet and phone permissions; location and audio are not guaranteed to arrive. If a life is at risk, call 999."),
            fontSize = 12.sp, lineHeight = 17.sp, color = RokkhonPalette.Muted
        )
    }

    if (showSmsSheet) SmsContactsSheet(vm, bn, smsContacts, onDismiss = { showSmsSheet = false })
    if (showHistory) HistorySheet(bn, history, onDismiss = { showHistory = false }, onOpen = { onOpenSession(it.sessionId); showHistory = false }, onDelete = { vm.deleteHistory(it) })
}

@Composable
private fun UnavailableCard(bn: Boolean, signedIn: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RokkhonPalette.Subtle)
            .padding(16.dp)
            .testTag("sos_unavailable")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(UIcons.Warning, null, tint = RokkhonPalette.Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (!signedIn) t(bn, "SOS-এর জন্য লগইন দরকার", "Sign in to use SOS") else t(bn, "SOS সার্ভার এখনও চালু হয়নি", "SOS server not set up yet"),
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RokkhonPalette.Ink
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (!signedIn) t(bn, "কে আপনার SOS দেখবে তা নিরাপদে মেলাতে Rokkhon অ্যাকাউন্ট লাগে। প্রোফাইল থেকে লগইন করুন। জরুরি হলে এখনই ৯৯৯-এ কল করুন।",
                "Rokkhon needs your account to match who may see your SOS. Sign in from Profile. In an emergency, call 999 now.")
            else t(bn, "অ্যাপের Firebase প্রজেক্টে Realtime Database তৈরি করা হয়নি, তাই লাইভ লোকেশন পাঠানো যাবে না। এখন ৯৯৯ ব্যবহার করুন।",
                "Realtime Database isn't created in this app's Firebase project, so live location can't be sent. Use 999 for now."),
            fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted
        )
    }
}

@Composable
private fun StrandedCard(bn: Boolean, onResume: () -> Unit, onEnd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, RokkhonPalette.Border, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag("sos_stranded")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(RokkhonPalette.Sos))
            Spacer(Modifier.width(8.dp))
            Text(t(bn, "আপনার একটি SOS এখনও চালু", "One of your SOS is still on"), color = RokkhonPalette.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t(bn, "ফোন বন্ধ বা অ্যাপ বন্ধ হওয়ায় লোকেশন যাওয়া থেমে গিয়েছিল। আবার চালু করবেন, নাকি SOS শেষ করবেন?",
                "Location stopped when the phone or app was closed. Resume sharing, or end the SOS?"),
            color = RokkhonPalette.Muted, fontSize = 13.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton(t(bn, "আবার চালু", "Resume"), onClick = onResume, testTag = "sos_resume") }
            SecondaryButton(t(bn, "শেষ করুন", "End SOS"), onClick = onEnd, modifier = Modifier.weight(1f), testTag = "sos_stranded_end")
        }
    }
}

// ------------------------------------------------------------------ the running SOS

@Composable
private fun OwnerDashboard(vm: PrivateSosViewModel, bn: Boolean, owner: OwnerSosState, onOpenAccess: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val session by vm.activeSession.collectAsStateWithLifecycle()
    val viewers by vm.viewers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }
    val s = (session as? Live.Value)?.value
    val linkBase = stringResource(R.string.sos_live_link_base)

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.startSharingNow()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setLiveAudio(true)
    }
    val smsContacts by vm.smsContacts.collectAsStateWithLifecycle()
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.sendSmsNow()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        // Header: what is going on, for how long.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RokkhonPalette.Primary)
                .padding(18.dp)
                .testTag("sos_owner_header")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(t(bn, "লাইভ", "LIVE"), Color.White, pulse = true, dotColor = RokkhonPalette.Sos)
                Spacer(Modifier.weight(1f))
                Text(
                    if (owner.online) t(bn, "অনলাইন", "Online") else t(bn, "অফলাইন — নেট ফিরলে পাঠাবে", "Offline — will send when back"),
                    color = Color.White.copy(alpha = 0.9f), fontSize = 12.5.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(clock(now - (s?.startedAt?.takeIf { it > 0 } ?: owner.startedAtMs), bn), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Text(t(bn, "SOS চলছে — আপনি বন্ধ না করা পর্যন্ত চলবে", "SOS running — until you end it"), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        // What is being shared right now.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Indicator(
                modifier = Modifier.weight(1f),
                icon = UIcons.LocationOn,
                title = t(bn, "লোকেশন", "Location"),
                value = when {
                    !owner.locationOn -> t(bn, "বন্ধ", "Off")
                    owner.gpsProblem == GpsProblem.NO_PERMISSION -> t(bn, "অনুমতি নেই", "No permission")
                    owner.gpsProblem == GpsProblem.LOCATION_OFF -> t(bn, "ফোনের লোকেশন বন্ধ", "Phone location off")
                    owner.lastFix == null -> t(bn, "খুঁজছে…", "Finding…")
                    else -> "±${bnOr(bn, owner.lastFix.accuracyM.toInt())} m"
                },
                detail = owner.lastFix?.let { ago(bn, owner.lastPublishMs, now) },
                live = owner.locationOn && owner.gpsProblem == GpsProblem.NONE,
                testTag = "sos_gps_indicator"
            )
            Indicator(
                modifier = Modifier.weight(1f),
                icon = UIcons.Mic,
                title = t(bn, "মাইক্রোফোন", "Microphone"),
                value = when (owner.mic) {
                    MicStatus.OFF -> t(bn, "বন্ধ", "Off")
                    MicStatus.NO_PERMISSION -> t(bn, "অনুমতি নেই", "No permission")
                    MicStatus.READY -> t(bn, "প্রস্তুত", "Ready")
                    MicStatus.LIVE -> t(bn, "${bnOr(bn, owner.listening.size)} জন শুনছেন", "${owner.listening.size} listening")
                },
                detail = if (owner.mic == MicStatus.READY) t(bn, "কেউ শুনলে চালু হবে", "On when someone listens") else null,
                live = owner.mic == MicStatus.LIVE,
                testTag = "sos_mic_indicator"
            )
        }
        if (owner.gpsProblem == GpsProblem.NO_PERMISSION) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(t(bn, "লোকেশনের অনুমতি দিন", "Allow location"), onClick = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, testTag = "sos_allow_location")
        }
        Spacer(Modifier.height(12.dp))

        SosCard {
            SwitchRow(UIcons.LocationOn, t(bn, "লোকেশন শেয়ার", "Share location"),
                t(bn, "বন্ধ করলে সাথে সাথে নতুন লোকেশন যাওয়া থামবে", "Turning off stops new updates at once"),
                owner.locationOn, { vm.setLocationSharing(it) }, testTag = "sos_toggle_location")
            HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SwitchRow(UIcons.Mic, t(bn, "লাইভ অডিও", "Live audio"),
                t(bn, "অনুমতি পাওয়া মানুষরা শুনতে পারবে", "People you allowed can listen"),
                s?.audio == true, { on ->
                    if (on && !vm.hasMicPermission) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else vm.setLiveAudio(on)
                }, testTag = "sos_toggle_audio")
            HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            SwitchRow(UIcons.Videocam, t(bn, "রেকর্ড করার অনুমতি", "Allow recording"),
                t(bn, "শ্রোতার ফোনে, কোথাও আপলোড হয় না", "On the listener's phone, never uploaded"),
                s?.recordingAllowed == true, { vm.setSessionRecordingAllowed(it) }, testTag = "sos_toggle_recording")
        }
        Spacer(Modifier.height(18.dp))

        SectionHeader(t(bn, "কারা জানল", "Who knows"))
        Spacer(Modifier.height(4.dp))
        PushLine(bn, owner.push, onRetry = { vm.resendAlert() })
        if (smsContacts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SmsLine(
                bn, owner.sms,
                onSend = { vm.sendSmsNow() },
                onAllow = { smsPermission.launch(Manifest.permission.SEND_SMS) },
                onOpenSmsApp = {
                    RokkhonIntents.smsComposer(context, smsContacts, vm.smsFallbackBody(), if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH)
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        val told = viewers.filter { it.status == GrantStatus.ACCEPTED && it.perms.anyLive }
        if (told.isEmpty()) {
            Text(t(bn, "কেউ যোগ করা নেই। নিচের লিংক বা ৯৯৯ ব্যবহার করুন।", "Nobody is on your list. Use the link below or 999."), fontSize = 13.sp, color = RokkhonPalette.Muted)
            Spacer(Modifier.height(6.dp))
            SecondaryButton(t(bn, "কাউকে যোগ করুন", "Add someone"), onClick = onOpenAccess, icon = UIcons.Add, modifier = Modifier.fillMaxWidth())
        }
        told.forEach { g -> ViewerStatusRow(bn, g, s, now) }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(t(bn, "লিংক শেয়ার", "Share link"), icon = UIcons.Share, modifier = Modifier.weight(1f), testTag = "sos_share_link", onClick = {
                val sid = owner.sessionId ?: return@SecondaryButton
                val text = t(bn,
                    "আমি বিপদে আছি। আমার লাইভ লোকেশন: $linkBase$sid (শুধু আমার Rokkhon তালিকার মানুষ লগইন করে দেখতে পারবেন)",
                    "I need help. My live location: $linkBase$sid (only people on my Rokkhon list can open it after signing in)")
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            })
            SecondaryButton(t(bn, "৯৯৯ কল", "Call 999"), icon = UIcons.Call, modifier = Modifier.weight(1f), testTag = "sos_dash_999", onClick = { RokkhonIntents.dial(context, "999", if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH) })
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(t(bn, "SOS শেষ করুন", "End SOS"), onClick = { confirmEnd = true }, enabled = !busy, testTag = "sos_end_button")
    }

    if (confirmEnd) {
        RokkhonBottomSheet(onDismiss = { confirmEnd = false }, title = t(bn, "SOS শেষ করবেন?", "End SOS?"), testTag = "sos_end_sheet") {
            Text(t(bn, "লোকেশন আর অডিও শেয়ার সাথে সাথে বন্ধ হবে, আর লিংকটি আর কাজ করবে না।", "Location and audio stop at once and the link stops working."), fontSize = 14.sp, lineHeight = 20.sp, color = RokkhonPalette.Ink)
            Spacer(Modifier.height(14.dp))
            PrimaryButton(t(bn, "হ্যাঁ, আমি নিরাপদ", "Yes, I'm safe"), onClick = { confirmEnd = false; vm.endSos() }, testTag = "sos_end_confirm")
            Spacer(Modifier.height(8.dp))
            SecondaryButton(t(bn, "চালু থাক", "Keep it on"), onClick = { confirmEnd = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun bnOr(bn: Boolean, n: Int) = if (bn) bnDigits(n) else n.toString()

@Composable
private fun Indicator(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, detail: String?, live: Boolean, testTag: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(RokkhonPalette.Subtle)
            .padding(14.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint = if (live) Color.White else RokkhonPalette.Ink, background = if (live) RokkhonPalette.Primary else Color.White, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 12.5.sp, color = RokkhonPalette.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, maxLines = 1)
        Text(detail ?: " ", fontSize = 12.sp, color = RokkhonPalette.Muted, maxLines = 1)
    }
}

@Composable
internal fun SmsLine(bn: Boolean, sms: SmsStatus, onSend: () -> Unit, onAllow: () -> Unit, onOpenSmsApp: () -> Unit) {
    val text = when (sms) {
        SmsStatus.None -> t(bn, "অটো SMS এখনও যায়নি", "Auto SMS hasn't gone yet")
        SmsStatus.Sending -> t(bn, "SMS পাঠানো হচ্ছে…", "Sending SMS…")
        is SmsStatus.Sent -> if (sms.ok > 0) t(bn,
            "SMS পাঠানো হয়েছে ${bnDigits(sms.ok)} নম্বরে" + if (sms.failed > 0) " · ${bnDigits(sms.failed)} নম্বরে যায়নি" else "",
            "SMS sent to ${sms.ok} numbers" + if (sms.failed > 0) " · ${sms.failed} failed" else ""
        ) else t(bn, "SMS যায়নি — সিম, ব্যালেন্স বা নম্বর দেখুন", "SMS didn't go — check SIM, balance or numbers")
        SmsStatus.NeedsPermission -> t(bn, "SMS নিজে পাঠাতে অনুমতি লাগবে", "Allow SMS so it can go out by itself")
        SmsStatus.NoTelephony -> t(bn, "এই ফোন থেকে SMS পাঠানো যায় না", "This device can't send SMS")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (sms == SmsStatus.NeedsPermission || sms == SmsStatus.NoTelephony) Color(0xFFFFF4E5) else RokkhonPalette.Subtle)
            .padding(12.dp)
            .testTag("sos_sms_status"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(UIcons.Sms, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = RokkhonPalette.Ink)
            if (sms is SmsStatus.Sent && sms.ok > 0) {
                Text(t(bn, "নতুন লোকেশন আবার পাঠাতে ডানের বাটন চাপুন", "Tap the button to text your newest position"), fontSize = 11.5.sp, color = RokkhonPalette.Muted)
            }
        }
        val action: Pair<String, () -> Unit>? = when (sms) {
            SmsStatus.NeedsPermission -> t(bn, "অনুমতি", "Allow") to onAllow
            SmsStatus.NoTelephony -> null
            SmsStatus.Sending -> null
            else -> t(bn, if (sms is SmsStatus.Sent && sms.ok > 0) "আবার" else "পাঠান", if (sms is SmsStatus.Sent && sms.ok > 0) "Again" else "Send") to onSend
        }
        action?.let { (label, onClick) ->
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.springClick(onClick = onClick).padding(6.dp).testTag("sos_sms_action"))
        }
        if (sms == SmsStatus.NeedsPermission || sms == SmsStatus.NoTelephony || (sms is SmsStatus.Sent && sms.ok == 0)) {
            Text(t(bn, "SMS অ্যাপ", "SMS app"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.springClick(onClick = onOpenSmsApp).padding(6.dp).testTag("sos_sms_open_app"))
        }
    }
}

/** Up to three numbers that get the SOS text from this phone, plus the SMS permission it needs. */
@Composable
internal fun SmsContactsSheet(vm: PrivateSosViewModel, bn: Boolean, initial: List<TrustedContact>, onDismiss: () -> Unit) {
    val max = PrivateSosViewModel.MAX_SMS_CONTACTS
    val names = remember { mutableStateListOf(*Array(max) { initial.getOrNull(it)?.name ?: "" }) }
    val phones = remember { mutableStateListOf(*Array(max) { initial.getOrNull(it)?.phone ?: "" }) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    RokkhonBottomSheet(onDismiss = onDismiss, title = t(bn, "অটো SMS নম্বর (সর্বোচ্চ ৩)", "Auto SMS numbers (up to 3)"), testTag = "sos_sms_sheet") {
        Text(
            t(bn, "SOS চালু হলেই আপনার ফোন থেকে এই নম্বরগুলোতে লাইভ লিংক আর অবস্থানসহ SMS চলে যাবে, আর শেষ করলে \"নিরাপদ\" SMS যাবে। খরচ আপনার সিম থেকে কাটবে। নম্বরগুলো শুধু এই ফোনে থাকে।",
                "When SOS starts, your phone texts these numbers the live link and position, and a \"safe\" text when you end it. It uses your SIM's SMS balance. Numbers stay on this phone only."),
            fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted
        )
        Text(
            t(bn, "লাইভ লিংক খুলতে Rokkhon অ্যাকাউন্ট লাগে (আপনার তালিকায় থাকলে); সবাই SMS-এর ম্যাপ লিংকে অবস্থান দেখতে পারবেন।",
                "The live link opens for Rokkhon accounts on your list; anyone can see the position in the map link."),
            fontSize = 12.sp, lineHeight = 17.sp, color = RokkhonPalette.Muted, modifier = Modifier.padding(top = 6.dp)
        )
        for (i in 0 until max) {
            Spacer(Modifier.height(10.dp))
            SheetField(names[i], { names[i] = it.take(40) }, t(bn, "নাম ${bnDigits(i + 1)}", "Name ${i + 1}"), KeyboardType.Text, "sos_sms_name_$i")
            Spacer(Modifier.height(6.dp))
            SheetField(phones[i], { phones[i] = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) }, "01XXXXXXXXX", KeyboardType.Phone, "sos_sms_phone_$i")
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(t(bn, "সেভ করুন", "Save"), onClick = {
            if (vm.saveSmsContacts((0 until max).map { TrustedContact(names[it], phones[it]) })) {
                val any = (0 until max).any { phones[it].isNotBlank() }
                if (any && !vm.hasSmsPermission && SosSms.hasTelephony(context)) permission.launch(Manifest.permission.SEND_SMS)
                onDismiss()
            }
        }, testTag = "sos_sms_save")
    }
}

@Composable
private fun SheetField(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, testTag: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RokkhonPalette.Subtle)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = RokkhonPalette.Faint, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = RokkhonPalette.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        )
    }
}

@Composable
private fun PushLine(bn: Boolean, push: PushStatus, onRetry: () -> Unit) {
    val (text, retry) = when (push) {
        PushStatus.NotSent -> t(bn, "নোটিফিকেশন পাঠানো হয়নি", "Notification not sent") to true
        PushStatus.Sending -> t(bn, "নোটিফিকেশন পাঠানো হচ্ছে…", "Sending notification…") to false
        is PushStatus.Sent -> t(bn,
            "নোটিফিকেশন গেছে ${bnDigits(push.delivered)} জনের ফোনে" + if (push.noDevice + push.failed > 0) " · ${bnDigits(push.noDevice + push.failed)} জনের ফোনে পৌঁছায়নি" else "",
            "Notification sent to ${push.delivered} phones" + if (push.noDevice + push.failed > 0) " · ${push.noDevice + push.failed} unreachable" else ""
        ) to (push.failed > 0)
        is PushStatus.Unavailable -> t(bn,
            "পুশ নোটিফিকেশন সার্ভার চালু নেই — যাদের ফোনে Rokkhon খোলা আছে শুধু তারাই এখনই জানবে। লিংক শেয়ার করুন বা কল দিন।",
            "Push server isn't available — only people with Rokkhon open will know right away. Share the link or call.") to true
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (push is PushStatus.Unavailable) Color(0xFFFFF4E5) else RokkhonPalette.Subtle)
            .padding(12.dp)
            .testTag("sos_push_status"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(UIcons.NotificationsActive, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = RokkhonPalette.Ink, modifier = Modifier.weight(1f))
        if (retry) {
            Spacer(Modifier.width(8.dp))
            Text(t(bn, "আবার", "Retry"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.springClick(onClick = onRetry).padding(6.dp))
        }
    }
}

@Composable
private fun ViewerStatusRow(bn: Boolean, g: SosGrant, s: SosSession?, now: Long) {
    val listener = s?.listeners?.get(g.viewerUid)
    val seen = s?.seen?.get(g.viewerUid)
    val (label, color) = when {
        listener?.recording == true -> t(bn, "রেকর্ড করছেন", "Recording") to RokkhonPalette.Sos
        listener?.state == "connected" -> t(bn, "শুনছেন", "Listening") to RokkhonPalette.Positive
        seen != null -> t(bn, "দেখেছেন · ${ago(bn, seen, now)}", "Opened · ${ago(bn, seen, now)}") to RokkhonPalette.Positive
        s?.notify?.get(g.viewerUid) == "sent" -> t(bn, "নোটিফিকেশন গেছে", "Notified") to RokkhonPalette.Ink
        s?.notify?.get(g.viewerUid) == "no_device" -> t(bn, "ফোনে পৌঁছায়নি", "Not reachable") to RokkhonPalette.Warning
        else -> t(bn, "এখনও দেখেননি", "Not opened yet") to RokkhonPalette.Muted
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("sos_viewer_status"), verticalAlignment = Alignment.CenterVertically) {
        Avatar(g.viewerName, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.viewerName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            Text(
                listOfNotNull(
                    if (g.perms.location) t(bn, "লোকেশন", "location") else null,
                    if (g.perms.audio) t(bn, "অডিও", "audio") else null
                ).joinToString(" · ").ifEmpty { t(bn, "শুধু অ্যালার্ট", "alerts only") },
                fontSize = 12.sp, color = RokkhonPalette.Muted
            )
        }
        StatusPill(label, color, pulse = listener?.recording == true)
    }
}

@Composable
internal fun HistorySheet(
    bn: Boolean,
    history: Live<List<SosHistoryItem>>,
    onDismiss: () -> Unit,
    onOpen: (SosHistoryItem) -> Unit,
    onDelete: ((SosHistoryItem) -> Unit)?
) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    RokkhonBottomSheet(onDismiss = onDismiss, title = t(bn, "আগের SOS", "Past SOS"), testTag = "sos_history_sheet") {
        when (history) {
            Live.Loading -> Text(t(bn, "লোড হচ্ছে…", "Loading…"), color = RokkhonPalette.Muted)
            Live.Denied -> Text(t(bn, "দেখার অনুমতি নেই", "You don't have access"), color = RokkhonPalette.Muted)
            is Live.Value -> {
                val items = history.value.orEmpty()
                if (items.isEmpty()) Text(t(bn, "এখনও কোনো SOS হয়নি", "No SOS yet"), color = RokkhonPalette.Muted, fontSize = 14.sp)
                items.take(30).forEachIndexed { i, h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .springClick(pressedScale = 0.98f) { onOpen(h) }
                            .padding(vertical = 10.dp)
                            .enterOnce(i),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconCircle(UIcons.History, background = RokkhonPalette.Subtle)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(h.startedAt)), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                            Text(
                                h.endedAt?.let { t(bn, "স্থায়িত্ব ", "Lasted ") + clock(it - h.startedAt, bn) } ?: t(bn, "চলছে", "Running"),
                                fontSize = 12.5.sp, color = RokkhonPalette.Muted
                            )
                        }
                        if (onDelete != null && h.endedAt != null) {
                            Icon(UIcons.DeleteOutline, contentDescription = "Delete", tint = RokkhonPalette.Muted,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).springClick { onDelete(h) }.padding(7.dp))
                        }
                    }
                }
            }
        }
    }
}
