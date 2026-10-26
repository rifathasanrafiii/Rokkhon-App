package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_AREAS
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.isValidBangladeshMobile
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.icons.UIcons
import com.example.ui.screens.BloodGroupPillGrid2x4
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.RokkhonPalette

/**
 * Full-Screen Emergency Blood Request Sheet with Searchable All-Bangladesh Area Selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostBloodRequestDialog(
    language: AppLanguage,
    defaultDistrict: String,
    defaultHospital: String,
    onDismiss: () -> Unit,
    onSubmit: (
        patientName: String,
        bloodGroup: String,
        unitsNeeded: Int,
        hemoglobinLevel: String,
        patientProblem: String,
        hospitalName: String,
        wardCabinNumber: String,
        neededDateTime: String,
        district: String,
        contactPhone: String,
        urgencyLevel: String
    ) -> Unit
) {
    var bloodGroup by remember { mutableStateOf<String?>(null) }
    var patientName by remember { mutableStateOf("") }
    var unitsNeeded by remember { mutableIntStateOf(2) }
    var hemoglobinLevel by remember { mutableStateOf("") }
    var patientProblem by remember { mutableStateOf("") }
    var hospitalName by remember { mutableStateOf("") }
    var wardCabinNumber by remember { mutableStateOf("") }
    var district by remember { mutableStateOf(defaultDistrict) }
    var contactPhone by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val groupMissing = bloodGroup == null
    val hospitalMissing = hospitalName.isBlank()
    val phoneInvalid = !isValidBangladeshMobile(contactPhone)
    var selectedUrgencySlot by remember { mutableStateOf("NOW") }
    var showAreaPickerDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = UIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = RokkhonPalette.Ink
                        )
                    }
                    Text(
                        text = "",
                        style = MaterialTheme.typography.titleMedium,
                        color = RokkhonPalette.Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (language == AppLanguage.BENGALI) "রক্তের আবেদন" else "Request blood",
                    fontSize = 30.sp,
                    color = RokkhonPalette.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 34.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (language == AppLanguage.BENGALI) {
                        "কার জন্য রক্ত লাগবে, জানিয়ে দিন। মিলে যাওয়া ডোনাররা সাথে সাথে জানবেন।"
                    } else {
                        "Tell us who needs blood. Matching donors are notified right away."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RokkhonPalette.Muted
                )
            }

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
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                .background(Color(0xFFE2E2E2))
                        )
                    }

                    Text(
                        text = if (language == AppLanguage.BENGALI) "কোন গ্রুপের রক্ত লাগবে?" else "Blood group needed",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.ExtraBold
                    )

                    BloodGroupPillGrid2x4(
                        selectedBloodGroup = bloodGroup,
                        onSelectBloodGroup = { bloodGroup = it }
                    )
                    if (showErrors && groupMissing) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "রক্তের গ্রুপ বেছে নিন" else "Choose the blood group",
                            color = IosSystemRed,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "হাসপাতাল কোন এলাকায়?" else "Hospital area",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            color = LifelineBlush,
                            shape = CircleShape,
                            modifier = Modifier.clickable { showAreaPickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(UIcons.Search, contentDescription = null, tint = IosSystemRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (language == AppLanguage.BENGALI) "সব জেলা খুঁজুন" else "Search All 64",
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
                                color = if (isSelected) IosSystemRed else Color(0xFFF6F6F6),
                                contentColor = if (isSelected) Color.White else Color(0xFF000000),
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFE8E8E8), CircleShape)
                                    .clickable {
                                        district = dist.nameEn
                                        hospitalName = dist.defaultHospitalEn
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

                    Text(
                        text = if (language == AppLanguage.BENGALI) "রোগীর তথ্য" else "Patient details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.ExtraBold
                    )

                    OutlinedTextField(
                        value = hospitalName,
                        onValueChange = { hospitalName = it },
                        leadingIcon = {
                            Icon(UIcons.LocalHospital, contentDescription = null, tint = IosSystemRed)
                        },
                        label = { Text(if (language == AppLanguage.BENGALI) "হাসপাতালের নাম (Hospital) *" else "Hospital Name *") },
                        placeholder = { Text(defaultHospital) },
                        isError = showErrors && hospitalMissing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_hospital_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        leadingIcon = {
                            Icon(UIcons.LocationOn, contentDescription = null, tint = IosSystemRed)
                        },
                        label = { Text(if (language == AppLanguage.BENGALI) "এলাকা / জেলা (Location)" else "Location / Area") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text(if (language == AppLanguage.BENGALI) "রোগীর নাম (Patient Name)" else "Patient Name") },
                        placeholder = { Text(if (language == AppLanguage.BENGALI) "রোগীর পুরো নাম" else "Patient's full name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_patient_name_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hemoglobinLevel,
                            onValueChange = { hemoglobinLevel = it },
                            label = { Text(if (language == AppLanguage.BENGALI) "হিমোগ্লোবিন (Hb)" else "Hemoglobin (g/dL)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("blood_request_hemoglobin_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = wardCabinNumber,
                            onValueChange = { wardCabinNumber = it },
                            label = { Text(if (language == AppLanguage.BENGALI) "ওয়ার্ড / কেবিন" else "Ward / Cabin") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("blood_request_ward_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) "রক্তের ব্যাগ:" else "Bags Needed:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.Bold
                        )
                        listOf(1, 2, 3, 4).forEach { count ->
                            val isSelected = unitsNeeded == count
                            Surface(
                                color = if (isSelected) IosSystemRed else Color(0xFFF6F6F6),
                                contentColor = if (isSelected) Color.White else Color(0xFF000000),
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFE8E8E8), CircleShape)
                                    .clickable { unitsNeeded = count }
                            ) {
                                Text(
                                    text = "$count ব্যাগ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = patientProblem,
                        onValueChange = { patientProblem = it },
                        label = { Text(if (language == AppLanguage.BENGALI) "রোগীর সমস্যা (Problem / Diagnosis)" else "Problem / Diagnosis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_problem_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                        label = { Text(if (language == AppLanguage.BENGALI) "জরুরি মোবাইল নম্বর (Phone) *" else "Contact Phone *") },
                        placeholder = { Text("01XXXXXXXXX") },
                        isError = showErrors && phoneInvalid,
                        supportingText = if (showErrors && phoneInvalid) {
                            { Text(if (language == AppLanguage.BENGALI) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blood_request_phone_input"),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Text(
                        text = if (language == AppLanguage.BENGALI) "How urgent is it? • কখন রক্ত লাগবে?" else "How urgent is it?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF000000),
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val slots = listOf(
                            Triple("NOW", "Now", "Emergency"),
                            Triple("SIX_HRS", "6 hrs", "Urgent"),
                            Triple("TODAY", "Today", "Flexible")
                        )
                        slots.forEach { (key, title, sub) ->
                            val isSel = selectedUrgencySlot == key
                            val iconVec = when (key) {
                                "NOW" -> UIcons.FlashOn
                                "SIX_HRS" -> UIcons.Schedule
                                else -> UIcons.WbSunny
                            }
                            Surface(
                                color = if (isSel) IosSystemRed else Color.White,
                                contentColor = if (isSel) Color.White else Color(0xFF000000),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isSel) 0.dp else 1.dp,
                                        color = Color(0xFFE8E8E8),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedUrgencySlot = key }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = title,
                                        tint = if (isSel) Color.White else IosSystemRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) Color.White.copy(alpha = 0.8f) else Color(0xFF5E5E5E)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val mappedUrgency = when (selectedUrgencySlot) {
                                "NOW" -> "CRITICAL"
                                "SIX_HRS" -> "URGENT"
                                else -> "STANDARD"
                            }
                            val mappedTimeText = when (selectedUrgencySlot) {
                                "NOW" -> "Now • Emergency"
                                "SIX_HRS" -> "Within 6 hrs • Urgent"
                                else -> "Today • Flexible"
                            }
                            // Required: blood group, hospital, a real mobile number. No made-up fallbacks.
                            if (groupMissing || hospitalMissing || phoneInvalid) {
                                showErrors = true
                                return@Button
                            }
                            onSubmit(
                                patientName.trim(),
                                bloodGroup!!,
                                unitsNeeded,
                                hemoglobinLevel.trim(),
                                patientProblem.trim(),
                                hospitalName.trim(),
                                wardCabinNumber.trim(),
                                mappedTimeText,
                                district.ifBlank { defaultDistrict },
                                contactPhone.trim(),
                                mappedUrgency
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_blood_request_button")
                    ) {
                        Text(
                            text = if (language == AppLanguage.BENGALI) {
                                "জরুরি রক্তের আবেদন পোস্ট করুন"
                            } else {
                                "Post Emergency Blood Request"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = UIcons.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }

    if (showAreaPickerDialog) {
        val currentDistObj = BANGLADESH_AREAS.firstOrNull { it.nameEn.equals(district, ignoreCase = true) }
            ?: BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: BANGLADESH_DISTRICTS.first()
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = currentDistObj,
            isAutoGpsActive = false,
            onSelectAutoGps = {
                district = currentDistObj.nameEn
                hospitalName = currentDistObj.defaultHospitalEn
            },
            onSelectDistrict = { dist ->
                district = dist.nameEn
                hospitalName = dist.defaultHospitalEn
            },
            onDismiss = { showAreaPickerDialog = false }
        )
    }
}
