package com.suseoaa.locationspoofer.ui.extensions

import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.BuildConfig

fun MapEngine.activeEngine(isDomestic: Boolean): MapEngine {
    return if (this == MapEngine.AUTO) {
        if (!isDomestic && MapEngine.GOOGLE.isEnable()) MapEngine.GOOGLE else MapEngine.AMAP
    } else {
        this
    }
}

fun MapEngine.isEnable(): Boolean = when (this) {
    MapEngine.AMAP, MapEngine.BAIDU -> true
    MapEngine.GOOGLE -> BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()
    else -> true
}
