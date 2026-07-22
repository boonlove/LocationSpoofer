package com.suseoaa.locationspoofer.ui.screen.spoofing

import com.suseoaa.locationspoofer.ui.components.BottomSheetValue
import com.suseoaa.locationspoofer.ui.screen.AppPoiItem

/**
 * Holds all transient UI state for the SpoofingScreen.
 * This separates view-specific toggles from the core business state (AppState).
 */
data class SpoofingUiState(
    // Dialog Toggles
    val showApiKeyWarningDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showSavedLocationsDialog: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val showMapTypeDialog: Boolean = false,
    val showCustomCoordDialog: Boolean = false,
    val showStartSpoofingDialog: Boolean = false,
    val showAppCoordinateScreen: Boolean = false,

    // Bottom Sheet & Search State
    val sheetState: BottomSheetValue = BottomSheetValue.HALF,
    val scrollOffset: Int = 0,
    val isSheetExpanded: Boolean = true,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AppPoiItem> = emptyList(),
    val showSearchResults: Boolean = false,

    // Map Display Mode
    val isMapFullscreen: Boolean = false,

    // Notifications / Error
    val toastMessage: String? = null
)
