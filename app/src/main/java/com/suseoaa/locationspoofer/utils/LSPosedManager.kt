package com.suseoaa.locationspoofer.utils

import com.suseoaa.locationspoofer.LocationApp

class LSPosedManager {
    fun isModuleActive(): Boolean {
        return LocationApp.isModuleActive.value
    }
}
