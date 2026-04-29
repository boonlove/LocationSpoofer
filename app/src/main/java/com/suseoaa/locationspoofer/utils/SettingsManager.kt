package com.suseoaa.locationspoofer.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SavedLocation(val name: String, val lat: Double, val lng: Double)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", true)
        set(value) = prefs.edit().putBoolean("is_dark_mode", value).apply()

    fun getSavedLocations(): List<SavedLocation> {
        val jsonString = prefs.getString("saved_locations", "[]") ?: "[]"
        val list = mutableListOf<SavedLocation>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SavedLocation(obj.getString("name"), obj.getDouble("lat"), obj.getDouble("lng")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.add(location)
        saveLocations(list)
    }

    fun removeSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { it.lat == location.lat && it.lng == location.lng }
        saveLocations(list)
    }

    private fun saveLocations(list: List<SavedLocation>) {
        val jsonArray = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("lat", it.lat)
            obj.put("lng", it.lng)
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_locations", jsonArray.toString()).apply()
    }
}
