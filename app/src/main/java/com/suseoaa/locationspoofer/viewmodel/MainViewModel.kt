package com.suseoaa.locationspoofer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.RoutePlanStage
import com.suseoaa.locationspoofer.data.model.RouteRunMode
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SimMode
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.data.repository.LocationRepository
import com.suseoaa.locationspoofer.data.repository.SettingsRepository
import com.suseoaa.locationspoofer.data.repository.WifiRepository
import com.suseoaa.locationspoofer.provider.SpooferProvider
import com.suseoaa.locationspoofer.service.SpoofingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val wifiRepository: WifiRepository,
    private val context: Context
) : ViewModel() {

    private val wigleToken =
        "QUlEODRhYjYwNzVjYjI4MTY5ZDU4Yjk2NzQxM2ZiYTFiMDA6YmY2NWE5M2RiYWQ1YzYwNmYwNzdkOTQ2NjE2NmI4MzM="

    private val _uiState = MutableStateFlow(
        AppState(savedLocations = settingsRepository.getSavedLocations())
    )
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private var locationSyncJob: Job? = null
    private var autoRouteJob: Job? = null

    init {
        initialize()
    }

    // 初始化

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = locationRepository.checkRootAccess()
            val lsposed = locationRepository.isModuleActive()
            wifiRepository.validateToken(wigleToken)

            if (SpoofingService.isRunning) {
                locationRepository.stopSpoofing(context)
            }

            _uiState.update {
                it.copy(
                    isInitializing = false,
                    hasRootAccess = root,
                    isLSPosedActive = lsposed,
                    isSpoofingActive = false,
                    routePlanStage = RoutePlanStage.IDLE
                )
            }
            fetchCurrentLocation(context)
        }
    }

    // 当前位置获取

    fun fetchCurrentLocation(ctx: Context) {
        val client = try {
            AMapLocationClient(ctx.applicationContext)
        } catch (e: Exception) {
            return
        }
        client.setLocationOption(AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
        })
        client.setLocationListener { loc ->
            if (loc != null && loc.errorCode == 0) {
                if (_uiState.value.longitudeInput.isEmpty() || _uiState.value.latitudeInput.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            latitudeInput = String.format("%.6f", loc.latitude),
                            longitudeInput = String.format("%.6f", loc.longitude),
                            showCoordinateError = false
                        )
                    }
                }
            }
            client.stopLocation()
            client.onDestroy()
        }
        client.startLocation()
    }

    // 坐标输入

    fun updateLongitude(value: String) {
        if (isValidCoord(value)) _uiState.update { it.copy(longitudeInput = value, showCoordinateError = false) }
    }

    fun updateLatitude(value: String) {
        if (isValidCoord(value)) _uiState.update { it.copy(latitudeInput = value, showCoordinateError = false) }
    }

    private fun isValidCoord(value: String): Boolean {
        if (value.isEmpty() || value == "-") return true
        return value.toDoubleOrNull() != null
    }

    // 定点模拟

    fun startSpoofing() {
        val state = _uiState.value
        val lng = state.longitudeInput.toDoubleOrNull()
        val lat = state.latitudeInput.toDoubleOrNull()
        if (lng == null || lat == null || lng !in -180.0..180.0 || lat !in -90.0..90.0) {
            _uiState.update { it.copy(showCoordinateError = true) }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            locationRepository.startSpoofing(
                context, lat, lng,
                "STILL", 0f, now,
                emptyList(), false
            )
            _uiState.update {
                it.copy(
                    isSpoofingActive = true,
                    wifiLoadStatus = WifiLoadStatus.LOADING,
                    wifiApCount = 0
                )
            }
            val wifiJson = wifiRepository.fetchWifiData(lat, lng, wigleToken)
            val apCount = try { org.json.JSONArray(wifiJson).length() } catch (e: Exception) { 0 }
            locationRepository.updateWifiJson(wifiJson)
            _uiState.update { it.copy(wifiLoadStatus = WifiLoadStatus.DONE, wifiApCount = apCount) }
        }
    }

    fun stopSpoofing() {
        locationSyncJob?.cancel()
        locationSyncJob = null
        autoRouteJob?.cancel()
        autoRouteJob = null
        viewModelScope.launch {
            locationRepository.stopSpoofing(context)
            _uiState.update {
                it.copy(
                    isSpoofingActive = false,
                    wifiLoadStatus = WifiLoadStatus.IDLE,
                    wifiApCount = 0
                )
            }
        }
    }

    // 摇杆控制

    fun moveByJoystick(bearing: Double, intensity: Float, maxSpeedMs: Float) {
        val elapsedSec = 0.1
        val distance = maxSpeedMs * intensity * elapsedSec
        val R = 6378137.0
        val bearingRad = Math.toRadians(bearing)
        val lat = _uiState.value.latitudeInput.toDoubleOrNull() ?: return
        val lng = _uiState.value.longitudeInput.toDoubleOrNull() ?: return
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        val newLatRad = Math.asin(
            kotlin.math.sin(latRad) * kotlin.math.cos(distance / R) +
            kotlin.math.cos(latRad) * kotlin.math.sin(distance / R) * kotlin.math.cos(bearingRad)
        )
        val newLngRad = lngRad + kotlin.math.atan2(
            kotlin.math.sin(bearingRad) * kotlin.math.sin(distance / R) * kotlin.math.cos(latRad),
            kotlin.math.cos(distance / R) - kotlin.math.sin(latRad) * kotlin.math.sin(newLatRad)
        )
        val newLat = Math.toDegrees(newLatRad)
        val newLng = Math.toDegrees(newLngRad)
        _uiState.update {
            it.copy(
                latitudeInput = String.format("%.6f", newLat),
                longitudeInput = String.format("%.6f", newLng),
                simBearing = bearing.toFloat(),
                showCoordinateError = false
            )
        }
        // 实时同步给 SpooferProvider
        SpooferProvider.latitude = newLat
        SpooferProvider.longitude = newLng
        SpooferProvider.simBearing = bearing.toFloat()
        SpooferProvider.startTimestamp = System.currentTimeMillis()
    }

    // 路线规划状态机

    /** 进入全屏地图，进入选点阶段 */
    fun enterRoutePlanning() {
        _uiState.update {
            it.copy(
                routePlanStage = RoutePlanStage.SELECTING,
                routePoints = emptyList()
            )
        }
    }

    /** 地图中心确认添加路点 */
    fun addRoutePoint(lat: Double, lng: Double) {
        _uiState.update { it.copy(routePoints = it.routePoints + RoutePoint(lat, lng)) }
    }

    /** 撤销最后一个路点 */
    fun undoLastRoutePoint() {
        _uiState.update { state ->
            if (state.routePoints.isEmpty()) state
            else state.copy(routePoints = state.routePoints.dropLast(1))
        }
    }

    /** 结束选点 → READY */
    fun finishSelectingPoints() {
        if (_uiState.value.routePoints.size < 2) return
        _uiState.update { it.copy(routePlanStage = RoutePlanStage.READY) }
    }

    /** 重新选点：清空路点，回到 SELECTING */
    fun restartSelectingPoints() {
        _uiState.update {
            it.copy(
                routePoints = emptyList(),
                routePlanStage = RoutePlanStage.SELECTING
            )
        }
    }

    /** 设置路线运行模式 */
    fun setRouteRunMode(mode: RouteRunMode) {
        _uiState.update { it.copy(routeRunMode = mode) }
    }

    /** 设置循环模式速度 */
    fun setRouteSimMode(mode: SimMode) {
        _uiState.update { it.copy(routeSimMode = mode) }
    }

    /** 设置自定义速度 (m/s) */
    fun setCustomSpeedMs(speed: Double) {
        _uiState.update { it.copy(customSpeedMs = speed.coerceIn(0.1, 100.0)) }
    }

    /** 获取实际生效的速度 (m/s) */
    private fun getEffectiveSpeedMs(): Double {
        val state = _uiState.value
        return if (state.routeSimMode == SimMode.CUSTOM) state.customSpeedMs
        else state.routeSimMode.speedMs
    }

    /** 首页地图确认选点 */
    fun confirmMapPoint(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(
                latitudeInput = String.format("%.6f", lat),
                longitudeInput = String.format("%.6f", lng),
                mapConfirmedPoint = Pair(lat, lng),
                showCoordinateError = false
            )
        }
    }

    /** 清除地图选点状态 */
    fun clearMapPoint() {
        _uiState.update { it.copy(mapConfirmedPoint = null) }
    }

    /**
     * 开始路线模拟。
     * - 手动模式：启动 spoofing（STILL），由摇杆驱动 moveByJoystick 实时更新坐标。
     * - 循环模式：启动 spoofing，自动沿路线点按速度移动，到终点后反向循环。
     */
    fun startRoutePlanning() {
        val state = _uiState.value
        if (state.routePoints.size < 2) return
        val startPoint = state.routePoints.first()

        _uiState.update {
            it.copy(
                latitudeInput = String.format("%.6f", startPoint.lat),
                longitudeInput = String.format("%.6f", startPoint.lng),
                routePlanStage = RoutePlanStage.RUNNING
            )
        }

        val isLoop = state.routeRunMode == RouteRunMode.LOOP

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val routePoints = if (isLoop) {
                // 循环路线不需要追加起点，由 autoRouteLoop 自行处理往返
                state.routePoints
            } else {
                state.routePoints
            }

            locationRepository.startSpoofing(
                context, startPoint.lat, startPoint.lng,
                if (isLoop) state.routeSimMode.name else "STILL",
                0f, now, routePoints, isLoop
            )
            _uiState.update {
                it.copy(
                    isSpoofingActive = true,
                    wifiLoadStatus = WifiLoadStatus.LOADING,
                    wifiApCount = 0
                )
            }
            val wifiJson = wifiRepository.fetchWifiData(startPoint.lat, startPoint.lng, wigleToken)
            val apCount = try { org.json.JSONArray(wifiJson).length() } catch (e: Exception) { 0 }
            locationRepository.updateWifiJson(wifiJson)
            _uiState.update { it.copy(wifiLoadStatus = WifiLoadStatus.DONE, wifiApCount = apCount) }
        }

        if (isLoop) {
            startAutoRouteLoop()
        }
        // 手动模式不需要 sync loop，由摇杆直接驱动
    }

    /** 停止路线模拟，重置所有状态 */
    fun stopRoutePlanning() {
        locationSyncJob?.cancel()
        locationSyncJob = null
        autoRouteJob?.cancel()
        autoRouteJob = null
        viewModelScope.launch {
            locationRepository.stopSpoofing(context)
            _uiState.update {
                it.copy(
                    isSpoofingActive = false,
                    wifiLoadStatus = WifiLoadStatus.IDLE,
                    wifiApCount = 0,
                    routePlanStage = RoutePlanStage.IDLE,
                    routePoints = emptyList(),
                    routeRunMode = RouteRunMode.MANUAL
                )
            }
        }
    }

    // 保存位置

    fun saveCurrentLocation(name: String) {
        val lng = _uiState.value.longitudeInput.toDoubleOrNull() ?: return
        val lat = _uiState.value.latitudeInput.toDoubleOrNull() ?: return
        settingsRepository.addSavedLocation(SavedLocation(name, lat, lng))
        _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
    }

    fun removeSavedLocation(loc: SavedLocation) {
        settingsRepository.removeSavedLocation(loc)
        _uiState.update { it.copy(savedLocations = settingsRepository.getSavedLocations()) }
    }

    // 搜索

    fun updateSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    fun updateSearchResults(results: List<SavedLocation>) {
        _uiState.update { it.copy(searchResults = results) }
    }

    // 内部工具

    /**
     * 循环模式自动移动。
     * 按路点顺序移动，到终点后反向，不断循环。
     * 同时实时同步坐标到 SpooferProvider。
     */
    private fun startAutoRouteLoop() {
        autoRouteJob?.cancel()
        autoRouteJob = viewModelScope.launch(Dispatchers.Default) {
            val points = _uiState.value.routePoints
            if (points.size < 2) return@launch

            val speedMs = getEffectiveSpeedMs()
            if (speedMs <= 0.0) return@launch

            val tickMs = 100L
            val tickSec = tickMs / 1000.0
            var forward = true
            var segmentIndex = 0
            var progress = 0.0 // 当前段上已走过的距离（米）

            while (isActive) {
                val fromIdx = if (forward) segmentIndex else segmentIndex + 1
                val toIdx = if (forward) segmentIndex + 1 else segmentIndex
                val from = points[fromIdx]
                val to = points[toIdx]
                val segLen = haversineMeters(from, to)

                val stepDist = speedMs * tickSec
                progress += stepDist

                if (progress >= segLen) {
                    // 到达当前段终点
                    progress -= segLen
                    if (forward) {
                        segmentIndex++
                        if (segmentIndex >= points.lastIndex) {
                            // 到达终点，反向
                            forward = false
                            segmentIndex = points.lastIndex - 1
                            progress = 0.0
                        }
                    } else {
                        segmentIndex--
                        if (segmentIndex < 0) {
                            // 回到起点，正向
                            forward = true
                            segmentIndex = 0
                            progress = 0.0
                        }
                    }
                    // 重新获取段信息并继续
                    val newFrom = if (forward) points[segmentIndex] else points[segmentIndex + 1]
                    updatePosition(newFrom.lat, newFrom.lng, 0f)
                } else {
                    // 在段中间插值
                    val ratio = if (segLen > 0) progress / segLen else 0.0
                    val lat = from.lat + (to.lat - from.lat) * ratio
                    val lng = from.lng + (to.lng - from.lng) * ratio
                    val bearing = bearingBetween(from, to).toFloat()
                    updatePosition(lat, lng, bearing)
                }

                delay(tickMs)
            }
        }
    }

    /** 更新当前模拟位置到 UI 和 SpooferProvider */
    private fun updatePosition(lat: Double, lng: Double, bearing: Float) {
        _uiState.update {
            it.copy(
                latitudeInput = String.format("%.6f", lat),
                longitudeInput = String.format("%.6f", lng),
                simBearing = bearing,
                showCoordinateError = false
            )
        }
        SpooferProvider.latitude = lat
        SpooferProvider.longitude = lng
        SpooferProvider.simBearing = bearing
        SpooferProvider.startTimestamp = System.currentTimeMillis()
    }

    private fun haversineMeters(a: RoutePoint, b: RoutePoint): Double {
        val R = 6378137.0
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLng / 2).let { it * it }
        return 2 * R * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }

    private fun bearingBetween(from: RoutePoint, to: RoutePoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val x = kotlin.math.sin(dLng) * kotlin.math.cos(lat2)
        val y = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
            kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLng)
        return (Math.toDegrees(kotlin.math.atan2(x, y)) + 360) % 360
    }
}
