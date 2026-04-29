package com.suseoaa.locationspoofer.utils

import android.content.Context
import android.content.SharedPreferences
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SavedRoute
import org.json.JSONArray
import org.json.JSONObject

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
        saveLocationList(list)
    }

    fun removeSavedLocation(location: SavedLocation) {
        val list = getSavedLocations().toMutableList()
        list.removeAll { it.lat == location.lat && it.lng == location.lng }
        saveLocationList(list)
    }

    private fun saveLocationList(list: List<SavedLocation>) {
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

    fun getSavedRoutes(): List<SavedRoute> {
        val jsonString = prefs.getString("saved_routes", "[]") ?: "[]"
        val list = mutableListOf<SavedRoute>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pointsArray = obj.getJSONArray("points")
                val points = (0 until pointsArray.length()).map { j ->
                    val p = pointsArray.getJSONObject(j)
                    RoutePoint(p.getDouble("lat"), p.getDouble("lng"))
                }
                list.add(SavedRoute(obj.getString("name"), points))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addSavedRoute(route: SavedRoute) {
        val list = getSavedRoutes().toMutableList()
        list.add(route)
        saveRouteList(list)
    }

    fun removeSavedRoute(route: SavedRoute) {
        val list = getSavedRoutes().toMutableList()
        list.removeAll { it.name == route.name }
        saveRouteList(list)
    }

    private fun saveRouteList(list: List<SavedRoute>) {
        val jsonArray = JSONArray()
        list.forEach { route ->
            val obj = JSONObject()
            obj.put("name", route.name)
            val pointsArray = JSONArray()
            route.points.forEach { p ->
                val pObj = JSONObject()
                pObj.put("lat", p.lat)
                pObj.put("lng", p.lng)
                pointsArray.put(pObj)
            }
            obj.put("points", pointsArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_routes", jsonArray.toString()).apply()
    }
}
