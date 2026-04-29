package com.suseoaa.locationspoofer.utils

import com.suseoaa.locationspoofer.data.model.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ConfigManager(private val rootManager: RootManager) {

    suspend fun saveConfig(
        lat: Double,
        lng: Double,
        active: Boolean,
        simMode: String = "STILL",
        simBearing: Float = 0f,
        startTimestamp: Long = System.currentTimeMillis(),
        routePoints: List<RoutePoint> = emptyList(),
        isRouteMode: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val routeArray = JSONArray()
        routePoints.forEach { p ->
            val obj = JSONObject()
            obj.put("lat", p.lat)
            obj.put("lng", p.lng)
            routeArray.put(obj)
        }

        val json = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            put("active", active)
            put("sim_mode", simMode)
            put("sim_bearing", simBearing.toDouble())
            put("start_timestamp", startTimestamp)
            put("route_points", routeArray)
            put("is_route_mode", isRouteMode)
        }

        val command = """
            echo '${json.toString()}' > /data/local/tmp/locationspoofer_config.json
            chmod 777 /data/local/tmp/locationspoofer_config.json
            chcon u:object_r:shell_data_file:s0 /data/local/tmp/locationspoofer_config.json
        """.trimIndent()

        rootManager.executeCommand(command)
    }
}
