package com.example.ui.sos

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.sos.LiveFix
import com.example.ui.components.RoundIconButton
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Live position on a MapLibre map with free OpenFreeMap tiles (no API key, no billing). Red dot for
 * the SOS phone, a translucent circle for GPS accuracy. Follows the dot until the viewer pans.
 */
@Composable
fun SosLiveMap(fix: LiveFix?, modifier: Modifier = Modifier, stale: Boolean = false) {
    // Robolectric and previews cannot load the native map: show the coordinates instead.
    if (LocalInspectionMode.current || Build.FINGERPRINT == "robolectric") {
        MapPlaceholder(fix, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var centeredOnce by remember { mutableStateOf(false) }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.uiSettings.isCompassEnabled = false
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) follow = false
                }
                m.setStyle(Style.Builder().fromUri(context.getString(R.string.sos_map_style_url))) { style ->
                    style.addSource(GeoJsonSource(SRC_ACC))
                    style.addSource(GeoJsonSource(SRC_DOT))
                    style.addLayer(
                        FillLayer(LAYER_ACC, SRC_ACC).withProperties(
                            PropertyFactory.fillColor("#E11900"),
                            PropertyFactory.fillOpacity(0.12f)
                        )
                    )
                    style.addLayer(
                        LineLayer(LAYER_ACC_LINE, SRC_ACC).withProperties(
                            PropertyFactory.lineColor("#E11900"),
                            PropertyFactory.lineOpacity(0.4f),
                            PropertyFactory.lineWidth(1f)
                        )
                    )
                    style.addLayer(
                        CircleLayer(LAYER_DOT, SRC_DOT).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor("#E11900"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(3f)
                        )
                    )
                    styleReady = true
                }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(fix, styleReady, follow) {
        val m = map ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.style?.let { style ->
            style.getSourceAs<GeoJsonSource>(SRC_DOT)?.setGeoJson(Point.fromLngLat(f.lng, f.lat))
            style.getSourceAs<GeoJsonSource>(SRC_ACC)?.setGeoJson(
                FeatureCollection.fromFeature(Feature.fromGeometry(circle(f.lat, f.lng, f.accuracyM.coerceIn(3.0, 5_000.0))))
            )
            style.getLayer(LAYER_DOT)?.setProperties(PropertyFactory.circleColor(if (stale) "#8A8A8A" else "#E11900"))
        }
        if (follow) {
            val target = LatLng(f.lat, f.lng)
            if (!centeredOnce) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16.0))
                centeredOnce = true
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLng(target), 600)
            }
        }
    }

    Box(modifier = modifier.testTag("sos_live_map")) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (!follow && fix != null) {
            RoundIconButton(
                icon = UIcons.MyLocation,
                onClick = { follow = true },
                filled = true,
                contentDescription = "Center",
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
    }
}

@Composable
private fun MapPlaceholder(fix: LiveFix?, modifier: Modifier) {
    Box(
        modifier = modifier.background(RokkhonPalette.Subtle).testTag("sos_live_map"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(UIcons.LocationOn, contentDescription = null, tint = RokkhonPalette.Sos, modifier = Modifier.size(30.dp))
            Text(
                text = fix?.let { "%.5f, %.5f".format(it.lat, it.lng) } ?: "—",
                fontSize = 13.sp,
                color = RokkhonPalette.Muted
            )
        }
    }
}

/** Accuracy circle as a 48-point polygon, so it stays true to scale at every zoom. */
private fun circle(lat: Double, lng: Double, radiusM: Double): Polygon {
    val r = radiusM / 6_371_000.0
    val latR = lat * PI / 180
    val lngR = lng * PI / 180
    val ring = (0..48).map { i ->
        val bearing = 2 * PI * i / 48
        val pLat = asin(sin(latR) * cos(r) + cos(latR) * sin(r) * cos(bearing))
        val pLng = lngR + atan2(sin(bearing) * sin(r) * cos(latR), cos(r) - sin(latR) * sin(pLat))
        Point.fromLngLat(pLng * 180 / PI, pLat * 180 / PI)
    }
    return Polygon.fromLngLats(listOf(ring))
}

private const val SRC_ACC = "sos-accuracy"
private const val SRC_DOT = "sos-position"
private const val LAYER_ACC = "sos-accuracy-fill"
private const val LAYER_ACC_LINE = "sos-accuracy-line"
private const val LAYER_DOT = "sos-position-dot"
