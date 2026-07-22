package com.suseoaa.locationspoofer.ui.screen.spoofing

import com.suseoaa.locationspoofer.ui.components.BottomSheetValue

/**
 * Defines all user actions on the SpoofingScreen.
 * This establishes a strict Unidirectional Data Flow (UDF) via MVI architecture.
 */
sealed class SpoofingIntent {
    // Navigation / Dialogs
    data class SetApiKeyWarningVisible(val visible: Boolean) : SpoofingIntent()
    data class SetSaveDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetSavedLocationsVisible(val visible: Boolean) : SpoofingIntent()
    data class SetUpdateDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetMapTypeDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetCustomCoordDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetStartSpoofingDialogVisible(val visible: Boolean) : SpoofingIntent()
    data class SetAppCoordinateScreenVisible(val visible: Boolean) : SpoofingIntent()

    // Bottom Sheet & Search UI
    data class SetSheetState(val sheetValue: BottomSheetValue) : SpoofingIntent()
    data class SetScrollOffset(val scrollValue: Int): SpoofingIntent()
    data class SetSheetExpanded(val expanded: Boolean) : SpoofingIntent()
    data class SetSearchActive(val active: Boolean) : SpoofingIntent()
    data class UpdateSearchQuery(val query: String) : SpoofingIntent()
    data object PerformSearch : SpoofingIntent()
    data class ClearSearchResults(val clearAll: Boolean = false) : SpoofingIntent()
    data class SetSearchResults(
        val results: List<com.suseoaa.locationspoofer.ui.screen.AppPoiItem>,
        val show: Boolean
    ) : SpoofingIntent()

    // Map Display Mode
    data class SetMapFullscreen(val isFullScreen: Boolean) : SpoofingIntent()

    // Map Actions
    data class ConfirmMapPoint(val lat: Double, val lng: Double) : SpoofingIntent()
    data object RequestCurrentLocation : SpoofingIntent()
}
