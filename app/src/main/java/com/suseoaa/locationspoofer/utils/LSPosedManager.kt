package com.suseoaa.locationspoofer.utils

class LSPosedManager {
    // 预留给Xposed模块Hook的方法。如果模块生效，Xposed将拦截并使其返回true
    fun isModuleActive(): Boolean {
        return false
    }
}
