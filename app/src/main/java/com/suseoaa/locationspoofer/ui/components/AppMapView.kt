package com.suseoaa.locationspoofer.ui.components

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng as AMapLatLng
import com.amap.api.maps.model.MarkerOptions as AMapMarkerOptions
import com.amap.api.maps.model.PolylineOptions as AMapPolylineOptions

import com.google.android.gms.maps.CameraUpdateFactory as GCameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GMapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory as GBitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng as GLatLng
import com.google.android.gms.maps.model.MarkerOptions as GMarkerOptions
import com.google.android.gms.maps.model.PolylineOptions as GPolylineOptions

interface AppMapMarker {
    fun setPosition(lat: Double, lng: Double)
}

enum class MarkerType { GREEN, RED, ORANGE, DEFAULT }

interface AppMapController {
    fun clear()
    fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float)
    fun addCircle(lat: Double, lng: Double, radius: Double, fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float)
    fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker
    fun animateCamera(lat: Double, lng: Double, zoom: Float? = null)
    fun moveCamera(lat: Double, lng: Double, zoom: Float? = null)
    val cameraTargetLat: Double?
    val cameraTargetLng: Double?
    fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit)
    fun disableUiControls()
}

class AMapControllerImpl(private val map: AMap) : AppMapController {
    override fun clear() { map.clear() }
    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        map.addPolyline(
            AMapPolylineOptions().color(colorInt).width(width).apply {
                points.forEach { add(AMapLatLng(it.first, it.second)) }
            }
        )
    }
    override fun addCircle(lat: Double, lng: Double, radius: Double, fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float) {
        map.addCircle(
            com.amap.api.maps.model.CircleOptions()
                .center(AMapLatLng(lat, lng))
                .radius(radius)
                .fillColor(fillColorInt)
                .strokeColor(strokeColorInt)
                .strokeWidth(strokeWidth)
        )
    }
    override fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker {
        val hue = when(type) {
            MarkerType.GREEN -> BitmapDescriptorFactory.HUE_GREEN
            MarkerType.RED -> BitmapDescriptorFactory.HUE_RED
            MarkerType.ORANGE -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_RED
        }
        val marker = map.addMarker(
            AMapMarkerOptions()
                .position(AMapLatLng(lat, lng))
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
        )
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) {
                marker?.position = AMapLatLng(lat, lng)
            }
        }
    }
    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(AMapLatLng(lat, lng), zoom))
        else map.animateCamera(CameraUpdateFactory.newLatLng(AMapLatLng(lat, lng)))
    }
    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(AMapLatLng(lat, lng), zoom))
        else map.moveCamera(CameraUpdateFactory.newLatLng(AMapLatLng(lat, lng)))
    }
    override val cameraTargetLat: Double? get() = map.cameraPosition?.target?.latitude
    override val cameraTargetLng: Double? get() = map.cameraPosition?.target?.longitude
    
    override fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit) {
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p0: com.amap.api.maps.model.CameraPosition?) {}
            override fun onCameraChangeFinish(p0: com.amap.api.maps.model.CameraPosition?) {
                p0?.target?.let { onFinish(it.latitude, it.longitude) }
            }
        })
    }
    override fun disableUiControls() {
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.setAllGesturesEnabled(true)
    }
}

class GMapControllerImpl(private val map: GoogleMap) : AppMapController {
    override fun clear() { map.clear() }
    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        map.addPolyline(
            GPolylineOptions().color(colorInt).width(width).apply {
                points.forEach { add(GLatLng(it.first, it.second)) }
            }
        )
    }
    override fun addCircle(lat: Double, lng: Double, radius: Double, fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float) {
        map.addCircle(
            com.google.android.gms.maps.model.CircleOptions()
                .center(GLatLng(lat, lng))
                .radius(radius)
                .fillColor(fillColorInt)
                .strokeColor(strokeColorInt)
                .strokeWidth(strokeWidth)
        )
    }
    override fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker {
        val hue = when(type) {
            MarkerType.GREEN -> GBitmapDescriptorFactory.HUE_GREEN
            MarkerType.RED -> GBitmapDescriptorFactory.HUE_RED
            MarkerType.ORANGE -> GBitmapDescriptorFactory.HUE_ORANGE
            else -> GBitmapDescriptorFactory.HUE_RED
        }
        val marker = map.addMarker(
            GMarkerOptions()
                .position(GLatLng(lat, lng))
                .title(title)
                .icon(GBitmapDescriptorFactory.defaultMarker(hue))
        )
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) {
                marker?.position = GLatLng(lat, lng)
            }
        }
    }
    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.animateCamera(GCameraUpdateFactory.newLatLngZoom(GLatLng(lat, lng), zoom))
        else map.animateCamera(GCameraUpdateFactory.newLatLng(GLatLng(lat, lng)))
    }
    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.moveCamera(GCameraUpdateFactory.newLatLngZoom(GLatLng(lat, lng), zoom))
        else map.moveCamera(GCameraUpdateFactory.newLatLng(GLatLng(lat, lng)))
    }
    override val cameraTargetLat: Double? get() = map.cameraPosition?.target?.latitude
    override val cameraTargetLng: Double? get() = map.cameraPosition?.target?.longitude
    
    override fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit) {
        map.setOnCameraIdleListener {
            val target = map.cameraPosition?.target
            if (target != null) {
                onFinish(target.latitude, target.longitude)
            }
        }
    }
    override fun disableUiControls() {
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.setAllGesturesEnabled(true)
    }
}

@Composable
fun AppMapView(isDomestic: Boolean, modifier: Modifier = Modifier, onMapReady: (AppMapController) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    if (isDomestic) {
        val amapView = remember { 
            val view = TextureMapView(context)
            view.onCreate(Bundle())
            view
        }
        DisposableEffect(lifecycle, amapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> amapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> amapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> amapView.onDestroy()
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) amapView.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                amapView.onPause()
                amapView.onDestroy()
            }
        }
        AndroidView(
            factory = {
                amapView.apply {
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                    map.setOnMapLoadedListener { onMapReady(AMapControllerImpl(map)) }
                }
            },
            modifier = modifier
        )
    } else {
        val gmapView = remember { 
            val view = GMapView(context)
            view.onCreate(Bundle())
            view
        }
        DisposableEffect(lifecycle, gmapView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> gmapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> gmapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> gmapView.onDestroy()
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) gmapView.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                gmapView.onPause()
                gmapView.onDestroy()
            }
        }
        AndroidView(
            factory = {
                gmapView.apply {
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                    getMapAsync { map -> onMapReady(GMapControllerImpl(map)) }
                }
            },
            modifier = modifier
        )
    }
}
