package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.BleSosBeacon
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SosAlert
import com.example.data.model.canDonateTo
import com.example.ui.icons.UIcons
import com.example.ui.screens.donorInitials
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemOrange
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarGlassCard
import com.example.ui.theme.RadarObsidianBg
import com.example.ui.viewmodel.MapLayerFilter
import kotlin.math.hypot

sealed interface SelectedMapPin {
    data class DonorPin(val donor: DonorNode) : SelectedMapPin
    data class RequestPin(val request: BloodRequest) : SelectedMapPin
    data class SosPin(val alert: SosAlert) : SelectedMapPin
    data class BlePin(val beacon: BleSosBeacon) : SelectedMapPin
}

/**
 * Real Interactive Bangladesh Street Map (OpenStreetMap Leaflet Tiles + High-Detail Street Fallback):
 * - Separates Available Donors (DONORS_ONLY) and Patient Blood Requests (BLOOD_REQUESTS_ONLY) cleanly.
 * - Highlights the selected Bangladesh area radius and updates pins in real time.
 * - Uses 100% vector graphics and SVG markers with zero emojis.
 */
@Composable
fun InteractiveBangladeshRadarMap(
    centerLat: Double,
    centerLng: Double,
    donors: List<DonorNode>,
    bloodRequests: List<BloodRequest>,
    sosAlerts: List<SosAlert>,
    bleBeacons: List<BleSosBeacon>,
    selectedBloodGroup: String?,
    layerFilter: MapLayerFilter,
    onPinSelected: (SelectedMapPin) -> Unit,
    showLiveFloatingCard: Boolean = true,
    isLiveLocationActive: Boolean = false,
    selectedAreaName: String = "Dhanmondi, Dhaka",
    /** The user's real GPS position, or null when there is no fix yet. */
    userLat: Double? = null,
    userLng: Double? = null,
    userBearing: Float = 0f,
    onRefreshLiveGps: () -> Unit = {},
    initialUseOsmTiles: Boolean = true,
    bengali: Boolean = false,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableFloatStateOf(1.15f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var useOsmRealTiles by remember { mutableStateOf(initialUseOsmTiles) }
    var followUserGps by remember { mutableStateOf(true) }
    val mapController = remember { RokkhonMapController() }
    // Enough cache for every district label, so panning does not re-run text layout each frame.
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)

    // The pulse only runs while the drawn map is visible; under the OSM street map it would
    // redraw a hidden canvas every frame.
    val infiniteTransition = rememberInfiniteTransition(label = "real_map_pulse")
    val radarPulseState: State<Float> = if (useOsmRealTiles) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_ring"
        )
    }
    val radarPulse by radarPulseState

    val filteredDonors = remember(donors, selectedBloodGroup, layerFilter) {
        if (layerFilter != MapLayerFilter.ALL && layerFilter != MapLayerFilter.DONORS_ONLY) {
            emptyList()
        } else {
            donors.filter { it.isEligibleNow && (selectedBloodGroup == null || canDonateTo(it.bloodGroup, selectedBloodGroup)) }
        }
    }

    val filteredRequests = remember(bloodRequests, selectedBloodGroup, layerFilter) {
        if (layerFilter != MapLayerFilter.ALL && layerFilter != MapLayerFilter.BLOOD_REQUESTS_ONLY) {
            emptyList()
        } else {
            bloodRequests.filter { it.status == "OPEN" && (selectedBloodGroup == null || it.bloodGroup == selectedBloodGroup) }
        }
    }

    // Active SOS alerts stay on the map until the sender marks Safe or 24 hours pass (same rule as the lists).
    val filteredSos = remember(sosAlerts) {
        sosAlerts.filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
    }



    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFEAE6DF))
            .testTag("interactive_bangladesh_map")
    ) {
        // 1. Drawn area map: the offline fallback, shown when the street map layer is off.
        if (!useOsmRealTiles) Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(centerLat, centerLng, filteredDonors, filteredRequests, filteredSos, bleBeacons, zoomLevel, panOffset) {
                    detectTapGestures { tapOffset ->
                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat() * 0.78f
                        val scale = 9200f * zoomLevel

                        fun latLngToScreen(lat: Double, lng: Double): Offset {
                            val dx = ((lng - centerLng) * scale).toFloat()
                            val dy = ((centerLat - lat) * scale).toFloat()
                            return Offset(widthPx / 2f + dx + panOffset.x, heightPx / 2f + dy + panOffset.y)
                        }

                        for (alert in filteredSos) {
                            val pos = latLngToScreen(alert.latitude, alert.longitude)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 44.dp.toPx()) {
                                onPinSelected(SelectedMapPin.SosPin(alert))
                                return@detectTapGestures
                            }
                        }

                        for (req in filteredRequests) {
                            val pos = latLngToScreen(req.latitude, req.longitude)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 44.dp.toPx()) {
                                onPinSelected(SelectedMapPin.RequestPin(req))
                                return@detectTapGestures
                            }
                        }

                        for (donor in filteredDonors) {
                            val pos = latLngToScreen(donor.latitude, donor.longitude)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 40.dp.toPx()) {
                                onPinSelected(SelectedMapPin.DonorPin(donor))
                                return@detectTapGestures
                            }
                        }

                        for (ble in bleBeacons) {
                            val pos = latLngToScreen(ble.latitude, ble.longitude)
                            if (hypot(tapOffset.x - pos.x, tapOffset.y - pos.y) <= 40.dp.toPx()) {
                                onPinSelected(SelectedMapPin.BlePin(ble))
                                return@detectTapGestures
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.6f, 3.2f)
                        panOffset += pan
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val scale = 9200f * zoomLevel
            val mapCenter = Offset(w / 2f + panOffset.x, h * 0.44f + panOffset.y)

            fun latLngToOffset(lat: Double, lng: Double): Offset {
                val dx = ((lng - centerLng) * scale).toFloat()
                val dy = ((centerLat - lat) * scale).toFloat()
                return Offset(mapCenter.x + dx, mapCenter.y + dy)
            }

            // Realistic Street Map Base (Warm Cartographic Land + Water + Parks + Arterial Roads)
            drawRect(color = Color(0xFFF2EFE9))

            // Dhanmondi Lake / Buriganga / Hatirjheel Water Channel Geometry
            val lakePath = Path().apply {
                moveTo(0f, mapCenter.y - 95.dp.toPx())
                cubicTo(
                    w * 0.28f, mapCenter.y - 45.dp.toPx(),
                    w * 0.55f, mapCenter.y - 120.dp.toPx(),
                    w, mapCenter.y - 60.dp.toPx()
                )
                lineTo(w, mapCenter.y - 25.dp.toPx())
                cubicTo(
                    w * 0.55f, mapCenter.y - 82.dp.toPx(),
                    w * 0.28f, mapCenter.y - 10.dp.toPx(),
                    0f, mapCenter.y - 58.dp.toPx()
                )
                close()
            }
            drawPath(path = lakePath, color = Color(0xFFAAD3DF))

            // Green Park Blocks (Ramna Park / Rabindra Sarobar)
            drawRoundRect(
                color = Color(0xFFCDEBB0).copy(alpha = 0.75f),
                topLeft = Offset(mapCenter.x - 130.dp.toPx(), mapCenter.y - 70.dp.toPx()),
                size = Size(85.dp.toPx(), 54.dp.toPx()),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFCDEBB0).copy(alpha = 0.75f),
                topLeft = Offset(mapCenter.x + 55.dp.toPx(), mapCenter.y + 18.dp.toPx()),
                size = Size(95.dp.toPx(), 62.dp.toPx()),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )

            // Secondary Street Grid
            val gridStep = (48.dp.toPx() * zoomLevel).coerceIn(34f, 120f)
            var gx = (mapCenter.x % gridStep)
            while (gx < w) {
                drawLine(
                    color = Color.White,
                    start = Offset(gx, 0f),
                    end = Offset(gx + 16.dp.toPx(), h),
                    strokeWidth = 3.5.dp.toPx()
                )
                gx += gridStep
            }
            var gy = (mapCenter.y % gridStep)
            while (gy < h) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, gy),
                    end = Offset(w, gy - 12.dp.toPx()),
                    strokeWidth = 3.5.dp.toPx()
                )
                gy += gridStep
            }

            // Major Arterial Highways (Mirpur Road / Kazi Nazrul Islam Ave / Satmasjid Road)
            drawLine(
                color = Color(0xFFFCD6A4),
                start = Offset(0f, mapCenter.y - h * 0.22f),
                end = Offset(w, mapCenter.y + h * 0.25f),
                strokeWidth = 7.dp.toPx()
            )
            drawLine(
                color = Color(0xFFFCD6A4),
                start = Offset(w * 0.18f, h),
                end = Offset(w * 0.82f, 0f),
                strokeWidth = 6.5.dp.toPx()
            )

            // Area Trigger Radius Ring around Selected Area
            drawCircle(
                color = PinCrimsonRed.copy(alpha = 0.09f),
                radius = 128.dp.toPx() * zoomLevel.coerceIn(0.8f, 1.3f),
                center = mapCenter
            )
            drawCircle(
                color = PinCrimsonRed.copy(alpha = 0.38f),
                radius = 128.dp.toPx() * zoomLevel.coerceIn(0.8f, 1.3f),
                center = mapCenter,
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(
                color = PinCrimsonRed.copy(alpha = (1f - radarPulse) * 0.22f),
                radius = 140.dp.toPx() * radarPulse,
                center = mapCenter
            )

            // Bangladesh Area Labels
            BANGLADESH_DISTRICTS.forEach { district ->
                val dPos = latLngToOffset(district.latitude, district.longitude)
                if (dPos.x in -80f..(w + 80f) && dPos.y in -80f..(h + 80f)) {
                    val labelLayout = textMeasurer.measure(
                        text = district.nameEn.uppercase(),
                        style = TextStyle(
                            color = Color(0xFF495057),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(dPos.x - labelLayout.size.width / 2f, dPos.y + 22.dp.toPx())
                    )
                }
            }

            // Draw Available Donor Teardrop Pins (Only when DONORS_ONLY or ALL)
            filteredDonors.forEach { donor ->
                val pos = latLngToOffset(donor.latitude, donor.longitude)
                val pinRadius = 15.dp.toPx()

                drawCircle(
                    color = PinCrimsonRed.copy(alpha = 0.22f),
                    radius = pinRadius * 1.45f,
                    center = pos
                )

                val pinPath = Path().apply {
                    moveTo(pos.x, pos.y + pinRadius * 1.45f)
                    cubicTo(
                        pos.x - pinRadius * 1.2f, pos.y + pinRadius * 0.3f,
                        pos.x - pinRadius, pos.y - pinRadius,
                        pos.x, pos.y - pinRadius
                    )
                    cubicTo(
                        pos.x + pinRadius, pos.y - pinRadius,
                        pos.x + pinRadius * 1.2f, pos.y + pinRadius * 0.3f,
                        pos.x, pos.y + pinRadius * 1.45f
                    )
                    close()
                }
                drawPath(path = pinPath, color = PinCrimsonRed)

                val bgLayout = textMeasurer.measure(
                    text = donor.bloodGroup,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                drawText(
                    textLayoutResult = bgLayout,
                    topLeft = Offset(
                        pos.x - bgLayout.size.width / 2f,
                        pos.y - pinRadius * 0.1f - bgLayout.size.height / 2f
                    )
                )
            }

            // Draw Patient Blood Request Hospital [H] Markers (Only when BLOOD_REQUESTS_ONLY or ALL)
            filteredRequests.forEach { req ->
                val pos = latLngToOffset(req.latitude, req.longitude)

                drawRoundRect(
                    color = Color(0xFF000000),
                    topLeft = Offset(pos.x - 20.dp.toPx(), pos.y - 20.dp.toPx()),
                    size = Size(40.dp.toPx(), 40.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 15.dp.toPx(),
                    center = pos
                )
                val hLayout = textMeasurer.measure(
                    text = req.bloodGroup,
                    style = TextStyle(
                        color = Color(0xFF000000),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                drawText(
                    textLayoutResult = hLayout,
                    topLeft = Offset(
                        pos.x - hLayout.size.width / 2f,
                        pos.y - hLayout.size.height / 2f
                    )
                )
            }

            // Draw Active SOS Alerts (Removed immediately when marked Safe)
            filteredSos.forEach { alert ->
                val pos = latLngToOffset(alert.latitude, alert.longitude)
                drawCircle(
                    color = IosSystemOrange.copy(alpha = (1f - radarPulse) * 0.5f),
                    radius = 38.dp.toPx() * (0.5f + radarPulse * 0.6f),
                    center = pos
                )
                drawRoundRect(
                    color = Color(0xFFE11900),
                    topLeft = Offset(pos.x - 28.dp.toPx(), pos.y - 14.dp.toPx()),
                    size = Size(56.dp.toPx(), 28.dp.toPx()),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                val sosLayout = textMeasurer.measure(
                    text = "SOS",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(
                    textLayoutResult = sosLayout,
                    topLeft = Offset(
                        pos.x - sosLayout.size.width / 2f,
                        pos.y - sosLayout.size.height / 2f
                    )
                )
            }

            // Live User GPS Dot + Directional Heading Arrow
            val gpsCenter = latLngToOffset(userLat ?: centerLat, userLng ?: centerLng)
            drawCircle(
                color = IosSystemBlue.copy(alpha = 0.25f),
                radius = 22.dp.toPx(),
                center = gpsCenter
            )
            val rad = Math.toRadians(userBearing.toDouble())
            val dirX = kotlin.math.sin(rad).toFloat()
            val dirY = -kotlin.math.cos(rad).toFloat()
            val perpX = -dirY
            val perpY = dirX
            val tip = Offset(gpsCenter.x + dirX * 20.dp.toPx(), gpsCenter.y + dirY * 20.dp.toPx())
            val baseCenter = Offset(gpsCenter.x + dirX * 5.dp.toPx(), gpsCenter.y + dirY * 5.dp.toPx())
            val wing = 6.5.dp.toPx()
            val dirArrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(baseCenter.x + perpX * wing, baseCenter.y + perpY * wing)
                lineTo(baseCenter.x - perpX * wing, baseCenter.y - perpY * wing)
                close()
            }
            drawPath(path = dirArrow, color = IosSystemBlue)
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = gpsCenter
            )
            drawCircle(
                color = IosSystemBlue,
                radius = 6.5.dp.toPx(),
                center = gpsCenter
            )
        }

        // 2. Real OpenStreetMap street map (native osmdroid tiles + pins).
        if (useOsmRealTiles) {
            RokkhonMap(
                centerLat = centerLat,
                centerLng = centerLng,
                donors = filteredDonors,
                requests = filteredRequests,
                sosAlerts = filteredSos,
                userLat = userLat ?: centerLat,
                userLng = userLng ?: centerLng,
                userBearing = userBearing,
                showUser = true,
                followUser = followUserGps,
                onUserGesture = { followUserGps = false },
                onPinSelected = onPinSelected,
                controller = mapController,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top-Right Map Controls (Zoom In, Zoom Out, Recenter, Toggle Street/Vector Layer)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = RadarObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                IconButton(
                    onClick = {
                        zoomLevel = (zoomLevel * 1.25f).coerceAtMost(3.2f)
                        mapController.zoomIn()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("map_zoom_in_button")
                ) {
                    Icon(UIcons.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                color = RadarObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                IconButton(
                    onClick = {
                        zoomLevel = (zoomLevel / 1.25f).coerceAtLeast(0.6f)
                        mapController.zoomOut()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("map_zoom_out_button")
                ) {
                    Icon(UIcons.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                color = if (followUserGps || isLiveLocationActive) IosSystemBlue else RadarObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                IconButton(
                    onClick = {
                        panOffset = Offset.Zero
                        zoomLevel = 1.15f
                        followUserGps = true
                        onRefreshLiveGps()
                        mapController.animateTo(userLat ?: centerLat, userLng ?: centerLng, 16.0)
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("map_recenter_button")
                ) {
                    Icon(UIcons.MyLocation, contentDescription = "Recenter", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Surface(
                color = if (useOsmRealTiles) Color.Black else RadarObsidianBg.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                IconButton(
                    onClick = { useOsmRealTiles = !useOsmRealTiles },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("map_style_toggle_button")
                ) {
                    Icon(UIcons.Layers, contentDescription = "Toggle Map Layer", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Bottom Floating Area Status Pill inside Map
        if (showLiveFloatingCard) {
            Surface(
                color = RadarGlassCard.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isLiveLocationActive) PinCrimsonRed else IosSystemGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val dirLabel = compassDirectionLabel(userBearing, bengali)
                            Text(
                                text = if (isLiveLocationActive) {
                                    if (bengali) "লাইভ SOS লোকেশন ($dirLabel) • $selectedAreaName" else "LIVE SOS ($dirLabel) • $selectedAreaName"
                                } else {
                                    if (bengali) "লাইভ লোকেশন ($dirLabel) • $selectedAreaName" else "LIVE GPS ($dirLabel) • $selectedAreaName"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val headlineText = if (bengali) {
                            when (layerFilter) {
                                MapLayerFilter.DONORS_ONLY -> "এলাকায় ${filteredDonors.size} জন উপলব্ধ ডোনার"
                                MapLayerFilter.BLOOD_REQUESTS_ONLY -> "এলাকায় ${filteredRequests.size}টি রক্তের আবেদন"
                                MapLayerFilter.SOS_ALERTS_ONLY -> "${filteredSos.size}টি সক্রিয় SOS"
                                MapLayerFilter.ALL -> "${filteredDonors.size} ডোনার • ${filteredRequests.size} আবেদন"
                            }
                        } else {
                            when (layerFilter) {
                                MapLayerFilter.DONORS_ONLY -> "${filteredDonors.size} Available Donors in Area"
                                MapLayerFilter.BLOOD_REQUESTS_ONLY -> "${filteredRequests.size} Patient Blood Requests in Area"
                                MapLayerFilter.SOS_ALERTS_ONLY -> "${filteredSos.size} Active Emergency SOS Alerts"
                                MapLayerFilter.ALL -> "${filteredDonors.size} Donors • ${filteredRequests.size} Requests"
                            }
                        }
                        Text(
                            text = headlineText,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (filteredDonors.isNotEmpty()) {
                        OverlappingDonorAvatarRow(
                            initials = filteredDonors.take(3).map { com.example.ui.screens.donorInitials(it.displayName) },
                            extraCount = (filteredDonors.size - 3).coerceAtLeast(0)
                        )
                    }
                }
            }
        }

        // OpenStreetMap's licence requires visible credit; the map's own credit sits under the card.
        if (useOsmRealTiles) {
            Text(
                text = "© OpenStreetMap contributors",
                fontSize = 9.sp,
                color = Color(0xFF3A3A3A),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (showLiveFloatingCard) 92.dp else 8.dp)
                    .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
fun OverlappingDonorAvatarRow(
    initials: List<String>,
    extraCount: Int,
    modifier: Modifier = Modifier
) {
    val avatarColors = listOf(
        Color(0xFF000000),
        Color(0xFF1565C0),
        Color(0xFF0F6E56),
        Color(0xFF7C2D12)
    )
    val totalWidth = (initials.size * 21 + if (extraCount > 0) 32 else 11).dp
    Box(
        modifier = modifier.width(totalWidth),
        contentAlignment = Alignment.CenterStart
    ) {
        initials.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .offset(x = (index * 21).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColors[index % avatarColors.size])
                    .border(1.8.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (extraCount > 0) Box(
            modifier = Modifier
                .offset(x = (initials.size * 21).dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color(0xFFE8E8E8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+$extraCount",
                color = Color(0xFF000000),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

fun compassDirectionLabel(bearingDegrees: Float, bengali: Boolean): String {
    val deg = ((bearingDegrees % 360f) + 360f) % 360f
    val sector = ((deg + 22.5f) / 45f).toInt() % 8
    val dir = if (bengali) {
        when (sector) {
            0 -> "উত্তর"
            1 -> "উত্তর-পূর্ব"
            2 -> "পূর্ব"
            3 -> "দক্ষিণ-পূর্ব"
            4 -> "দক্ষিণ"
            5 -> "দক্ষিণ-পশ্চিম"
            6 -> "পশ্চিম"
            else -> "উত্তর-পশ্চিম"
        }
    } else {
        when (sector) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            else -> "NW"
        }
    }
    return "$dir ${deg.toInt()}°"
}

