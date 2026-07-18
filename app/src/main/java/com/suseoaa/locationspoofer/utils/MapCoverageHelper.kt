package com.suseoaa.locationspoofer.utils

import com.suseoaa.locationspoofer.data.db.LocationRecord
import com.suseoaa.locationspoofer.ui.components.AppMapController

object MapCoverageHelper {
    fun drawCoverage(controller: AppMapController, locations: List<LocationRecord>) {
        val fillColor = android.graphics.Color.argb(50, 46, 204, 113) // 带透明度的 AccentGreen
        val strokeColor = android.graphics.Color.argb(100, 46, 204, 113)
        
        locations.forEach { loc ->
            controller.addCircle(loc.lat, loc.lng, 50.0, fillColor, strokeColor, 2f)
        }
    }
}
