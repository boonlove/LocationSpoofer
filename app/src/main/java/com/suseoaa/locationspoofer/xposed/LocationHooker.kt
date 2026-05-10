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

    // ── GCJ-02 → BD-09 转换(百度坐标系) ──
    //
    // BD-09是百度在GCJ-02基础上施加的二次偏移坐标系。百度地图/百度定位SDK(BDLocation)
    // 内部期望接收BD-09坐标,若直接传入GCJ-02会产生约100-500米的固定偏移。
    //
    // 算法原理:
    // 1. 将GCJ-02坐标解释为以(0,0)为中心的直角坐标(x=lng, y=lat)
    // 2. 施加百度公开的偏移常量(x偏移0.0065度, y偏移0.006度)
    // 3. 将偏移后的直角坐标转为极坐标(r, theta),其中r=sqrt(x^2+y^2), theta=atan2(y,x)
    // 4. 对极角theta叠加一个与r相关的微小旋转量: theta += BD_PI * sin(r * BD_PI) * 0.000003
    //    BD_PI = pi * 3000/180 ≈ 52.3598..., 这是百度定义的旋转频率系数
    // 5. 对极径r叠加微小伸缩: r += BD_PI * cos(r * BD_PI) * 0.00002
    // 6. 将修正后的极坐标转回直角坐标,即为BD-09经纬度
    //
    // 为何不能省略此转换:
    // BDLocation.getLatitude()被Hook后如果返回GCJ-02坐标,百度SDK内部不会再做转换,
    // 直接将该值作为BD-09渲染到地图上,导致显示位置相对真实位置偏移数百米。

    /** 百度坐标系旋转频率常量: pi * 3000 / 180 */
    private val BD_PI = Math.PI * 3000.0 / 180.0

    /**
     * GCJ-02坐标转BD-09坐标
     *
     * @param gcjLat GCJ-02纬度(高德/腾讯坐标系)
     * @param gcjLng GCJ-02经度
     * @return Pair(BD-09纬度, BD-09经度)
     */
    private fun gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
        // 施加百度偏移常量
        val x = gcjLng + 0.0065
        val y = gcjLat + 0.006
        // 转极坐标
        val z = sqrt(x * x + y * y) + 0.00002 * sin(y * BD_PI)
        val theta = Math.atan2(y, x) + 0.000003 * cos(x * BD_PI)
        // 极坐标转回直角坐标即为BD-09
        val bdLng = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return Pair(bdLat, bdLng)
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
            // android.location.Location 标准接口: 返回GCJ-02坐标
            //
            // 关键决策 -- 为何不返回WGS-84:
            // 在中国大陆,系统GPS HAL层已内置GCJ-02强制加偏(国家测绘法规要求)。
            // 因此android.location.Location.getLatitude()在中国设备上实际返回的是GCJ-02坐标,
            // 而非API文档声称的WGS-84。所有中国地图App(高德/腾讯/百度)都基于这一事实编写:
            // 它们从Location拿到坐标后不会再做WGS-84到GCJ-02的转换,而是直接使用。
            //
            // 如果我们返回真正的WGS-84,App会把它当GCJ-02直接传给地图SDK渲染,
            // 由于WGS-84与GCJ-02之间存在约300-500米的非线性偏移,地图上会出现固定偏移。
            // 这正是之前微信和学习通出现定位偏移的根本原因。
            val getLatHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val gcjLat = config.optDouble("lat", param.result as Double)
                        val gcjLng = config.optDouble("lng", 0.0)
                        param.result = getJitteredLocation(gcjLat, gcjLng).first
                    }
                }
            }

            val getLngHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val gcjLat = config.optDouble("lat", 0.0)
                        val gcjLng = config.optDouble("lng", param.result as Double)
                        param.result = getJitteredLocation(gcjLat, gcjLng).second
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

            // ── 高德SDK专属Hook(含抖动,与原生Location保持同步) ──
            // 使用findClassIfExists安全探测: 微信小程序子进程(:appbrand0等)不加载高德SDK,
            // 直接findAndHookMethod会抛出ClassNotFoundError,中断整个hookLocationAPIs执行流。
            // findClassIfExists在类不存在时返回null而非抛异常,可安全跳过。
            val amapLocClazz = XposedHelpers.findClassIfExists(
                "com.amap.api.location.AMapLocation", classLoader
            )

            if (amapLocClazz != null) {
                XposedBridge.log("[LocationSpoofer] AMapLocation class found, installing AMap hooks")
                val amapLocClass = "com.amap.api.location.AMapLocation"

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
                    XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getLatitude", amapHook)
                    XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getLongitude", amapHook)
                } catch (e: Throwable) { /* AMap SDK方法签名不匹配则跳过 */ }

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
                    // 1. getMockData() -> null（直接砍掉mockData字段的数据来源）
                    XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getMockData", amapNullHook)
                    // 2. getMockFlag() / getMockType() -> 0
                    try { XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getMockFlag", amapZeroHook) } catch (e: Throwable) {}
                    try { XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getMockType", amapZeroHook) } catch (e: Throwable) {}
                    // 3. isMocked() -> false（AMap SDK 12.0+ 新接口）
                    try { XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "isMocked", amapFalseHook) } catch (e: Throwable) {}
                    // 4. getErrorCode() -> 0（非0表示定位失败）
                    XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getErrorCode", amapZeroHook)
                    // 5. getLocationType() -> 1（GPS类型，最可信）
                    XposedHelpers.findAndHookMethod(
                        amapLocClass, classLoader, "getLocationType",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val config = readConfig()
                                if (config != null && config.optBoolean("active", false)) param.result = 1 // 1 = GPS定位
                            }
                        })
                    // 6. getProvider() -> "gps"
                    XposedHelpers.findAndHookMethod(
                        amapLocClass, classLoader, "getProvider",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val config = readConfig()
                                if (config != null && config.optBoolean("active", false)) param.result = "gps"
                            }
                        })
                    // 7. 直接写底层 mock 相关字段（防反射读字段绕过 getter）
                    val setFieldHook = object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = readConfig()
                            if (config != null && config.optBoolean("active", false)) {
                                val obj = param.thisObject ?: return
                                try { XposedHelpers.setObjectField(obj, "mockData", null) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(obj, "mockFlag", 0) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(obj, "mockType", 0) } catch (e: Throwable) {}
                                try { XposedHelpers.setBooleanField(obj, "isMocked", false) } catch (e: Throwable) {}
                                try { XposedHelpers.setBooleanField(obj, "mMock", false) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(obj, "errorCode", 0) } catch (e: Throwable) {}
                            }
                        }
                    }
                    XposedHelpers.findAndHookMethod(amapLocClass, classLoader, "getLatitude", setFieldHook)
                } catch (e: Throwable) {
                    XposedBridge.log(e)
                }

                // 8. AMapLocationQualityReport 质量报告也要清零
                val qualityClazz = XposedHelpers.findClassIfExists(
                    "com.amap.api.location.AMapLocationQualityReport", classLoader
                )
                if (qualityClazz != null) {
                    try { XposedHelpers.findAndHookMethod(qualityClazz, "getMockInfo", amapNullHook) } catch (e: Throwable) {}
                    try { XposedHelpers.findAndHookMethod(qualityClazz, "isMockLocation", amapFalseHook) } catch (e: Throwable) {}
                }

                // 9. setMockEnable(false) 让高德SDK禁用自身的 mock 校验流程
                val clientClazz = XposedHelpers.findClassIfExists(
                    "com.amap.api.location.AMapLocationClient", classLoader
                )
                if (clientClazz != null) {
                    try {
                        XposedHelpers.findAndHookMethod(
                            clientClazz, "setMockEnable",
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
                    } catch (e: Throwable) {}
                }
            } else {
                XposedBridge.log("[LocationSpoofer] AMapLocation class not found in ${classLoader}, skipping AMap hooks")
            }

        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // ── 第三方地图SDK深度Hook(腾讯/百度) ──
        hookTencentSDK(classLoader)
        hookBaiduSDK(classLoader)
    }

    /**
     * 腾讯定位SDK深度Hook
     *
     * 架构分析:
     * TencentLocation在腾讯SDK中是一个**接口(interface)**,不是具体类。
     * 其方法签名为: public interface TencentLocation { double getLatitude(); ... }
     * Xposed的findAndHookMethod无法Hook接口方法(接口没有方法体),
     * 必须找到实现该接口的具体类并对其进行Hook。
     *
     * 腾讯SDK常见的实现类名(不同版本可能不同):
     * - com.tencent.map.geolocation.internal.TencentLocationImpl
     * - com.tencent.map.geolocation.TencentLocationImpl
     * - 部分版本使用ProGuard混淆后类名不固定
     *
     * 策略: 先尝试已知实现类名,若均不存在则降级为hookAllMethods扫描所有实现。
     *
     * 坐标系: GCJ-02(与高德相同)
     */
    private fun hookTencentSDK(classLoader: ClassLoader) {
        // 腾讯SDK已知的实现类名(按优先级排列)
        val implCandidates = listOf(
            "com.tencent.map.geolocation.internal.TencentLocationImpl",
            "com.tencent.map.geolocation.TencentLocationImpl",
            "com.tencent.tencentmap.mapsdk.map.model.TencentLocationImpl"
        )

        // 阶段1: 尝试直接Hook已知实现类
        var hooked = false
        for (implClass in implCandidates) {
            val clazz = XposedHelpers.findClassIfExists(implClass, classLoader)
            if (clazz != null) {
                hookTencentLocationClass(clazz, classLoader)
                hooked = true
                XposedBridge.log("[LocationSpoofer] TencentLocation impl found: $implClass")
                break
            }
        }

        // 阶段2: 若已知类名均不存在,尝试通过接口反向查找
        if (!hooked) {
            val interfaceClazz = XposedHelpers.findClassIfExists(
                "com.tencent.map.geolocation.TencentLocation", classLoader
            )
            if (interfaceClazz != null && interfaceClazz.isInterface) {
                // TencentLocation是接口,无法直接Hook。
                // 但腾讯SDK的定位结果最终会通过TencentLocationListener.onLocationChanged(TencentLocation)
                // 回调给App。我们Hook这个回调,在App拿到结果前篡改TencentLocation实例的字段。
                hookTencentLocationCallback(classLoader)
                hooked = true
            } else if (interfaceClazz != null) {
                // 某些版本中TencentLocation是具体类而非接口
                hookTencentLocationClass(interfaceClazz, classLoader)
                hooked = true
            }
        }

        if (!hooked) {
            XposedBridge.log("[LocationSpoofer] TencentLocation SDK not found, skipped")
        }
    }

    /**
     * 对TencentLocation的具体实现类进行方法Hook
     */
    private fun hookTencentLocationClass(clazz: Class<*>, classLoader: ClassLoader) {
        val coordHook = object : XC_MethodHook() {
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
            // hookAllMethods: 不管方法签名如何变化,只要方法名匹配就Hook
            XposedBridge.hookAllMethods(clazz, "getLatitude", coordHook)
            XposedBridge.hookAllMethods(clazz, "getLongitude", coordHook)
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] TencentLocation class hook failed: $e")
            return
        }

        // getProvider -> "gps"
        try {
            XposedBridge.hookAllMethods(clazz, "getProvider", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = "gps"
                    }
                }
            })
        } catch (e: Throwable) { /* 忽略 */ }

        // getAccuracy -> 抖动精度
        try {
            XposedBridge.hookAllMethods(clazz, "getAccuracy", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = getJitteredAccuracy()
                    }
                }
            })
        } catch (e: Throwable) { /* 忽略 */ }

        // isMockGps -> 0(非模拟)
        try {
            XposedBridge.hookAllMethods(clazz, "isMockGps", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = 0
                    }
                }
            })
        } catch (e: Throwable) { /* 忽略 */ }

        XposedBridge.log("[LocationSpoofer] TencentLocation hooks installed on ${clazz.name}")
    }

    /**
     * 通过拦截TencentLocationListener回调来修改坐标
     *
     * 当无法直接Hook TencentLocation实现类时的降级方案:
     * Hook TencentLocationListener.onLocationChanged(TencentLocation, int, String)回调,
     * 在回调触发时通过反射修改TencentLocation实例的内部字段。
     */
    private fun hookTencentLocationCallback(classLoader: ClassLoader) {
        val listenerClass = XposedHelpers.findClassIfExists(
            "com.tencent.map.geolocation.TencentLocationListener", classLoader
        ) ?: return

        try {
            // hookAllMethods可以Hook接口的所有实现类中的方法
            XposedBridge.hookAllMethods(
                listenerClass, "onLocationChanged",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config == null || !config.optBoolean("active", false)) return
                        if (param.args.isEmpty()) return

                        val tencentLoc = param.args[0] ?: return
                        val baseLat = config.optDouble("lat", 0.0)
                        val baseLng = config.optDouble("lng", 0.0)
                        val jittered = getJitteredLocation(baseLat, baseLng)

                        // 通过反射直接写入TencentLocation实现类的经纬度字段
                        try { XposedHelpers.callMethod(tencentLoc, "setLatitude", jittered.first) } catch (e: Throwable) {
                            try { XposedHelpers.setDoubleField(tencentLoc, "latitude", jittered.first) } catch (e2: Throwable) {
                                try { XposedHelpers.setDoubleField(tencentLoc, "mLatitude", jittered.first) } catch (e3: Throwable) {
                                    try { XposedHelpers.setDoubleField(tencentLoc, "a", jittered.first) } catch (e4: Throwable) {}
                                }
                            }
                        }
                        try { XposedHelpers.callMethod(tencentLoc, "setLongitude", jittered.second) } catch (e: Throwable) {
                            try { XposedHelpers.setDoubleField(tencentLoc, "longitude", jittered.second) } catch (e2: Throwable) {
                                try { XposedHelpers.setDoubleField(tencentLoc, "mLongitude", jittered.second) } catch (e3: Throwable) {
                                    try { XposedHelpers.setDoubleField(tencentLoc, "b", jittered.second) } catch (e4: Throwable) {}
                                }
                            }
                        }
                    }
                }
            )
            XposedBridge.log("[LocationSpoofer] TencentLocationListener callback hook installed")
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] TencentLocationListener hook failed: $e")
        }
    }

    /**
     * 百度定位SDK深度Hook
     *
     * 百度定位SDK的核心定位回调对象为com.baidu.location.BDLocation。
     * 百度地图使用BD-09坐标系,这是在GCJ-02基础上施加二次偏移的专有坐标系。
     *
     * 关键区别:
     * - 高德/腾讯: 使用GCJ-02,直接返回config中的lat/lng
     * - 百度: 使用BD-09,必须调用gcj02ToBd09()转换后再返回
     *
     * 双重保险策略:
     * 1. 直接Hook BDLocation.getLatitude/getLongitude(方法级拦截)
     * 2. Hook BDAbstractLocationListener.onReceiveLocation回调(回调级拦截)
     * 两者互为补充,确保无论百度SDK内部架构如何变化,BD-09坐标都能正确注入。
     */
    private fun hookBaiduSDK(classLoader: ClassLoader) {
        val baiduLocClass = "com.baidu.location.BDLocation"

        // 安全探测: 当前进程是否加载了百度定位SDK
        val baiduClazz = XposedHelpers.findClassIfExists(baiduLocClass, classLoader)
        if (baiduClazz == null) {
            XposedBridge.log("[LocationSpoofer] BDLocation class not found, skipping")
            return
        }

        // ── 方案1: 直接Hook BDLocation的Getter方法 ──
        val baiduCoordHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val bdLat = config.optDouble("bd09_lat", 0.0)
                    val bdLng = config.optDouble("bd09_lng", 0.0)
                    val jittered = getJitteredLocation(bdLat, bdLng)
                    when (param.method.name) {
                        "getLatitude" -> param.result = jittered.first
                        "getLongitude" -> param.result = jittered.second
                    }
                }
            }
        }

        try {
            // 使用hookAllMethods: BDLocation在不同版本中可能有多个getLatitude重载
            XposedBridge.hookAllMethods(baiduClazz, "getLatitude", baiduCoordHook)
            XposedBridge.hookAllMethods(baiduClazz, "getLongitude", baiduCoordHook)

            // getLocType -> 61(GPS定位)
            XposedBridge.hookAllMethods(baiduClazz, "getLocType", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        param.result = 61 // 61 = BDLocation.TypeGpsLocation
                    }
                }
            })

            // getRadius(精度) -> 与全局抖动精度同步
            try {
                XposedBridge.hookAllMethods(baiduClazz, "getRadius", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            param.result = getJitteredAccuracy()
                        }
                    }
                })
            } catch (e: Throwable) { /* 忽略 */ }

            // getMockGps -> 0(非模拟)
            try {
                XposedBridge.hookAllMethods(baiduClazz, "getMockGps", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            param.result = 0
                        }
                    }
                })
            } catch (e: Throwable) { /* 忽略 */ }

            // getCoorType -> "bd09ll"
            try {
                XposedBridge.hookAllMethods(baiduClazz, "getCoorType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            param.result = "bd09ll"
                        }
                    }
                })
            } catch (e: Throwable) { /* 忽略 */ }

            // getSatelliteNumber -> 12-18颗
            try {
                XposedBridge.hookAllMethods(baiduClazz, "getSatelliteNumber", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            param.result = 12 + rng.nextInt(7)
                        }
                    }
                })
            } catch (e: Throwable) { /* 忽略 */ }

            XposedBridge.log("[LocationSpoofer] BDLocation method hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] BDLocation method hook failed: $e")
        }

        // ── 方案2(补充): Hook百度定位回调,在App接收BDLocation前修改其内部字段 ──
        // BDAbstractLocationListener是百度SDK 7.0+推荐的回调基类
        val listenerCandidates = listOf(
            "com.baidu.location.BDAbstractLocationListener",
            "com.baidu.location.BDLocationListener"
        )
        for (listenerClassName in listenerCandidates) {
            val listenerClazz = XposedHelpers.findClassIfExists(listenerClassName, classLoader) ?: continue
            try {
                XposedBridge.hookAllMethods(
                    listenerClazz, "onReceiveLocation",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val config = readConfig() ?: return
                            if (!config.optBoolean("active", false)) return
                            if (param.args.isEmpty()) return

                            val bdLoc = param.args[0] ?: return
                            val bdLat = config.optDouble("bd09_lat", 0.0)
                            val bdLng = config.optDouble("bd09_lng", 0.0)
                            val jittered = getJitteredLocation(bdLat, bdLng)

                            // 通过反射直接写入BDLocation实例的经纬度
                            try { XposedHelpers.callMethod(bdLoc, "setLatitude", jittered.first) } catch (e: Throwable) {
                                try { XposedHelpers.setDoubleField(bdLoc, "mLatitude", jittered.first) } catch (e2: Throwable) {}
                            }
                            try { XposedHelpers.callMethod(bdLoc, "setLongitude", jittered.second) } catch (e: Throwable) {
                                try { XposedHelpers.setDoubleField(bdLoc, "mLongitude", jittered.second) } catch (e2: Throwable) {}
                            }
                            // 同时修改定位类型和坐标系标记
                            try { XposedHelpers.callMethod(bdLoc, "setLocType", 61) } catch (e: Throwable) {}
                            try { XposedHelpers.callMethod(bdLoc, "setCoorType", "bd09ll") } catch (e: Throwable) {}
                        }
                    }
                )
                XposedBridge.log("[LocationSpoofer] $listenerClassName callback hook installed")
            } catch (e: Throwable) { /* 忽略 */ }
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

    /**
     * 从本地文件读取模拟配置(纯文件方案,无ContentProvider跨进程调用)
     *
     * 设计决策 -- 为何彻底废弃ContentProvider:
     *
     * 1. Android11+(API30)的包可见性(PackageVisibility)机制:
     *    目标App进程通过contentResolver.query()访问com.suseoaa.locationspoofer.provider时,
     *    如果目标App的AndroidManifest.xml未声明<queries>对本App的可见性,
     *    系统会返回null并在ActivityThread中打印"Failed to find provider info"错误,
     *    此错误无法被try-catch捕获(发生在系统框架层),导致Logcat被疯狂刷屏。
     *
     * 2. 部分App(如学习通com.chaoxing.mobile)运行在受限沙盒中,
     *    即使声明了权限也无法跨进程查询外部Provider。
     *
     * 3. 文件方案的可靠性:
     *    ConfigManager通过root权限将JSON写入/data/local/tmp/locationspoofer_config.json,
     *    并设置chmod 777 + chcon u:object_r:shell_data_file:s0,
     *    所有进程(含system_server、目标App、Xposed模块进程)均可直接读取,
     *    无需任何Android权限或可见性声明。
     *
     * 缓存策略:
     *    800毫秒内的重复调用直接返回内存缓存的lastConfig,避免高频磁盘I/O。
     *    800ms这个阈值的选择依据: Hook回调频率约为1-2次/秒(GPS更新周期),
     *    800ms确保每个GPS更新周期内最多读取1次文件,同时保证配置变更在1秒内生效。
     *
     * 预计算优化:
     *    在此处集中预计算WGS-84和BD-09坐标,避免每次Hook回调都重复执行三角函数运算。
     *    gcj02ToWgs84()包含6次sin/cos调用+2次sqrt,预计算可节约约95%的CPU开销。
     */
    private fun readConfig(): JSONObject? {
        val currentTime = System.currentTimeMillis()
        // 800毫秒内存缓存: 避免高频Hook回调导致的密集磁盘I/O
        if (currentTime - lastReadTime < 800 && lastConfig != null) {
            return lastConfig
        }

        return try {
            val file = File("/data/local/tmp/locationspoofer_config.json")
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                val config = JSONObject(content)

                // 确保wifi_json字段存在(部分旧版本配置文件可能缺失)
                if (!config.has("wifi_json")) config.put("wifi_json", org.json.JSONArray())

                // ── 集中预计算坐标系转换,避免每次Hook回调都重复计算 ──
                val lat = config.optDouble("lat", 0.0)  // GCJ-02纬度
                val lng = config.optDouble("lng", 0.0)  // GCJ-02经度

                // 预计算WGS-84: 供android.location.Location的getLatitude/getLongitude使用
                val wgs84 = gcj02ToWgs84(lat, lng)
                config.put("wgs84_lat", wgs84.first)
                config.put("wgs84_lng", wgs84.second)

                // 预计算BD-09: 供百度BDLocation的getLatitude/getLongitude使用
                val bd09 = gcj02ToBd09(lat, lng)
                config.put("bd09_lat", bd09.first)
                config.put("bd09_lng", bd09.second)

                lastConfig = config
                lastReadTime = currentTime
                config
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
