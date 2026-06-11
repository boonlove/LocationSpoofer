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
        isRouteMode: Boolean = false,
        wifiJson: String = "[]",
        appCoordinateSystems: Map<String, String> = emptyMap(),
        cellJson: String = "[]",
        bluetoothJson: String = "[]",
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true
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
            val wifiObj = try {
                JSONObject(wifiJson)
            } catch (e: Exception) {
                JSONObject().apply {
                    put("isConnected", false)
                    put("connectedWifi", JSONObject.NULL)
                    put("nearbyWifi", JSONArray())
                }
            }
            put("wifi_json", wifiObj)
            put("cell_json", JSONArray(cellJson))
            put("bluetooth_json", JSONArray(bluetoothJson))
            put("mock_wifi", mockWifi)
            put("mock_cell", mockCell)
            put("mock_bluetooth", mockBluetooth)
            put("enable_jitter", enableJitter)
            
            val coordSysObj = JSONObject()
            appCoordinateSystems.forEach { (pkg, sys) -> coordSysObj.put(pkg, sys) }
            put("app_coordinate_systems", coordSysObj)
        }

        // 使用heredoc写入,避免JSON中的特殊字符(单引号等)被shell误解析
        val escapedJson = json.toString().replace("\\", "\\\\").replace("\"", "\\\"")
        val command = """
            echo "$escapedJson" > /data/local/tmp/locationspoofer_config.json
            chmod 777 /data/local/tmp/locationspoofer_config.json
            chcon u:object_r:shell_data_file:s0 /data/local/tmp/locationspoofer_config.json
        """.trimIndent()

        rootManager.executeCommand(command)
    }
}
