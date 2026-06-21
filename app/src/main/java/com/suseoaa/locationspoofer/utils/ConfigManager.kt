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
        enableJitter: Boolean = true,
        altitude: Double = 0.0,
        satelliteCount: Int = 20
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
            put("altitude", altitude)
            put("satellite_count", satelliteCount)
            
            val coordSysObj = JSONObject()
            appCoordinateSystems.forEach { (pkg, sys) -> coordSysObj.put(pkg, sys) }
            put("app_coordinate_systems", coordSysObj)
        }
        val cellCount = json.optJSONArray("cell_json")?.length() ?: 0
        android.util.Log.d(
            "OpenCellID",
            "saveConfig: active=$active mockCell=$mockCell lat=$lat lng=$lng cellJsonCount=$cellCount"
        )

        // 使用 quoted heredoc 写入，避免 JSON 中的引号、美元符号等被 shell 解析。
        val jsonText = json.toString()
        val command = """
            cat > /data/local/tmp/locationspoofer_config.json <<'LOCATIONSPOOFER_JSON'
            $jsonText
            LOCATIONSPOOFER_JSON
            chmod 666 /data/local/tmp/locationspoofer_config.json
            chcon u:object_r:shell_data_file:s0 /data/local/tmp/locationspoofer_config.json 2>/dev/null || true

            cat > /data/system/locationspoofer_config.json <<'LOCATIONSPOOFER_JSON_SYSTEM'
            $jsonText
            LOCATIONSPOOFER_JSON_SYSTEM
            chown system:system /data/system/locationspoofer_config.json 2>/dev/null || true
            chmod 644 /data/system/locationspoofer_config.json
            chcon u:object_r:system_data_file:s0 /data/system/locationspoofer_config.json 2>/dev/null || true
        """.trimIndent()

        val result = rootManager.executeCommand(command)
        android.util.Log.d(
            "OpenCellID",
            "saveConfig: wrote config copies to /data/local/tmp and /data/system, result=${result.take(200)}"
        )
    }
}
