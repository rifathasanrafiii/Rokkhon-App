package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppLanguage
import com.example.data.model.parseDonationDateMillis
import com.example.data.model.isValidBangladeshMobile
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.GUARDIAN_REWARD_TIERS
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarObsidianBg)
    ) {
        // Dark Burgundy Profile Hero Header with Streak & Guardian Points Badges
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)
                    )
                )
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(58.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(PinCrimsonRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bloodGroup,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(if (isAvailableDonor) IosSystemGreen else Color.Gray)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName.ifBlank { if (language == AppLanguage.BENGALI) "রক্ষণ গার্ডিয়ান" else "Rokkhon Guardian" },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = Color(0xFFFFB3C0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (language == AppLanguage.BENGALI) "ট্রাস্ট স্কোর: ${myDonor?.trustScore ?: 100}% • $district" else "Trust Score: ${myDonor?.trustScore ?: 100}% • $district",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Daily Streak & Guardian Points Summary Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = "Daily Streak",
                            tint = IosSystemOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "$dailyStreakCount দিনের স্ট্রিক" else "$dailyStreakCount Days Streak",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (checkedInToday) { if (language == AppLanguage.BENGALI) "আজ চেক-ইন হয়েছে" else "Checked in today" } else { if (language == AppLanguage.BENGALI) "চেক-ইন করতে ট্যাপ করুন" else "Tap to check in" },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Guardian Points",
                            tint = Color(0xFFFFD166),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == AppLanguage.BENGALI) "$guardianPoints পয়েন্ট" else "$guardianPoints Points",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI) "গার্ডিয়ান রিওয়ার্ড" else "Guardian Rewards",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
        }

        // Rounded Pure White Sheet
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D5D7))
                    )
                }

                // Snapchat-Style Daily Safety Streak & Rewards Card
                Surface(
                    color = Color(0xFFFFF8F2),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IosSystemOrange.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(IosSystemOrange.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = null,
                                        tint = IosSystemOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "ডেইলি সেফটি স্ট্রিক ও রিওয়ার্ডস"
                                        } else {
                                            "Daily Safety Streak & Rewards"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF1A0F12),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BENGALI) {
                                            "প্রতিদিন ১ বার নিরাপদ মার্ক করে স্ট্রিক ও পয়েন্ট বাড়ান"
                                        } else {
                                            "Check in daily to build your streak & unlock rewards"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF756568)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.performDailySafeCheckIn() },
                            enabled = !checkedInToday,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checkedInToday) IosSystemGreen else IosSystemOrange,
                                disabledContainerColor = IosSystemGreen.copy(alpha = 0.18f),
                                disabledContentColor = IosSystemGreen
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("profile_daily_checkin_button")
                        ) {
                            Icon(
                                imageVector = if (checkedInToday) Icons.Default.CheckCircle else Icons.Default.Whatshot,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (checkedInToday) {
                                    if (language == AppLanguage.BENGALI) {
                                        "আজকের চেক-ইন সম্পন্ন ($dailyStreakCount দিনের স্ট্রিক)"
                                    } else {
                                        "Checked In Today ($dailyStreakCount Day Streak)"
                                    }
                                } else {
                                    if (language == AppLanguage.BENGALI) {
                                        "আজ আমি নিরাপদ ও প্রস্তুত (+২৫ পয়েন্ট)"
                                    } else {
                                        "Mark Safe & Ready Today (+25 Pts)"
                                    }
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Upcoming Reward Tiers
                        GUARDIAN_REWARD_TIERS.forEach { tier ->
                            val unlocked = guardianPoints >= tier.requiredPoints
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        0.8.dp,
                                        if (unlocked) IosSystemGreen.copy(alpha = 0.45f) else Color(0xFFEDE2E4),
                                        RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CardGiftcard,
                                            contentDescription = null,
                                            tint = if (unlocked) IosSystemGreen else IosSystemRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) tier.titleBn else tier.titleEn,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFF1A0F12),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (language == AppLanguage.BENGALI) tier.subtitleBn else tier.subtitleEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF756568)
                                            )
                                        }
                                    }
                                    Surface(
                                        color = if (unlocked) IosSystemGreen.copy(alpha = 0.14f) else LifelineBlush,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = if (unlocked) "UNLOCKED" else tier.badgeLabel,
                                            color = if (unlocked) IosSystemGreen else IosSystemRed,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Active / Inactive Donor Toggle Row
                Surface(
                    color = if (isAvailableDonor) LifelineBlush else Color(0xFFFAF6F6),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFEDE2E4), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    "রক্তদানের জন্য প্রোফাইল সক্রিয় রাখুন"
                                } else {
                                    "Available Blood Donor Status"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF1A0F12),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == AppLanguage.BENGALI) {
                                    if (isAvailableDonor) "আপনাকে ওই এলাকার উপলব্ধ ডোনার ফিল্টারে দেখা যাবে" else "আপনার ডোনার পিন ম্যাপে লুকানো থাকবে"
                                } else {
                                    if (isAvailableDonor) "Visible in your area's Available Donors filter" else "Hidden — you will not appear as an available donor"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF756568)
                            )
                        }
                        Switch(
                            checked = isAvailableDonor,
                            onCheckedChange = {
                                isAvailableDonor = it
                                viewModel.toggleDonorActiveStatus(myDonor, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosSystemRed
                            ),
                            modifier = Modifier.testTag("profile_donor_availability_switch")
                        )
                    }
                }

                Text(
                    text = if (language == AppLanguage.BENGALI) "ডোনার রেজিস্ট্রেশন ও এলাকা তথ্য" else "Donor Registration & Area",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1A0F12),
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = if (language == AppLanguage.BENGALI) "রক্তের গ্রুপ (Blood Group)" else "Blood Group",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF756568)
                )

                BloodGroupPillGrid2x4(
                    selectedBloodGroup = bloodGroup,
                    onSelectBloodGroup = { bloodGroup = it }
                )

                // Quick Area Selector Pills + Searchable 64-District Picker
                var showProfileAreaPicker by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == AppLanguage.BENGALI) "আপনার এলাকা সিলেক্ট করুন (Area)" else "Select Your Area",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF756568)
                    )
                    Surface(
                        color = LifelineBlush,
                        shape = CircleShape,
                        modifier = Modifier
                            .clickable { showProfileAreaPicker = true }
                            .testTag("profile_open_64_districts_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = IosSystemRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) "৬৪ জেলা সার্চ করুন" else "Search All 64 Districts",
                                style = MaterialTheme.typography.labelSmall,
                                color = IosSystemRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BANGLADESH_DISTRICTS.take(16).forEach { dist ->
                        val isSelected = district.contains(dist.nameEn.substringBefore(","))
                        Surface(
                            color = if (isSelected) IosSystemRed else Color(0xFFFAF6F6),
                            contentColor = if (isSelected) Color.White else Color(0xFF1A0F12),
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, Color(0xFFEDE2E4), CircleShape)
                                .clickable {
                                    district = dist.nameEn
                                    viewModel.selectDistrict(dist)
                                }
                        ) {
                            Text(
                                text = dist.nameEn,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (showProfileAreaPicker) {
                    BangladeshAreaPickerDialog(
                        selectedDistrict = BANGLADESH_DISTRICTS.firstOrNull {
                            district.equals(it.nameEn, ignoreCase = true)
                        } ?: selectedDistrict,
                        isAutoGpsActive = false,
                        language = language,
                        onSelectDistrict = { picked ->
                            district = picked.nameEn
                            viewModel.selectDistrict(picked)
                        },
                        onSelectAutoGps = {},
                        onDismiss = { showProfileAreaPicker = false }
                    )
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(if (language == AppLanguage.BENGALI) "আপনার নাম (Full Name)" else "Full Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text(if (language == AppLanguage.BENGALI) "এলাকা / জেলা (Area / District)" else "Area / District in Bangladesh") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (language == AppLanguage.BENGALI) "জরুরি মোবাইল নম্বর (Phone) *" else "Emergency Contact Phone *") },
                    placeholder = { Text("01XXXXXXXXX") },
                    isError = showErrors && phoneInvalid,
                    supportingText = if (showErrors && phoneInvalid) {
                        { Text(if (language == AppLanguage.BENGALI) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = lastDonationDate,
                    onValueChange = { lastDonationDate = it },
                    label = { Text(if (language == AppLanguage.BENGALI) "শেষ রক্তদানের তারিখ" else "Last donation date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    isError = showErrors && dateInvalid,
                    // The 90-day rule needs a real date; blank means never / not sure.
                    supportingText = {
                        Text(
                            if (showErrors && dateInvalid) {
                                if (language == AppLanguage.BENGALI) "তারিখ এভাবে লিখুন: 2025-01-31" else "Write the date like 2025-01-31"
                            } else {
                                if (language == AppLanguage.BENGALI) "রক্ত দেওয়ার পর ৯০ দিন আপনাকে 'উপলব্ধ' দেখানো হবে না" else "For 90 days after donating you aren't listed as available"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (phoneInvalid || dateInvalid) {
                            showErrors = true
                            return@Button
                        }
                        viewModel.saveDonorProfile(
                            displayName = displayName,
                            bloodGroup = bloodGroup,
                            isAvailableDonor = isAvailableDonor,
                            district = district,
                            contactPhone = contactPhone,
                            lastDonationDate = lastDonationDate
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_donor_profile_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) "ডোনার তথ্য সেভ করুন" else "Save Donor Profile",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = IosSystemRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == AppLanguage.BENGALI) "লগ আউট / সাইন-ইন পেইজ" else "Sign Out / Switch Account",
                        color = IosSystemRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
