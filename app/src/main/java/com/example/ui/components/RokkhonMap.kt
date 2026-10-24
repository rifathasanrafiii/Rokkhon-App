package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.testTag
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
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** Something drawn on the map that the user can tap. */
sealed interface SelectedMapPin {
    data class DonorPin(val donor: DonorNode) : SelectedMapPin
    data class RequestPin(val request: BloodRequest) : SelectedMapPin
    data class SosPin(val alert: SosAlert) : SelectedMapPin
}

/** Imperative handle so Compose buttons (zoom, my location) can drive the map view. */
class RokkhonMapController {
    internal var mapView: MapView? = null
    fun zoomIn() { mapView?.controller?.zoomIn() }
    fun zoomOut() { mapView?.controller?.zoomOut() }
    fun animateTo(lat: Double, lng: Double, zoom: Double? = null) {
        val map = mapView ?: return
        if (zoom != null) map.controller.setZoom(zoom)
        map.controller.animateTo(GeoPoint(lat, lng))
    }
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    // OpenStreetMap's tile policy requires an identifying User-Agent.
    config.userAgentValue = context.packageName
    config.osmdroidBasePath = context.cacheDir.resolve("osmdroid")
    config.osmdroidTileCache = context.cacheDir.resolve("osmdroid/tiles")
}

/**
 * Real OpenStreetMap street map (osmdroid, no API key). Tiles are cached on the phone so
 * areas you have opened stay visible with a weak connection.
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
    showUser: Boolean,
    onPinSelected: (SelectedMapPin) -> Unit,
    controller: RokkhonMapController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPinSelected by rememberUpdatedState(onPinSelected)
    val pinIcons = remember { PinIconFactory(context) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = modifier.testTag("rokkhon_map")) {
        AndroidView(
            factory = { ctx ->
                configureOsmdroid(ctx)
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    // We call onDetach() ourselves when the composable leaves; a temporary window
                    // detach must not tear the map down while markers are still being added.
                    setDestroyMode(false)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    minZoomLevel = 6.0
                    maxZoomLevel = 19.0
                    this.controller.setZoom(14.5)
                    this.controller.setCenter(GeoPoint(centerLat, centerLng))
                    overlays.add(CopyrightOverlay(ctx))
                    mapView = this
                }
            },
            onRelease = { map ->
                map.tag = DETACHED_TAG
                if (controller.mapView === map) controller.mapView = null
                if (mapView === map) mapView = null
                map.onDetach()
            },
            modifier = Modifier.matchParentSize()
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val map = mapView
        controller.mapView = map
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Move the camera when the chosen area (or GPS centre) changes.
    LaunchedEffect(mapView, centerLat, centerLng) {
        mapView?.takeIf { it.tag != DETACHED_TAG }?.controller?.animateTo(GeoPoint(centerLat, centerLng))
    }

    // Rebuild markers whenever the data changes.
    LaunchedEffect(mapView, donors, requests, sosAlerts, userLat, userLng, showUser) {
        val map = mapView ?: return@LaunchedEffect
        if (map.tag == DETACHED_TAG) return@LaunchedEffect
        map.overlays.removeAll { it is Marker }
        fun add(lat: Double, lng: Double, icon: Bitmap, description: String, onTap: (() -> Unit)?) {
            val marker = Marker(map).apply {
                position = GeoPoint(lat, lng)
                this.icon = BitmapDrawable(context.resources, icon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                title = description
                setOnMarkerClickListener { _, _ ->
                    onTap?.invoke()
                    true
                }
            }
            map.overlays.add(marker)
        }
        donors.forEach { d ->
            add(d.latitude, d.longitude, pinIcons.donor(d.bloodGroup), d.displayName) {
                latestOnPinSelected(SelectedMapPin.DonorPin(d))
            }
        }
        requests.forEach { r ->
            add(r.latitude, r.longitude, pinIcons.request(r.bloodGroup), r.hospitalName) {
                latestOnPinSelected(SelectedMapPin.RequestPin(r))
            }
        }
        sosAlerts.forEach { s ->
            add(s.latitude, s.longitude, pinIcons.sos(), s.senderName) {
                latestOnPinSelected(SelectedMapPin.SosPin(s))
            }
        }
        if (showUser) add(userLat, userLng, pinIcons.user(), "You", null)
        map.invalidate()
    }
}

private const val DETACHED_TAG = "rokkhon_map_detached"

/** Draws the map pins once per label and caches them. */
private class PinIconFactory(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val cache = HashMap<String, Bitmap>()
    private fun dp(v: Float) = v * density

    private fun textPaint(color: Int, sizeSp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = dp(sizeSp)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun pill(key: String, label: String, fill: Int, stroke: Int, text: Int): Bitmap = cache.getOrPut(key) {
        val tp = textPaint(text, 12f)
        val w = (tp.measureText(label) + dp(18f)).coerceAtLeast(dp(34f))
        val h = dp(26f)
        val pad = dp(3f)
        val bmp = Bitmap.createBitmap((w + pad * 2).toInt(), (h + pad * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        c.drawRoundRect(RectF(pad, pad + dp(1.5f), pad + w, pad + h + dp(1.5f)), h / 2, h / 2, shadow)
        val body = RectF(pad, pad, pad + w, pad + h)
        c.drawRoundRect(body, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawRoundRect(body, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke; style = Paint.Style.STROKE; strokeWidth = dp(2f)
        })
        val baseline = body.centerY() - (tp.descent() + tp.ascent()) / 2
        c.drawText(label, body.centerX(), baseline, tp)
        bmp
    }

    fun donor(group: String) = pill("d_$group", group, 0xFFFFFFFF.toInt(), 0xFFC8102E.toInt(), 0xFFC8102E.toInt())
    fun request(group: String) = pill("r_$group", "+ $group", 0xFFC8102E.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
    fun sos() = pill("sos", "SOS", 0xFF1A1416.toInt(), 0xFFFF3B55.toInt(), 0xFFFFFFFF.toInt())

    fun user(): Bitmap = cache.getOrPut("user") {
        val size = dp(26f)
        val bmp = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val center = size / 2
        c.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332563EB })
        c.drawCircle(center, center, dp(8f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(center, center, dp(5.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2563EB.toInt() })
        bmp
    }
}
