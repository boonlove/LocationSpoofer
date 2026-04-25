package com.suseoaa.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ConfigManager(private val rootManager: RootManager) {

    suspend fun saveConfig(lat: Double, lng: Double, active: Boolean) =
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("active", active)
            }
            val command = """
            echo '${json.toString()}' > /data/local/tmp/locationspoofer_config.json
            chmod 777 /data/local/tmp/locationspoofer_config.json
            chcon u:object_r:shell_data_file:s0 /data/local/tmp/locationspoofer_config.json
        """.trimIndent()

            rootManager.executeCommand(command)
        }
}
