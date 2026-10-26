package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_AREAS
import com.example.data.model.DONATION_GAP_DAYS
import com.example.data.model.GUARDIAN_REWARD_TIERS
import com.example.data.model.daysUntilEligible
import com.example.data.model.isValidBangladeshMobile
import com.example.data.model.parseDonationDateMillis
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.BloodGroupGrid
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RollingNumber
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun DonorProfileScreen(
    viewModel: RokkhonViewModel,
    onSignOut: () -> Unit,
    onOpenSosAccess: (() -> Unit)? = null
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val dailyStreakCount by viewModel.dailyStreakCount.collectAsStateWithLifecycle()
    val checkedInToday by viewModel.checkedInToday.collectAsStateWithLifecycle()
    val guardianPoints by viewModel.guardianPoints.collectAsStateWithLifecycle()

    val myDonor = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == viewModel.currentUserId }

    var displayName by remember { mutableStateOf(myDonor?.displayName ?: viewModel.currentUserName) }
    var bloodGroup by remember { mutableStateOf(myDonor?.bloodGroup ?: "O+") }
    var isAvailableDonor by remember { mutableStateOf(myDonor?.isAvailableDonor ?: true) }
    var district by remember { mutableStateOf(myDonor?.district ?: selectedDistrict.nameEn) }
    // No sample number or text: whatever is saved here is shown to patients' families.
    var contactPhone by remember { mutableStateOf(myDonor?.contactPhone.orEmpty()) }
    var lastDonationDate by remember { mutableStateOf(myDonor?.lastDonationDate.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val phoneInvalid = !isValidBangladeshMobile(contactPhone)
    val dateInvalid = lastDonationDate.isNotBlank() && parseDonationDateMillis(lastDonationDate) == null

    LaunchedEffect(myDonor) {
        if (myDonor != null) {
            displayName = myDonor.displayName
            bloodGroup = myDonor.bloodGroup
            isAvailableDonor = myDonor.isAvailableDonor
            district = myDonor.district
            contactPhone = myDonor.contactPhone
            lastDonationDate = myDonor.lastDonationDate
        }
    }

    val bn = language == AppLanguage.BENGALI
    var showEditSheet by remember { mutableStateOf(false) }
    val donorEditorRequested by viewModel.donorEditorRequested.collectAsStateWithLifecycle()
    LaunchedEffect(donorEditorRequested) {
        if (donorEditorRequested) {
            showEditSheet = true
            viewModel.consumeDonorEditorRequest()
        }
    }
    var showDonatedConfirm by remember { mutableStateOf(false) }
    var showProfileAreaPicker by remember { mutableStateOf(false) }

    fun saveProfile(): Boolean {
        if (phoneInvalid || dateInvalid) {
            showErrors = true
            return false
        }
        viewModel.saveDonorProfile(
            displayName = displayName,
            bloodGroup = bloodGroup,
            isAvailableDonor = isAvailableDonor,
            district = district,
            contactPhone = contactPhone,
            lastDonationDate = lastDonationDate
        )
        return true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("profile_screen")
    ) {
        item(key = "profile_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName.ifBlank { if (bn) "রক্ষণ গার্ডিয়ান" else "Rokkhon Guardian" },
                            color = RokkhonPalette.Ink,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RokkhonPalette.Subtle)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(UIcons.LocationOn, contentDescription = null, tint = RokkhonPalette.Muted, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(district.substringBefore(","), color = RokkhonPalette.Muted, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(72.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(RokkhonPalette.Chip)
                                .springClick { showEditSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(bloodGroup, color = RokkhonPalette.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(if (isAvailableDonor) RokkhonPalette.Positive else RokkhonPalette.Faint)
                                .border(3.dp, Color.White, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat(
                        icon = UIcons.Whatshot,
                        value = dailyStreakCount,
                        label = if (checkedInToday) (if (bn) "দিনের স্ট্রিক" else "day streak") else (if (bn) "চেক-ইন করুন" else "Check in"),
                        highlight = !checkedInToday,
                        onClick = { if (!checkedInToday) viewModel.performDailySafeCheckIn() },
                        modifier = Modifier.weight(1f),
                        testTag = "profile_daily_checkin_button"
                    )
                    HeroStat(
                        icon = UIcons.Stars,
                        value = guardianPoints,
                        label = if (bn) "পয়েন্ট" else "points",
                        onClick = { viewModel.selectTab(RokkhonTab.REWARDS) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        icon = UIcons.CardGiftcard,
                        value = GUARDIAN_REWARD_TIERS.count { guardianPoints >= it.requiredPoints },
                        label = if (bn) "ব্যাজ" else "badges",
                        onClick = { viewModel.selectTab(RokkhonTab.REWARDS) },
                        modifier = Modifier.weight(1f),
                        testTag = "profile_open_rewards"
                    )
                }
            }
        }

        item(key = "profile_sheet") {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    if (myDonor == null) {
                        // Not registered yet: the one thing to do on this page
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.Black)
                                .springClick(pressedScale = 0.97f) { showEditSheet = true }
                                .padding(16.dp)
                                .testTag("profile_register_cta"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(UIcons.Bloodtype, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (bn) "ডোনার হিসেবে নিবন্ধন করুন" else "Register as a donor", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (bn) "১ মিনিট • এলাকার রোগীরা আপনাকে খুঁজে পাবে" else "1 minute • patients nearby can find you", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            Icon(UIcons.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Availability: one switch
                    SettingsGroup {
                        SettingsRow(
                            icon = UIcons.Bloodtype,
                            iconTint = if (isAvailableDonor) RokkhonPalette.Positive else RokkhonPalette.Ink,
                            title = if (bn) "রক্ত দিতে প্রস্তুত" else "Available to donate",
                            value = if (isAvailableDonor) (if (bn) "ডোনার তালিকায় দেখা যাচ্ছে" else "Shown in donor lists") else (if (bn) "লুকানো" else "Hidden"),
                            onClick = {
                                isAvailableDonor = !isAvailableDonor
                                viewModel.toggleDonorActiveStatus(myDonor, isAvailableDonor)
                            },
                            trailing = {
                                Switch(
                                    checked = isAvailableDonor,
                                    onCheckedChange = {
                                        isAvailableDonor = it
                                        viewModel.toggleDonorActiveStatus(myDonor, it)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Black),
                                    modifier = Modifier.testTag("profile_donor_availability_switch")
                                )
                            }
                        )
                    }

                    if (myDonor != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DonationCycleCard(
                            bn = bn,
                            lastDonationDate = lastDonationDate,
                            onDonatedToday = { showDonatedConfirm = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader(
                        title = if (bn) "ডোনার তথ্য" else "Donor details",
                        trailing = {
                            RoundIconButton(
                                icon = UIcons.Edit,
                                onClick = { showEditSheet = true },
                                contentDescription = if (bn) "এডিট" else "Edit",
                                testTag = "profile_edit_button"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsGroup {
                        SettingsRow(UIcons.Bloodtype, RokkhonPalette.Ink, if (bn) "রক্তের গ্রুপ" else "Blood group", bloodGroup, { showEditSheet = true })
                        SettingsDivider()
                        SettingsRow(
                            UIcons.Phone, RokkhonPalette.Ink, if (bn) "জরুরি ফোন" else "Emergency phone",
                            contactPhone.ifBlank { if (bn) "যোগ করুন" else "Add" }, { showEditSheet = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            UIcons.Event, RokkhonPalette.Ink, if (bn) "শেষ রক্তদান" else "Last donation",
                            lastDonationDate.ifBlank { if (bn) "জানা নেই" else "Not set" }, { showEditSheet = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            UIcons.LocationOn, RokkhonPalette.Ink, if (bn) "এলাকা" else "Area", district,
                            { showProfileAreaPicker = true },
                            testTag = "profile_open_64_districts_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroup {
                        SettingsRow(
                            UIcons.CardGiftcard, RokkhonPalette.Ink, if (bn) "রিওয়ার্ড ও ব্যাজ" else "Rewards & badges",
                            if (bn) "${bnDigits(guardianPoints)} পয়েন্ট" else "$guardianPoints pts",
                            { viewModel.selectTab(RokkhonTab.REWARDS) },
                            testTag = "profile_rewards_row"
                        )
                        if (onOpenSosAccess != null) {
                            SettingsDivider()
                            SettingsRow(
                                UIcons.Shield, RokkhonPalette.Ink, if (bn) "SOS অ্যাক্সেস" else "SOS Access",
                                if (bn) "কে দেখবে" else "Who sees it", onOpenSosAccess,
                                testTag = "profile_sos_access"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroup {
                        SettingsRow(
                            icon = UIcons.Logout,
                            iconTint = RokkhonPalette.Ink,
                            title = if (bn) "লগ আউট" else "Sign out",
                            value = null,
                            onClick = onSignOut,
                            titleColor = RokkhonPalette.Ink,
                            testTag = "sign_out_button"
                        )
                    }

                    Image(
                        painter = painterResource(R.drawable.rokkhon_wordmark_black),
                        contentDescription = "রক্ষণ",
                        modifier = Modifier
                            .padding(top = 26.dp)
                            .height(22.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Icons: Uicons by Flaticon",
                        color = RokkhonPalette.Faint,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(top = 6.dp, bottom = 12.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (showProfileAreaPicker) {
        BangladeshAreaPickerDialog(
            selectedDistrict = BANGLADESH_AREAS.firstOrNull { district.equals(it.nameEn, ignoreCase = true) } ?: selectedDistrict,
            language = language,
            onDetectLocation = { done -> viewModel.detectNearestArea(done) },
            onSelectDistrict = { picked ->
                district = picked.nameEn
                viewModel.selectDistrict(picked)
                if (myDonor != null) saveProfile()
            },
            onDismiss = { showProfileAreaPicker = false }
        )
    }

    if (showDonatedConfirm) {
        RokkhonBottomSheet(
            onDismiss = { showDonatedConfirm = false },
            title = if (bn) "আজ রক্ত দিয়েছেন?" else "Did you donate today?",
            testTag = "donated_today_sheet"
        ) {
            Text(
                text = if (bn) {
                    "ধন্যবাদ! পরের ৯০ দিন আপনাকে তালিকায় \"বিশ্রামে\" দেখানো হবে। তারপর আপনি নিজে থেকেই আবার সক্রিয় হয়ে যাবেন — কিছু চালু করতে হবে না।"
                } else {
                    "Thank you! For the next 90 days you'll show as resting. After that you're active again automatically — nothing to switch on."
                },
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = RokkhonPalette.Muted
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .springClick {
                        viewModel.markDonatedToday()
                        lastDonationDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        showDonatedConfirm = false
                    }
                    .testTag("confirm_donated_today"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (bn) "হ্যাঁ, আজ দিয়েছি" else "Yes, I donated today", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (bn) "না, ভুল করে চাপ লেগেছে" else "No, cancel",
                fontSize = 15.sp,
                color = RokkhonPalette.Ink,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .springClick { showDonatedConfirm = false }
                    .padding(vertical = 12.dp)
            )
        }
    }

    if (showEditSheet) {
        RokkhonBottomSheet(
            onDismiss = { showEditSheet = false },
            title = if (bn) "ডোনার তথ্য" else "Donor details",
            testTag = "profile_edit_sheet"
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (bn) "রক্তের গ্রুপ" else "Blood group", fontSize = 13.sp, color = RokkhonPalette.Muted, fontWeight = FontWeight.SemiBold)
                BloodGroupGrid(selected = bloodGroup, onSelect = { bloodGroup = it }, tagPrefix = "profile_group_")
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(if (bn) "আপনার নাম" else "Full name") },
                    leadingIcon = { Icon(UIcons.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (bn) "জরুরি মোবাইল নম্বর *" else "Emergency phone *") },
                    leadingIcon = { Icon(UIcons.Phone, contentDescription = null) },
                    placeholder = { Text("01XXXXXXXXX") },
                    isError = showErrors && phoneInvalid,
                    supportingText = if (showErrors && phoneInvalid) {
                        { Text(if (bn) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastDonationDate,
                    onValueChange = { lastDonationDate = it },
                    label = { Text(if (bn) "শেষ রক্তদানের তারিখ" else "Last donation date") },
                    leadingIcon = { Icon(UIcons.Event, contentDescription = null) },
                    placeholder = { Text("YYYY-MM-DD") },
                    isError = showErrors && dateInvalid,
                    // The 90-day rule needs a real date; blank means never / not sure.
                    supportingText = {
                        Text(
                            if (showErrors && dateInvalid) {
                                if (bn) "তারিখ এভাবে লিখুন: 2025-01-31" else "Write the date like 2025-01-31"
                            } else {
                                if (bn) "রক্ত দেওয়ার পর ৯০ দিন 'উপলব্ধ' দেখাবে না" else "Not listed as available for 90 days after donating"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text(if (bn) "এলাকা / জেলা" else "Area / district") },
                    leadingIcon = { Icon(UIcons.LocationOn, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            UIcons.Search,
                            contentDescription = if (bn) "৬৪ জেলা" else "64 districts",
                            modifier = Modifier.springClick { showProfileAreaPicker = true }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Button(
                    onClick = { if (saveProfile()) showEditSheet = false },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = RokkhonPalette.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_donor_profile_button")
                ) {
                    Icon(UIcons.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (bn) "সেভ করুন" else "Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Grey tile in the profile header: thin icon, rolling number, one word (Uber's Help/Wallet/Activity row). */
@Composable
private fun HeroStat(
    icon: ImageVector,
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    highlight: Boolean = false,
    testTag: String = ""
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Color.Black else RokkhonPalette.Subtle)
            .springClick(onClick = onClick)
            .padding(vertical = 14.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (highlight) Color.White else RokkhonPalette.Ink, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(6.dp))
        RollingNumber(value = value, fontSize = 20.sp, color = if (highlight) Color.White else RokkhonPalette.Ink, fontWeight = FontWeight.Bold, suffix = suffix)
        Text(label, color = if (highlight) Color.White.copy(alpha = 0.8f) else RokkhonPalette.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RokkhonPalette.Subtle),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(1.dp)
            .background(Color(0xFFE8E8E8))
    )
}

/** Icon square, title, current value, chevron (or a custom trailing control). */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String?,
    onClick: () -> Unit,
    testTag: String = "",
    titleColor: Color = RokkhonPalette.Ink,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springClick(pressedScale = 0.98f, onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = titleColor)
            if (value != null) {
                Text(value, fontSize = 12.5.sp, color = RokkhonPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(UIcons.KeyboardArrowRight, contentDescription = null, tint = RokkhonPalette.Faint, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Where the donor is in the 90-day cycle: resting with a countdown, or able to give now.
 * Nothing needs switching back on — the app shows them as available again after 90 days.
 */
@Composable
private fun DonationCycleCard(bn: Boolean, lastDonationDate: String, onDonatedToday: () -> Unit) {
    val last = parseDonationDateMillis(lastDonationDate)
    val daysLeft = daysUntilEligible(lastDonationDate)
    val resting = last != null && daysLeft > 0
    val progress by animateFloatAsState(
        targetValue = if (resting) (DONATION_GAP_DAYS - daysLeft).toFloat() / DONATION_GAP_DAYS else 1f,
        animationSpec = tween(900),
        label = "cycle_progress"
    )
    val activeAgain = last?.let {
        java.text.SimpleDateFormat("d MMMM", if (bn) java.util.Locale("bn", "BD") else java.util.Locale.ENGLISH)
            .format(java.util.Date(it + DONATION_GAP_DAYS * 24L * 60L * 60L * 1000L))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RokkhonPalette.Subtle)
            .padding(16.dp)
            .testTag("donation_cycle_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (resting) Color.Black else RokkhonPalette.Positive,
                    trackColor = Color.White,
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round
                )
                if (resting) {
                    Text(if (bn) bnDigits(daysLeft) else "$daysLeft", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                } else {
                    Icon(UIcons.CheckCircle, contentDescription = null, tint = RokkhonPalette.Positive, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (resting) (if (bn) "বিশ্রামে আছেন — আর ${bnDigits(daysLeft)} দিন" else "Resting — $daysLeft days left")
                    else (if (bn) "এখন রক্ত দিতে পারবেন" else "You can donate now"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RokkhonPalette.Ink
                )
                Text(
                    text = if (resting) (if (bn) "$activeAgain থেকে নিজে থেকেই আবার সক্রিয় দেখাবে" else "Active again automatically from $activeAgain")
                    else (if (bn) "রক্ত দেওয়ার পর নিচের বাটনে চাপুন" else "Tap below after you donate"),
                    fontSize = 13.sp,
                    color = RokkhonPalette.Muted
                )
            }
        }
        if (!resting || last == null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .springClick(pressedScale = 0.97f, onClick = onDonatedToday)
                    .testTag("profile_donated_today_button"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(UIcons.Bloodtype, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (bn) "আজ রক্ত দিয়েছি" else "I donated today", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
            }
        }
    }
}
