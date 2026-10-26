package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AppLanguage
import com.example.data.model.AreaDetect
import com.example.data.model.AreaDistrict
import com.example.data.model.AreaDivision
import com.example.data.model.BANGLADESH_AREAS
import com.example.data.model.BANGLADESH_AREA_TREE
import com.example.data.model.BangladeshDistrict
import com.example.data.model.areaPath
import com.example.data.model.shortNameBn
import com.example.data.model.shortNameEn
import com.example.ui.icons.UIcons
import com.example.ui.screens.bnDigits
import com.example.ui.theme.RokkhonPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Area picker, the way people think about where they live: বিভাগ → জেলা → উপজেলা.
 * Search finds any of the 8 divisions, 64 districts and 494 upazilas directly; GPS picks the
 * nearest one. Full screen and white, like a native settings page.
 */
@Composable
fun BangladeshAreaPickerDialog(
    language: AppLanguage,
    selectedDistrict: BangladeshDistrict,
    onSelectDistrict: (BangladeshDistrict) -> Unit,
    onDismiss: () -> Unit,
    /** Finds the nearest area from GPS and reports back; null hides "use my location". */
    onDetectLocation: (((AreaDetect) -> Unit) -> Unit)? = null,
    /** What to do with a GPS result (defaults to a normal pick). */
    onAreaDetected: (BangladeshDistrict) -> Unit = onSelectDistrict
) {
    val bn = language == AppLanguage.BENGALI
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var divisionName by rememberSaveable { mutableStateOf<String?>(null) }
    var districtName by rememberSaveable { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<GpsUi>(GpsUi.Idle) }
    val division = BANGLADESH_AREA_TREE.firstOrNull { it.nameEn == divisionName }
    val district = division?.districts?.firstOrNull { it.nameEn == districtName }
    val selectedPath = remember(selectedDistrict.areaKey) { areaPath(selectedDistrict) }
    val level = when {
        query.isNotBlank() -> 3
        district != null -> 2
        division != null -> 1
        else -> 0
    }

    fun pick(area: BangladeshDistrict) {
        onSelectDistrict(area)
        onDismiss()
    }

    fun detect() {
        val run = onDetectLocation ?: return
        gps = GpsUi.Searching
        run { result ->
            gps = GpsUi.Done(result)
            if (result is AreaDetect.Found) {
                onAreaDetected(result.area)
                // Let the person see what was found before the page closes.
                scope.launch {
                    delay(1100)
                    onDismiss()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) detect() else gps = GpsUi.Done(AreaDetect.NoPermission)
    }

    fun back() {
        when {
            query.isNotBlank() -> query = ""
            district != null -> districtName = null
            division != null -> divisionName = null
            else -> onDismiss()
        }
    }

    FullScreenDialog(onDismiss = onDismiss) {
        BackHandler { back() }
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .testTag("bangladesh_area_picker_dialog")
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    icon = if (level == 0) UIcons.Close else UIcons.ArrowBack,
                    onClick = { back() },
                    contentDescription = if (bn) "ফিরে যান" else "Back",
                    testTag = "area_picker_back"
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            district != null -> if (bn) district.nameBn else district.nameEn
                            division != null -> (if (bn) "${division.nameBn} বিভাগ" else "${division.nameEn} Division")
                            else -> if (bn) "এলাকা" else "Area"
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            district != null -> if (bn) "${district.divisionBn} বিভাগ · ${bnDigits(district.upazilas.size)}টি উপজেলা" else "${district.divisionEn} Division · ${district.upazilas.size} upazilas"
                            division != null -> if (bn) "${bnDigits(division.districts.size)}টি জেলা · ${bnDigits(division.upazilaCount)}টি উপজেলা" else "${division.districts.size} districts · ${division.upazilaCount} upazilas"
                            else -> if (bn) "৮ বিভাগ · ৬৪ জেলা · ৪৯৪ উপজেলা" else "8 divisions · 64 districts · 494 upazilas"
                        },
                        fontSize = 12.sp, color = RokkhonPalette.Muted
                    )
                }
            }

            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(RokkhonPalette.Subtle)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(UIcons.Search, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(if (bn) "জেলা বা উপজেলা খুঁজুন" else "Search a district or upazila", color = RokkhonPalette.Faint, fontSize = 14.5.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.5.sp, color = RokkhonPalette.Ink),
                        modifier = Modifier.fillMaxWidth().testTag("area_search_input")
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(UIcons.Close, if (bn) "মুছুন" else "Clear", tint = RokkhonPalette.Muted,
                        modifier = Modifier.size(30.dp).clip(CircleShape).springClick { query = "" }.padding(6.dp))
                }
            }

            if (onDetectLocation != null && level == 0) {
                Spacer(Modifier.height(10.dp))
                GpsRow(
                    bn = bn,
                    state = gps,
                    onClick = {
                        if (gps != GpsUi.Searching) {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (granted) detect()
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    onOpenLocationSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }
                )
            }
            Spacer(Modifier.height(6.dp))

            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(260)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(150)))
                },
                label = "area_level",
                modifier = Modifier.weight(1f)
            ) { lvl ->
                when (lvl) {
                    0 -> DivisionGrid(bn, selectedDistrict, selectedPath?.first) { divisionName = it.nameEn }
                    1 -> division?.let { div ->
                        DistrictList(bn, div, selectedPath?.second) { districtName = it.nameEn }
                    }
                    2 -> district?.let { d ->
                        PlaceList(bn, d, selectedDistrict) { pick(it) }
                    }
                    else -> SearchResults(bn, query, selectedDistrict) { pick(it) }
                }
            }
        }
    }
}

private sealed interface GpsUi {
    data object Idle : GpsUi
    data object Searching : GpsUi
    data class Done(val result: AreaDetect) : GpsUi
}

/** "Use my location" with its result right here: searching, found, or what to fix. */
@Composable
private fun GpsRow(bn: Boolean, state: GpsUi, onClick: () -> Unit, onOpenLocationSettings: () -> Unit) {
    val result = (state as? GpsUi.Done)?.result
    val found = result as? AreaDetect.Found
    val (title, subtitle) = when {
        state == GpsUi.Searching -> (if (bn) "আপনার লোকেশন খুঁজছি…" else "Finding your location…") to
            (if (bn) "কয়েক সেকেন্ড লাগতে পারে" else "This can take a few seconds")
        found != null -> (if (bn) "পাওয়া গেছে: ${found.area.nameBn}" else "Found: ${found.area.nameEn}") to
            (if (bn) "এই এলাকা বেছে নেওয়া হলো" else "Selected this area")
        result == AreaDetect.NoPermission -> (if (bn) "লোকেশনের অনুমতি নেই" else "No location permission") to
            (if (bn) "ট্যাপ করে অনুমতি দিন" else "Tap to allow")
        result == AreaDetect.LocationOff -> (if (bn) "ফোনের লোকেশন বন্ধ" else "Phone location is off") to
            (if (bn) "চালু করে আবার চেষ্টা করুন" else "Turn it on and try again")
        result == AreaDetect.OutsideBangladesh -> (if (bn) "আপনি বাংলাদেশের বাইরে আছেন" else "You're outside Bangladesh") to
            (if (bn) "নিচ থেকে এলাকা বেছে নিন" else "Pick an area below")
        result == AreaDetect.Failed -> (if (bn) "লোকেশন পাওয়া যায়নি" else "Couldn't get your location") to
            (if (bn) "আবার চেষ্টা করতে ট্যাপ করুন" else "Tap to try again")
        else -> (if (bn) "আমার লোকেশন" else "Use my location") to
            (if (bn) "GPS দিয়ে সবচেয়ে কাছের জেলা" else "Nearest district from GPS")
    }
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (found != null) RokkhonPalette.Primary else RokkhonPalette.Subtle)
            .springClick(pressedScale = 0.98f, onClick = if (result == AreaDetect.LocationOff) onOpenLocationSettings else onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("area_picker_auto_gps_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (found != null) Color.White.copy(alpha = 0.16f) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (state == GpsUi.Searching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RokkhonPalette.Ink)
            } else {
                Icon(
                    if (found != null) UIcons.CheckCircle else UIcons.MyLocation, null,
                    tint = if (found != null) Color.White else RokkhonPalette.Ink, modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = if (found != null) Color.White else RokkhonPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = if (found != null) Color.White.copy(alpha = 0.8f) else RokkhonPalette.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun DivisionGrid(
    bn: Boolean,
    selected: BangladeshDistrict,
    selectedDivision: AreaDivision?,
    onOpen: (AreaDivision) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (bn) "বিভাগ" else "Divisions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.weight(1f))
                Icon(UIcons.LocationOn, null, tint = RokkhonPalette.Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    if (bn) selected.shortNameBn() else selected.shortNameEn(),
                    fontSize = 12.5.sp, color = RokkhonPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("area_picker_current")
                )
            }
        }
        itemsIndexed(BANGLADESH_AREA_TREE, key = { _, d -> d.nameEn }) { _, div ->
            val isSelected = selectedDivision?.nameEn == div.nameEn
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) RokkhonPalette.Primary else RokkhonPalette.Subtle)
                    .springClick(pressedScale = 0.96f) { onOpen(div) }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
                    .testTag("division_chip_${div.nameEn}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (bn) div.nameBn else div.nameEn, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else RokkhonPalette.Ink, maxLines = 1)
                    Text(
                        if (bn) "${bnDigits(div.districts.size)} জেলা · ${bnDigits(div.upazilaCount)} উপজেলা" else "${div.districts.size} dist · ${div.upazilaCount} upz",
                        fontSize = 11.5.sp, color = if (isSelected) Color.White.copy(alpha = 0.8f) else RokkhonPalette.Muted, maxLines = 1
                    )
                }
                Icon(UIcons.KeyboardArrowRight, null, tint = if (isSelected) Color.White else RokkhonPalette.Faint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DistrictList(bn: Boolean, division: AreaDivision, selectedDistrict: AreaDistrict?, onOpen: (AreaDistrict) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        modifier = Modifier
    ) {
        item { GroupLabel(if (bn) "জেলা" else "Districts") }
        itemsIndexed(division.districts, key = { _, d -> d.nameEn }) { i, d ->
            Row(
                Modifier
                    .enterOnce(i)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .springClick(pressedScale = 0.98f) { onOpen(d) }
                    .padding(vertical = 9.dp, horizontal = 4.dp)
                    .testTag("district_row_${d.nameEn}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (selectedDistrict?.nameEn == d.nameEn) RokkhonPalette.Primary else RokkhonPalette.Subtle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (if (bn) d.nameBn else d.nameEn).take(1),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (selectedDistrict?.nameEn == d.nameEn) Color.White else RokkhonPalette.Ink
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (bn) d.nameBn else d.nameEn, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                    Text(
                        if (bn) "${bnDigits(d.upazilas.size)}টি উপজেলা" + if (d.popular.isNotEmpty()) " · ${bnDigits(d.popular.size)}টি শহর এলাকা" else ""
                        else "${d.upazilas.size} upazilas" + if (d.popular.isNotEmpty()) " · ${d.popular.size} city areas" else "",
                        fontSize = 12.5.sp, color = RokkhonPalette.Muted
                    )
                }
                Icon(UIcons.KeyboardArrowRight, null, tint = RokkhonPalette.Faint, modifier = Modifier.size(18.dp))
            }
            if (i < division.districts.lastIndex) HorizontalDivider(color = RokkhonPalette.Divider, modifier = Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun PlaceList(bn: Boolean, district: AreaDistrict, selected: BangladeshDistrict, onPick: (BangladeshDistrict) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        modifier = Modifier
    ) {
        item {
            PlaceRow(
                title = if (bn) district.main.nameBn else district.main.nameEn,
                subtitle = (if (bn) "জেলা সদর · " else "District centre · ") + (if (bn) district.main.defaultHospitalBn else district.main.defaultHospitalEn),
                selected = selected.areaKey == district.main.areaKey,
                emphasized = true,
                onClick = { onPick(district.main) },
                testTag = "area_option_${district.main.areaKey}"
            )
        }
        if (district.popular.isNotEmpty()) {
            item { GroupLabel(if (bn) "শহরের এলাকা" else "City areas") }
            placeItems(bn, district.popular, selected, onPick, full = true)
        }
        item { GroupLabel(if (bn) "উপজেলা" else "Upazilas") }
        placeItems(bn, district.upazilas, selected, onPick, full = false)
    }
}

private fun LazyListScope.placeItems(
    bn: Boolean,
    places: List<BangladeshDistrict>,
    selected: BangladeshDistrict,
    onPick: (BangladeshDistrict) -> Unit,
    full: Boolean
) {
    items(places, key = { it.areaKey }) { p ->
        PlaceRow(
            title = if (full) (if (bn) p.shortNameBn() else p.shortNameEn()) else (if (bn) p.shortNameBn() else p.shortNameEn()),
            subtitle = if (full) (if (bn) p.defaultHospitalBn else p.defaultHospitalEn) else null,
            selected = selected.areaKey == p.areaKey,
            onClick = { onPick(p) },
            testTag = "area_option_${p.areaKey}"
        )
    }
}

@Composable
private fun SearchResults(bn: Boolean, query: String, selected: BangladeshDistrict, onPick: (BangladeshDistrict) -> Unit) {
    val results = remember(query) { BANGLADESH_AREAS.filter { it.matchesSearch(query) }.take(80) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        modifier = Modifier
    ) {
        if (results.isEmpty()) {
            item {
                Text(
                    if (bn) "\"$query\" নামে কোনো এলাকা পাওয়া যায়নি। বানান বদলে দেখুন, বা বিভাগ থেকে খুঁজুন।" else "No area called \"$query\". Try another spelling or browse by division.",
                    fontSize = 14.sp, color = RokkhonPalette.Muted, modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(results, key = { it.areaKey }) { p ->
            val path = remember(p.areaKey) { areaPath(p) }
            val where = path?.let { (div, d) ->
                val kind = when {
                    d.main.areaKey == p.areaKey -> if (bn) "জেলা সদর" else "District centre"
                    d.upazilas.any { it.areaKey == p.areaKey } -> if (bn) "উপজেলা" else "Upazila"
                    else -> if (bn) "শহর এলাকা" else "City area"
                }
                if (bn) "$kind · ${d.nameBn} · ${div.nameBn} বিভাগ" else "$kind · ${d.nameEn} · ${div.nameEn} Division"
            }
            PlaceRow(
                title = if (bn) p.shortNameBn() else p.shortNameEn(),
                subtitle = where,
                selected = selected.areaKey == p.areaKey,
                onClick = { onPick(p) },
                testTag = "area_option_${p.areaKey}"
            )
        }
    }
}

@Composable
private fun PlaceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, testTag: String, emphasized: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) RokkhonPalette.Subtle else if (emphasized) RokkhonPalette.Subtle else Color.White)
            .springClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(UIcons.LocationOn, null, tint = if (selected) RokkhonPalette.Ink else RokkhonPalette.Faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = if (selected || emphasized) FontWeight.Bold else FontWeight.Medium, color = RokkhonPalette.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = RokkhonPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(UIcons.CheckCircle, null, tint = RokkhonPalette.Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp))
}

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, dark: Boolean = false) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(if (dark) RokkhonPalette.Primary else Color.White), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (dark) Color.White else RokkhonPalette.Ink, modifier = Modifier.size(19.dp))
    }
}
