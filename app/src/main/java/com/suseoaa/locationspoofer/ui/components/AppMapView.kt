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
import com.suseoaa.locationspoofer.data.model.AppMapProvider

import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory as BBitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.TextureMapView as BTextureMapView
import com.baidu.mapapi.map.MarkerOptions as BMarkerOptions
import com.baidu.mapapi.map.PolylineOptions as BPolylineOptions
import com.baidu.mapapi.map.CircleOptions as BCircleOptions
import com.baidu.mapapi.model.LatLng as BLatLng

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
    private val baiduMap: BaiduMap,
    private val context: android.content.Context
) : AppMapController {
    override fun clear() { baiduMap.clear() }
    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        baiduMap.addOverlay(
            BPolylineOptions().color(colorInt).width(width.toInt()).points(
                points.map { BLatLng(it.first, it.second) }
            )
        )
    }

    override fun addCircle(
        lat: Double, lng: Double, radius: Double,
        fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float
    ) {
        baiduMap.addOverlay(
            BCircleOptions()
                .center(BLatLng(lat, lng))
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
        val marker = (baiduMap.addOverlay(
            BMarkerOptions()
                .position(BLatLng(lat, lng))
                .title(title)
                .icon(icon)
        ) as? com.baidu.mapapi.map.Marker)
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) {
                marker?.position = BLatLng(lat, lng)
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
        val descriptor = BBitmapDescriptorFactory.fromBitmap(bitmap)
        bitmap.recycle()
        return descriptor
    }

    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null)
            baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(BLatLng(lat, lng), zoom))
        else
            baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(BLatLng(lat, lng)))
    }

    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null)
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(BLatLng(lat, lng), zoom))
        else
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(BLatLng(lat, lng)))
    }

    override val cameraTargetLat: Double? get() = baiduMap.mapStatus?.target?.latitude
    override val cameraTargetLng: Double? get() = baiduMap.mapStatus?.target?.longitude

    override fun setOnCameraChangeListener(onFinish: (lat: Double, lng: Double) -> Unit) {
        baiduMap.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            private var isFromGesture = false

            override fun onMapStatusChangeStart(mapStatus: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeStart(mapStatus: com.baidu.mapapi.map.MapStatus?, reason: Int) {
                // REASON_GESTURE = 用户手势拖拽, REASON_API_ANIMATION = animateMapStatus, REASON_API_SET = setMapStatus
                isFromGesture = (reason == BaiduMap.OnMapStatusChangeListener.REASON_GESTURE)
            }
            override fun onMapStatusChange(mapStatus: com.baidu.mapapi.map.MapStatus?) {}
            override fun onMapStatusChangeFinish(mapStatus: com.baidu.mapapi.map.MapStatus?) {
                // 只对用户手势触发回调, 防止 animateCamera → onMapStatusChangeFinish → LaunchedEffect → animateCamera 的反馈循环
                if (isFromGesture) {
                    mapStatus?.target?.let { onFinish(it.latitude, it.longitude) }
                }
            }
        })
    }

    override fun disableUiControls() {
        baiduMap.uiSettings.setAllGesturesEnabled(true)
    }

    override fun setMapType(type: AppMapType) {
        when (type) {
            AppMapType.NORMAL -> {
                baiduMap.mapType = BaiduMap.MAP_TYPE_NORMAL
                baiduMap.isBuildingsEnabled = false
                resetOverlook()
            }
            AppMapType.SATELLITE -> {
                baiduMap.mapType = BaiduMap.MAP_TYPE_SATELLITE
                resetOverlook()
            }
            AppMapType.MAP_3D -> {
                baiduMap.mapType = BaiduMap.MAP_TYPE_NORMAL
                baiduMap.isBuildingsEnabled = true
                val status = baiduMap.mapStatus ?: return
                val builder = com.baidu.mapapi.map.MapStatus.Builder(status)
                    .overlook(45f)
                baiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
            }
        }
    }

    private fun resetOverlook() {
        val status = baiduMap.mapStatus ?: return
        if (status.overlook != 0f) {
            val builder = com.baidu.mapapi.map.MapStatus.Builder(status).overlook(0f)
            baiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        }
    }
}

@Composable
fun AppMapView(mapProvider: AppMapProvider, modifier: Modifier = Modifier, onMapReady: (AppMapController) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var mapController by remember { mutableStateOf<AppMapController?>(null) }

    LaunchedEffect(isDark, mapController) {
        mapController?.setDarkMode(isDark, context)
    }

    when (mapProvider) {
        AppMapProvider.AMAP -> {
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
        }
        AppMapProvider.BAIDU_MAPS -> {
            val mapView = remember {
                val view = BTextureMapView(context)
                view.onCreate(context, Bundle())
                view
            }
            DisposableEffect(lifecycle, mapView) {
                var destroyed = false
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        Lifecycle.Event.ON_DESTROY -> {
                            if (!destroyed) {
                                destroyed = true  // 防止 Back 键导致 onDestroy 被双次调用，Activity 重建后新 BMapView 无法正确初始化
                                mapView.onDestroy()
                            }
                        }
                        else -> {}
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
                onDispose {
                    lifecycle.removeObserver(observer)
                    if (!destroyed) {
                        mapView.onPause()
                        mapView.onDestroy()
                    }
                }
            }
            AndroidView(
                factory = {
                    mapView.apply {
                        setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                        map.setOnMapLoadedCallback { onMapReady(BaiduMapControllerImpl(map, context)) }
                    }
                },
                modifier = modifier
            )
        }
        AppMapProvider.GOOGLE_MAPS -> {
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
}
