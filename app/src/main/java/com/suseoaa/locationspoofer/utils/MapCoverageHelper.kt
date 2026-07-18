package com.suseoaa.locationspoofer.utils

import android.graphics.Color.argb
import com.suseoaa.locationspoofer.data.db.LocationRecord
import com.suseoaa.locationspoofer.ui.components.AppMapController

object MapCoverageHelper {
    fun drawCoverage(controller: AppMapController, locations: List<LocationRecord>) {
        val fillColor = argb(50, 46, 204, 113) // 带透明度的 AccentGreen
        val strokeColor = argb(100, 46, 204, 113)

        locations.forEach { loc ->
            controller.addCircle(loc.lat, loc.lng, 50.0, fillColor, strokeColor, 2f)
        }
    }
}
