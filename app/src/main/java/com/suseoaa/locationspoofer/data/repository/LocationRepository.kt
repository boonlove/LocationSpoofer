package com.suseoaa.locationspoofer.data.repository

import android.content.Context
import android.content.Intent
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.provider.SpooferProvider
import com.suseoaa.locationspoofer.service.SpoofingService
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import org.json.JSONArray
import org.json.JSONObject

class LocationRepository(
    private val configManager: ConfigManager,
    private val rootManager: RootManager,
    private val lsposedManager: LSPosedManager
) {
    suspend fun checkRootAccess(): Boolean = rootManager.checkRootAccess()

    fun isModuleActive(): Boolean = lsposedManager.isModuleActive()

    suspend fun startSpoofing(
        context: Context,
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean
    ) {
        SpooferProvider.isActive = true
        SpooferProvider.latitude = lat
        SpooferProvider.longitude = lng
        SpooferProvider.startTimestamp = startTime
        SpooferProvider.simMode = simMode
        SpooferProvider.simBearing = simBearing
        SpooferProvider.wifiJson = "[]"
        SpooferProvider.routeJson = routePointsToJson(routePoints)
        SpooferProvider.isRouteMode = isRouteMode

        configManager.saveConfig(lat, lng, true, simMode, simBearing, startTime, routePoints, isRouteMode)
        rootManager.grantMockLocation()

        context.startForegroundService(
            Intent(context, SpoofingService::class.java).apply {
                action = SpoofingService.ACTION_START
                putExtra(SpoofingService.EXTRA_LAT, lat)
                putExtra(SpoofingService.EXTRA_LNG, lng)
            }
        )
    }

    suspend fun stopSpoofing(context: Context) {
        SpooferProvider.isActive = false
        SpooferProvider.wifiJson = "[]"
        SpooferProvider.routeJson = "[]"
        SpooferProvider.isRouteMode = false
        configManager.saveConfig(0.0, 0.0, false)
        context.startService(Intent(context, SpoofingService::class.java).apply {
            action = SpoofingService.ACTION_STOP
        })
        rootManager.revokeMockLocation()
    }

    suspend fun updateConfig(
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean
    ) {
        SpooferProvider.latitude = lat
        SpooferProvider.longitude = lng
        SpooferProvider.startTimestamp = startTime
        SpooferProvider.simMode = simMode
        SpooferProvider.simBearing = simBearing
        SpooferProvider.routeJson = routePointsToJson(routePoints)
        SpooferProvider.isRouteMode = isRouteMode
        configManager.saveConfig(lat, lng, true, simMode, simBearing, startTime, routePoints, isRouteMode)
    }

    fun updateWifiJson(wifiJson: String) {
        SpooferProvider.wifiJson = wifiJson
    }

    private fun routePointsToJson(points: List<RoutePoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lng", p.lng)
            })
        }
        return arr.toString()
    }
}
