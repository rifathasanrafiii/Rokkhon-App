package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SosAlert
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class RokkhonMapController {
    internal var mapView: MapView? = null
    fun zoomIn() { mapView?.controller?.zoomIn() }
    fun zoomOut() { mapView?.controller?.zoomOut() }
    fun animateTo(lat: Double, lng: Double, zoom: Double = 14.5) {
        mapView?.controller?.setZoom(zoom)
        mapView?.controller?.animateTo(GeoPoint(lat, lng))
    }
}

private fun ensureOsmDroidConfigured(context: Context) {
    val appCtx = context.applicationContext
    val cfg = Configuration.getInstance()
    val pkg = appCtx.packageName.ifBlank { "com.aistudio.rokkhon" }
    cfg.userAgentValue = "$pkg/1.0"
    val base = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
    cfg.osmdroidBasePath = base
    cfg.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
}

/**
 * Native OpenStreetMap view (osmdroid) with a custom direct-canvas Overlay for real-time
 * directional user marker, donors, blood requests, and SOS pins (zero MarkerInfoWindow/MapViewRepository NPE).
 */
@Composable
fun RokkhonMap(
    centerLat: Double,
    centerLng: Double,
    donors: List<DonorNode>,
    requests: List<BloodRequest>,
    sosAlerts: List<SosAlert>,
    userLat: Double,
    userLng: Double,
    userBearing: Float = 0f,
    showUser: Boolean,
    followUser: Boolean = false,
    onUserGesture: () -> Unit = {},
    onPinSelected: (SelectedMapPin) -> Unit,
    controller: RokkhonMapController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember(context) {
        ensureOsmDroidConfigured(context)
        true
    }

    val pinIcons = remember(context) { PinIcons(context) }
    val currentOnPinSelected by rememberUpdatedState(onPinSelected)
    val currentOnUserGesture by rememberUpdatedState(onUserGesture)
    val pinsOverlay = remember(pinIcons) {
        RokkhonPinsOverlay(
            pinIcons = pinIcons,
            onPinTap = { pin -> currentOnPinSelected(pin) }
        )
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastAnimatedLat by remember { mutableStateOf(Double.NaN) }
    var lastAnimatedLng by remember { mutableStateOf(Double.NaN) }

    LaunchedEffect(mapView, centerLat, centerLng) {
        val mv = mapView ?: return@LaunchedEffect
        mv.controller?.animateTo(GeoPoint(centerLat, centerLng))
        lastAnimatedLat = centerLat
        lastAnimatedLng = centerLng
    }

    LaunchedEffect(mapView, userLat, userLng, followUser) {
        val mv = mapView ?: return@LaunchedEffect
        if (followUser && showUser) {
            val movedEnough = lastAnimatedLat.isNaN() ||
                kotlin.math.abs(userLat - lastAnimatedLat) > 0.00001 ||
                kotlin.math.abs(userLng - lastAnimatedLng) > 0.00001
            if (movedEnough) {
                mv.controller?.setCenter(GeoPoint(userLat, userLng))
                lastAnimatedLat = userLat
                lastAnimatedLng = userLng
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ensureOsmDroidConfigured(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 6.5
                maxZoomLevel = 19.5
                this.controller.setZoom(15.0)
                this.controller.setCenter(GeoPoint(centerLat, centerLng))
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            if (event.actionMasked == MotionEvent.ACTION_MOVE || event.pointerCount >= 2) {
                                currentOnUserGesture()
                            }
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
                overlays.add(pinsOverlay)
                controller.mapView = this
                mapView = this
            }
        },
        update = { map ->
            pinsOverlay.showUser = showUser
            pinsOverlay.userLat = userLat
            pinsOverlay.userLng = userLng
            pinsOverlay.userBearing = userBearing
            pinsOverlay.donors = donors
            pinsOverlay.requests = requests
            pinsOverlay.sosAlerts = sosAlerts
            if (!map.overlays.contains(pinsOverlay)) {
                map.overlays.add(pinsOverlay)
            }
            map.postInvalidate()
        },
        onRelease = { map ->
            if (controller.mapView === map) {
                controller.mapView = null
            }
            if (mapView === map) {
                mapView = null
            }
            try {
                map.onPause()
                map.onDetach()
            } catch (_: Exception) {
            }
        }
    )
}

/**
 * Direct-canvas osmdroid Overlay that draws all markers and handles tap hit-testing
 * without touching MapViewRepository or MarkerInfoWindow.
 */
private class RokkhonPinsOverlay(
    private val pinIcons: PinIcons,
    private val onPinTap: (SelectedMapPin) -> Unit
) : Overlay() {

    var showUser: Boolean = true
    var userLat: Double = 23.8103
    var userLng: Double = 90.4125
    var userBearing: Float = 0f
    var donors: List<DonorNode> = emptyList()
    var requests: List<BloodRequest> = emptyList()
    var sosAlerts: List<SosAlert> = emptyList()

    private val tmpPoint = Point()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection ?: return

        // 1. Donors (bottom-anchored teardrop)
        donors.forEach { d ->
            proj.toPixels(GeoPoint(d.latitude, d.longitude), tmpPoint)
            val bmp = pinIcons.donorBitmap(d.bloodGroup)
            canvas.drawBitmap(
                bmp,
                tmpPoint.x - bmp.width / 2f,
                tmpPoint.y - bmp.height.toFloat(),
                bmpPaint
            )
        }

        // 2. Patient Blood Requests (center-anchored badge)
        requests.forEach { r ->
            proj.toPixels(GeoPoint(r.latitude, r.longitude), tmpPoint)
            val bmp = pinIcons.requestBitmap(r.bloodGroup)
            canvas.drawBitmap(
                bmp,
                tmpPoint.x - bmp.width / 2f,
                tmpPoint.y - bmp.height / 2f,
                bmpPaint
            )
        }

        // 3. Active SOS Alerts (center-anchored pill)
        sosAlerts.forEach { s ->
            proj.toPixels(GeoPoint(s.latitude, s.longitude), tmpPoint)
            val bmp = pinIcons.sosBitmap(s.isCommunityDisputed && !s.isShieldedByEvidenceOrResponders)
            canvas.drawBitmap(
                bmp,
                tmpPoint.x - bmp.width / 2f,
                tmpPoint.y - bmp.height / 2f,
                bmpPaint
            )
        }

        // 4. Live User GPS Dot + Directional Heading Arrow (drawn on top)
        if (showUser) {
            proj.toPixels(GeoPoint(userLat, userLng), tmpPoint)
            val bmp = pinIcons.userBitmap(userBearing)
            canvas.drawBitmap(
                bmp,
                tmpPoint.x - bmp.width / 2f,
                tmpPoint.y - bmp.height / 2f,
                bmpPaint
            )
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val proj = mapView.projection ?: return false
        val tapX = e.x
        val tapY = e.y
        val hitRadiusPx = 42f * pinIcons.density

        for (s in sosAlerts) {
            proj.toPixels(GeoPoint(s.latitude, s.longitude), tmpPoint)
            if (hypot(tapX - tmpPoint.x, tapY - tmpPoint.y) <= hitRadiusPx) {
                onPinTap(SelectedMapPin.SosPin(s))
                return true
            }
        }

        for (r in requests) {
            proj.toPixels(GeoPoint(r.latitude, r.longitude), tmpPoint)
            if (hypot(tapX - tmpPoint.x, tapY - tmpPoint.y) <= hitRadiusPx) {
                onPinTap(SelectedMapPin.RequestPin(r))
                return true
            }
        }

        for (d in donors) {
            proj.toPixels(GeoPoint(d.latitude, d.longitude), tmpPoint)
            if (hypot(tapX - tmpPoint.x, tapY - (tmpPoint.y - 16f * pinIcons.density)) <= hitRadiusPx) {
                onPinTap(SelectedMapPin.DonorPin(d))
                return true
            }
        }

        return false
    }
}

private class PinIcons(context: Context) {
    val density: Float = context.resources.displayMetrics.density
    private val d = density
    private val cache = HashMap<String, Bitmap>()

    fun donorBitmap(bloodGroup: String): Bitmap = cache.getOrPut("d_$bloodGroup") {
        val w = (34 * d).toInt().coerceAtLeast(1)
        val h = (42 * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = w / 2f - 2 * d
        val cx = w / 2f
        val cy = r + 2 * d
        val path = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.55f, cy + r * 0.55f)
            lineTo(cx, h - 2 * d)
            lineTo(cx + r * 0.55f, cy + r * 0.55f)
            close()
        }
        c.drawPath(path, fill(0xFFB80F24.toInt()))
        c.drawCircle(cx, cy, r, stroke(0xFFFFFFFF.toInt(), 2f * d))
        drawCentredText(c, bloodGroup, cx, cy, 11f * d, 0xFFFFFFFF.toInt())
        bmp
    }

    fun requestBitmap(bloodGroup: String): Bitmap = cache.getOrPut("r_$bloodGroup") {
        val s = (38 * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        c.drawCircle(cx, cx, cx - 2 * d, fill(0xFF7A0C18.toInt()))
        c.drawCircle(cx, cx, cx - 6 * d, fill(0xFFFFFFFF.toInt()))
        drawCentredText(c, bloodGroup, cx, cx, 11f * d, 0xFF7A0C18.toInt())
        bmp
    }

    fun sosBitmap(disputed: Boolean): Bitmap = cache.getOrPut("s_$disputed") {
        val w = (54 * d).toInt().coerceAtLeast(1)
        val h = (28 * d).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(2 * d, 2 * d, w - 2 * d, h - 2 * d)
        val bg = if (disputed) 0xFFD97706.toInt() else 0xFFE11D48.toInt()
        c.drawRoundRect(rect, h / 2f, h / 2f, fill(bg))
        c.drawRoundRect(rect, h / 2f, h / 2f, stroke(0xFFFFFFFF.toInt(), 2f * d))
        drawCentredText(c, "SOS", w / 2f, h / 2f, 11f * d, 0xFFFFFFFF.toInt())
        bmp
    }

    /**
     * Directional live user marker: quantizes bearing to 5-degree steps and draws a clear
     * directional navigation cone + arrow pointing in the exact direction of movement.
     */
    fun userBitmap(bearingDegrees: Float): Bitmap {
        val bucket = (((bearingDegrees % 360f) + 360f) % 360f / 5f).roundToInt() * 5 % 360
        return cache.getOrPut("u_$bucket") {
            val s = (50 * d).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = s / 2f
            val cy = s / 2f

            // Outer soft halo
            c.drawCircle(cx, cy, cx - 4 * d, fill(0x33007AFF))

            // Directional heading cone + arrow tip in the exact movement direction
            val rad = Math.toRadians(bucket.toDouble())
            val dirX = sin(rad).toFloat()
            val dirY = -cos(rad).toFloat()
            val perpX = -dirY
            val perpY = dirX

            val tipX = cx + dirX * (cx - 2 * d)
            val tipY = cy + dirY * (cx - 2 * d)
            val baseCenterX = cx + dirX * (6 * d)
            val baseCenterY = cy + dirY * (6 * d)
            val halfWing = 7.5f * d

            val arrowPath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(baseCenterX + perpX * halfWing, baseCenterY + perpY * halfWing)
                lineTo(baseCenterX - perpX * halfWing, baseCenterY - perpY * halfWing)
                close()
            }
            c.drawPath(arrowPath, fill(0xFF007AFF.toInt()))
            c.drawPath(arrowPath, stroke(0xFFFFFFFF.toInt(), 1.5f * d))

            // Core GPS dot
            c.drawCircle(cx, cy, 9.5f * d, fill(0xFFFFFFFF.toInt()))
            c.drawCircle(cx, cy, 7f * d, fill(0xFF007AFF.toInt()))
            bmp
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private fun drawCentredText(c: Canvas, text: String, cx: Float, cy: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText(text, cx, cy - (p.descent() + p.ascent()) / 2f, p)
    }
}
