package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.isValidBangladeshMobile
import com.example.ui.components.BangladeshAreaPickerDialog
import com.example.ui.components.InkText
import com.example.ui.components.MutedText
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg

/**
 * Blood request form. Starts empty (the old form was pre-filled with fake Hb, diagnosis, ward and
 * phone values that ended up in real posts) and validates the fields donors need to act.
 */
@Composable
fun PostBloodRequestDialog(
    language: AppLanguage,
    defaultDistrict: String,
    defaultHospital: String,
    defaultPhone: String = "",
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
    val bn = language == AppLanguage.BENGALI
    var bloodGroup by remember { mutableStateOf<String?>(null) }
    var patientName by remember { mutableStateOf("") }
    var unitsNeeded by remember { mutableIntStateOf(1) }
    var hemoglobinLevel by remember { mutableStateOf("") }
    var patientProblem by remember { mutableStateOf("") }
    var hospitalName by remember { mutableStateOf("") }
    var wardCabinNumber by remember { mutableStateOf("") }
    var district by remember { mutableStateOf(defaultDistrict) }
    var contactPhone by remember { mutableStateOf(defaultPhone) }
    var urgencySlot by remember { mutableStateOf("NOW") }
    var showAreaPickerDialog by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val bloodGroupError = bloodGroup == null
    val hospitalError = hospitalName.isBlank()
    val phoneError = !isValidBangladeshMobile(contactPhone)
    val hasErrors = bloodGroupError || hospitalError || phoneError

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAF8F8))
                .testTag("post_blood_request_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF4C0D18), RadarDarkMaroon, RadarObsidianBg)))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (bn) "ফিরে যান" else "Back", tint = Color.White)
                    }
                    Text(if (bn) "রক্তের আবেদন" else "Blood request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = if (bn) "সঠিক ডোনার খুঁজে দিই।" else "Let's find the right donor.",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = if (bn) "পোস্ট করলে ওই এলাকার ম্যাচিং গ্রুপের ডোনাররা নোটিফিকেশন পাবেন।" else "Matching donors in that area get notified when you post.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel(if (bn) "কোন গ্রুপের রক্ত লাগবে? *" else "Blood group needed *", showErrors && bloodGroupError)
                BloodGroupPillGrid2x4(selectedBloodGroup = bloodGroup, onSelectBloodGroup = { bloodGroup = if (bloodGroup == it) null else it })

                SectionLabel(if (bn) "কত ব্যাগ?" else "How many bags?", false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { count ->
                        ChoicePill(
                            text = if (bn) "$count ব্যাগ" else "$count",
                            selected = unitsNeeded == count,
                            onClick = { unitsNeeded = count },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SectionLabel(if (bn) "কখন লাগবে?" else "When is it needed?", false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("NOW", if (bn) "এখনই" else "Now", Icons.Default.FlashOn),
                        Triple("SIX_HRS", if (bn) "৬ ঘণ্টায়" else "6 hrs", Icons.Default.Schedule),
                        Triple("TODAY", if (bn) "আজকের মধ্যে" else "Today", Icons.Default.WbSunny)
                    ).forEach { (key, label, icon) ->
                        val sel = urgencySlot == key
                        Surface(
                            color = if (sel) IosSystemRed else Color.White,
                            contentColor = if (sel) Color.White else InkText,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(if (sel) 0.dp else 1.dp, Color(0xFFEDE2E4), RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { urgencySlot = key }
                                .testTag("urgency_$key")
                        ) {
                            Column(modifier = Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                SectionLabel(if (bn) "হাসপাতাল ও এলাকা" else "Hospital & area", false)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFDCD2D4), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showAreaPickerDialog = true }
                        .testTag("blood_request_area_picker")
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = IosSystemRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (bn) "এলাকা / জেলা" else "Area / district", color = MutedText, fontSize = 12.sp)
                            Text(district, color = InkText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = InkText)
                    }
                }
                OutlinedTextField(
                    value = hospitalName,
                    onValueChange = { hospitalName = it.take(120) },
                    leadingIcon = { Icon(Icons.Default.LocalHospital, contentDescription = null, tint = IosSystemRed) },
                    label = { Text(if (bn) "হাসপাতালের নাম *" else "Hospital name *") },
                    placeholder = { Text(defaultHospital, maxLines = 1) },
                    isError = showErrors && hospitalError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blood_request_hospital_input")
                )
                OutlinedTextField(
                    value = wardCabinNumber,
                    onValueChange = { wardCabinNumber = it.take(80) },
                    label = { Text(if (bn) "ওয়ার্ড / বেড / কেবিন" else "Ward / bed / cabin") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blood_request_ward_input")
                )

                SectionLabel(if (bn) "রোগীর তথ্য" else "Patient details", false)
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it.take(100) },
                    label = { Text(if (bn) "রোগীর নাম" else "Patient name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blood_request_patient_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hemoglobinLevel,
                        onValueChange = { hemoglobinLevel = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                        label = { Text(if (bn) "Hb (g/dL)" else "Hb (g/dL)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("blood_request_hemoglobin_input")
                    )
                    OutlinedTextField(
                        value = patientProblem,
                        onValueChange = { patientProblem = it.take(160) },
                        label = { Text(if (bn) "সমস্যা" else "Problem") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.6f)
                            .testTag("blood_request_problem_input")
                    )
                }
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text(if (bn) "যোগাযোগের মোবাইল নম্বর *" else "Contact mobile *") },
                    placeholder = { Text("01XXXXXXXXX") },
                    isError = showErrors && phoneError,
                    supportingText = if (showErrors && phoneError) {
                        { Text(if (bn) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number") }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blood_request_phone_input")
                )

                if (showErrors && hasErrors) {
                    Text(
                        text = if (bn) "* চিহ্নিত ঘরগুলো পূরণ করুন" else "Please fill in the fields marked *",
                        color = IosSystemRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        if (hasErrors) {
                            showErrors = true
                            return@Button
                        }
                        val urgency = when (urgencySlot) {
                            "NOW" -> "CRITICAL"
                            "SIX_HRS" -> "URGENT"
                            else -> "STANDARD"
                        }
                        val neededText = when (urgencySlot) {
                            "NOW" -> if (bn) "এখনই • জরুরি" else "Now • Emergency"
                            "SIX_HRS" -> if (bn) "৬ ঘণ্টার মধ্যে" else "Within 6 hours"
                            else -> if (bn) "আজকের মধ্যে" else "Today"
                        }
                        onSubmit(
                            patientName.trim(),
                            bloodGroup ?: return@Button,
                            unitsNeeded,
                            if (hemoglobinLevel.isBlank()) "" else "${hemoglobinLevel.trim()} g/dL",
                            patientProblem.trim(),
                            hospitalName.trim(),
                            wardCabinNumber.trim(),
                            neededText,
                            district,
                            contactPhone.trim(),
                            urgency
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_blood_request_button")
                ) {
                    Text(if (bn) "আবেদন পোস্ট করুন" else "Post request", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAreaPickerDialog) {
        val selected = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: BANGLADESH_DISTRICTS.first()
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = selected,
            isAutoGpsActive = false,
            onSelectAutoGps = {},
            onSelectDistrict = { dist -> district = dist.nameEn },
            onDismiss = { showAreaPickerDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) IosSystemRed else InkText,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (selected) IosSystemRed else Color.White,
        contentColor = if (selected) Color.White else InkText,
        shape = CircleShape,
        modifier = modifier
            .height(40.dp)
            .border(if (selected) 0.dp else 1.dp, Color(0xFFEDE2E4), CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
    }
}
