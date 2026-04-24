package com.suseoaa.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WigleClient {
    private val client = OkHttpClient()

    /** 验证 token 是否有效（调用 /profile/user 接口） */
    suspend fun validateToken(token: String): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        return@withContext try {
            val request = Request.Builder()
                .url("https://api.wigle.net/api/v2/profile/user")
                .addHeader("Authorization", "Basic $token")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                org.json.JSONObject(body).optString("success") == "true"
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchWifiData(lat: Double, lng: Double, token: String): String =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) {
                return@withContext generateFallbackWifi()
            }

            val latrange1 = lat - 0.002
            val latrange2 = lat + 0.002
            val longrange1 = lng - 0.002
            val longrange2 = lng + 0.002

            val url =
                "https://api.wigle.net/api/v2/network/search?latrange1=$latrange1&latrange2=$latrange2&longrange1=$longrange1&longrange2=$longrange2"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic $token")
                .addHeader("Accept", "application/json")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext generateFallbackWifi()
                    val jsonObject = JSONObject(body)
                    val results = jsonObject.optJSONArray("results")
                    if (results == null || results.length() == 0) {
                        return@withContext generateFallbackWifi()
                    }

                    val wifiList = mutableListOf<JSONObject>()
                    val count = minOf(results.length(), 10)

                    for (i in 0 until count) {
                        val item = results.getJSONObject(i)
                        val wifi = JSONObject()
                        wifi.put("ssid", item.optString("ssid", "WLAN_${(1000..9999).random()}"))
                        wifi.put("bssid", item.optString("netid", generateRandomBssid()))
                        wifiList.add(wifi)
                    }
                    return@withContext wifiList.toString()
                } else {
                    return@withContext generateFallbackWifi()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext generateFallbackWifi()
            }
        }

    private fun generateRandomBssid(): String {
        val ouis = listOf("00:14:22", "cc:2d:e0", "44:a8:42", "00:25:9c")
        return "${ouis.random()}:${
            String.format(
                "%02x:%02x:%02x",
                (0..255).random(),
                (0..255).random(),
                (0..255).random()
            )
        }"
    }

    private fun generateFallbackWifi(): String {
        val list = mutableListOf<JSONObject>()
        for (i in 0..9) {
            val wifi = JSONObject()
            wifi.put("ssid", "WLAN_${(1000..9999).random()}")
            wifi.put("bssid", generateRandomBssid())
            list.add(wifi)
        }
        return list.toString()
    }
}
