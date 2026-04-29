package com.suseoaa.locationspoofer.data.repository

import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SavedRoute
import com.suseoaa.locationspoofer.utils.SettingsManager

class SettingsRepository(private val settingsManager: SettingsManager) {

    fun getSavedLocations(): List<SavedLocation> = settingsManager.getSavedLocations()

    fun addSavedLocation(location: SavedLocation) = settingsManager.addSavedLocation(location)

    fun removeSavedLocation(location: SavedLocation) = settingsManager.removeSavedLocation(location)

    fun getSavedRoutes(): List<SavedRoute> = settingsManager.getSavedRoutes()

    fun addSavedRoute(route: SavedRoute) = settingsManager.addSavedRoute(route)

    fun removeSavedRoute(route: SavedRoute) = settingsManager.removeSavedRoute(route)
}
