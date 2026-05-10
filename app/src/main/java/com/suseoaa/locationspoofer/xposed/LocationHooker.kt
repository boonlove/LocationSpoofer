package com.suseoaa.locationspoofer.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONObject
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
        hookGnssStatus(lpparam.classLoader)
    }

    private var startTimestamp = System.currentTimeMillis()

    // ── GCJ-02 → WGS-84 转换（Xposed模块运行在目标App进程，必须自带转换代码）──
    private val GCJ_A = 6378245.0
    private val GCJ_EE = 0.00669342162296594

    private fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
        if (gcjLng < 72.004 || gcjLng > 137.8347 || gcjLat < 0.8293 || gcjLat > 55.8271)
            return Pair(gcjLat, gcjLng)
        val dLat = gcjTransformLat(gcjLng - 105.0, gcjLat - 35.0)
        val dLng = gcjTransformLng(gcjLng - 105.0, gcjLat - 35.0)
        val radLat = gcjLat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - GCJ_EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val mLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
        val mLng = (dLng * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * Math.PI)
        return Pair(gcjLat - mLat, gcjLng - mLng)
    }

    private fun gcjTransformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun gcjTransformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }

    /**
     * 高斯随机游走状态(Xposed进程内独立维护)
     * 使用Ornstein-Uhlenbeck过程: X(t+dt) = X(t) + sigma*sqrt(dt)*N(0,1) - alpha*X(t)*dt
     * 产生白噪声频谱,FFT检测无法发现单频峰
     */
    private val rng = Random()
    private var hookDriftLat = 0.0
    private var hookDriftLng = 0.0
    private var hookAccuracyDrift = 0.0
    private var hookLastCallTime = 0L

    private fun getJitteredLocation(baseLat: Double, baseLng: Double): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        val dt = if (hookLastCallTime > 0) {
            ((now - hookLastCallTime) / 1000.0).coerceIn(0.01, 5.0)
        } else 1.0
        hookLastCallTime = now

        // sigma=0.000005度(约0.5米), alpha=0.05(均值回归防止无限漂移)
        val sigma = 0.000005
        val alpha = 0.05
        hookDriftLat += sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLat * dt
        hookDriftLng += sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLng * dt

        return Pair(baseLat + hookDriftLat, baseLng + hookDriftLng)
    }

    private fun getJitteredAccuracy(): Float {
        // 精度值在基准20m附近做高斯漂移,模拟GDOP变化
        hookAccuracyDrift += 0.5 * rng.nextGaussian() - 0.03 * hookAccuracyDrift
        return (20.0 + hookAccuracyDrift).coerceIn(3.0, 45.0).toFloat()
    }


    private fun hookLocationAPIs(classLoader: ClassLoader) {
        try {
            // android.location.Location 标准接口：返回 WGS-84（GPS坐标系）
            // readConfig() 已预计算 wgs84_lat/wgs84_lng，直接读取即可
            val getLatHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val wgsLat = config.optDouble("wgs84_lat", param.result as Double)
                        val wgsLng = config.optDouble("wgs84_lng", 0.0)
                        param.result = getJitteredLocation(wgsLat, wgsLng).first
                    }
                }
            }

            val getLngHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val wgsLat = config.optDouble("wgs84_lat", 0.0)
                        val wgsLng = config.optDouble("wgs84_lng", param.result as Double)
                        param.result = getJitteredLocation(wgsLat, wgsLng).second
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

        // 2. 伪造Wi-Fi扫描列表(getScanResults) -- 多维度拟真
        // 真实扫描周期约4-5秒,timestamp字段必须接近SystemClock.elapsedRealtimeNanos()
        // 缺失timestamp是反作弊SDK最常用的检测维度之一
        val wifiScanHook = object : XC_MethodHook() {
            // 真实设备常见的加密协议组合(从真机抓包统计)
            // 单一的[WPA2-PSK-CCMP][ESS]会被标记为批量生成特征
            val realCapabilities = listOf(
                "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
                "[WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS]",
                "[WPA2-PSK-CCMP][ESS][WPS]",
                "[WPA-PSK-TKIP+CCMP][WPA2-PSK-TKIP+CCMP][ESS]",
                "[RSN-PSK-CCMP][ESS]",
                "[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]",
                "[ESS]",
                "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]",
                "[WPA2-SAE-CCMP][RSN-SAE-CCMP][ESS]",
                "[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]"
            )

            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val fakeList = java.util.ArrayList<Any>()
                    val wifiArray = config.optJSONArray("wifi_json")
                    if (wifiArray != null && wifiArray.length() > 0) {
                        try {
                            val scanResultClass =
                                XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                            // 基准时间戳: 当前系统单调时钟(纳秒)
                            val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()
                            for (i in 0 until wifiArray.length()) {
                                val wifi = wifiArray.getJSONObject(i)
                                val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                                XposedHelpers.setObjectField(
                                    fakeScanResult, "SSID", wifi.optString("ssid")
                                )
                                XposedHelpers.setObjectField(
                                    fakeScanResult, "BSSID", wifi.optString("bssid")
                                )
                                // 信号强度: 高斯分布(均值-65dBm, 标准差10dBm)
                                // 真实环境中AP信号受多径衰落影响呈正态分布
                                val level = (-65 + (rng.nextGaussian() * 10).toInt())
                                    .coerceIn(-90, -30)
                                XposedHelpers.setIntField(fakeScanResult, "level", level)
                                XposedHelpers.setIntField(
                                    fakeScanResult, "frequency",
                                    listOf(2412, 2417, 2422, 2427, 2432, 2437, 2442,
                                        2447, 2452, 2457, 2462, 5180, 5200, 5220, 5240,
                                        5260, 5280, 5300, 5320, 5745, 5765, 5785, 5805).random()
                                )
                                // 加密协议: 从真实常见组合中随机抽取
                                XposedHelpers.setObjectField(
                                    fakeScanResult, "capabilities",
                                    realCapabilities.random()
                                )
                                // 时间戳: 基准时间 - 随机微秒偏移(模拟各AP被扫描到的先后差异)
                                // 每个AP的扫描时间差约在0-200毫秒(200_000微秒)之间
                                try {
                                    val offsetNanos = (rng.nextInt(200_000) * 1000L)
                                    XposedHelpers.setLongField(
                                        fakeScanResult, "timestamp",
                                        (baseTimestamp - offsetNanos) / 1000 // timestamp字段单位为微秒
                                    )
                                } catch (e: Throwable) { /* 部分ROM该字段可能不存在 */ }
                                fakeList.add(fakeScanResult)
                            }
                        } catch (e: Throwable) { // 忽略
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
                                } catch (e: Throwable) { // 忽略
                                }
                            }
                        }
                    }
                })
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // 5. 基站信息伪造(CellLocation/AllCellInfo/NeighboringCellInfo) -- 动态构造
        // 旧实现问题: 硬编码LAC=1234/CID=5678,且getAllCellInfo返回空列表
        // 反作弊SDK会检查: 1)基站参数是否与GPS坐标地理一致 2)CellInfo列表是否为空
        // 新方案: 基于目标坐标的hash值生成伪随机但确定性的TAC/CI,确保同一位置始终返回相同基站
        val cellHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val lat = config.optDouble("lat", 0.0)
                    val lng = config.optDouble("lng", 0.0)

                    when (param.method.name) {
                        "getCellLocation" -> {
                            try {
                                val gsmCellLocationClass = XposedHelpers.findClass(
                                    "android.telephony.gsm.GsmCellLocation",
                                    classLoader
                                )
                                val fakeLocation = XposedHelpers.newInstance(gsmCellLocationClass)
                                // 基于坐标生成确定性的LAC/CID(同一位置始终相同)
                                val coordSeed = ((lat * 1e5).toLong() xor (lng * 1e5).toLong())
                                val lac = (10000 + (coordSeed and 0xFFFF).toInt() % 50000)
                                    .coerceIn(1, 65534)
                                val cid = (100000 + ((coordSeed shr 16) and 0xFFFFFF).toInt() % 900000)
                                    .coerceIn(1, 268435455)
                                XposedHelpers.callMethod(fakeLocation, "setLacAndCid", lac, cid)
                                param.result = fakeLocation
                            } catch (e: Throwable) {
                                param.result = null
                            }
                        }

                        "getAllCellInfo" -> {
                            // 构造2-3个CellInfoLte对象,模拟服务小区+邻区
                            try {
                                param.result = buildFakeCellInfoList(classLoader, lat, lng)
                            } catch (e: Throwable) {
                                XposedBridge.log("[LocationSpoofer] CellInfo构造失败: $e")
                                param.result = java.util.ArrayList<Any>()
                            }
                        }

                        "getNeighboringCellInfo" -> param.result =
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

    /**
     * 通过反射+Parcel机制构造CellInfoLte对象列表
     *
     * CellInfoLte/CellIdentityLte等类的构造器在Android各版本中签名不同,
     * 直接new会因API版本差异崩溃。通过反射调用内部构造器并设置字段值,
     * 兼容Android 7.0~14。
     *
     * 参数生成策略:
     * - MCC=460(中国), MNC=01(中国移动)或11(中国电信): 使用中国运营商真实前缀
     * - TAC(Tracking Area Code): 基于经纬度hash生成,范围1-65534
     * - CI(Cell Identity): 基于坐标生成,范围1-268435455(28bit)
     * - 生成2-3个基站: 第一个为服务小区(isRegistered=true),其余为邻区
     *
     * @param classLoader 目标App的ClassLoader
     * @param lat 目标纬度(GCJ-02)
     * @param lng 目标经度(GCJ-02)
     * @return 包含2-3个CellInfoLte对象的ArrayList
     */
    private fun buildFakeCellInfoList(
        classLoader: ClassLoader, lat: Double, lng: Double
    ): java.util.ArrayList<Any> {
        val result = java.util.ArrayList<Any>()
        val coordSeed = ((lat * 1e5).toLong() xor (lng * 1e5).toLong())

        // 中国运营商MCC/MNC组合
        val operators = listOf(
            Pair(460, 0),  // 中国移动
            Pair(460, 1),  // 中国联通
            Pair(460, 11)  // 中国电信
        )

        // 生成2-3个基站(1个服务小区+1-2个邻区)
        val cellCount = 2 + (coordSeed and 1).toInt()
        for (i in 0 until cellCount) {
            try {
                val mcc = operators[i % operators.size].first
                val mnc = operators[i % operators.size].second
                // 每个基站的TAC/CI基于坐标+索引偏移,确保同一位置的多个基站参数不同但确定
                val tac = (10000 + ((coordSeed + i * 7919) and 0xFFFF).toInt() % 50000)
                    .coerceIn(1, 65534)
                val ci = (100000 + (((coordSeed shr 8) + i * 104729) and 0xFFFFFF).toInt() % 900000)
                    .coerceIn(1, 268435455)
                val pci = (coordSeed + i * 31).toInt() and 0x1FF // 物理小区ID, 0-503

                // 方案A: 通过反射CellIdentityLte构造器(Android 9+有多参数版本)
                val cellIdentityLteClass = XposedHelpers.findClass(
                    "android.telephony.CellIdentityLte", classLoader
                )
                val cellInfoLteClass = XposedHelpers.findClass(
                    "android.telephony.CellInfoLte", classLoader
                )

                val cellInfo = XposedHelpers.newInstance(cellInfoLteClass)

                // 设置isRegistered: 第一个为服务小区
                try {
                    XposedHelpers.setBooleanField(cellInfo, "mRegistered", i == 0)
                } catch (e: Throwable) {
                    try {
                        XposedHelpers.callMethod(cellInfo, "setRegistered", i == 0)
                    } catch (e2: Throwable) { /* 忽略 */ }
                }

                // 设置时间戳
                try {
                    XposedHelpers.setLongField(
                        cellInfo, "mTimeStamp",
                        android.os.SystemClock.elapsedRealtimeNanos()
                    )
                } catch (e: Throwable) { /* 忽略 */ }

                // 构造CellIdentityLte并注入字段
                val cellIdentity = try {
                    // Android 9+ 构造器: (int ci, int pci, int tac, int earfcn, ...mcc, mnc...)
                    XposedHelpers.newInstance(
                        cellIdentityLteClass,
                        mcc, mnc, ci, pci, tac
                    )
                } catch (e: Throwable) {
                    // 降级: 用空构造器+反射写字段
                    val identity = XposedHelpers.newInstance(cellIdentityLteClass)
                    try { XposedHelpers.setIntField(identity, "mMcc", mcc) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(identity, "mMnc", mnc) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(identity, "mCi", ci) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(identity, "mPci", pci) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(identity, "mTac", tac) } catch (e2: Throwable) {}
                    identity
                }

                // 将CellIdentityLte写入CellInfoLte
                try {
                    XposedHelpers.setObjectField(cellInfo, "mCellIdentityLte", cellIdentity)
                } catch (e: Throwable) { /* 忽略 */ }

                // 构造CellSignalStrengthLte
                try {
                    val cssClass = XposedHelpers.findClass(
                        "android.telephony.CellSignalStrengthLte", classLoader
                    )
                    val css = XposedHelpers.newInstance(cssClass)
                    // RSRP: -140~-44 dBm, 典型值-80~-100
                    val rsrp = -80 - rng.nextInt(20)
                    // RSRQ: -20~-3 dB
                    val rsrq = -10 - rng.nextInt(7)
                    // RSSI: -113~-51 dBm
                    val rssi = -70 - rng.nextInt(20)
                    try { XposedHelpers.setIntField(css, "mRsrp", rsrp) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(css, "mRsrq", rsrq) } catch (e2: Throwable) {}
                    try { XposedHelpers.setIntField(css, "mSignalStrength", rssi) } catch (e2: Throwable) {}
                    XposedHelpers.setObjectField(cellInfo, "mCellSignalStrengthLte", css)
                } catch (e: Throwable) { /* 忽略 */ }

                result.add(cellInfo)
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] 构造第${i}个CellInfo失败: $e")
            }
        }
        return result
    }

    /**
     * 拦截GnssStatus回调,注入伪造的卫星星座数据
     *
     * 反作弊SDK通过registerGnssStatusCallback获取卫星可见数和信噪比(C/N0),
     * 若Location坐标正常但卫星数为0或信噪比全为0,则判定为模拟位置。
     *
     * 伪造策略:
     * - 可见卫星数: 12-18颗(真实室外环境的典型值)
     * - 信噪比(C/N0): 15-40 dB-Hz(真实GPS信号的典型范围)
     * - 卫星类型: GPS(1) + GLONASS(3) + BDS(5)混合星座
     */
    private fun hookGnssStatus(classLoader: ClassLoader) {
        try {
            // Hook GnssStatus.getSatelliteCount() -- 返回伪造的卫星数
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getSatelliteCount",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            // 12-18颗可见卫星(随时间缓慢波动)
                            param.result = 12 + rng.nextInt(7)
                        }
                    }
                }
            )

            // Hook GnssStatus.getCn0DbHz(int) -- 返回伪造的信噪比
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getCn0DbHz",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            // 信噪比15-40 dB-Hz,高斯分布(均值28, 标准差6)
                            val cn0 = (28.0 + rng.nextGaussian() * 6.0)
                                .coerceIn(15.0, 42.0).toFloat()
                            param.result = cn0
                        }
                    }
                }
            )

            // Hook GnssStatus.usedInFix(int) -- 标记部分卫星参与定位
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "usedInFix",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val satIndex = param.args[0] as Int
                            // 约70%的可见卫星参与定位(真实场景中部分卫星仰角低或被遮挡)
                            param.result = (satIndex % 10) < 7
                        }
                    }
                }
            )

            // Hook GnssStatus.getConstellationType(int) -- 返回混合星座类型
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getConstellationType",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val satIndex = param.args[0] as Int
                            // GPS(1), SBAS(2), GLONASS(3), QZSS(4), BDS(5), GALILEO(6)
                            param.result = when (satIndex % 5) {
                                0, 1, 2 -> 1 // GPS(约60%)
                                3 -> 3        // GLONASS
                                else -> 5     // BDS(北斗)
                            }
                        }
                    }
                }
            )

            // Hook GnssStatus.getAzimuthDegrees(int) -- 方位角
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getAzimuthDegrees",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val satIndex = param.args[0] as Int
                            // 均匀分布在0-360度(卫星在天球上的方位角)
                            param.result = ((satIndex * 137.5f + rng.nextFloat() * 10f) % 360f)
                        }
                    }
                }
            )

            // Hook GnssStatus.getElevationDegrees(int) -- 仰角
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getElevationDegrees",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val satIndex = param.args[0] as Int
                            // 仰角5-85度(低于5度的信号通常被遮挡忽略)
                            param.result = 5f + (satIndex * 23.7f + rng.nextFloat() * 8f) % 80f
                        }
                    }
                }
            )

            // Hook GnssStatus.getSvid(int) -- 卫星编号
            XposedHelpers.findAndHookMethod(
                "android.location.GnssStatus", classLoader, "getSvid",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val satIndex = param.args[0] as Int
                            // GPS: 1-32, GLONASS: 65-96, BDS: 201-237
                            param.result = when (satIndex % 5) {
                                0, 1, 2 -> 1 + (satIndex * 7) % 32   // GPS PRN
                                3 -> 65 + (satIndex * 3) % 24         // GLONASS
                                else -> 201 + (satIndex * 5) % 37     // BDS
                            }
                        }
                    }
                }
            )

            XposedBridge.log("[LocationSpoofer] GnssStatus hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] GnssStatus hook failed: $e")
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

                    val simModeIdx = cursor.getColumnIndex("sim_mode")
                    val simMode = if (simModeIdx != -1) cursor.getString(simModeIdx) else "STILL"

                    val simBearingIdx = cursor.getColumnIndex("sim_bearing")
                    val simBearing = if (simBearingIdx != -1) cursor.getFloat(simBearingIdx) else 0f

                    val startTimestampIdx = cursor.getColumnIndex("start_timestamp")
                    val startTimestamp = if (startTimestampIdx != -1) cursor.getLong(startTimestampIdx) else System.currentTimeMillis()

                    cursor.close()

                    val config = JSONObject()
                    config.put("active", active)
                    config.put("lat", lat)           // GCJ-02
                    config.put("lng", lng)           // GCJ-02
                    // 预计算 WGS-84，避免每次 hook 调用都重复转换
                    val wgs84 = gcj02ToWgs84(lat, lng)
                    config.put("wgs84_lat", wgs84.first)
                    config.put("wgs84_lng", wgs84.second)
                    config.put("wifi_json", org.json.JSONArray(wifiJson))
                    config.put("sim_mode", simMode)
                    config.put("sim_bearing", simBearing.toDouble())
                    config.put("start_timestamp", startTimestamp)

                    lastConfig = config
                    lastReadTime = currentTime
                    return config
                }
            } catch (e: Throwable) { // 忽略查询错误
            }
        }

        // 回退到本地文件
        return try {
            val file = File("/data/local/tmp/locationspoofer_config.json")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                val config = JSONObject(content)
                if (!config.has("wifi_json")) config.put("wifi_json", org.json.JSONArray())
                // 预计算 WGS-84
                val lat = config.optDouble("lat", 0.0)
                val lng = config.optDouble("lng", 0.0)
                val wgs84 = gcj02ToWgs84(lat, lng)
                config.put("wgs84_lat", wgs84.first)
                config.put("wgs84_lng", wgs84.second)
                lastConfig = config
                lastReadTime = currentTime
                config
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
