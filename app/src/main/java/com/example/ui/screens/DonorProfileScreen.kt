package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.GUARDIAN_REWARD_TIERS
import com.example.data.model.isValidBangladeshMobile
import com.example.data.model.parseDonationDateMillis
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RollingNumber
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun DonorProfileScreen(
    viewModel: RokkhonViewModel,
    onSignOut: () -> Unit
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
    val trustScore = (myDonor?.trustScore ?: 100L).toInt()
    var showEditSheet by remember { mutableStateOf(false) }
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(RokkhonPalette.HeroTop, RokkhonPalette.HeroMid, RokkhonPalette.HeroBottom)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(92.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(14.dp, CircleShape, ambientColor = PinCrimsonRed, spotColor = PinCrimsonRed)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFFF3B52), Color(0xFFC8102E))))
                            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .springClick { showEditSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(bloodGroup, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(if (isAvailableDonor) IosSystemGreen else Color.Gray)
                            .border(3.dp, RokkhonPalette.HeroMid, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName.ifBlank { if (bn) "রক্ষণ গার্ডিয়ান" else "Rokkhon Guardian" },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFFFFB3C0), modifier = Modifier.size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(district, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat(
                        icon = Icons.Default.Whatshot,
                        iconTint = Color(0xFFFFA726),
                        value = dailyStreakCount,
                        label = if (checkedInToday) (if (bn) "দিনের স্ট্রিক" else "day streak") else (if (bn) "চেক-ইন করুন" else "Check in"),
                        highlight = !checkedInToday,
                        onClick = { if (!checkedInToday) viewModel.performDailySafeCheckIn() },
                        modifier = Modifier.weight(1f),
                        testTag = "profile_daily_checkin_button"
                    )
                    HeroStat(
                        icon = Icons.Default.Stars,
                        iconTint = Color(0xFFFFD166),
                        value = guardianPoints,
                        label = if (bn) "পয়েন্ট" else "points",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    HeroStat(
                        icon = Icons.Default.VerifiedUser,
                        iconTint = Color(0xFF86EFAC),
                        value = trustScore,
                        suffix = "%",
                        label = if (bn) "ট্রাস্ট" else "trust",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "profile_sheet") {
            Box(modifier = Modifier.fillMaxWidth().background(RokkhonPalette.HeroBottom)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(RokkhonPalette.Background)
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
                ) {
                    if (myDonor == null) {
                        // Not registered yet: the one thing to do on this page
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFC8102E), Color(0xFF8B1124))))
                                .springClick(pressedScale = 0.97f) { showEditSheet = true }
                                .padding(16.dp)
                                .testTag("profile_register_cta"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bloodtype, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (bn) "ডোনার হিসেবে নিবন্ধন করুন" else "Register as a donor", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (bn) "১ মিনিট • এলাকার রোগীরা আপনাকে খুঁজে পাবে" else "1 minute • patients nearby can find you", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Availability: one switch
                    SettingsGroup {
                        SettingsRow(
                            icon = Icons.Default.Bloodtype,
                            iconTint = if (isAvailableDonor) IosSystemGreen else RokkhonPalette.Muted,
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
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosSystemGreen),
                                    modifier = Modifier.testTag("profile_donor_availability_switch")
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader(title = if (bn) "রিওয়ার্ড" else "Rewards")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GUARDIAN_REWARD_TIERS.forEachIndexed { index, tier ->
                            RewardTierCard(
                                title = if (bn) tier.titleBn else tier.titleEn,
                                requiredPoints = tier.requiredPoints,
                                points = guardianPoints,
                                tierIndex = index,
                                modifier = Modifier.enterOnce(index)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader(
                        title = if (bn) "ডোনার তথ্য" else "Donor details",
                        trailing = {
                            RoundIconButton(
                                icon = Icons.Default.Edit,
                                onClick = { showEditSheet = true },
                                contentDescription = if (bn) "এডিট" else "Edit",
                                testTag = "profile_edit_button"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsGroup {
                        SettingsRow(Icons.Default.Bloodtype, RokkhonPalette.Crimson, if (bn) "রক্তের গ্রুপ" else "Blood group", bloodGroup, { showEditSheet = true })
                        SettingsDivider()
                        SettingsRow(
                            Icons.Default.Phone, RokkhonPalette.Crimson, if (bn) "জরুরি ফোন" else "Emergency phone",
                            contactPhone.ifBlank { if (bn) "যোগ করুন" else "Add" }, { showEditSheet = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            Icons.Default.Event, RokkhonPalette.Crimson, if (bn) "শেষ রক্তদান" else "Last donation",
                            lastDonationDate.ifBlank { if (bn) "জানা নেই" else "Not set" }, { showEditSheet = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            Icons.Default.LocationOn, RokkhonPalette.Crimson, if (bn) "এলাকা" else "Area", district,
                            { showProfileAreaPicker = true },
                            testTag = "profile_open_64_districts_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroup {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = IosSystemRed,
                            title = if (bn) "লগ আউট" else "Sign out",
                            value = null,
                            onClick = onSignOut,
                            titleColor = IosSystemRed,
                            testTag = "sign_out_button"
                        )
                    }

                    Image(
                        painter = painterResource(R.drawable.rokkhon_wordmark_black),
                        contentDescription = "রক্ষণ",
                        modifier = Modifier
                            .padding(top = 22.dp, bottom = 12.dp)
                            .height(22.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (showProfileAreaPicker) {
        BangladeshAreaPickerDialog(
            selectedDistrict = BANGLADESH_DISTRICTS.firstOrNull { district.equals(it.nameEn, ignoreCase = true) } ?: selectedDistrict,
            isAutoGpsActive = false,
            language = language,
            onSelectDistrict = { picked ->
                district = picked.nameEn
                viewModel.selectDistrict(picked)
                if (myDonor != null) saveProfile()
            },
            onSelectAutoGps = {},
            onDismiss = { showProfileAreaPicker = false }
        )
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
                BloodGroupChipRow(language = language, selected = bloodGroup, onSelect = { if (it != null) bloodGroup = it }, includeAll = false)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(if (bn) "আপনার নাম" else "Full name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (bn) "জরুরি মোবাইল নম্বর *" else "Emergency phone *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
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
                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
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
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Search,
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
                    colors = ButtonDefaults.buttonColors(containerColor = RokkhonPalette.Crimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_donor_profile_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (bn) "সেভ করুন" else "Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Glass stat in the profile hero: icon, rolling number, one word. */
@Composable
private fun HeroStat(
    icon: ImageVector,
    iconTint: Color,
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = if (highlight) 0.35f else 0.14f), RoundedCornerShape(20.dp))
            .springClick(onClick = onClick)
            .padding(vertical = 12.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        RollingNumber(value = value, fontSize = 20.sp, color = Color.White, suffix = suffix)
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Reward tier as a small card with progress towards it. */
@Composable
private fun RewardTierCard(title: String, requiredPoints: Int, points: Int, tierIndex: Int, modifier: Modifier = Modifier) {
    val unlocked = points >= requiredPoints
    val tierColor = listOf(Color(0xFFB45309), Color(0xFF64748B), Color(0xFFCA8A04)).getOrElse(tierIndex) { RokkhonPalette.Crimson }
    val progress by animateFloatAsState((points.toFloat() / requiredPoints).coerceIn(0f, 1f), label = "tier_progress")
    Column(
        modifier = modifier
            .width(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, if (unlocked) IosSystemGreen.copy(alpha = 0.45f) else RokkhonPalette.Border, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tierColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (unlocked) Icons.Default.CheckCircle else Icons.Default.CardGiftcard, contentDescription = null, tint = if (unlocked) IosSystemGreen else tierColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RokkhonPalette.Ink, maxLines = 1)
        Text("$requiredPoints pts", fontSize = 12.sp, color = RokkhonPalette.Muted)
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (unlocked) IosSystemGreen else tierColor,
            trackColor = RokkhonPalette.Track,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, RokkhonPalette.Border, RoundedCornerShape(22.dp)),
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
            .background(Color(0xFFF4ECEE))
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
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.12f)),
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFB9AEB1), modifier = Modifier.size(22.dp))
        }
    }
}
