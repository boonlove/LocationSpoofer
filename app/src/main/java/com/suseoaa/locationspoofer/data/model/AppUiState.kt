package com.suseoaa.locationspoofer.data.model

enum class WifiLoadStatus { IDLE, LOADING, DONE }

enum class SimMode(val label: String, val speedMs: Double) {
    STILL("静止", 0.0),
    WALKING("步行", 1.4),
    RUNNING("慢跑", 3.0),
    CYCLING("骑行", 5.5),
    DRIVING("驾车", 15.0)
}

/** 路线规划阶段 */
enum class RoutePlanStage {
    /** 未开始，默认状态 */
    IDLE,
    /** 正在点击地图添加路点 */
    SELECTING,
    /** 已结束选点，等待配置并启动 */
    READY,
    /** 路线模拟运行中 */
    RUNNING
}

/** 路线运行模式 */
enum class RouteRunMode {
    /** 手动模式：摇杆控制移动方向和速度 */
    MANUAL,
    /** 循环模式：按路线自动来回移动 */
    LOOP
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
    val simBearing: Float = 0f,
    val savedRoutes: List<SavedRoute> = emptyList(),
    // 路线规划
    val routePoints: List<RoutePoint> = emptyList(),
    val routePlanStage: RoutePlanStage = RoutePlanStage.IDLE,
    /** 路线运行模式（手动 / 循环） */
    val routeRunMode: RouteRunMode = RouteRunMode.MANUAL,
    /** 循环模式使用的速度 */
    val routeSimMode: SimMode = SimMode.WALKING
)
