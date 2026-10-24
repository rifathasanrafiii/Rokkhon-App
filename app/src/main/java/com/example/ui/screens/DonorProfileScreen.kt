package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.DonorNode
import com.example.data.model.daysUntilEligible
import com.example.data.model.isValidBangladeshMobile
import com.example.data.model.parseDonationDateMillis
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.BloodBadge
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.IconTile
import com.example.ui.components.PrimaryButton
import com.example.ui.components.RkCard
import com.example.ui.components.ScreenHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.donationStatusText
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Profile: who you are as a donor (summary + edit form), availability, what you have done,
 * and settings.
 */
@Composable
fun DonorProfileScreen(
    viewModel: RokkhonViewModel,
    canSignIn: Boolean = true,
    onOpenTutorial: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val pledgedIds by viewModel.pledgedRequestIds.collectAsStateWithLifecycle()
    val respondedSosIds by viewModel.respondedSosIds.collectAsStateWithLifecycle()
    val guardEnabled by viewModel.guardNetworkEnabled.collectAsStateWithLifecycle()

    val me = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == viewModel.currentUserId }
    val hasProfile = me != null && me.contactPhone.isNotBlank()
    var showEditor by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rk.Bg)
            .verticalScroll(rememberScrollState())
            .testTag("profile_screen")
    ) {
        ScreenHeader(title = if (bn) "প্রোফাইল" else "Profile")
        Column(
            modifier = Modifier.padding(horizontal = Rk.Gutter, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (viewModel.isDemoMode) DemoModeBanner(language)

            if (hasProfile) {
                RkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BloodBadge(me!!.bloodGroup, size = 58, filled = true)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(me.displayName, color = Rk.Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(me.contactPhone, color = Rk.Muted, fontSize = 13.5.sp)
                            Text(me.district, color = Rk.Muted, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { showEditor = true }, modifier = Modifier.testTag("edit_profile_button")) {
                            Icon(Icons.Default.Edit, contentDescription = if (bn) "এডিট" else "Edit", tint = Rk.Ink)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Rk.Line)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (bn) "রক্তদানে উপলব্ধ" else "Available to donate", color = Rk.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val (status, color) = donationStatusText(me, language)
                            Spacer(modifier = Modifier.height(3.dp))
                            StatusChip(text = status, color = color)
                        }
                        Switch(
                            checked = me.isAvailableDonor,
                            onCheckedChange = { viewModel.toggleDonorActiveStatus(me, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Rk.Green),
                            modifier = Modifier.testTag("profile_donor_availability_switch")
                        )
                    }
                }
            } else {
                RkCard(color = Rk.Maroon, border = Rk.Maroon) {
                    Text(if (bn) "রক্তদাতা হিসেবে যোগ দিন" else "Become a donor", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (bn) "রক্তের গ্রুপ, এলাকা আর নম্বর দিন। আপনার এলাকায় আপনার গ্রুপের রক্ত দরকার হলে নোটিফিকেশন পাবেন।" else "Add your blood group, area and number. You'll be notified when your group is needed nearby.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PrimaryButton(
                        text = if (bn) "প্রোফাইল তৈরি করুন" else "Create profile",
                        onClick = { showEditor = true },
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth().testTag("create_profile_button")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(value = "${pledgedIds.size}", label = if (bn) "রক্তদানে সাড়া" else "Donation pledges", modifier = Modifier.weight(1f))
                StatTile(value = "${respondedSosIds.size}", label = if (bn) "SOS-এ সাহায্য" else "SOS responses", modifier = Modifier.weight(1f))
                StatTile(
                    value = me?.let { val d = daysUntilEligible(it.lastDonationDate); if (d == 0) (if (bn) "এখন" else "Now") else (if (bn) "$d দিন" else "${d}d") } ?: "-",
                    label = if (bn) "পরের রক্তদান" else "Next donation",
                    modifier = Modifier.weight(1f)
                )
            }

            RkCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                    title = if (bn) "সুরক্ষা নেটওয়ার্ক" else "Safety network",
                    subtitle = if (bn) "অ্যাপ বন্ধ থাকলেও ১ কি.মি.-এর SOS পাবেন ও ব্লুটুথে অন্যদের কাছে পৌঁছে দেবেন" else "Get SOS within 1 km and relay them over Bluetooth, even when the app is closed",
                    tag = "profile_guard_toggle",
                    trailing = {
                        Switch(
                            checked = guardEnabled,
                            onCheckedChange = { viewModel.setGuardNetworkEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Rk.Green)
                        )
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Translate,
                    title = if (bn) "ভাষা" else "Language",
                    subtitle = if (bn) "বাংলা • ইংরেজিতে বদলাতে ট্যাপ করুন" else "English • tap for Bangla",
                    onClick = { viewModel.toggleLanguage() },
                    tag = "profile_language_toggle"
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = if (bn) "অ্যাপ কীভাবে কাজ করে" else "How the app works",
                    subtitle = if (bn) "পরিচিতি আবার দেখুন" else "See the introduction again",
                    onClick = onOpenTutorial,
                    tag = "profile_open_tutorial"
                )
                if (viewModel.isGuestSession) {
                    if (canSignIn) {
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Login,
                            title = if (bn) "গুগল দিয়ে সাইন-ইন" else "Sign in with Google",
                            subtitle = if (bn) "আসল ডোনার নেটওয়ার্কে যোগ দিন" else "Join the real donor network",
                            onClick = onSignOut,
                            tag = "sign_in_button"
                        )
                    }
                } else {
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = if (bn) "লগ আউট" else "Sign out",
                        subtitle = null,
                        onClick = onSignOut,
                        tag = "sign_out_button",
                        danger = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showEditor) {
        DonorProfileEditor(
            viewModel = viewModel,
            language = language,
            me = me,
            onDone = { showEditor = false }
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    RkCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Text(value, color = Rk.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Rk.Muted, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = Rk.Line, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tag: String,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint = if (danger) Rk.Crimson else Rk.InkSoft, background = if (danger) Rk.Rose else Rk.SurfaceMuted, size = 38)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (danger) Rk.Crimson else Rk.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Rk.Muted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Rk.Faint)
        }
    }
}

/** Full-screen donor form with validation, area picker and last-donation date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonorProfileEditor(
    viewModel: RokkhonViewModel,
    language: AppLanguage,
    me: DonorNode?,
    onDone: () -> Unit
) {
    val bn = language == AppLanguage.BENGALI
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    var displayName by rememberSaveable {
        mutableStateOf(me?.displayName?.ifBlank { null } ?: viewModel.currentUserName.takeUnless { it == "Guest" || it == "Demo User" }.orEmpty())
    }
    var bloodGroup by rememberSaveable { mutableStateOf(me?.bloodGroup?.takeIf { me.contactPhone.isNotBlank() }) }
    var district by rememberSaveable { mutableStateOf(me?.district?.takeIf { it.isNotBlank() } ?: selectedDistrict.nameEn) }
    var contactPhone by rememberSaveable { mutableStateOf(me?.contactPhone.orEmpty()) }
    var lastDonationDate by rememberSaveable { mutableStateOf(me?.lastDonationDate.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    var showAreaPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val phoneError = !isValidBangladeshMobile(contactPhone)
    val nameError = displayName.isBlank()
    val groupError = bloodGroup == null

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Rk.Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("profile_editor")
        ) {
            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (bn) "ফিরে যান" else "Back", tint = Rk.Ink)
                }
                Text(if (bn) "ডোনার তথ্য" else "Donor details", color = Rk.Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = Rk.Gutter),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    label = { Text(if (bn) "আপনার নাম *" else "Your name *") },
                    isError = showErrors && nameError,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )
                Text(
                    text = if (bn) "রক্তের গ্রুপ *" else "Blood group *",
                    color = if (showErrors && groupError) Rk.Crimson else Rk.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                BloodGroupPillGrid2x4(selectedBloodGroup = bloodGroup, onSelectBloodGroup = { bloodGroup = it })
                PickerField(
                    label = if (bn) "এলাকা" else "Area",
                    value = district,
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { showAreaPicker = true },
                    tag = "profile_open_64_districts_button"
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (bn) "মোবাইল নম্বর *" else "Mobile number *") },
                    placeholder = { Text("01XXXXXXXXX") },
                    isError = showErrors && phoneError,
                    supportingText = {
                        Text(
                            if (showErrors && phoneError) {
                                if (bn) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number"
                            } else {
                                if (bn) "রোগীর পরিবার এই নম্বরে কল করবে" else "Patients' families will call this number"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                )
                PickerField(
                    label = if (bn) "শেষ রক্তদানের তারিখ" else "Last donation date",
                    value = lastDonationDate.ifBlank { if (bn) "কখনো দেইনি / মনে নেই" else "Never / not sure" },
                    icon = Icons.Default.CalendarMonth,
                    onClick = { showDatePicker = true },
                    tag = "profile_last_donation_picker"
                )
                Text(
                    text = if (bn) "শেষ রক্তদানের পর ৯০ দিন আপনাকে 'উপলব্ধ' তালিকায় দেখানো হবে না।" else "For 90 days after donating you won't be listed as available.",
                    color = Rk.Muted,
                    fontSize = 12.5.sp
                )
                if (lastDonationDate.isNotBlank()) {
                    TextButton(onClick = { lastDonationDate = "" }) { Text(if (bn) "তারিখ মুছুন" else "Clear date", color = Rk.Muted) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(modifier = Modifier.padding(Rk.Gutter)) {
                PrimaryButton(
                    text = if (bn) "সেভ করুন" else "Save",
                    onClick = {
                        if (nameError || groupError || phoneError) {
                            showErrors = true
                        } else {
                            val saved = viewModel.saveDonorProfile(
                                displayName = displayName,
                                bloodGroup = bloodGroup ?: return@PrimaryButton,
                                isAvailableDonor = me?.isAvailableDonor ?: true,
                                district = district,
                                contactPhone = contactPhone,
                                lastDonationDate = lastDonationDate
                            )
                            if (saved) onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_donor_profile_button")
                )
            }
        }
    }

    if (showAreaPicker) {
        val selected = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: selectedDistrict
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selected,
            isAutoGpsActive = false,
            onSelectAutoGps = {},
            onSelectDistrict = { dist -> district = dist.nameEn },
            onDismiss = { showAreaPicker = false }
        )
    }

    if (showDatePicker) {
        val utc = remember { TimeZone.getTimeZone("UTC") }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseDonationDateMillis(lastDonationDate),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        lastDonationDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text(if (bn) "ঠিক আছে" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (bn) "বাতিল" else "Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, tag: String) {
    Surface(
        color = Rk.Surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Rk.Faint),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Rk.Muted, fontSize = 12.sp)
                Text(value, color = Rk.Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(icon, contentDescription = null, tint = Rk.Ink)
        }
    }
}
