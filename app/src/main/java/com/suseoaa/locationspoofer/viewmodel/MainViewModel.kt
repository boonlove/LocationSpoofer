package com.suseoaa.locationspoofer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SavedRoute
import com.suseoaa.locationspoofer.data.model.SimMode
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.data.repository.LocationRepository
import com.suseoaa.locationspoofer.data.repository.SettingsRepository
import com.suseoaa.locationspoofer.data.repository.WifiRepository
import com.suseoaa.locationspoofer.provider.SpooferProvider
import com.suseoaa.locationspoofer.service.SpoofingService
import com.suseoaa.locationspoofer.utils.TrajectorySimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        AppState(
            savedLocations = settingsRepository.getSavedLocations(),
            savedRoutes = settingsRepository.getSavedRoutes()
        )
    )
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = locationRepository.checkRootAccess()
            val lsposed = locationRepository.isModuleActive()
            wifiRepository.validateToken(wigleToken)
            _uiState.update {
                it.copy(
                    isInitializing = false,
                    hasRootAccess = root,
                    isLSPosedActive = lsposed,
                    isSpoofingActive = SpoofingService.isRunning
                )
            }
        }
    }

    fun fetchCurrentLocation(ctx: Context) {
        val locationClient = AMapLocationClient(ctx.applicationContext)
        val locationOption = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
        }
        locationClient.setLocationOption(locationOption)
        locationClient.setLocationListener { amapLocation ->
            if (amapLocation != null && amapLocation.errorCode == 0) {
                if (_uiState.value.longitudeInput.isEmpty() || _uiState.value.latitudeInput.isEmpty()) {
                    updateLongitude(String.format("%.6f", amapLocation.longitude))
                    updateLatitude(String.format("%.6f", amapLocation.latitude))
                }
            }
            locationClient.stopLocation()
            locationClient.onDestroy()
        }
        locationClient.startLocation()
    }

    // ── 坐标输入 ──────────────────────────────────────────────

    fun updateLongitude(value: String) {
        if (isValidInput(value)) _uiState.update { it.copy(longitudeInput = value, showCoordinateError = false) }
    }

    fun updateLatitude(value: String) {
        if (isValidInput(value)) _uiState.update { it.copy(latitudeInput = value, showCoordinateError = false) }
    }

    private fun isValidInput(value: String): Boolean {
        if (value.isEmpty() || value == "-") return true
        return value.toDoubleOrNull() != null
    }

    // ── 模拟模式 ──────────────────────────────────────────────

    fun updateSimMode(mode: SimMode) {
        _uiState.update { it.copy(simMode = mode) }
        updateSimulationParameters()
    }

    fun updateSimBearing(bearing: Float) {
        _uiState.update { it.copy(simBearing = bearing) }
        updateSimulationParameters()
    }

    private fun updateSimulationParameters() {
        if (!_uiState.value.isSpoofingActive) return
        val state = _uiState.value
        val now = System.currentTimeMillis()

        val newBase = if (state.isRouteMode && state.routePoints.size >= 2) {
            TrajectorySimulator.calculateRoutePosition(
                state.routePoints, SpooferProvider.startTimestamp, SpooferProvider.simMode, now
            )
        } else {
            TrajectorySimulator.calculateSimulatedLocation(
                SpooferProvider.latitude, SpooferProvider.longitude,
                SpooferProvider.startTimestamp, SpooferProvider.simMode, SpooferProvider.simBearing, now
            )
        }

        viewModelScope.launch {
            locationRepository.updateConfig(
                newBase.lat, newBase.lng,
                state.simMode.name, state.simBearing, now,
                state.routePoints, state.isRouteMode
            )
        }
    }

    // ── 虚拟定位控制 ──────────────────────────────────────────────

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
                state.simMode.name, state.simBearing, now,
                state.routePoints, state.isRouteMode
            )
            _uiState.update {
                it.copy(isSpoofingActive = true, wifiLoadStatus = WifiLoadStatus.LOADING, wifiApCount = 0)
            }

            val wifiJson = wifiRepository.fetchWifiData(lat, lng, wigleToken)
            val apCount = try { org.json.JSONArray(wifiJson).length() } catch (e: Exception) { 0 }
            locationRepository.updateWifiJson(wifiJson)
            _uiState.update { it.copy(wifiLoadStatus = WifiLoadStatus.DONE, wifiApCount = apCount) }
        }
    }

    fun startRouteSimulation() {
        val state = _uiState.value
        if (state.routePoints.size < 2) return
        val startPoint = state.routePoints.first()
        _uiState.update {
            it.copy(
                longitudeInput = String.format("%.6f", startPoint.lng),
                latitudeInput = String.format("%.6f", startPoint.lat),
                isRouteMode = true,
                isEditingRoute = false
            )
        }
        startSpoofing()
    }

    fun setEditingRoute(enabled: Boolean) {
        _uiState.update { it.copy(isEditingRoute = enabled) }
    }

    fun stopSpoofing() {
        viewModelScope.launch {
            locationRepository.stopSpoofing(context)
            _uiState.update {
                it.copy(
                    isSpoofingActive = false,
                    wifiLoadStatus = WifiLoadStatus.IDLE,
                    wifiApCount = 0,
                    isRouteMode = false
                )
            }
        }
    }

    // ── 保存位置 ──────────────────────────────────────────────

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

    // ── 路线管理 ──────────────────────────────────────────────

    fun toggleRouteEditing() {
        _uiState.update { it.copy(isEditingRoute = !it.isEditingRoute) }
    }

    fun addRoutePoint(lat: Double, lng: Double) {
        _uiState.update { it.copy(routePoints = it.routePoints + RoutePoint(lat, lng)) }
    }

    fun removeLastRoutePoint() {
        _uiState.update { state ->
            if (state.routePoints.isEmpty()) state
            else state.copy(routePoints = state.routePoints.dropLast(1))
        }
    }

    fun clearRoute() {
        _uiState.update { it.copy(routePoints = emptyList(), isRouteMode = false) }
    }

    fun saveCurrentRoute(name: String) {
        val points = _uiState.value.routePoints
        if (points.size < 2) return
        settingsRepository.addSavedRoute(SavedRoute(name, points))
        _uiState.update { it.copy(savedRoutes = settingsRepository.getSavedRoutes()) }
    }

    fun loadRoute(route: SavedRoute) {
        _uiState.update {
            it.copy(
                routePoints = route.points,
                isEditingRoute = false,
                showRoutesDialog = false,
                longitudeInput = String.format("%.6f", route.points.first().lng),
                latitudeInput = String.format("%.6f", route.points.first().lat)
            )
        }
    }

    fun deleteRoute(route: SavedRoute) {
        settingsRepository.removeSavedRoute(route)
        _uiState.update { it.copy(savedRoutes = settingsRepository.getSavedRoutes()) }
    }

    fun setShowRoutesDialog(show: Boolean) {
        _uiState.update { it.copy(showRoutesDialog = show) }
    }

    // ── 搜索 ──────────────────────────────────────────────

    fun updateSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    fun updateSearchResults(results: List<SavedLocation>) {
        _uiState.update { it.copy(searchResults = results) }
    }
}
