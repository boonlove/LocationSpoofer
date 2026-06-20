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
import com.suseoaa.locationspoofer.data.model.AppMapType

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
    fun fitBounds(points: List<Pair<Double, Double>>, padding: Int)
    fun moveCamera(lat: Double, lng: Double, zoom: Float? = null)
    val cameraTargetLat: Double?
    val cameraTargetLng: Double?
    fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit)
    fun disableUiControls()
    fun setMapType(type: AppMapType)
    fun setDarkMode(isDark: Boolean, context: android.content.Context)
}

class AMapControllerImpl(private val map: AMap) : AppMapController {
    private var isDarkMode: Boolean = false
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: android.content.Context) {
        isDarkMode = isDark
        setMapType(currentMapType)
    }

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
    override fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) {
        if (points.isEmpty()) return
        val builder = com.amap.api.maps.model.LatLngBounds.Builder()
        points.forEach { builder.include(AMapLatLng(it.first, it.second)) }
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
        } catch (e: Exception) { e.printStackTrace() }
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
    
    override fun setMapType(type: AppMapType) {
        currentMapType = type
        when (type) {
            AppMapType.NORMAL -> {
                map.mapType = if (isDarkMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
                val cameraPosition = map.cameraPosition ?: return
                val newCam = com.amap.api.maps.model.CameraPosition(
                    cameraPosition.target,
                    cameraPosition.zoom,
                    0f,
                    cameraPosition.bearing
                )
                map.moveCamera(CameraUpdateFactory.newCameraPosition(newCam))
            }
            AppMapType.SATELLITE -> {
                map.mapType = AMap.MAP_TYPE_SATELLITE
                val cameraPosition = map.cameraPosition ?: return
                val newCam = com.amap.api.maps.model.CameraPosition(
                    cameraPosition.target,
                    cameraPosition.zoom,
                    0f,
                    cameraPosition.bearing
                )
                map.moveCamera(CameraUpdateFactory.newCameraPosition(newCam))
            }
            AppMapType.MAP_3D -> {
                map.mapType = if (isDarkMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
                val cameraPosition = map.cameraPosition ?: return
                val newCam = com.amap.api.maps.model.CameraPosition(
                    cameraPosition.target,
                    cameraPosition.zoom,
                    45f,
                    cameraPosition.bearing
                )
                map.moveCamera(CameraUpdateFactory.newCameraPosition(newCam))
            }
        }
    }
}

class GMapControllerImpl(private val map: GoogleMap) : AppMapController {
    private var isDarkMode: Boolean = false
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: android.content.Context) {
        isDarkMode = isDark
        try {
            if (isDark) {
                map.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(context, com.suseoaa.locationspoofer.R.raw.map_style_dark))
            } else {
                map.setMapStyle(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setMapType(currentMapType)
    }

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
    override fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) {
        if (points.isEmpty()) return
        val builder = com.google.android.gms.maps.model.LatLngBounds.Builder()
        points.forEach { builder.include(GLatLng(it.first, it.second)) }
        try {
            map.animateCamera(GCameraUpdateFactory.newLatLngBounds(builder.build(), padding))
        } catch (e: Exception) { e.printStackTrace() }
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

    override fun setMapType(type: AppMapType) {
        currentMapType = type
        when (type) {
            AppMapType.NORMAL -> {
                map.mapType = GoogleMap.MAP_TYPE_NORMAL
                val cameraPosition = map.cameraPosition
                val newCam = com.google.android.gms.maps.model.CameraPosition.builder()
                    .target(cameraPosition.target)
                    .zoom(cameraPosition.zoom)
                    .tilt(0f)
                    .bearing(cameraPosition.bearing)
                    .build()
                map.moveCamera(GCameraUpdateFactory.newCameraPosition(newCam))
            }
            AppMapType.SATELLITE -> {
                map.mapType = GoogleMap.MAP_TYPE_HYBRID
                val cameraPosition = map.cameraPosition
                val newCam = com.google.android.gms.maps.model.CameraPosition.builder()
                    .target(cameraPosition.target)
                    .zoom(cameraPosition.zoom)
                    .tilt(0f)
                    .bearing(cameraPosition.bearing)
                    .build()
                map.moveCamera(GCameraUpdateFactory.newCameraPosition(newCam))
            }
            AppMapType.MAP_3D -> {
                map.mapType = GoogleMap.MAP_TYPE_NORMAL
                map.isBuildingsEnabled = true
                val cameraPosition = map.cameraPosition
                val newCam = com.google.android.gms.maps.model.CameraPosition.builder()
                    .target(cameraPosition.target)
                    .zoom(cameraPosition.zoom)
                    .tilt(45f)
                    .bearing(cameraPosition.bearing)
                    .build()
                map.moveCamera(GCameraUpdateFactory.newCameraPosition(newCam))
            }
        }
    }
}

class BaiduMapControllerImpl(
    private val map: com.baidu.mapapi.map.BaiduMap,
    private val mapView: com.baidu.mapapi.map.TextureMapView,
    private val context: android.content.Context,
    private val styleId: String = ""
) : AppMapController {
    private var isDarkMode: Boolean = false
    private var currentMapType: AppMapType = AppMapType.NORMAL

    override fun setDarkMode(isDark: Boolean, context: android.content.Context) {
        isDarkMode = isDark
        val customStyleOptions = com.baidu.mapapi.map.MapCustomStyleOptions()
        if (isDark && styleId.isNotBlank()) {
            customStyleOptions.customStyleId(styleId)
            mapView.setMapCustomStyle(customStyleOptions, object : com.baidu.mapapi.map.CustomMapStyleCallBack {
                override fun onPreLoadLastCustomMapStyle(p0: String?): Boolean = false
                override fun onCustomMapStyleLoadSuccess(p0: Boolean, p1: String?): Boolean = true
                override fun onCustomMapStyleLoadFailed(p0: Int, p1: String?, p2: String?): Boolean = false
            })
            mapView.setMapCustomStyleEnable(true)
        } else {
            mapView.setMapCustomStyleEnable(false)
        }
        setMapType(currentMapType)
    }

    override fun clear() { map.clear() }
    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        if (points.size < 2) return
        val latLngList = points.map { com.baidu.mapapi.model.LatLng(it.first, it.second) }
        map.addOverlay(
            com.baidu.mapapi.map.PolylineOptions()
                .color(colorInt)
                .width(width.toInt())
                .points(latLngList)
        )
    }
    override fun addCircle(lat: Double, lng: Double, radius: Double, fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float) {
        map.addOverlay(
            com.baidu.mapapi.map.CircleOptions()
                .center(com.baidu.mapapi.model.LatLng(lat, lng))
                .radius(radius.toInt())
                .fillColor(fillColorInt)
                .stroke(com.baidu.mapapi.map.Stroke(strokeWidth.toInt(), strokeColorInt))
        )
    }
    override fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker {
        // 以下所使用 png 资源来自于高德地图，构建时自动打包到 assets
        val icon = when(type) {
            MarkerType.GREEN -> bitmapDescriptorFromAssets(context, "GREEN.png")
            MarkerType.RED -> bitmapDescriptorFromAssets(context, "RED.png")
            MarkerType.ORANGE -> bitmapDescriptorFromAssets(context, "ORANGE.png")
            else -> bitmapDescriptorFromAssets(context, "RED.png")
        }
        val marker = map.addOverlay(
            com.baidu.mapapi.map.MarkerOptions()
                .position(com.baidu.mapapi.model.LatLng(lat, lng))
                .title(title)
                .icon(icon)
        ) as? com.baidu.mapapi.map.Marker
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) {
                marker?.position = com.baidu.mapapi.model.LatLng(lat, lng)
            }
        }
    }
    private fun bitmapDescriptorFromAssets(
        context: android.content.Context,
        assetPath: String
    ): com.baidu.mapapi.map.BitmapDescriptor {
        val inputStream = context.assets.open(assetPath)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val descriptor = com.baidu.mapapi.map.BitmapDescriptorFactory.fromBitmap(bitmap)
        bitmap.recycle()
        return descriptor
    }
    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        val update = if (zoom != null) com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(com.baidu.mapapi.model.LatLng(lat, lng), zoom)
        else com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(com.baidu.mapapi.model.LatLng(lat, lng))
        map.animateMapStatus(update)
    }
    override fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) {
        if (points.isEmpty()) return
        try {
            var minLat = 90.0
            var maxLat = -90.0
            var minLng = 180.0
            var maxLng = -180.0
            points.forEach {
                if (it.first < minLat) minLat = it.first
                if (it.first > maxLat) maxLat = it.first
                if (it.second < minLng) minLng = it.second
                if (it.second > maxLng) maxLng = it.second
            }
            val centerLat = (minLat + maxLat) / 2
            val centerLng = (minLng + maxLng) / 2

            val results = FloatArray(1)
            android.location.Location.distanceBetween(minLat, minLng, maxLat, maxLng, results)
            val distance = results[0]

            // 百度地图距离与缩放级别的经验映射
            // 如果处于小窗模式 (padding 较小)，适当缩小缩放级别以显示更多内容
            val paddingFactor = if (padding < 50) 1.5f else 1.0f
            val adjustedDistance = distance * paddingFactor

            val zoom = when {
                adjustedDistance < 50 -> 20f
                adjustedDistance < 200 -> 18f
                adjustedDistance < 500 -> 17f
                adjustedDistance < 1000 -> 16f
                adjustedDistance < 2000 -> 15f
                adjustedDistance < 5000 -> 14f
                adjustedDistance < 10000 -> 13f
                adjustedDistance < 20000 -> 12f
                adjustedDistance < 50000 -> 11f
                adjustedDistance < 100000 -> 10f
                adjustedDistance < 200000 -> 9f
                adjustedDistance < 500000 -> 8f
                adjustedDistance < 1000000 -> 7f
                adjustedDistance < 2000000 -> 6f
                else -> 5f
            }

            val update = com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(
                com.baidu.mapapi.model.LatLng(centerLat, centerLng), zoom
            )
            map.animateMapStatus(update)
        } catch (e: Exception) { e.printStackTrace() }
    }
    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        val update = if (zoom != null) com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(com.baidu.mapapi.model.LatLng(lat, lng), zoom)
        else com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(com.baidu.mapapi.model.LatLng(lat, lng))
        map.setMapStatus(update)
    }
    override val cameraTargetLat: Double? get() = map.mapStatus?.target?.latitude
    override val cameraTargetLng: Double? get() = map.mapStatus?.target?.longitude

    override fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit) {
        map.setOnMapStatusChangeListener(object : com.baidu.mapapi.map.BaiduMap.OnMapStatusChangeListener {
            private var lastReason: Int = 0
            override fun onMapStatusChangeStart(p0: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeStart(p0: com.baidu.mapapi.map.MapStatus?, p1: Int) {
                lastReason = p1
            }
            override fun onMapStatusChange(p0: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeFinish(p0: com.baidu.mapapi.map.MapStatus?) {
                if (lastReason == com.baidu.mapapi.map.BaiduMap.OnMapStatusChangeListener.REASON_GESTURE) {
                    p0?.target?.let { onFinish(it.latitude, it.longitude) }
                }
            }
        })
    }
    override fun disableUiControls() {
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isOverlookingGesturesEnabled = true
        map.uiSettings.isRotateGesturesEnabled = true
    }

    override fun setMapType(type: AppMapType) {
        currentMapType = type
        when (type) {
            AppMapType.NORMAL -> {
                map.mapType = com.baidu.mapapi.map.BaiduMap.MAP_TYPE_NORMAL
            }
            AppMapType.SATELLITE -> {
                map.mapType = com.baidu.mapapi.map.BaiduMap.MAP_TYPE_SATELLITE
            }
            AppMapType.MAP_3D -> {
                map.mapType = com.baidu.mapapi.map.BaiduMap.MAP_TYPE_NORMAL
            }
        }
    }
}

@Composable
fun AppMapView(mapEngine: com.suseoaa.locationspoofer.data.model.MapEngine, isDomestic: Boolean, isDark: Boolean, modifier: Modifier = Modifier, onMapReady: (AppMapController) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapController by remember { mutableStateOf<AppMapController?>(null) }

    LaunchedEffect(isDark, mapController) {
        mapController?.setDarkMode(isDark, context)
    }

    val activeEngine = if (mapEngine == com.suseoaa.locationspoofer.data.model.MapEngine.AUTO) {
        if (isDomestic) com.suseoaa.locationspoofer.data.model.MapEngine.AMAP else com.suseoaa.locationspoofer.data.model.MapEngine.GOOGLE
    } else {
        mapEngine
    }

    if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.AMAP) {
        val amapView = remember {
            val view = TextureMapView(context)
            view.onCreate(Bundle())
            view
        }
        DisposableEffect(lifecycle, amapView) {
            var isDestroyed = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> amapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> amapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> {
                        if (!isDestroyed) {
                            isDestroyed = true
                            amapView.onDestroy()
                        }
                    }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) amapView.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                if (!isDestroyed) {
                    isDestroyed = true
                    amapView.onPause()
                    amapView.onDestroy()
                }
            }
        }
        AndroidView(
            factory = {
                amapView.apply {
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                    map.setOnMapLoadedListener {
                        val controller = AMapControllerImpl(map)
                        mapController = controller
                        controller.setDarkMode(isDark, context)
                        onMapReady(controller)
                    }
                }
            },
            modifier = modifier
        )
    } else if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.BAIDU) {
        val baiduMapView = remember { 
            val view = com.baidu.mapapi.map.TextureMapView(context)
            view
        }
        DisposableEffect(lifecycle, baiduMapView) {
            var isDestroyed = false  //  // 防止 Back 键导致 onDestroy 被双次调用，Activity 重建后新 BMapView 无法正确初始化
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> baiduMapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> baiduMapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> {
                        if (!isDestroyed) {
                            isDestroyed = true
                            baiduMapView.onDestroy()
                        }
                    }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) baiduMapView.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                if (!isDestroyed) {
                    isDestroyed = true
                    baiduMapView.onPause()
                    baiduMapView.onDestroy()
                }
            }
        }
        AndroidView(
            factory = {
                baiduMapView.apply {
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                    map.setOnMapLoadedCallback {
                        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                        val styleId = prefs.getString("baidu_style_id", "") ?: ""
                        val controller = BaiduMapControllerImpl(map, this, context, styleId)
                        mapController = controller
                        controller.setDarkMode(isDark, context)
                        onMapReady(controller) 
                    }
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
            var isDestroyed = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> gmapView.onResume()
                    Lifecycle.Event.ON_PAUSE   -> gmapView.onPause()
                    Lifecycle.Event.ON_DESTROY -> {
                        if (!isDestroyed) {
                            isDestroyed = true
                            gmapView.onDestroy()
                        }
                    }
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) gmapView.onResume()
            onDispose {
                lifecycle.removeObserver(observer)
                if (!isDestroyed) {
                    isDestroyed = true
                    gmapView.onPause()
                    gmapView.onDestroy()
                }
            }
        }
        AndroidView(
            factory = {
                gmapView.apply {
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                    getMapAsync { map -> 
                        val controller = GMapControllerImpl(map)
                        mapController = controller
                        controller.setDarkMode(isDark, context)
                        onMapReady(controller) 
                    }
                }
            },
            modifier = modifier
        )
    }
}
