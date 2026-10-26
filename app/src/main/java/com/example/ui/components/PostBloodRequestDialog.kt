package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppLanguage
import com.example.data.model.AreaDetect
import com.example.data.model.BANGLADESH_AREAS
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.areaPath
import com.example.data.model.isValidBangladeshMobile
import com.example.ui.icons.UIcons
import com.example.ui.screens.bnDigits
import com.example.ui.theme.RokkhonPalette

/**
 * "Request blood": one compact page. What is needed (group, bags, when), where (area, hospital),
 * who (patient) and how to reach you. The summary and the send button stay at the bottom.
 */
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
    ) -> Unit,
    onDetectArea: (((AreaDetect) -> Unit) -> Unit)? = null
) {
    val bn = language == AppLanguage.BENGALI
    var bloodGroup by remember { mutableStateOf<String?>(null) }
    var unitsNeeded by remember { mutableIntStateOf(1) }
    var urgency by remember { mutableIntStateOf(0) }
    var area by remember {
        mutableStateOf(
            BANGLADESH_AREAS.firstOrNull { it.nameEn.equals(defaultDistrict, ignoreCase = true) }
                ?: BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(defaultDistrict) }
                ?: BANGLADESH_DISTRICTS.first()
        )
    }
    var hospitalName by remember { mutableStateOf("") }
    var wardCabinNumber by remember { mutableStateOf("") }
    var patientName by remember { mutableStateOf("") }
    var hemoglobinLevel by remember { mutableStateOf("") }
    var patientProblem by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var showAreaPicker by remember { mutableStateOf(false) }

    val groupMissing = bloodGroup == null
    val hospitalMissing = hospitalName.isBlank()
    val phoneInvalid = !isValidBangladeshMobile(contactPhone)
    val urgencies = listOf(
        Triple(if (bn) "এখনই" else "Now", "CRITICAL", "Now • Emergency"),
        Triple(if (bn) "৬ ঘণ্টায়" else "In 6 h", "URGENT", "Within 6 hrs • Urgent"),
        Triple(if (bn) "আজ" else "Today", "STANDARD", "Today • Flexible")
    )
    val suggestedHospital = (if (bn) area.defaultHospitalBn else area.defaultHospitalEn).ifBlank { defaultHospital }

    fun submit() {
        if (groupMissing || hospitalMissing || phoneInvalid) {
            showErrors = true
            return
        }
        val (_, level, timeText) = urgencies[urgency]
        onSubmit(
            patientName.trim(), bloodGroup!!, unitsNeeded, hemoglobinLevel.trim(), patientProblem.trim(),
            hospitalName.trim(), wardCabinNumber.trim(), timeText, area.nameEn, contactPhone.trim(), level
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(icon = UIcons.Close, onClick = onDismiss, contentDescription = if (bn) "বন্ধ" else "Close", testTag = "blood_request_close")
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (bn) "রক্তের আবেদন" else "Request blood", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                    Text(
                        if (bn) "মিলে যাওয়া ডোনাররা সাথে সাথে জানবেন" else "Matching donors nearby are told right away",
                        fontSize = 12.sp, color = RokkhonPalette.Muted
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormSection(if (bn) "কোন গ্রুপের রক্ত?" else "Blood group") {
                    BloodGroupGrid(selected = bloodGroup, onSelect = { bloodGroup = it })
                    if (showErrors && groupMissing) ErrorText(if (bn) "রক্তের গ্রুপ বেছে নিন" else "Choose the blood group")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(0.9f)) {
                        FieldLabel(if (bn) "কত ব্যাগ" else "Bags")
                        Row(
                            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(RokkhonPalette.Subtle),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepButton(UIcons.Remove, enabled = unitsNeeded > 1, testTag = "bags_minus") { unitsNeeded-- }
                            Text(
                                if (bn) "${bnDigits(unitsNeeded)} ব্যাগ" else "$unitsNeeded bag" + if (unitsNeeded > 1) "s" else "",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink,
                                modifier = Modifier.weight(1f).testTag("bags_value"),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            StepButton(UIcons.Add, enabled = unitsNeeded < 10, testTag = "bags_plus") { unitsNeeded++ }
                        }
                    }
                    Column(Modifier.weight(1.4f)) {
                        FieldLabel(if (bn) "কখন লাগবে" else "When")
                        SegmentedControl(
                            segments = urgencies.map { Segment(it.first) },
                            selectedIndex = urgency,
                            onSelect = { urgency = it },
                            height = 46.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                FormSection(if (bn) "কোথায়" else "Where") {
                    val path = remember(area.areaKey) { areaPath(area) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RokkhonPalette.Subtle)
                            .springClick(pressedScale = 0.98f) { showAreaPicker = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("blood_request_area"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(UIcons.LocationOn, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (bn) area.nameBn else area.nameEn, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            path?.let { (div, d) ->
                                Text(if (bn) "${d.nameBn} · ${div.nameBn} বিভাগ" else "${d.nameEn} · ${div.nameEn} Division", fontSize = 12.sp, color = RokkhonPalette.Muted)
                            }
                        }
                        Text(if (bn) "বদলান" else "Change", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    FormField(
                        value = hospitalName,
                        onChange = { hospitalName = it },
                        placeholder = if (bn) "হাসপাতালের নাম *" else "Hospital name *",
                        icon = UIcons.LocalHospital,
                        error = showErrors && hospitalMissing,
                        testTag = "blood_request_hospital_input"
                    )
                    if (hospitalName.isBlank() && suggestedHospital.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .border(1.dp, RokkhonPalette.Border, CircleShape)
                                .springClick { hospitalName = suggestedHospital }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(UIcons.Add, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(suggestedHospital, fontSize = 12.sp, color = RokkhonPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (showErrors && hospitalMissing) ErrorText(if (bn) "হাসপাতালের নাম দিন" else "Enter the hospital")
                    Spacer(Modifier.height(8.dp))
                    FormField(
                        value = wardCabinNumber,
                        onChange = { wardCabinNumber = it },
                        placeholder = if (bn) "ওয়ার্ড / কেবিন / বেড" else "Ward / cabin / bed",
                        icon = UIcons.MedicalServices,
                        testTag = "blood_request_ward_input"
                    )
                }

                FormSection(if (bn) "রোগী" else "Patient") {
                    FormField(
                        value = patientName,
                        onChange = { patientName = it },
                        placeholder = if (bn) "রোগীর নাম" else "Patient name",
                        icon = UIcons.Person,
                        testTag = "blood_request_patient_name_input"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            value = hemoglobinLevel,
                            onChange = { hemoglobinLevel = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                            placeholder = if (bn) "হিমোগ্লোবিন" else "Hb (g/dL)",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(0.8f),
                            testTag = "blood_request_hemoglobin_input"
                        )
                        FormField(
                            value = patientProblem,
                            onChange = { patientProblem = it },
                            placeholder = if (bn) "সমস্যা (যেমন অপারেশন)" else "Reason (e.g. surgery)",
                            modifier = Modifier.weight(1.2f),
                            testTag = "blood_request_problem_input"
                        )
                    }
                }

                FormSection(if (bn) "যোগাযোগ" else "Contact") {
                    FormField(
                        value = contactPhone,
                        onChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                        placeholder = if (bn) "মোবাইল নম্বর * (01XXXXXXXXX)" else "Mobile number * (01XXXXXXXXX)",
                        icon = UIcons.Call,
                        keyboardType = KeyboardType.Phone,
                        error = showErrors && phoneInvalid,
                        testTag = "blood_request_phone_input"
                    )
                    if (showErrors && phoneInvalid) ErrorText(if (bn) "সঠিক বাংলাদেশি মোবাইল নম্বর দিন" else "Enter a valid Bangladeshi mobile number")
                }
            }

            // Summary + send, always in reach.
            Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(RokkhonPalette.Divider))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(bloodGroup ?: "—", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = RokkhonPalette.Ink)
                            Spacer(Modifier.width(8.dp))
                            if (urgency == 0) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(RokkhonPalette.Sos))
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                (if (bn) "${bnDigits(unitsNeeded)} ব্যাগ" else "$unitsNeeded bag" + if (unitsNeeded > 1) "s" else "") + " · " + urgencies[urgency].first,
                                fontSize = 13.sp, color = RokkhonPalette.Muted
                            )
                        }
                        Text(if (bn) area.nameBn else area.nameEn, fontSize = 12.sp, color = RokkhonPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(RokkhonPalette.Primary)
                            .springClick(pressedScale = 0.96f) { submit() }
                            .padding(horizontal = 20.dp)
                            .testTag("submit_blood_request_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (bn) "আবেদন পাঠান" else "Send request", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(UIcons.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAreaPicker) {
        BangladeshAreaPickerDialog(
            language = language,
            selectedDistrict = area,
            onSelectDistrict = { area = it },
            onDismiss = { showAreaPicker = false },
            onDetectLocation = onDetectArea
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Muted, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
}

@Composable
private fun ErrorText(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Sos, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color.White else Color.Transparent)
            .springClick(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) RokkhonPalette.Ink else RokkhonPalette.Faint, modifier = Modifier.size(16.dp))
    }
}

/** Grey filled field: placeholder inside, thin red edge only when it needs fixing. */
@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: Boolean = false
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RokkhonPalette.Subtle)
            .then(if (error) Modifier.border(1.dp, RokkhonPalette.Sos, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 14.5.sp, color = RokkhonPalette.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.5.sp, color = RokkhonPalette.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth().testTag(testTag)
            )
        }
    }
}
