package com.suseoaa.locationspoofer.data.model

enum class WifiLoadStatus { IDLE, LOADING, DONE }

enum class SimMode(val label: String) {
    STILL("静止"),
    WALKING("走路"),
    RUNNING("跑步"),
    CYCLING("骑车"),
    DRIVING("开车")
}

data class AppState(
    val isInitializing: Boolean = true,
    val hasRootAccess: Boolean = false,
    val isLSPosedActive: Boolean = false,
    val longitudeInput: String = "",
    val latitudeInput: String = "",
    val showCoordinateError: Boolean = false,
    val isSpoofingActive: Boolean = false,
    val wifiLoadStatus: WifiLoadStatus = WifiLoadStatus.IDLE,
    val wifiApCount: Int = 0,
    val savedLocations: List<SavedLocation> = emptyList(),
    val searchKeyword: String = "",
    val searchResults: List<SavedLocation> = emptyList(),
    val simMode: SimMode = SimMode.STILL,
    val simBearing: Float = 0f,
    val routePoints: List<RoutePoint> = emptyList(),
    val isRouteMode: Boolean = false,
    val isEditingRoute: Boolean = false,
    val savedRoutes: List<SavedRoute> = emptyList(),
    val showRoutesDialog: Boolean = false
)
