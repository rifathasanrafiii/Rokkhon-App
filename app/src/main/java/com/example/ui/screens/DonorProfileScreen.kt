package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.GUARDIAN_REWARD_TIERS
import com.example.data.model.isValidBangladeshMobile
import com.example.data.model.parseDonationDateMillis
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.InkText
import com.example.ui.components.MutedText
import com.example.ui.components.StatusChip
import com.example.ui.components.donationStatusText
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Profile: donor details (validated), availability, last donation date (drives the 90-day rule),
 * streak/points badges and account actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val dailyStreakCount by viewModel.dailyStreakCount.collectAsStateWithLifecycle()
    val checkedInToday by viewModel.checkedInToday.collectAsStateWithLifecycle()
    val guardianPoints by viewModel.guardianPoints.collectAsStateWithLifecycle()

    val myDonor = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == viewModel.currentUserId }

    // Re-initialise the form when the stored profile first arrives (cloud loads after the first frame).
    val formKey = myDonor?.updatedAt?.seconds ?: myDonor?.userId ?: "new"
    var displayName by rememberSaveable(formKey) {
        mutableStateOf(myDonor?.displayName?.ifBlank { null } ?: viewModel.currentUserName.takeUnless { it == "Guest" || it == "Demo User" }.orEmpty())
    }
    var bloodGroup by rememberSaveable(formKey) { mutableStateOf(myDonor?.bloodGroup) }
    var district by rememberSaveable(formKey) { mutableStateOf(myDonor?.district?.takeIf { it.isNotBlank() } ?: selectedDistrict.nameEn) }
    var contactPhone by rememberSaveable(formKey) { mutableStateOf(myDonor?.contactPhone.orEmpty()) }
    var lastDonationDate by rememberSaveable(formKey) { mutableStateOf(myDonor?.lastDonationDate.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    var showAreaPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val isAvailable = myDonor?.isAvailableDonor ?: true
    val phoneError = !isValidBangladeshMobile(contactPhone)
    val nameError = displayName.isBlank()
    val groupError = bloodGroup == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F8))
            .verticalScroll(rememberScrollState())
            .imePadding()
            .testTag("profile_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(myDonor?.bloodGroup ?: "?", color = IosSystemRed, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = myDonor?.displayName?.ifBlank { null } ?: viewModel.currentUserName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if ((myDonor?.verifiedSosCount ?: 0) > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = when {
                            viewModel.isDemoMode -> if (bn) "ডেমো মোড • শুধু এই ফোনে সেভ হয়" else "Demo mode • saved on this phone only"
                            myDonor == null -> if (bn) "প্রোফাইল এখনো তৈরি হয়নি" else "Profile not created yet"
                            else -> (if (bn) "ট্রাস্ট স্কোর " else "Trust score ") + "${myDonor.trustScore}%"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    if (myDonor != null) {
                        Spacer(modifier = Modifier.height(5.dp))
                        val (status, color) = donationStatusText(myDonor, language)
                        StatusChip(text = status, color = if (color == MutedText) Color.White else color)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    icon = Icons.Default.Whatshot,
                    value = "$dailyStreakCount",
                    label = if (bn) "দিনের স্ট্রিক" else "day streak",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    icon = Icons.Default.Stars,
                    value = "$guardianPoints",
                    label = if (bn) "পয়েন্ট" else "points",
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.performDailySafeCheckIn() },
                enabled = !checkedInToday,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE01E37),
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = Color.White
                ),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_daily_checkin_button")
            ) {
                Text(
                    text = if (checkedInToday) {
                        if (bn) "আজকের চেক-ইন সম্পন্ন ✓" else "Checked in today ✓"
                    } else {
                        if (bn) "আজ আমি নিরাপদ ও প্রস্তুত (+২৫)" else "I'm safe & ready today (+25)"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (bn) "রক্তদানে উপলব্ধ" else "Available to donate", color = InkText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (isAvailable) {
                                if (bn) "রোগীরা আপনাকে ডোনার তালিকায় দেখবেন" else "Patients can see you in donor lists"
                            } else {
                                if (bn) "আপনি ডোনার তালিকায় লুকানো" else "You are hidden from donor lists"
                            },
                            color = MutedText,
                            fontSize = 12.5.sp
                        )
                    }
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { viewModel.toggleDonorActiveStatus(myDonor, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosSystemGreen),
                        modifier = Modifier.testTag("profile_donor_availability_switch")
                    )
                }
            }

            ProfileCard {
                Text(if (bn) "ডোনার তথ্য" else "Donor details", color = InkText, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    label = { Text(if (bn) "আপনার নাম *" else "Your name *") },
                    isError = showErrors && nameError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (bn) "রক্তের গ্রুপ *" else "Blood group *",
                    color = if (showErrors && groupError) IosSystemRed else InkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                BloodGroupPillGrid2x4(selectedBloodGroup = bloodGroup, onSelectBloodGroup = { bloodGroup = it })
                Spacer(modifier = Modifier.height(14.dp))
                PickerField(
                    label = if (bn) "এলাকা" else "Area",
                    value = district,
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { showAreaPicker = true },
                    tag = "profile_open_64_districts_button"
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )
                Spacer(modifier = Modifier.height(4.dp))
                PickerField(
                    label = if (bn) "শেষ রক্তদানের তারিখ" else "Last donation date",
                    value = lastDonationDate.ifBlank { if (bn) "কখনো দেইনি / মনে নেই" else "Never / not sure" },
                    icon = Icons.Default.CalendarMonth,
                    onClick = { showDatePicker = true },
                    tag = "profile_last_donation_picker"
                )
                if (lastDonationDate.isNotBlank()) {
                    TextButton(onClick = { lastDonationDate = "" }) {
                        Text(if (bn) "তারিখ মুছুন" else "Clear date", color = MutedText)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = if (bn) "শেষ রক্তদানের ৯০ দিন পর্যন্ত আপনাকে 'উপলব্ধ' তালিকায় দেখানো হবে না।" else "For 90 days after a donation you won't be listed as available.",
                    color = MutedText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (nameError || groupError || phoneError) {
                            showErrors = true
                        } else {
                            viewModel.saveDonorProfile(
                                displayName = displayName,
                                bloodGroup = bloodGroup ?: return@Button,
                                isAvailableDonor = isAvailable,
                                district = district,
                                contactPhone = contactPhone,
                                lastDonationDate = lastDonationDate
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_donor_profile_button")
                ) {
                    Text(if (bn) "প্রোফাইল সেভ করুন" else "Save profile", fontWeight = FontWeight.ExtraBold)
                }
            }

            ProfileCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFEAB308))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (bn) "গার্ডিয়ান ব্যাজ" else "Guardian badges", color = InkText, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                Text(
                    text = if (bn) "চেক-ইন +২৫ • রক্তদানে সাড়া +১০০ • SOS-এ সাড়া +৫০" else "Check-in +25 • donation pledge +100 • SOS response +50",
                    color = MutedText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                GUARDIAN_REWARD_TIERS.forEach { tier ->
                    val unlocked = guardianPoints >= tier.requiredPoints
                    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (unlocked) Color(0xFFFFF4D6) else Color(0xFFF1ECED)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (unlocked) Color(0xFFD99A00) else MutedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (bn) tier.titleBn else tier.titleEn, color = InkText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (bn) tier.subtitleBn else tier.subtitleEn, color = MutedText, fontSize = 12.sp)
                        }
                        StatusChip(
                            text = if (unlocked) (if (bn) "অর্জিত" else "Earned") else "${guardianPoints}/${tier.requiredPoints}",
                            color = if (unlocked) IosSystemGreen else IosSystemOrange
                        )
                    }
                }
            }

            ProfileCard {
                SettingsRow(
                    icon = Icons.Default.Translate,
                    title = if (bn) "ভাষা: বাংলা" else "Language: English",
                    subtitle = if (bn) "ইংরেজিতে বদলাতে ট্যাপ করুন" else "Tap to switch to Bangla",
                    onClick = { viewModel.toggleLanguage() },
                    tag = "profile_language_toggle"
                )
                SettingsRow(
                    icon = Icons.Default.HelpOutline,
                    title = if (bn) "অ্যাপ কীভাবে কাজ করে" else "How the app works",
                    subtitle = if (bn) "টিউটোরিয়াল আবার দেখুন" else "Show the tutorial again",
                    onClick = onOpenTutorial,
                    tag = "profile_open_tutorial"
                )
                if (viewModel.isGuestSession) {
                    if (canSignIn) {
                        SettingsRow(
                            icon = Icons.Default.Login,
                            title = if (bn) "গুগল দিয়ে সাইন-ইন করুন" else "Sign in with Google",
                            subtitle = if (bn) "আসল ডোনার নেটওয়ার্কে যোগ দিন" else "Join the real donor network",
                            onClick = onSignOut,
                            tag = "sign_in_button"
                        )
                    }
                } else {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = if (bn) "লগ আউট" else "Sign out",
                        subtitle = if (bn) "অন্য অ্যাকাউন্টে যেতে" else "Switch account",
                        onClick = onSignOut,
                        tag = "sign_out_button",
                        tint = IosSystemRed
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(if (bn) "বাতিল" else "Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun StatBox(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, tag: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF857375), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = MutedText, fontSize = 12.sp)
                Text(value, color = InkText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(icon, contentDescription = null, tint = InkText)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    tint: Color = IosSystemBlue
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (tint == IosSystemRed) LifelineBlush else tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = if (tint == IosSystemRed) IosSystemRed else InkText, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(subtitle, color = MutedText, fontSize = 12.sp)
        }
    }
}
