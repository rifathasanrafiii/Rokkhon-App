package com.example.ui.sos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sos.GrantStatus
import com.example.data.sos.SosContact
import com.example.data.sos.SosGrant
import com.example.data.sos.SosPerms
import com.example.data.sos.SosSms
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.PrivateSosViewModel

/** One person on the SOS list: a number the user added, and the Rokkhon access that comes with it. */
internal data class SosPerson(val key: String, val name: String, val phone: String?, val grant: SosGrant?, val contact: SosContact?)

/** Numbers first, in the order they were added; access given before numbers existed comes after. */
internal fun sosPeople(contacts: List<SosContact>, viewers: List<SosGrant>): List<SosPerson> {
    val byUid = viewers.associateBy { it.viewerUid }
    val linked = contacts.mapNotNull { it.uid }.toSet()
    return contacts.map { c -> SosPerson(c.key, c.name, c.phone, c.uid?.let(byUid::get), c) } +
        viewers.filter { it.viewerUid !in linked }.map { g -> SosPerson("uid_${g.viewerUid}", g.viewerName, null, g, null) }
}

internal fun personTags(bn: Boolean, p: SosPerson): String = listOfNotNull(
    if (p.grant?.perms?.location == true) t(bn, "লোকেশন", "Location") else null,
    if (p.grant?.perms?.audio == true) t(bn, "অডিও", "Audio") else null,
    if (p.phone != null) "SMS" else null
).joinToString(" · ")

@Composable
internal fun PeopleSection(bn: Boolean, people: List<SosPerson>, max: Int, onAdd: () -> Unit, onOpen: (SosPerson) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t(bn, "যারা খবর পাবেন", "Who gets told"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.weight(1f))
        if (people.isNotEmpty() && people.size < max) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(RokkhonPalette.Subtle).springClick(onClick = onAdd).padding(horizontal = 12.dp, vertical = 7.dp).testTag("sos_add_person_small"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(UIcons.Add, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(t(bn, "যোগ", "Add"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            }
        }
    }
    if (people.isEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RokkhonPalette.Subtle)
                .springClick(pressedScale = 0.98f, onClick = onAdd)
                .padding(16.dp)
                .testTag("sos_add_person"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle(UIcons.Add)
            Spacer(Modifier.width(12.dp))
            Text(t(bn, "নম্বর যোগ করুন", "Add a number"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
        }
        return
    }
    SosCard {
        people.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .springClick(pressedScale = 0.99f) { onOpen(p) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("sos_person_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(p.name, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, maxLines = 1)
                    Text(personTags(bn, p), fontSize = 12.5.sp, color = RokkhonPalette.Muted, maxLines = 1)
                }
                Icon(UIcons.KeyboardArrowRight, null, tint = RokkhonPalette.Faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Picks one phone number from the phone book; the picked row is readable without a contacts permission. */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun readPicked(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
}.getOrNull()

@Composable
internal fun AddPersonSheet(vm: PrivateSosViewModel, bn: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var perms by remember { mutableStateOf(SosPerms(location = true, audio = true, alerts = true, history = false)) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDismiss() }
    val picker = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        uri?.let { readPicked(context, it) }?.let { (n, p) ->
            if (name.isBlank()) name = n.take(40)
            phone = p.filter { ch -> ch.isDigit() || ch == '+' }.take(14)
        }
    }

    RokkhonBottomSheet(onDismiss = onDismiss, title = t(bn, "নম্বর যোগ করুন", "Add a number"), testTag = "sos_add_sheet") {
        SheetField(name, { name = it.take(40) }, t(bn, "নাম", "Name"), KeyboardType.Text, "sos_add_name")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SheetField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) }, "01XXXXXXXXX", KeyboardType.Phone, "sos_add_phone")
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(RokkhonPalette.Subtle)
                    .springClick { runCatching { picker.launch(Unit) } }.testTag("sos_add_pick"),
                contentAlignment = Alignment.Center
            ) {
                Icon(UIcons.People, contentDescription = t(bn, "কন্টাক্ট", "Contacts"), tint = RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        PermSwitches(bn, perms) { perms = it }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(t(bn, "যোগ করুন", "Add"), enabled = phone.isNotBlank(), testTag = "sos_add_save", onClick = {
            if (vm.addContact(name, phone, perms)) {
                if (!vm.hasSmsPermission && SosSms.hasTelephony(context)) smsPermission.launch(Manifest.permission.SEND_SMS)
                else onDismiss()
            }
        })
    }
}

@Composable
internal fun EditPersonSheet(vm: PrivateSosViewModel, bn: Boolean, person: SosPerson, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    RokkhonBottomSheet(onDismiss = onDismiss, title = person.name, testTag = "sos_edit_sheet") {
        person.phone?.let { Text(it, fontSize = 14.sp, color = RokkhonPalette.Muted, modifier = Modifier.padding(bottom = 12.dp)) }
        person.grant?.takeIf { it.status == GrantStatus.ACCEPTED || it.status == GrantStatus.PENDING }?.let { g ->
            PermSwitches(bn, g.perms) { vm.updatePerms(g, it) }
            Spacer(Modifier.height(12.dp))
        }
        if (!confirm) {
            SecondaryButton(t(bn, "সরিয়ে দিন", "Remove"), onClick = { confirm = true }, icon = UIcons.Block, modifier = Modifier.fillMaxWidth(), testTag = "sos_remove_person")
        } else {
            PrimaryButton(t(bn, "হ্যাঁ, সরিয়ে দিন", "Yes, remove"), danger = true, testTag = "sos_remove_confirm", onClick = {
                when {
                    person.contact != null -> vm.removeContact(person.contact)
                    person.grant != null -> vm.revoke(person.grant)
                }
                onDismiss()
            })
        }
    }
}

@Composable
private fun PermSwitches(bn: Boolean, perms: SosPerms, onChange: (SosPerms) -> Unit) {
    SosCard {
        SwitchRow(UIcons.LocationOn, t(bn, "লাইভ লোকেশন", "Live location"), null, perms.location, { onChange(perms.copy(location = it, alerts = true)) }, testTag = "perm_location")
        HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(horizontal = 16.dp))
        SwitchRow(UIcons.Mic, t(bn, "লাইভ অডিও", "Live audio"), null, perms.audio, { onChange(perms.copy(audio = it, alerts = true)) }, testTag = "perm_audio")
    }
}

@Composable
internal fun SheetField(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, testTag: String) {
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

/** A status chip for one person while the SOS runs: the furthest they got. */
internal fun personLiveStatus(bn: Boolean, p: SosPerson, listener: com.example.data.sos.SosListener?, seen: Long?, notify: String?, sms: com.example.data.sos.SmsStatus, now: Long): Pair<String, androidx.compose.ui.graphics.Color> {
    val smsFailed = p.phone != null && sms is com.example.data.sos.SmsStatus.Sent && p.phone in sms.failedNumbers
    val smsSent = p.phone != null && sms is com.example.data.sos.SmsStatus.Sent && !smsFailed
    return when {
        listener?.recording == true -> t(bn, "রেকর্ড করছেন", "Recording") to RokkhonPalette.Sos
        listener?.state == "connected" -> t(bn, "শুনছেন", "Listening") to RokkhonPalette.Positive
        seen != null -> t(bn, "দেখছেন · ${ago(bn, seen, now)}", "Watching · ${ago(bn, seen, now)}") to RokkhonPalette.Positive
        notify == "sent" -> t(bn, "নোটিফিকেশন গেছে", "Notified") to RokkhonPalette.Ink
        smsSent -> t(bn, "SMS গেছে", "SMS sent") to RokkhonPalette.Ink
        smsFailed -> t(bn, "SMS যায়নি", "SMS failed") to RokkhonPalette.Warning
        p.phone != null && sms == com.example.data.sos.SmsStatus.Sending -> t(bn, "পাঠানো হচ্ছে…", "Sending…") to RokkhonPalette.Muted
        else -> t(bn, "এখনও দেখেননি", "Not opened yet") to RokkhonPalette.Muted
    }
}
