package com.suseoaa.locationspoofer.ui.extensions

import com.suseoaa.locationspoofer.data.model.MapEngine

fun MapEngine.activeEngine(isDomestic: Boolean): MapEngine {
    return if (this == MapEngine.AUTO) {
        if (isDomestic) MapEngine.AMAP else MapEngine.GOOGLE
    } else {
        this
    }
}
