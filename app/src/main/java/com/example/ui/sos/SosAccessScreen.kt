package com.example.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sos.GrantStatus
import com.example.data.sos.SosAccount
import com.example.data.sos.SosGrant
import com.example.data.sos.SosPerms
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonEmptyState
import com.example.ui.components.Segment
import com.example.ui.components.SegmentedControl
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.AccountSearch
import com.example.ui.viewmodel.PrivateSosViewModel

/**
 * "SOS Access": choose the Rokkhon accounts that may see your SOS and what each may do, and answer
 * invitations from people who want you in theirs. Everything here is enforced by the database rules.
 */
@Composable
fun SosAccessScreen(
    vm: PrivateSosViewModel,
    bn: Boolean,
    onBack: () -> Unit,
    onOpenHistory: (SosGrant) -> Unit
) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SosGrant?>(null) }
    val viewers by vm.viewers.collectAsStateWithLifecycle()
    val toMe by vm.grantsToMe.collectAsStateWithLifecycle()
    val pendingForMe = toMe.count { it.status == GrantStatus.PENDING }

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            SosTopBar(title = t(bn, "SOS অ্যাক্সেস", "SOS Access"), onBack = onBack)
            SegmentedControl(
                segments = listOf(
                    Segment(t(bn, "যারা আমার SOS পাবে", "Who sees my SOS"), testTag = "access_tab_mine"),
                    Segment(
                        t(bn, "যারা আমাকে যোগ করেছে", "Who added me") + if (pendingForMe > 0) " · $pendingForMe" else "",
                        testTag = "access_tab_others"
                    )
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
                MyTrustedList(bn, viewers, onAdd = { showAdd = true }, onEdit = { editing = it })
            } else {
                AddedMeList(bn, toMe, vm, onOpenHistory)
            }
        }
    }

    if (showAdd) AddPersonSheet(vm, bn, onDismiss = { showAdd = false; vm.clearSearch() })
    editing?.let { g ->
        // Show the latest copy so a switch flips immediately after the server confirms it.
        val live = viewers.firstOrNull { it.viewerUid == g.viewerUid }
        if (live == null) editing = null else EditGrantSheet(vm, bn, live, onDismiss = { editing = null })
    }
}

@Composable
private fun MyTrustedList(bn: Boolean, viewers: List<SosGrant>, onAdd: () -> Unit, onEdit: (SosGrant) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.navigationBarsPadding()
    ) {
        item {
            Text(
                t(
                    bn,
                    "আপনি SOS চালু করলে শুধু এরাই খবর পাবেন, আর আপনি যতটুকু অনুমতি দেবেন ততটুকুই দেখতে পাবেন। যেকোনো সময় বাতিল করতে পারবেন।",
                    "Only these people hear about your SOS, and only see what you allow. You can remove anyone at any time."
                ),
                fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted
            )
        }
        item {
            PrimaryButton(t(bn, "বিশ্বস্ত কাউকে যোগ করুন", "Add someone you trust"), onClick = onAdd, testTag = "access_add_person")
        }
        if (viewers.isEmpty()) {
            item {
                RokkhonEmptyState(
                    icon = UIcons.People,
                    title = t(bn, "এখনও কেউ নেই", "Nobody yet"),
                    body = t(bn, "পরিবারের কেউ বা কাছের বন্ধু — যাকে বিপদে প্রথমে জানাতে চান তাকে যোগ করুন। তার ফোনেও Rokkhon থাকতে হবে।", "Add a family member or close friend you'd want to know first. They need Rokkhon too."),
                    testTag = "access_empty"
                )
            }
        }
        items(viewers, key = { it.viewerUid }) { g ->
            GrantRow(
                name = g.viewerName,
                status = g.status,
                perms = g.perms,
                bn = bn,
                onClick = { onEdit(g) },
                modifier = Modifier.enterOnce()
            )
        }
    }
}

@Composable
private fun GrantRow(name: String, status: GrantStatus, perms: SosPerms, bn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RokkhonPalette.Subtle)
            .springClick(pressedScale = 0.98f, onClick = onClick)
            .padding(14.dp)
            .testTag("grant_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(statusText(bn, status), statusColor(status))
                Spacer(Modifier.width(8.dp))
                PermIcons(perms)
            }
        }
        Icon(UIcons.KeyboardArrowRight, contentDescription = null, tint = RokkhonPalette.Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PermIcons(perms: SosPerms) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (perms.location) Icon(UIcons.LocationOn, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(15.dp))
        if (perms.audio) Icon(UIcons.Mic, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(15.dp))
        if (perms.alerts) Icon(UIcons.NotificationsActive, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(15.dp))
        if (perms.history) Icon(UIcons.History, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(15.dp))
    }
}

internal fun statusText(bn: Boolean, s: GrantStatus) = when (s) {
    GrantStatus.PENDING -> t(bn, "অপেক্ষায়", "Pending")
    GrantStatus.ACCEPTED -> t(bn, "যুক্ত", "Active")
    GrantStatus.DECLINED -> t(bn, "ফিরিয়ে দিয়েছেন", "Declined")
}

internal fun statusColor(s: GrantStatus) = when (s) {
    GrantStatus.PENDING -> RokkhonPalette.Warning
    GrantStatus.ACCEPTED -> RokkhonPalette.Positive
    GrantStatus.DECLINED -> RokkhonPalette.Muted
}

@Composable
internal fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 44.dp, dark: Boolean = false) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (dark) RokkhonPalette.Primary else RokkhonPalette.Chip),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, color = if (dark) Color.White else RokkhonPalette.Ink)
    }
}

@Composable
private fun PermissionSwitches(bn: Boolean, perms: SosPerms, onChange: (SosPerms) -> Unit) {
    SosCard {
        SwitchRow(UIcons.LocationOn, t(bn, "লাইভ লোকেশন দেখবে", "View live location"),
            t(bn, "SOS চলাকালীন ম্যাপে আপনার অবস্থান", "Your position on a map while SOS is on"),
            perms.location, { onChange(perms.copy(location = it)) }, testTag = "perm_location")
        SwitchRow(UIcons.Mic, t(bn, "লাইভ অডিও শুনবে", "Listen to live audio"),
            t(bn, "শুধু যখন আপনি SOS-এ মাইক্রোফোন চালু করবেন", "Only when you turn the microphone on in an SOS"),
            perms.audio, { onChange(perms.copy(audio = it)) }, testTag = "perm_audio")
        SwitchRow(UIcons.NotificationsActive, t(bn, "SOS অ্যালার্ট পাবে", "Receive SOS alerts"),
            t(bn, "SOS শুরু হলেই নোটিফিকেশন", "A notification the moment you start"),
            perms.alerts, { onChange(perms.copy(alerts = it)) }, testTag = "perm_alerts")
        SwitchRow(UIcons.History, t(bn, "আগের SOS দেখবে", "View SOS history"),
            t(bn, "শেষ হওয়া SOS-এর সময় আর শেষ অবস্থান", "Times and last position of past SOS"),
            perms.history, { onChange(perms.copy(history = it)) }, testTag = "perm_history")
    }
}

@Composable
private fun AddPersonSheet(vm: PrivateSosViewModel, bn: Boolean, onDismiss: () -> Unit) {
    val search by vm.search.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var perms by remember { mutableStateOf(SosPerms(location = true, audio = false, alerts = true, history = false)) }

    RokkhonBottomSheet(onDismiss = onDismiss, title = t(bn, "কাকে যোগ করবেন?", "Who should we add?"), testTag = "access_add_sheet") {
        Text(
            t(bn, "তার Rokkhon অ্যাকাউন্টের পুরো ইমেইল বা ফোন নম্বর লিখুন। গোপনীয়তার জন্য নাম দিয়ে খোঁজা যায় না।",
                "Type the exact email or phone number of their Rokkhon account. For privacy you can't search by name."),
            fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RokkhonPalette.Subtle)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(UIcons.Search, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(t(bn, "name@gmail.com বা 01XXXXXXXXX", "name@gmail.com or 01XXXXXXXXX"), color = RokkhonPalette.Faint, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; if (search !is AccountSearch.Idle) vm.clearSearch() },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = RokkhonPalette.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchAccount(query) }),
                    modifier = Modifier.fillMaxWidth().testTag("access_search_input")
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (val s = search) {
            AccountSearch.Idle, AccountSearch.Searching -> PrimaryButton(
                if (s == AccountSearch.Searching) t(bn, "খুঁজছি…", "Searching…") else t(bn, "খুঁজুন", "Find"),
                onClick = { vm.searchAccount(query) },
                enabled = s != AccountSearch.Searching && query.isNotBlank(),
                testTag = "access_search_button"
            )
            AccountSearch.NotFound -> Hint(t(bn, "এই ইমেইল/নম্বরে কোনো Rokkhon অ্যাকাউন্ট পাওয়া যায়নি। তাকে একবার অ্যাপে লগইন করতে বলুন।", "No Rokkhon account uses this. Ask them to sign in to the app once."))
            AccountSearch.Invalid -> Hint(t(bn, "পুরো ইমেইল বা ফোন নম্বর দিন।", "Enter a full email or phone number."))
            AccountSearch.Self -> Hint(t(bn, "এটা আপনার নিজের অ্যাকাউন্ট।", "That's your own account."))
            is AccountSearch.Found -> {
                FoundCard(bn, s.account, s.existing)
                Spacer(Modifier.height(12.dp))
                if (s.existing?.status == GrantStatus.ACCEPTED || s.existing?.status == GrantStatus.PENDING) {
                    Hint(t(bn, "ইতিমধ্যে আপনার তালিকায় আছেন। তালিকা থেকে অনুমতি বদলান।", "Already on your list. Change permissions from the list."))
                } else {
                    Text(t(bn, "সে কী কী পারবে", "What they can do"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                    Spacer(Modifier.height(8.dp))
                    PermissionSwitches(bn, perms) { perms = it }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        t(bn, "আমন্ত্রণ পাঠান", "Send invitation"),
                        onClick = { vm.invite(s.account, perms); onDismiss() },
                        enabled = !busy && perms.any,
                        testTag = "access_send_invite"
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Ink, modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(RokkhonPalette.Subtle)
        .padding(14.dp)
        .testTag("access_hint"))
}

@Composable
private fun FoundCard(bn: Boolean, account: SosAccount, existing: SosGrant?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RokkhonPalette.Subtle)
            .padding(12.dp)
            .testTag("access_found"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(account.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            Text(t(bn, "Rokkhon অ্যাকাউন্ট পাওয়া গেছে", "Rokkhon account found"), fontSize = 12.5.sp, color = RokkhonPalette.Muted)
        }
        existing?.let { StatusPill(statusText(bn, it.status), statusColor(it.status)) }
    }
}

@Composable
private fun EditGrantSheet(vm: PrivateSosViewModel, bn: Boolean, grant: SosGrant, onDismiss: () -> Unit) {
    var confirmRevoke by remember { mutableStateOf(false) }
    RokkhonBottomSheet(onDismiss = onDismiss, title = grant.viewerName, testTag = "access_edit_sheet") {
        StatusPill(statusText(bn, grant.status), statusColor(grant.status))
        Spacer(Modifier.height(12.dp))
        if (grant.status == GrantStatus.DECLINED) {
            Hint(t(bn, "উনি আমন্ত্রণ ফিরিয়ে দিয়েছেন। চাইলে আবার পাঠাতে পারেন।", "They declined. You can invite them again."))
            Spacer(Modifier.height(10.dp))
            PrimaryButton(t(bn, "আবার আমন্ত্রণ পাঠান", "Invite again"), onClick = {
                vm.invite(SosAccount(grant.viewerUid, grant.viewerName), grant.perms)
            })
            Spacer(Modifier.height(12.dp))
        }
        PermissionSwitches(bn, grant.perms) { vm.updatePerms(grant, it) }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(!confirmRevoke) {
            SecondaryButton(
                t(bn, "অ্যাক্সেস বাতিল করুন", "Remove access"),
                onClick = { confirmRevoke = true },
                icon = UIcons.Block,
                modifier = Modifier.fillMaxWidth(),
                testTag = "access_revoke"
            )
        }
        AnimatedVisibility(confirmRevoke) {
            Column {
                Text(
                    t(bn, "${grant.viewerName} সাথে সাথে আর আপনার লোকেশন, অডিও বা অ্যালার্ট পাবেন না — চলমান SOS-ও।",
                        "${grant.viewerName} immediately loses your location, audio and alerts — including a running SOS."),
                    fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Ink
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(t(bn, "হ্যাঁ, বাতিল করুন", "Yes, remove"), onClick = { vm.revoke(grant); onDismiss() }, danger = true, testTag = "access_revoke_confirm")
            }
        }
    }
}

@Composable
private fun AddedMeList(bn: Boolean, grants: List<SosGrant>, vm: PrivateSosViewModel, onOpenHistory: (SosGrant) -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.navigationBarsPadding()
    ) {
        item {
            Text(
                t(bn, "এরা চান বিপদে পড়লে আপনি খবর পান। আপনি রাজি হলে তবেই তাদের SOS আপনার কাছে আসবে।",
                    "These people want you to know if they're in trouble. Their SOS reaches you only if you accept."),
                fontSize = 13.sp, lineHeight = 19.sp, color = RokkhonPalette.Muted
            )
        }
        if (grants.isEmpty()) {
            item {
                RokkhonEmptyState(
                    icon = UIcons.Shield,
                    title = t(bn, "কোনো আমন্ত্রণ নেই", "No invitations"),
                    body = t(bn, "কেউ আপনাকে তাদের SOS-এ যোগ করলে এখানে দেখাবে।", "When someone adds you to their SOS, it shows up here."),
                    testTag = "added_me_empty"
                )
            }
        }
        items(grants, key = { it.ownerUid }) { g ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RokkhonPalette.Subtle)
                    .padding(14.dp)
                    .testTag("added_me_row")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(g.ownerName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.ownerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(statusText(bn, g.status), statusColor(g.status))
                            Spacer(Modifier.width(8.dp))
                            PermIcons(g.perms)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(permSummary(bn, g.perms), fontSize = 12.5.sp, lineHeight = 18.sp, color = RokkhonPalette.Muted)
                Spacer(Modifier.height(10.dp))
                when (g.status) {
                    GrantStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(t(bn, "না", "Decline"), onClick = { vm.respond(g, false) }, modifier = Modifier.weight(1f), testTag = "invite_decline")
                        Box(Modifier.weight(1f)) {
                            PrimaryButton(t(bn, "রাজি", "Accept"), onClick = { vm.respond(g, true) }, enabled = !busy, testTag = "invite_accept")
                        }
                    }
                    GrantStatus.ACCEPTED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (g.perms.history) {
                            SecondaryButton(t(bn, "আগের SOS", "Past SOS"), onClick = { onOpenHistory(g) }, icon = UIcons.History, modifier = Modifier.weight(1f))
                        }
                        SecondaryButton(t(bn, "সরে আসুন", "Leave"), onClick = { vm.leave(g) }, icon = UIcons.Logout, modifier = Modifier.weight(1f), testTag = "invite_leave")
                    }
                    GrantStatus.DECLINED -> SecondaryButton(t(bn, "তালিকা থেকে মুছুন", "Remove"), onClick = { vm.leave(g) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun permSummary(bn: Boolean, p: SosPerms): String {
    val parts = buildList {
        if (p.alerts) add(t(bn, "অ্যালার্ট", "alerts"))
        if (p.location) add(t(bn, "লাইভ লোকেশন", "live location"))
        if (p.audio) add(t(bn, "লাইভ অডিও", "live audio"))
        if (p.history) add(t(bn, "আগের SOS", "past SOS"))
    }
    return if (parts.isEmpty()) t(bn, "এখনও কোনো অনুমতি দেননি", "No permissions yet")
    else t(bn, "আপনি পাবেন: ", "You get: ") + parts.joinToString(", ")
}
