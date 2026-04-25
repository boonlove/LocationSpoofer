package com.suseoaa.locationspoofer.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONObject
import java.io.File

class LocationHooker : IXposedHookLoadPackage {

    companion object {
        // 需要注入的目标应用包名（含前缀匹配，覆盖所有子进程如 :appbrand0, :tools 等）
        val TARGET_PACKAGES = setOf(
            "com.tencent.mm",           // 微信（含所有 :appbrand 小程序子进程）
            "com.chaoxing.mobile",      // 超星学习通
            "cn.chaoxing.lemon",        // 学习通备用包名
            "com.alibaba.android.rimet",// 钉钉
            "com.sankuai.meituan",      // 美团
            "com.baidu.BaiduMap",       // 百度地图
            "com.autonavi.minimap",     // 高德地图
            "com.tencent.map",          // 腾讯地图
            "com.android.systemui",     // 系统UI（覆盖系统级定位弹窗）
            "com.google.android.gms",   // Google Play 服务（覆盖 Fused Location Provider）
        )

        // 系统进程同样需要覆盖（android进程持有LocationManagerService）
        val SYSTEM_PACKAGES = setOf("android", "system", "com.android.phone")
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        val pkg = lpparam.packageName

        // 宿主App自报平安
        if (pkg == "com.suseoaa.locationspoofer") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.suseoaa.locationspoofer.utils.LSPosedManager",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }
            return // 宿主App不需要注入定位Hook
        }

        // 系统进程：只Hook Location API，不动Wi-Fi（避免系统崩溃）
        if (SYSTEM_PACKAGES.contains(pkg)) {
            hookLocationAPIs(lpparam.classLoader)
            return
        }

        // 精确包名匹配 + 子进程前缀匹配（如 com.tencent.mm:appbrand0）
        val isTarget = TARGET_PACKAGES.any { target ->
            pkg == target || pkg.startsWith("$target:")
        }

        if (!isTarget) return

        XposedBridge.log("[LocationSpoofer] Hooking package: $pkg")

        hookLocationAPIs(lpparam.classLoader)
        hookNetworkAndCellAPIs(lpparam.classLoader)
        hookBluetoothLE(lpparam.classLoader)
    }

    private var startTimestamp = System.currentTimeMillis()

    private fun getJitteredLocation(baseLat: Double, baseLng: Double): Pair<Double, Double> {
        val elapsed = System.currentTimeMillis() - startTimestamp
        val driftLat = kotlin.math.sin(elapsed / 10000.0) * 0.000015
        val driftLng = kotlin.math.cos(elapsed / 12000.0) * 0.000015
        return Pair(baseLat + driftLat, baseLng + driftLng)
    }

    private fun getJitteredAccuracy(): Float {
        val elapsed = System.currentTimeMillis() - startTimestamp
        return (20.0 + 10.0 * kotlin.math.sin(elapsed / 5000.0)).toFloat()
    }

    private fun hookLocationAPIs(classLoader: ClassLoader) {
        try {
            val getLatHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val baseLat = config.optDouble("lat", param.result as Double)
                        val baseLng = config.optDouble("lng", 0.0)
                        param.result = getJitteredLocation(baseLat, baseLng).first
                    }
                }
            }

            val getLngHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val baseLat = config.optDouble("lat", 0.0)
                        val baseLng = config.optDouble("lng", param.result as Double)
                        param.result = getJitteredLocation(baseLat, baseLng).second
                    }
                }
            }

            val getAccHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = getJitteredAccuracy()
                    }
                }
            }

            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLatitude",
                getLatHook
            )
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLongitude",
                getLngHook
            )
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getAccuracy",
                getAccHook
            )

            // ★ 核心反检测：抹除 isFromMockProvider 标志位（strategy:100 的根本来源）
            val antiMockHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = false
                    }
                }
            }
            // Android 6~11: isFromMockProvider()
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "isFromMockProvider",
                antiMockHook
            )
            // Android 12+: isMock()
            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.Location",
                    classLoader,
                    "isMock",
                    antiMockHook
                )
            } catch (e: Throwable) { /* API < 31 没有此方法 */
            }

            // ★ Android 13 专项：直接对 Location 对象的 mMock / mIsFromMockProvider 字段写 false
            // (Android 12+ 字段名改为 mMock，Android 6-11 为 mIsFromMockProvider)
            val fieldCleanHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val loc = param.thisObject ?: return
                        try {
                            XposedHelpers.setBooleanField(loc, "mMock", false)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false)
                        } catch (e: Throwable) {
                        }
                        // 清理 extras bundle 中可能残留的 mock 标记
                        try {
                            val extras =
                                XposedHelpers.callMethod(loc, "getExtras") as? android.os.Bundle
                            extras?.remove("mockLocation")
                            extras?.remove("isMock")
                        } catch (e: Throwable) {
                        }
                    }
                }
            }
            // 在 getLatitude/getLongitude/getAccuracy 时同步清字段，确保在实际读值前已抹除
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLatitude",
                fieldCleanHook
            )
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLongitude",
                fieldCleanHook
            )

            // ★ 拦截 Settings.Secure.getInt("mock_location") — 部分ROM通过这个判断是否开了开发者模式模拟位置
            try {
                XposedHelpers.findAndHookMethod(
                    "android.provider.Settings\$Secure",
                    classLoader,
                    "getInt",
                    android.content.ContentResolver::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false)) {
                                val key = param.args[1] as? String ?: return
                                if (key == "mock_location" || key == "allow_mock_location") {
                                    param.result = 0 // 0 = 关闭模拟位置（欺骗系统认为我们没开）
                                }
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // ★ 拦截 getProvider：将 "mock" / "test" 提供者名隐藏，换成 "gps"
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getProvider",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val provider = param.result as? String ?: return
                            if (provider.contains("mock", ignoreCase = true) ||
                                provider.contains("test", ignoreCase = true) ||
                                provider.contains("fake", ignoreCase = true)
                            ) {
                                param.result = android.location.LocationManager.GPS_PROVIDER
                            }
                        }
                    }
                })

            // ★ 拦截 LocationManager.getProviders() / getAllProviders()：移除 mock/test 提供者
            val providerListHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        @Suppress("UNCHECKED_CAST")
                        val list = param.result as? MutableList<String> ?: return
                        val cleaned = list.filterNot {
                            it.contains("mock", ignoreCase = true) ||
                                    it.contains("test", ignoreCase = true) ||
                                    it.contains("fake", ignoreCase = true)
                        }.toMutableList()
                        if (!cleaned.contains(android.location.LocationManager.GPS_PROVIDER))
                            cleaned.add(android.location.LocationManager.GPS_PROVIDER)
                        param.result = cleaned
                    }
                }
            }
            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.LocationManager", classLoader, "getProviders",
                    Boolean::class.javaPrimitiveType!!, providerListHook
                )
                XposedHelpers.findAndHookMethod(
                    "android.location.LocationManager", classLoader, "getAllProviders",
                    providerListHook
                )
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // AMap SDK 专属 Hook（含抖动，与原生Location保持同步）
            val amapHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val baseLat = config.optDouble("lat", 0.0)
                        val baseLng = config.optDouble("lng", 0.0)
                        val jittered = getJitteredLocation(baseLat, baseLng)
                        when (param.method.name) {
                            "getLatitude" -> param.result = jittered.first
                            "getLongitude" -> param.result = jittered.second
                        }
                    }
                }
            }
            try {
                XposedHelpers.findAndHookMethod(
                    "com.amap.api.location.AMapLocation",
                    classLoader,
                    "getLatitude",
                    amapHook
                )
                XposedHelpers.findAndHookMethod(
                    "com.amap.api.location.AMapLocation",
                    classLoader,
                    "getLongitude",
                    amapHook
                )
            } catch (e: Throwable) { /* AMap SDK 不存在则跳过 */
            }

            // ★★★ 高德SDK深度反检测（strategy:500 的来源）
            // mockData JSON 就是 AMapLocation.getMockData() 的返回值，直接抹零
            val amapNullHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = null
                    }
                }
            }
            val amapFalseHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = false
                    }
                }
            }
            val amapZeroHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = 0
                    }
                }
            }

            try {
                val amapLocClass = "com.amap.api.location.AMapLocation"

                // 1. getMockData() → null（直接砍掉 mockData 字段的数据来源）
                XposedHelpers.findAndHookMethod(
                    amapLocClass,
                    classLoader,
                    "getMockData",
                    amapNullHook
                )
                // 2. getMockFlag() / getMockType() → 0
                try {
                    XposedHelpers.findAndHookMethod(
                        amapLocClass,
                        classLoader,
                        "getMockFlag",
                        amapZeroHook
                    )
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.findAndHookMethod(
                        amapLocClass,
                        classLoader,
                        "getMockType",
                        amapZeroHook
                    )
                } catch (e: Throwable) {
                }
                // 3. isMocked() → false（AMap SDK 12.0+ 新接口）
                try {
                    XposedHelpers.findAndHookMethod(
                        amapLocClass,
                        classLoader,
                        "isMocked",
                        amapFalseHook
                    )
                } catch (e: Throwable) {
                }
                // 4. getErrorCode() → 0（非0表示定位失败）
                XposedHelpers.findAndHookMethod(
                    amapLocClass,
                    classLoader,
                    "getErrorCode",
                    amapZeroHook
                )
                // 5. getLocationType() → 1（GPS类型，最可信）
                XposedHelpers.findAndHookMethod(
                    amapLocClass, classLoader, "getLocationType",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false)) param.result =
                                1 // 1 = GPS定位
                        }
                    })
                // 6. getProvider() → "gps"
                XposedHelpers.findAndHookMethod(
                    amapLocClass, classLoader, "getProvider",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false)) param.result =
                                "gps"
                        }
                    })
                // 7. 直接写底层 mock 相关字段（防反射读字段绕过 getter）
                val setFieldHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val obj = param.thisObject ?: return
                            try {
                                XposedHelpers.setObjectField(obj, "mockData", null)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(obj, "mockFlag", 0)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(obj, "mockType", 0)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setBooleanField(obj, "isMocked", false)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setBooleanField(obj, "mMock", false)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(obj, "errorCode", 0)
                            } catch (e: Throwable) {
                            }
                        }
                    }
                }
                XposedHelpers.findAndHookMethod(
                    amapLocClass,
                    classLoader,
                    "getLatitude",
                    setFieldHook
                )
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // 8. AMapLocationQualityReport 质量报告也要清零
            try {
                val qualityClass = "com.amap.api.location.AMapLocationQualityReport"
                try {
                    XposedHelpers.findAndHookMethod(
                        qualityClass,
                        classLoader,
                        "getMockInfo",
                        amapNullHook
                    )
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.findAndHookMethod(
                        qualityClass,
                        classLoader,
                        "isMockLocation",
                        amapFalseHook
                    )
                } catch (e: Throwable) {
                }
            } catch (e: Throwable) {
            }

            // 9. setMockEnable(false) 让高德SDK禁用自身的 mock 校验流程
            try {
                XposedHelpers.findAndHookMethod(
                    "com.amap.api.location.AMapLocationClient", classLoader, "setMockEnable",
                    Boolean::class.javaPrimitiveType!!,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false)) {
                                // 强制设为 true，让高德自己相信当前位置是真实的
                                param.args[0] = true
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
            }

        } catch (e: Throwable) {
            XposedBridge.log(e)
        }
    }

    private fun hookNetworkAndCellAPIs(classLoader: ClassLoader) {
        // 1. 伪造 WifiInfo Getter（getBSSID / getSSID / getMacAddress）
        val wifiInfoHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val wifiArray = config.optJSONArray("wifi_json")
                    val firstWifi =
                        if (wifiArray != null && wifiArray.length() > 0) wifiArray.getJSONObject(0) else null
                    when (param.method.name) {
                        "getBSSID", "getMacAddress" -> param.result =
                            firstWifi?.optString("bssid") ?: "ac:22:0b:f4:11:33"

                        "getSSID" -> param.result =
                            "\"${firstWifi?.optString("ssid") ?: "HOME_WIFI"}\""

                        "getNetworkId" -> param.result = 1
                    }
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiInfo",
                classLoader,
                "getBSSID",
                wifiInfoHook
            )
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiInfo",
                classLoader,
                "getMacAddress",
                wifiInfoHook
            )
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiInfo",
                classLoader,
                "getSSID",
                wifiInfoHook
            )
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiInfo",
                classLoader,
                "getNetworkId",
                wifiInfoHook
            )
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // 2. 伪造 Wi-Fi 扫描列表（getScanResults）
        val wifiScanHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val fakeList = java.util.ArrayList<Any>()
                    val wifiArray = config.optJSONArray("wifi_json")
                    if (wifiArray != null && wifiArray.length() > 0) {
                        try {
                            val scanResultClass =
                                XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                            for (i in 0 until wifiArray.length()) {
                                val wifi = wifiArray.getJSONObject(i)
                                val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                                XposedHelpers.setObjectField(
                                    fakeScanResult,
                                    "SSID",
                                    wifi.optString("ssid")
                                )
                                XposedHelpers.setObjectField(
                                    fakeScanResult,
                                    "BSSID",
                                    wifi.optString("bssid")
                                )
                                XposedHelpers.setIntField(
                                    fakeScanResult,
                                    "level",
                                    (-80..-40).random()
                                )
                                XposedHelpers.setIntField(
                                    fakeScanResult,
                                    "frequency",
                                    listOf(2412, 2437, 2462, 5180, 5240).random()
                                )
                                XposedHelpers.setObjectField(
                                    fakeScanResult,
                                    "capabilities",
                                    "[WPA2-PSK-CCMP][ESS]"
                                )
                                fakeList.add(fakeScanResult)
                            }
                        } catch (e: Throwable) { /* Ignore */
                        }
                    }
                    param.result = fakeList
                }
            }
        }

        // 3. 完整的 WifiManager Hook 组合
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager",
                classLoader,
                "getScanResults",
                wifiScanHook
            )

            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getWifiState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) param.result =
                            3 // WIFI_STATE_ENABLED
                    }
                })

            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "isWifiEnabled",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) param.result =
                            true
                    }
                })

            // 4. 拦截 getConnectionInfo：返回伪造的 WifiInfo 对象（包含当地真实 BSSID）
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getConnectionInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val wifiArray = config.optJSONArray("wifi_json")
                            if (wifiArray != null && wifiArray.length() > 0) {
                                val firstWifi = wifiArray.getJSONObject(0)
                                try {
                                    val wifiInfoClass = XposedHelpers.findClass(
                                        "android.net.wifi.WifiInfo",
                                        classLoader
                                    )
                                    val fakeWifiInfo = XposedHelpers.newInstance(wifiInfoClass)
                                    try {
                                        XposedHelpers.setObjectField(
                                            fakeWifiInfo,
                                            "mBSSID",
                                            firstWifi.optString("bssid")
                                        )
                                    } catch (e: Throwable) {
                                    }
                                    try {
                                        XposedHelpers.setObjectField(
                                            fakeWifiInfo,
                                            "mMacAddress",
                                            firstWifi.optString("bssid")
                                        )
                                    } catch (e: Throwable) {
                                    }
                                    try {
                                        val wifiSsidClass = XposedHelpers.findClass(
                                            "android.net.wifi.WifiSsid",
                                            classLoader
                                        )
                                        val createMethod = XposedHelpers.findMethodExact(
                                            wifiSsidClass,
                                            "createFromAsciiEncoded",
                                            String::class.java
                                        )
                                        val wifiSsid =
                                            createMethod.invoke(null, firstWifi.optString("ssid"))
                                        XposedHelpers.setObjectField(
                                            fakeWifiInfo,
                                            "mWifiSsid",
                                            wifiSsid
                                        )
                                    } catch (e: Throwable) {
                                        try {
                                            XposedHelpers.setObjectField(
                                                fakeWifiInfo,
                                                "mSSID",
                                                "\"${firstWifi.optString("ssid")}\""
                                            )
                                        } catch (e2: Throwable) {
                                        }
                                    }
                                    try {
                                        XposedHelpers.setIntField(fakeWifiInfo, "mNetworkId", 1)
                                    } catch (e: Throwable) {
                                    }
                                    param.result = fakeWifiInfo
                                } catch (e: Throwable) { /* Ignore */
                                }
                            }
                        }
                    }
                })
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // 5. 基站信息伪造（CellLocation / AllCellInfo / NeighboringCellInfo）
        val cellHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    when (param.method.name) {
                        "getCellLocation" -> {
                            try {
                                val gsmCellLocationClass = XposedHelpers.findClass(
                                    "android.telephony.gsm.GsmCellLocation",
                                    classLoader
                                )
                                val fakeLocation = XposedHelpers.newInstance(gsmCellLocationClass)
                                XposedHelpers.callMethod(fakeLocation, "setLacAndCid", 1234, 5678)
                                param.result = fakeLocation
                            } catch (e: Throwable) {
                                param.result = null
                            }
                        }

                        "getAllCellInfo", "getNeighboringCellInfo" -> param.result =
                            java.util.ArrayList<Any>()
                    }
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                classLoader,
                "getAllCellInfo",
                cellHook
            )
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                classLoader,
                "getCellLocation",
                cellHook
            )
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                classLoader,
                "getNeighboringCellInfo",
                cellHook
            )
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }
    }

    /**
     * 拦截蓝牙 BLE 扫描结果，防止通过附近 BLE 信标定位。
     * 当模拟激活时，返回空列表，屏蔽所有 iBeacon / Eddystone 信标探测。
     */
    private fun hookBluetoothLE(classLoader: ClassLoader) {
        val bleEmptyResultHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    param.result = java.util.ArrayList<Any>()
                }
            }
        }

        try {
            // Android 5.0+ BLE Scanner
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.le.BluetoothLeScanner",
                classLoader,
                "startScan",
                java.util.List::class.java,
                android.bluetooth.le.ScanSettings::class.java,
                android.bluetooth.le.ScanCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            // 替换 callback 为无操作版本，阻止真实扫描结果传递
                            param.result = null // startScan 返回 void，直接短路执行
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // 同时Hook老接口（Android 4.x BluetoothAdapter.startLeScan）
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.BluetoothAdapter",
                classLoader,
                "startLeScan",
                android.bluetooth.BluetoothAdapter.LeScanCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            param.result = false // 假装开启失败，不返回任何扫描结果
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }
    }

    private var lastConfig: JSONObject? = null
    private var lastReadTime: Long = 0

    private fun readConfig(): JSONObject? {
        val app = android.app.AndroidAppHelper.currentApplication()
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastReadTime < 800 && lastConfig != null) {
            return lastConfig
        }

        if (app != null) {
            try {
                val uri =
                    android.net.Uri.parse("content://com.suseoaa.locationspoofer.provider/config")
                val cursor = app.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val active = cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1
                    val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat"))
                    val lng = cursor.getDouble(cursor.getColumnIndexOrThrow("lng"))
                    val wifiJson = cursor.getString(cursor.getColumnIndexOrThrow("wifi_json"))
                    cursor.close()

                    val config = JSONObject()
                    config.put("active", active)
                    config.put("lat", lat)
                    config.put("lng", lng)
                    config.put("wifi_json", org.json.JSONArray(wifiJson))

                    lastConfig = config
                    lastReadTime = currentTime
                    return config
                }
            } catch (e: Throwable) { /* Ignore query error */
            }
        }

        // Fallback to local file
        return try {
            val file = File("/data/local/tmp/locationspoofer_config.json")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                val config = JSONObject(content)
                if (!config.has("wifi_json")) config.put("wifi_json", org.json.JSONArray())
                lastConfig = config
                lastReadTime = currentTime
                config
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
