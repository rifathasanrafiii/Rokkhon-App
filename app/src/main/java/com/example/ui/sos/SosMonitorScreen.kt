package com.example.ui.sos

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.sos.AudioLink
import com.example.data.sos.LinkQuality
import com.example.data.sos.Live
import com.example.data.sos.LiveFix
import com.example.data.sos.SosRecordings
import com.example.data.sos.SosSession
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonIntents
import com.example.ui.components.SectionHeader
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.MonitorBlock
import com.example.ui.viewmodel.SosMonitorViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A fix older than this is shown greyed out as "last known", not as live. */
private const val STALE_MS = 60_000L

/**
 * What a trusted person sees for one SOS: status, live map with accuracy and time, Open in Google
 * Maps, live audio with its connection state, local recording, and clear ended / revoked states.
 */
@Composable
fun SosMonitorScreen(vm: SosMonitorViewModel, bn: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val now = rememberNow()
    val sessionLive by vm.session.collectAsStateWithLifecycle()
    val fixLive by vm.fix.collectAsStateWithLifecycle()
    val grantLive by vm.grant.collectAsStateWithLifecycle()
    val block by vm.block.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val listen by vm.listen.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    var consentOpen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<File?>(null) }

    val session = (sessionLive as? Live.Value)?.value
    val fix = (fixLive as? Live.Value)?.value
    val perms = (grantLive as? Live.Value)?.value?.perms
    val isOwner = session != null && session.ownerUid == vm.myUid
    val listeningHere = listen.sessionId == vm.sessionId

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            SosTopBar(title = session?.ownerName ?: t(bn, "SOS", "SOS"), onBack = onBack)
            when {
                block != MonitorBlock.NONE -> BlockedState(bn, block)
                sessionLive is Live.Loading -> Centered(t(bn, "লোড হচ্ছে…", "Loading…"))
                session == null -> BlockedState(bn, MonitorBlock.NO_ACCESS)
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp)
                ) {
                    StatusHeader(bn, session, now, online)
                    Spacer(Modifier.height(12.dp))

                    val canSeeLocation = isOwner || perms?.location == true || perms?.history == true
                    if (canSeeLocation) {
                        LocationBlock(bn, session, fix, fixLive, now, onOpenMaps = { f ->
                            // Only these coordinates are handed over; Google Maps does not keep tracking.
                            val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${f.lat},${f.lng}")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        })
                    } else {
                        InfoBox(t(bn, "লোকেশন দেখার অনুমতি দেওয়া হয়নি।", "You weren't given location access."))
                    }
                    Spacer(Modifier.height(16.dp))

                    if (session.active && !isOwner && perms?.audio == true) {
                        AudioBlock(
                            bn = bn,
                            session = session,
                            listening = listeningHere,
                            link = if (listeningHere) listen.link else AudioLink.IDLE,
                            quality = listen.quality,
                            muted = listen.muted,
                            recording = listeningHere && listen.recording,
                            recordingMs = listen.recordingMs,
                            onListen = { vm.startListening() },
                            onStop = { vm.stopListening() },
                            onMute = { vm.setMuted(!listen.muted) },
                            onRetry = { vm.retryAudio() },
                            onRecord = { if (listen.recording) vm.stopRecording() else consentOpen = true }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (recordings.isNotEmpty()) {
                        RecordingsBlock(bn, recordings, playing,
                            onPlay = { playing = if (playing == it) null else it },
                            onShare = { f -> runCatching { context.startActivity(Intent.createChooser(SosRecordings.shareIntent(context, f), null)) } },
                            onSave = { f -> vm.saveRecording(f) },
                            onDelete = { f -> if (playing == f) playing = null; vm.deleteRecording(f) })
                        Spacer(Modifier.height(16.dp))
                    }

                    SecondaryButton(
                        t(bn, "৯৯৯-এ কল করুন", "Call 999"), icon = UIcons.Call, modifier = Modifier.fillMaxWidth(), testTag = "monitor_call_999",
                        onClick = { RokkhonIntents.dial(context, "999", if (bn) AppLanguage.BENGALI else AppLanguage.ENGLISH) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        t(bn, "লোকেশন ও অডিও ইন্টারনেটের ওপর নির্ভর করে; সবসময় পৌঁছাবে এমন নিশ্চয়তা নেই। বিপদ মনে হলে দেরি না করে ৯৯৯-এ কল করুন।",
                            "Location and audio depend on the internet and aren't guaranteed. If you think they're in danger, call 999 without waiting."),
                        fontSize = 12.sp, lineHeight = 17.sp, color = RokkhonPalette.Muted
                    )
                }
            }
        }
    }

    playing?.let { file -> Player(file, onDone = { playing = null }) }

    if (consentOpen) {
        RokkhonBottomSheet(onDismiss = { consentOpen = false }, title = t(bn, "রেকর্ড শুরু করবেন?", "Start recording?"), testTag = "record_consent_sheet") {
            Text(
                t(bn,
                    "রেকর্ডিং শুধু আপনার ফোনে থাকবে, কোথাও আপলোড হবে না। ${session?.ownerName ?: ""} দেখতে পাবেন যে আপনি রেকর্ড করছেন। শুধু তার নিরাপত্তার জন্য ব্যবহার করুন — অনুমতি ছাড়া অন্যের কথা রেকর্ড বা প্রচার করা আইনত অপরাধ হতে পারে।",
                    "The recording stays on your phone and is never uploaded. ${session?.ownerName ?: ""} will see that you are recording. Use it only for their safety — recording or sharing someone without consent can be illegal."),
                fontSize = 14.sp, lineHeight = 20.sp, color = RokkhonPalette.Ink
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(t(bn, "বুঝেছি, রেকর্ড করুন", "I understand, record"), testTag = "record_consent_confirm", onClick = {
                consentOpen = false
                vm.startRecording(session?.ownerName ?: "sos")
            })
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = RokkhonPalette.Muted) }
}

@Composable
private fun BlockedState(bn: Boolean, block: MonitorBlock) {
    val (title, body) = when (block) {
        MonitorBlock.REVOKED -> t(bn, "অ্যাক্সেস বাতিল হয়েছে", "Access removed") to
            t(bn, "এই SOS আর আপনার সাথে শেয়ার করা নেই।", "This SOS is no longer shared with you.")
        MonitorBlock.SIGNED_OUT -> t(bn, "লগইন করুন", "Sign in") to
            t(bn, "এই SOS শুধু অনুমতি পাওয়া Rokkhon অ্যাকাউন্ট দেখতে পারে।", "Only Rokkhon accounts that were given access can see this SOS.")
        MonitorBlock.NOT_CONFIGURED -> t(bn, "SOS সার্ভার চালু নেই", "SOS server not set up") to
            t(bn, "অ্যাপের Realtime Database এখনও তৈরি হয়নি।", "The app's Realtime Database hasn't been created yet.")
        else -> t(bn, "দেখার অনুমতি নেই", "No access") to
            t(bn, "লিংকটি ভুল, SOS শেষ হয়ে গেছে, বা আপনাকে অনুমতি দেওয়া হয়নি।", "The link is wrong, the SOS ended, or you weren't given access.")
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("monitor_blocked"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconCircle(UIcons.Block, background = RokkhonPalette.Subtle, size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, color = RokkhonPalette.Muted, lineHeight = 20.sp)
    }
}

@Composable
private fun StatusHeader(bn: Boolean, s: SosSession, now: Long, online: Boolean) {
    val ownerQuiet = s.active && (!s.ownerOnline || (s.heartbeat != null && now - s.heartbeat > 90_000))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (s.active) RokkhonPalette.Primary else Color(0xFF3A3A3A))
            .padding(18.dp)
            .testTag("monitor_status")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                if (s.active) t(bn, "SOS চলছে", "SOS active") else t(bn, "SOS শেষ", "SOS ended"), Color.White,
                pulse = s.active, dotColor = if (s.active) RokkhonPalette.Sos else Color.White
            )
            Spacer(Modifier.weight(1f))
            if (!online) Text(t(bn, "আপনি অফলাইনে", "You're offline"), color = Color.White, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (s.active) clock(now - s.startedAt, bn) else clock((s.endedAt ?: now) - s.startedAt, bn),
            color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold
        )
        Text(
            when {
                !s.active -> t(bn, "${s.ownerName} SOS বন্ধ করেছেন", "${s.ownerName} ended the SOS")
                ownerQuiet -> t(bn, "ওনার ফোন এখন সংযোগে নেই — শেষ জানা অবস্থান দেখানো হচ্ছে", "Their phone is not connected — showing last known position")
                !s.locationOn -> t(bn, "উনি লোকেশন শেয়ার বন্ধ রেখেছেন", "They paused location sharing")
                else -> t(bn, "সংযোগে আছেন", "Connected")
            },
            color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp
        )
    }
}

@Composable
private fun LocationBlock(bn: Boolean, s: SosSession, fix: LiveFix?, fixLive: Live<LiveFix>, now: Long, onOpenMaps: (LiveFix) -> Unit) {
    val stale = fix == null || !s.active || now - fix.ts > STALE_MS || fix.fromLastKnown
    SosLiveMap(
        fix = fix,
        stale = stale,
        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp))
    )
    Spacer(Modifier.height(10.dp))
    SosCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(UIcons.LocationOn)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        fixLive is Live.Denied -> t(bn, "লোকেশন দেখার অনুমতি নেই", "No location access")
                        fix == null -> t(bn, "এখনও লোকেশন আসেনি", "No location yet")
                        !s.active -> t(bn, "শেষ অবস্থান", "Last position")
                        stale -> t(bn, "শেষ জানা অবস্থান", "Last known position")
                        else -> t(bn, "লাইভ অবস্থান", "Live position")
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink
                )
                if (fix != null) {
                    val time = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(fix.ts))
                    Text(
                        t(bn, "${ago(bn, fix.ts, now)} · $time · নির্ভুলতা ±${fix.accuracyM.toInt()} m", "${ago(bn, fix.ts, now)} · $time · accuracy ±${fix.accuracyM.toInt()} m"),
                        fontSize = 12.5.sp, color = RokkhonPalette.Muted, modifier = Modifier.testTag("monitor_fix_detail")
                    )
                }
            }
        }
        if (fix != null) {
            HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            NavRow(
                UIcons.Map, t(bn, "Google Maps-এ খুলুন", "Open in Google Maps"),
                t(bn, "এই মুহূর্তের অবস্থান খুলবে; ম্যাপে লাইভ আপডেট হবে না", "Opens this position only; it won't update live there"),
                onClick = { onOpenMaps(fix) }, testTag = "monitor_open_maps"
            )
        }
    }
}

@Composable
private fun AudioBlock(
    bn: Boolean,
    session: SosSession,
    listening: Boolean,
    link: AudioLink,
    quality: LinkQuality,
    muted: Boolean,
    recording: Boolean,
    recordingMs: Long,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onMute: () -> Unit,
    onRetry: () -> Unit,
    onRecord: () -> Unit
) {
    SectionHeader(t(bn, "লাইভ অডিও", "Live audio"))
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RokkhonPalette.Subtle)
            .padding(16.dp)
            .testTag("monitor_audio")
    ) {
        if (!session.audio) {
            Text(t(bn, "উনি মাইক্রোফোন চালু করেননি।", "They haven't turned on the microphone."), fontSize = 14.sp, color = RokkhonPalette.Muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (label, color) = when (link) {
                AudioLink.IDLE, AudioLink.STOPPED -> t(bn, "শুনছেন না", "Not listening") to RokkhonPalette.Muted
                AudioLink.CONNECTING -> t(bn, "সংযোগ হচ্ছে…", "Connecting…") to RokkhonPalette.Warning
                AudioLink.CONNECTED -> t(bn, "শুনছেন", "Listening") to RokkhonPalette.Positive
                AudioLink.RECONNECTING -> t(bn, "আবার সংযোগ হচ্ছে…", "Reconnecting…") to RokkhonPalette.Warning
                AudioLink.FAILED -> t(bn, "সংযোগ হয়নি", "Couldn't connect") to RokkhonPalette.Sos
            }
            StatusPill(label, color, pulse = link == AudioLink.CONNECTED)
            Spacer(Modifier.weight(1f))
            if (link == AudioLink.CONNECTED) {
                Text(
                    when (quality) {
                        LinkQuality.GOOD -> t(bn, "সংযোগ ভালো", "Good connection")
                        LinkQuality.FAIR -> t(bn, "সংযোগ মাঝারি", "Fair connection")
                        LinkQuality.POOR -> t(bn, "সংযোগ দুর্বল", "Weak connection")
                        LinkQuality.UNKNOWN -> ""
                    },
                    fontSize = 12.5.sp, color = RokkhonPalette.Muted
                )
            }
        }
        if (link == AudioLink.FAILED) {
            Spacer(Modifier.height(8.dp))
            Text(
                t(bn, "দুই ফোনের নেটওয়ার্কের মধ্যে সরাসরি পথ পাওয়া যায়নি (কিছু মোবাইল নেটওয়ার্কে রিলে সার্ভার লাগে)। Wi-Fi বা অন্য নেটওয়ার্কে আবার চেষ্টা করুন। লোকেশন এরপরও লাইভ থাকবে।",
                    "No direct path between the two networks (some mobile networks need a relay server). Try again on Wi-Fi or another network. Location stays live."),
                fontSize = 12.5.sp, lineHeight = 18.sp, color = RokkhonPalette.Ink
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!listening) {
            PrimaryButton(t(bn, "শুনুন", "Listen"), onClick = onListen, testTag = "monitor_listen")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (muted) t(bn, "আওয়াজ চালু", "Unmute") else t(bn, "মিউট", "Mute"), onClick = onMute, modifier = Modifier.weight(1f), testTag = "monitor_mute")
                if (link == AudioLink.FAILED) SecondaryButton(t(bn, "আবার চেষ্টা", "Retry"), onClick = onRetry, icon = UIcons.Refresh, modifier = Modifier.weight(1f))
                SecondaryButton(t(bn, "থামুন", "Stop"), onClick = onStop, icon = UIcons.Stop, modifier = Modifier.weight(1f), testTag = "monitor_stop_listen")
            }
            if (session.recordingAllowed) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (recording) RokkhonPalette.Primary else Color.White)
                        .springClick(enabled = link == AudioLink.CONNECTED || recording, pressedScale = 0.97f, onClick = onRecord)
                        .padding(horizontal = 16.dp)
                        .testTag("monitor_record"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (recording) UIcons.Stop else UIcons.Mic, null, tint = if (recording) RokkhonPalette.Sos else RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (recording) t(bn, "রেকর্ড হচ্ছে · ", "Recording · ") + clock(recordingMs, bn)
                        else t(bn, "ফোনে রেকর্ড করুন", "Record on this phone"),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (recording) Color.White else RokkhonPalette.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    if (recording) Text(t(bn, "থামান", "Stop"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(t(bn, "উনি রেকর্ড করার অনুমতি দেননি।", "They haven't allowed recording."), fontSize = 12.5.sp, color = RokkhonPalette.Muted)
            }
        }
    }
}

@Composable
private fun RecordingsBlock(
    bn: Boolean,
    files: List<File>,
    playing: File?,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onSave: (File) -> Boolean,
    onDelete: (File) -> Unit
) {
    var savedName by remember { mutableStateOf<String?>(null) }
    SectionHeader(t(bn, "এই ফোনের রেকর্ডিং", "Recordings on this phone"))
    Spacer(Modifier.height(8.dp))
    SosCard {
        files.take(10).forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag("recording_row"), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).springClick { onPlay(f) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (playing == f) UIcons.Stop else UIcons.PlayArrow, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(f.lastModified())), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                    Text("${f.length() / 1024} KB · M4A", fontSize = 12.sp, color = RokkhonPalette.Muted)
                }
                SmallIcon(UIcons.Share) { onShare(f) }
                SmallIcon(UIcons.CheckCircle) { savedName = if (onSave(f)) f.name else "" }
                SmallIcon(UIcons.DeleteOutline) { onDelete(f) }
            }
        }
    }
    savedName?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            if (it.isEmpty()) t(bn, "সেভ হয়নি — শেয়ার দিয়ে অন্য অ্যাপে রাখুন", "Couldn't save — use Share instead")
            else t(bn, "Downloads/Rokkhon ফোল্ডারে সেভ হয়েছে", "Saved to Downloads/Rokkhon"),
            fontSize = 12.5.sp, color = RokkhonPalette.Muted
        )
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Icon(icon, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).springClick(onClick = onClick).padding(9.dp))
}

@Composable
private fun InfoBox(text: String) {
    Text(text, fontSize = 13.sp, color = RokkhonPalette.Ink, modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(RokkhonPalette.Subtle)
        .padding(14.dp))
}

/** Plays a local recording; stops when the row is tapped again or the screen goes away. */
@Composable
private fun Player(file: File, onDone: () -> Unit) {
    DisposableEffect(file) {
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onDone() }
                prepare()
                start()
            }
        }.getOrElse { onDone(); null }
        onDispose { runCatching { player?.stop() }; player?.release() }
    }
}
