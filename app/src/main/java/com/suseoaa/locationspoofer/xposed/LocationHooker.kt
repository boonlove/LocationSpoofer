package com.suseoaa.locationspoofer.xposed

import com.suseoaa.locationspoofer.BuildConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.lang.reflect.Member

// --- Legacy Compatibility Layer ---
abstract class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}

    class MethodHookParam {
        var method: Member? = null
        var thisObject: Any? = null
        var args: Array<Any?> = emptyArray()
        var returnEarly = false
        var result: Any? = null
            set(value) {
                field = value
                returnEarly = true
            }
        var throwable: Throwable? = null
            set(value) {
                field = value
                returnEarly = true
            }
    }
}

object XposedHelpers {
    lateinit var module: XposedModule

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try { findClass(className, classLoader) } catch (e: Throwable) { null }
    }

    fun getObjectField(obj: Any, fieldName: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(obj)
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException(fieldName)
    }

    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException(fieldName)
    }

    fun setIntField(obj: Any, fieldName: String, value: Int) { setObjectField(obj, fieldName, value) }
    fun setDoubleField(obj: Any, fieldName: String, value: Double) { setObjectField(obj, fieldName, value) }
    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) { setObjectField(obj, fieldName, value) }
    fun setLongField(obj: Any, fieldName: String, value: Long) { setObjectField(obj, fieldName, value) }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val argTypes = args.map { it?.javaClass ?: Any::class.java }
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size) {
                    m.isAccessible = true
                    return m.invoke(obj, *args)
                }
            }
            clazz = clazz.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        var c: Class<*>? = clazz
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size && java.lang.reflect.Modifier.isStatic(m.modifiers)) {
                    m.isAccessible = true
                    return m.invoke(null, *args)
                }
            }
            c = c.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName, *parameterTypes)
                m.isAccessible = true
                return m
            } catch (e: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodException(methodName)
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        for (c in clazz.declaredConstructors) {
            if (c.parameterCount == args.size) {
                c.isAccessible = true
                return c.newInstance(*args)
            }
        }
        throw NoSuchMethodException("Constructor for " + clazz.name + " not found")
    }

    fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg args: Any?) {
        try {
            val clazz = findClass(className, classLoader)
            findAndHookMethod(clazz, methodName, *args)
        } catch (e: Throwable) {
            // log
        }
    }

    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg args: Any?) {
        val hookIndex = args.indexOfLast { it is XC_MethodHook }
        if (hookIndex == -1) return
        val callback = args[hookIndex] as XC_MethodHook
        val paramTypes = args.slice(0 until hookIndex).map {
            when (it) {
                is Class<*> -> it
                is String -> findClass(it, clazz.classLoader)
                else -> throw IllegalArgumentException("Invalid argument type")
            }
        }.toTypedArray()

        val method = findMethodExact(clazz, methodName, *paramTypes)
        hookMethod(method, callback)
    }

    fun hookMethod(executable: java.lang.reflect.Executable, callback: XC_MethodHook) {
        module.hook(executable).intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                val param = XC_MethodHook.MethodHookParam().apply {
                    this.method = executable
                    this.thisObject = chain.thisObject
                    this.args = chain.args.toTypedArray()
                }

                try {
                    callback.beforeHookedMethod(param)
                } catch (e: Throwable) {
                    param.throwable = e
                }

                if (!param.returnEarly) {
                    try {
                        param.result = chain.proceed(param.args)
                    } catch (e: Throwable) {
                        param.throwable = e
                    }
                }

                try {
                    callback.afterHookedMethod(param)
                } catch (e: Throwable) {
                    param.throwable = e
                }

                if (param.throwable != null) throw param.throwable!!
                return param.result
            }
        })
    }
}

object XposedBridge {
    fun log(msg: String) {
        android.util.Log.i("LocationSpoofer_Xposed", msg)
        try { XposedHelpers.module.log(android.util.Log.INFO, "LocationSpoofer", msg) } catch (e: Throwable) {}
    }
    fun log(t: Throwable) {
        android.util.Log.e("LocationSpoofer_Xposed", "Error", t)
        try { XposedHelpers.module.log(android.util.Log.ERROR, "LocationSpoofer", "Error", t) } catch (e: Throwable) {}
    }
    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: XC_MethodHook) {
        var hooked = false
        for (m in clazz.declaredMethods) {
            if (m.name == methodName) {
                XposedHelpers.hookMethod(m, callback)
                hooked = true
            }
        }
    }
}

class LocationHooker : XposedModule() {
    init {
        XposedHelpers.module = this
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        // Nothing here for now
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        val classLoader = param.classLoader
        handleLoadPackage(pkg, classLoader)
    }
    
    // --- Original Logic ---


    companion object {

        // 系统进程同样需要覆盖（android进程持有LocationManagerService）
        val SYSTEM_PACKAGES = setOf("android", "system", "com.android.phone")
    }

    fun handleLoadPackage(pkg: String, classLoader: ClassLoader) {
        

        // 宿主App自报平安
        if (pkg == BuildConfig.APPLICATION_ID) {
            return // 宿主App不需要注入定位Hook
        }

        // 系统进程：只Hook Location API，不动Wi-Fi（避免系统崩溃）
        if (SYSTEM_PACKAGES.contains(pkg)) {
            hookLocationAPIs(classLoader, pkg)
            return
        }


        XposedBridge.log("[LocationSpoofer] Hooking package: $pkg")

        // ★ 反检测: 必须在其他Hook之前安装,隐藏Xposed环境
        hookAntiDetection(classLoader)

        hookLocationAPIs(classLoader, pkg)
        hookWifiEnvironment(classLoader)
        hookCellEnvironment(classLoader)
        hookConnectivityLayer(classLoader)
        hookBluetoothLE(classLoader)
        hookGnssStatus(classLoader)
    }

    /**
     * ★ 反检测: 隐藏Xposed环境,防止反作弊SDK检测到Hook
     *
     * 设计原则:
     * 1. 只使用精确匹配,绝不使用宽泛的contains/startsWith,避免误杀正常类
     * 2. 不Hook ClassLoader.loadClass的宽泛模式(会导致App卡死)
     * 3. 不Hook BufferedReader.readLine(开销巨大)
     * 4. 不Hook File.exists/Runtime.exec(干扰正常功能)
     */
    private fun hookAntiDetection(classLoader: ClassLoader) {

        // ── 1. 堆栈帧过滤 ──
        // 反作弊SDK通过getStackTrace()检查调用链,发现Xposed帧即判定为Hook环境
        // 只过滤精确匹配的Xposed类名,不影响正常堆栈
        val xposedClassNames = setOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XC_MethodReplacement",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.XC_MethodHook\$MethodHookParam",
            "io.github.libxposed.api.XposedModule",
            "io.github.libxposed.api.XposedInterface",
            "io.github.libxposed.api.XposedModuleInterface",
            "org.lsposed.manager.MainApplication",
            "io.github.lsposed.manager.App"
        )

        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Throwable", classLoader, "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stackTrace = param.result as? Array<StackTraceElement> ?: return
                        val filtered = stackTrace.filter { elem ->
                            elem.className !in xposedClassNames
                        }.toTypedArray()
                        if (filtered.size != stackTrace.size) {
                            param.result = filtered
                        }
                    }
                })
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Thread", classLoader, "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stackTrace = param.result as? Array<StackTraceElement> ?: return
                        val filtered = stackTrace.filter { elem ->
                            elem.className !in xposedClassNames
                        }.toTypedArray()
                        if (filtered.size != stackTrace.size) {
                            param.result = filtered
                        }
                    }
                })
        } catch (_: Throwable) {}

        // ── 2. Class.forName 精确匹配 ──
        // 反作弊SDK通过Class.forName()尝试加载Xposed类,成功则判定为Hook环境
        // 使用精确匹配(不是contains),只拦截已知Xposed类名
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Class", classLoader, "forName",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                ClassLoader::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className in xposedClassNames) {
                            throw ClassNotFoundException()
                        }
                    }
                })
        } catch (_: Throwable) {
            // 降级: 尝试2参数版本
            try {
                XposedHelpers.findAndHookMethod(
                    "java.lang.Class", classLoader, "forName",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val className = param.args[0] as? String ?: return
                            if (className in xposedClassNames) {
                                throw ClassNotFoundException()
                            }
                        }
                    })
            } catch (_: Throwable) {}
        }

        // ── 3. ClassLoader.loadClass 精确匹配 ──
        // 同样使用精确匹配,只拦截已知Xposed类名
        // loadClass被调用频率很高,精确匹配确保零误杀
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.ClassLoader", classLoader, "loadClass",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className in xposedClassNames) {
                            throw ClassNotFoundException()
                        }
                    }
                })
        } catch (_: Throwable) {}

        XposedBridge.log("[LocationSpoofer] Anti-detection hooks installed")
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
        val x = gcjLng
        val y = gcjLat
        val z = sqrt(x * x + y * y) + 0.00002 * sin(y * BD_PI)
        val theta = Math.atan2(y, x) + 0.000003 * cos(x * BD_PI)
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

        // sigma=0.000002度(约0.2米步长), alpha=0.05(均值回归)
        val sigma = 0.000002
        val alpha = 0.05
        
        // 使用 Ornstein-Uhlenbeck 过程生成自然偏移，并硬性限制在 4 米以内 (约 0.00004 度)
        hookDriftLat = (hookDriftLat + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLat * dt)
            .coerceIn(-0.00004, 0.00004)
        hookDriftLng = (hookDriftLng + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLng * dt)
            .coerceIn(-0.00004, 0.00004)

        return Pair(baseLat + hookDriftLat, baseLng + hookDriftLng)
    }

    private fun getJitteredAccuracy(): Float {
        // 精度值在基准20m附近做高斯漂移,模拟GDOP变化
        hookAccuracyDrift += 0.5 * rng.nextGaussian() - 0.03 * hookAccuracyDrift
        return (20.0 + hookAccuracyDrift).coerceIn(3.0, 45.0).toFloat()
    }


    private fun hookLocationAPIs(classLoader: ClassLoader, currentPkg: String) {
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
                        val appSystems = config.optJSONObject("app_coordinate_systems")
                        val basePkg = currentPkg.substringBefore(":")
                        // optString 在 key 不存在时返回 ""（空字符串），不是 null！
                        // 必须用 has() 先检查，否则会错误匹配到空字符串分支
                        val targetSys = if (appSystems?.has(basePkg) == true) {
                            appSystems.optString(basePkg, "GCJ-02")
                        } else {
                            "GCJ-02"
                        }
                        val baseLat = when (targetSys) {
                            "WGS-84" -> config.optDouble("wgs84_lat", param.result as Double)
                            "BD-09" -> config.optDouble("bd09_lat", param.result as Double)
                            else -> config.optDouble("lat", param.result as Double)
                        }
                        val baseLng = when (targetSys) {
                            "WGS-84" -> config.optDouble("wgs84_lng", 0.0)
                            "BD-09" -> config.optDouble("bd09_lng", 0.0)
                            else -> config.optDouble("lng", 0.0)
                        }
                        param.result = getJitteredLocation(baseLat, baseLng).first
                    }
                }
            }

            val getLngHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val appSystems = config.optJSONObject("app_coordinate_systems")
                        val basePkg = currentPkg.substringBefore(":")
                        // optString 在 key 不存在时返回 ""（空字符串），不是 null！
                        val targetSys = if (appSystems?.has(basePkg) == true) {
                            appSystems.optString(basePkg, "GCJ-02")
                        } else {
                            "GCJ-02"
                        }
                        val baseLat = when (targetSys) {
                            "WGS-84" -> config.optDouble("wgs84_lat", 0.0)
                            "BD-09" -> config.optDouble("bd09_lat", 0.0)
                            else -> config.optDouble("lat", 0.0)
                        }
                        val baseLng = when (targetSys) {
                            "WGS-84" -> config.optDouble("wgs84_lng", param.result as Double)
                            "BD-09" -> config.optDouble("bd09_lng", param.result as Double)
                            else -> config.optDouble("lng", param.result as Double)
                        }
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
                            when (param.method!!.name) {
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
                    when (param.method!!.name) {
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
                    // 动态获取当前BDLocation期望的坐标系(App可通过LocationClientOption.setCoorType设置)
                    val coorType = try {
                        XposedHelpers.callMethod(param.thisObject!!, "getCoorType") as? String
                    } catch (e: Throwable) { null }

                    val targetLat: Double
                    val targetLng: Double
                    
                    when (coorType) {
                        "bd09ll", "bd09mc", "bd09" -> {
                            targetLat = config.optDouble("bd09_lat", 0.0)
                            targetLng = config.optDouble("bd09_lng", 0.0)
                        }
                        "wgs84" -> {
                            targetLat = config.optDouble("wgs84_lat", 0.0)
                            targetLng = config.optDouble("wgs84_lng", 0.0)
                        }
                        else -> { // gcj02 或默认(中国标准坐标系)
                            targetLat = config.optDouble("lat", 0.0)
                            targetLng = config.optDouble("lng", 0.0)
                        }
                    }

                    val jittered = getJitteredLocation(targetLat, targetLng)
                    when (param.method!!.name) {
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
                            
                            val coorType = try {
                                XposedHelpers.callMethod(bdLoc, "getCoorType") as? String
                            } catch (e: Throwable) { null }

                            val targetLat: Double
                            val targetLng: Double
                            when (coorType) {
                                "bd09ll", "bd09mc", "bd09" -> {
                                    targetLat = config.optDouble("bd09_lat", 0.0)
                                    targetLng = config.optDouble("bd09_lng", 0.0)
                                }
                                "wgs84" -> {
                                    targetLat = config.optDouble("wgs84_lat", 0.0)
                                    targetLng = config.optDouble("wgs84_lng", 0.0)
                                }
                                else -> { // gcj02 或默认(中国标准坐标系)
                                    targetLat = config.optDouble("lat", 0.0)
                                    targetLng = config.optDouble("lng", 0.0)
                                }
                            }
                            
                            val jittered = getJitteredLocation(targetLat, targetLng)

                            // 通过反射直接写入BDLocation实例的经纬度
                            try { XposedHelpers.callMethod(bdLoc, "setLatitude", jittered.first) } catch (e: Throwable) {
                                try { XposedHelpers.setDoubleField(bdLoc, "mLatitude", jittered.first) } catch (e2: Throwable) {}
                            }
                            try { XposedHelpers.callMethod(bdLoc, "setLongitude", jittered.second) } catch (e: Throwable) {
                                try { XposedHelpers.setDoubleField(bdLoc, "mLongitude", jittered.second) } catch (e2: Throwable) {}
                            }
                            // 只修改定位类型，不强制覆盖坐标系类型
                            try { XposedHelpers.callMethod(bdLoc, "setLocType", 61) } catch (e: Throwable) {}
                        }
                    }
                )
                XposedBridge.log("[LocationSpoofer] $listenerClassName callback hook installed")
            } catch (e: Throwable) { /* 忽略 */ }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Wi-Fi 环境伪造 — 覆盖 WifiInfo / WifiManager / NetworkInfo
    // ══════════════════════════════════════════════════════════════════════════
    private fun hookWifiEnvironment(classLoader: ClassLoader) {

        // ── 1. WifiInfo getter Hook ──
        // 拦截所有 WifiInfo 属性读取，返回数据库中第一个 AP 的信息
        // 无数据时返回伪造的断开状态值，避免泄漏真实 Wi-Fi 指纹
        val wifiInfoHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig() ?: return
                if (!config.optBoolean("active", false)) return
                val wifiArray = config.optJSONArray("wifi_json")
                val firstWifi =
                    if (wifiArray != null && wifiArray.length() > 0) wifiArray.getJSONObject(0) else null
                when (param.method!!.name) {
                    "getBSSID" -> param.result =
                        firstWifi?.optString("bssid") ?: "02:00:00:00:00:00"
                    "getMacAddress" -> param.result =
                        firstWifi?.optString("bssid") ?: "02:00:00:00:00:00"
                    "getSSID" -> param.result =
                        if (firstWifi != null) "\"${firstWifi.optString("ssid", "HOME_WIFI")}\"" else "<unknown ssid>"
                    "getNetworkId" -> param.result =
                        if (firstWifi != null) 1 else -1
                    "getRssi" -> param.result = -65
                    "getLinkSpeed" -> param.result = 65
                    "getFrequency" -> param.result =
                        firstWifi?.optInt("frequency", 2412) ?: 2412
                    "getIpAddress" -> param.result = 0x6401A8C0 // 192.168.1.100 小端序
                }
            }
        }

        try {
            val wifiInfoMethods = listOf(
                "getBSSID", "getMacAddress", "getSSID", "getNetworkId",
                "getRssi", "getLinkSpeed", "getFrequency", "getIpAddress"
            )
            for (method in wifiInfoMethods) {
                try {
                    XposedHelpers.findAndHookMethod(
                        "android.net.wifi.WifiInfo", classLoader, method, wifiInfoHook
                    )
                } catch (e: Throwable) { /* 部分方法在低版本可能不存在 */ }
            }
        } catch (e: Throwable) { XposedBridge.log(e) }

        // ── 1b. WifiInfo.getSupplicantState() → COMPLETED ──
        // 表示 Wi-Fi 已完成四次握手，处于正常连接状态
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiInfo", classLoader, "getSupplicantState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        try {
                            val enumClass = XposedHelpers.findClass(
                                "android.net.wifi.SupplicantState", classLoader
                            )
                            param.result = enumClass.getField("COMPLETED").get(null)
                        } catch (e: Throwable) { /* 忽略 */ }
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        // ── 2. Wi-Fi 扫描结果伪造 (getScanResults) ──
        // 真实扫描周期约4-5秒，timestamp 字段必须接近 SystemClock.elapsedRealtimeNanos()
        // 缺失 timestamp 是反作弊 SDK 最常用的检测维度之一
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

        val wifiScanHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig() ?: return
                if (!config.optBoolean("active", false)) return
                val fakeList = java.util.ArrayList<Any>()
                val wifiArray = config.optJSONArray("wifi_json")
                if (wifiArray != null && wifiArray.length() > 0) {
                    try {
                        val scanResultClass =
                            XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                        val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()
                        for (i in 0 until wifiArray.length()) {
                            val wifi = wifiArray.getJSONObject(i)
                            val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                            XposedHelpers.setObjectField(fakeScanResult, "SSID", wifi.optString("ssid"))
                            XposedHelpers.setObjectField(fakeScanResult, "BSSID", wifi.optString("bssid"))
                            // 信号强度: 高斯分布(均值-65dBm, 标准差10dBm)
                            val level = (-65 + (rng.nextGaussian() * 10).toInt()).coerceIn(-90, -30)
                            XposedHelpers.setIntField(fakeScanResult, "level", level)
                            XposedHelpers.setIntField(
                                fakeScanResult, "frequency",
                                wifi.optInt("frequency",
                                    listOf(2412, 2417, 2422, 2427, 2432, 2437, 2442,
                                        2447, 2452, 2457, 2462, 5180, 5200, 5220, 5240,
                                        5260, 5280, 5300, 5320, 5745, 5765, 5785, 5805).random()
                                )
                            )
                            XposedHelpers.setObjectField(
                                fakeScanResult, "capabilities",
                                wifi.optString("capabilities", realCapabilities.random())
                            )
                            // 时间戳: 基准时间 - 随机微秒偏移(模拟各 AP 被扫描到的先后差异)
                            try {
                                val offsetNanos = (rng.nextInt(200_000) * 1000L)
                                XposedHelpers.setLongField(
                                    fakeScanResult, "timestamp",
                                    (baseTimestamp - offsetNanos) / 1000 // timestamp 字段单位为微秒
                                )
                            } catch (e: Throwable) { /* 部分 ROM 该字段可能不存在 */ }
                            fakeList.add(fakeScanResult)
                        }
                    } catch (e: Throwable) { /* 忽略 */ }
                }
                // 无论有无数据，都替换结果（空列表 = 周围没有 AP，不泄漏真实数据）
                param.result = fakeList
            }
        }

        // ── 3. WifiManager 整体 Hook ──
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getScanResults", wifiScanHook
            )

            // getWifiState() → 3 (WIFI_STATE_ENABLED)
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getWifiState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = 3
                    }
                })

            // isWifiEnabled() → true
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "isWifiEnabled",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = true
                    }
                })

            // getConnectionInfo() — 返回伪造的 WifiInfo 对象（包含目标地点的 BSSID/SSID）
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getConnectionInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        val wifiArray = config.optJSONArray("wifi_json")
                        if (wifiArray != null && wifiArray.length() > 0) {
                            val firstWifi = wifiArray.getJSONObject(0)
                            try {
                                val wifiInfoClass = XposedHelpers.findClass(
                                    "android.net.wifi.WifiInfo", classLoader
                                )
                                val fakeWifiInfo = XposedHelpers.newInstance(wifiInfoClass)
                                try { XposedHelpers.setObjectField(fakeWifiInfo, "mBSSID", firstWifi.optString("bssid")) } catch (e: Throwable) {}
                                try { XposedHelpers.setObjectField(fakeWifiInfo, "mMacAddress", firstWifi.optString("bssid")) } catch (e: Throwable) {}
                                // 设置 SSID（优先使用 WifiSsid 对象，降级为字符串）
                                try {
                                    val wifiSsidClass = XposedHelpers.findClass("android.net.wifi.WifiSsid", classLoader)
                                    val createMethod = XposedHelpers.findMethodExact(wifiSsidClass, "createFromAsciiEncoded", String::class.java)
                                    val wifiSsid = createMethod.invoke(null, firstWifi.optString("ssid"))
                                    XposedHelpers.setObjectField(fakeWifiInfo, "mWifiSsid", wifiSsid)
                                } catch (e: Throwable) {
                                    try { XposedHelpers.setObjectField(fakeWifiInfo, "mSSID", "\"${firstWifi.optString("ssid")}\"") } catch (e2: Throwable) {}
                                }
                                try { XposedHelpers.setIntField(fakeWifiInfo, "mNetworkId", 1) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(fakeWifiInfo, "mRssi", -65) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(fakeWifiInfo, "mLinkSpeed", 65) } catch (e: Throwable) {}
                                try { XposedHelpers.setIntField(fakeWifiInfo, "mFrequency", firstWifi.optInt("frequency", 2412)) } catch (e: Throwable) {}
                                param.result = fakeWifiInfo
                            } catch (e: Throwable) { /* 忽略 */ }
                        }
                    }
                })

            // getConfiguredNetworks() → 空列表（防止已保存 Wi-Fi 配置泄漏）
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getConfiguredNetworks",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = java.util.ArrayList<Any>()
                    }
                })

            // getDhcpInfo() → 伪造的 DHCP 信息（192.168.1.x 网段）
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager", classLoader, "getDhcpInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        try {
                            val dhcpClass = XposedHelpers.findClass("android.net.DhcpInfo", classLoader)
                            val dhcp = XposedHelpers.newInstance(dhcpClass)
                            // IP 地址采用小端序: 192.168.1.100 → 0x6401A8C0
                            XposedHelpers.setIntField(dhcp, "ipAddress", 0x6401A8C0.toInt())
                            XposedHelpers.setIntField(dhcp, "gateway", 0x0101A8C0)     // 192.168.1.1
                            XposedHelpers.setIntField(dhcp, "netmask", 0x00FFFFFF)     // 255.255.255.0
                            XposedHelpers.setIntField(dhcp, "dns1", 0x0101A8C0)        // 192.168.1.1
                            XposedHelpers.setIntField(dhcp, "dns2", 0x08080808)        // 8.8.8.8
                            XposedHelpers.setIntField(dhcp, "serverAddress", 0x0101A8C0)
                            param.result = dhcp
                        } catch (e: Throwable) { /* 忽略 */ }
                    }
                })
        } catch (e: Throwable) { XposedBridge.log(e) }

        // ── 4. NetworkInfo.getExtraInfo() ──
        // 这是高德/抖音用来交叉验证 Wi-Fi 身份的核心 API 之一
        // 直接返回当前连接的真实 SSID，必须拦截！
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.NetworkInfo", classLoader, "getExtraInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        val wifiArray = config.optJSONArray("wifi_json")
                        if (wifiArray != null && wifiArray.length() > 0) {
                            param.result = "\"${wifiArray.getJSONObject(0).optString("ssid", "HOME_WIFI")}\""
                        } else {
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Throwable) { XposedBridge.log(e) }

        XposedBridge.log("[LocationSpoofer] Wi-Fi environment hooks installed")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 基站/蜂窝网络环境伪造 — 覆盖 TelephonyManager / PhoneStateListener
    // ══════════════════════════════════════════════════════════════════════════
    private fun hookCellEnvironment(classLoader: ClassLoader) {

        // ── 1. 基站信息伪造（CellLocation / AllCellInfo / NeighboringCellInfo）──
        val cellHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig() ?: return
                if (!config.optBoolean("active", false)) return
                val lat = config.optDouble("lat", 0.0)
                val lng = config.optDouble("lng", 0.0)

                when (param.method!!.name) {
                    "getCellLocation" -> {
                        try {
                            val cellArray = config.optJSONArray("cell_json")
                            if (cellArray != null && cellArray.length() > 0) {
                                // 有采集数据时，使用数据库中的 LAC/CID
                                val cell = cellArray.getJSONObject(0)
                                val gsmCellLocationClass = XposedHelpers.findClass(
                                    "android.telephony.gsm.GsmCellLocation", classLoader
                                )
                                val fakeLocation = XposedHelpers.newInstance(gsmCellLocationClass)
                                val lac = cell.optInt("tac", 10000)
                                val cid = if (cell.has("ci")) cell.optInt("ci") else cell.optInt("cid", 100000)
                                XposedHelpers.callMethod(fakeLocation, "setLacAndCid", lac, cid)
                                param.result = fakeLocation
                            } else {
                                // 无数据时返回 null，不生成伪随机数据
                                param.result = null
                            }
                        } catch (e: Throwable) {
                            param.result = null
                        }
                    }

                    "getAllCellInfo" -> {
                        try {
                            param.result = buildFakeCellInfoList(classLoader, lat, lng, config)
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] CellInfo构造失败: $e")
                            param.result = java.util.ArrayList<Any>()
                        }
                    }

                    "getNeighboringCellInfo" -> param.result = java.util.ArrayList<Any>()
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader, "getAllCellInfo", cellHook)
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader, "getCellLocation", cellHook)
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader, "getNeighboringCellInfo", cellHook)
        } catch (e: Throwable) { XposedBridge.log(e) }

        // ── 2. TelephonyManager 元数据 Hook ──
        // 防止 MCC/MNC/运营商名称/网络类型泄漏真实地理位置
        // 高德用 getNetworkOperator() 验证基站数据是否与 GPS 位置地理一致
        val telephonyMetaHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val config = readConfig() ?: return
                if (!config.optBoolean("active", false)) return
                val cellArray = config.optJSONArray("cell_json")
                when (param.method!!.name) {
                    "getNetworkOperator" -> {
                        // 返回 MCC+MNC 字符串（如 "46000"），必须与基站数据一致
                        if (cellArray != null && cellArray.length() > 0) {
                            val cell = cellArray.getJSONObject(0)
                            val mcc = cell.optInt("mcc", 460)
                            val mnc = cell.optInt("mnc", 0)
                            param.result = String.format(java.util.Locale.US, "%d%02d", mcc, mnc)
                        }
                        // 无数据时不修改，保留真实值（避免因空值触发异常）
                    }
                    "getNetworkOperatorName" -> {
                        if (cellArray != null && cellArray.length() > 0) {
                            val mnc = cellArray.getJSONObject(0).optInt("mnc", 0)
                            param.result = when (mnc) {
                                0, 2, 7 -> "中国移动"
                                1, 6, 9 -> "中国联通"
                                3, 5, 11 -> "中国电信"
                                else -> "中国移动"
                            }
                        }
                    }
                    // SIM 卡运营商编码/名称 — 不修改（SIM 是物理的，修改反而可疑）
                    "getSimOperator" -> { /* 保留真实值 */ }
                    "getSimOperatorName" -> { /* 保留真实值 */ }
                    "getNetworkType" -> param.result = 13   // NETWORK_TYPE_LTE
                    "getDataNetworkType" -> param.result = 13
                    "getPhoneType" -> param.result = 1      // PHONE_TYPE_GSM
                }
            }
        }

        val telephonyMetaMethods = listOf(
            "getNetworkOperator", "getNetworkOperatorName",
            "getNetworkType", "getDataNetworkType", "getPhoneType"
        )
        for (method in telephonyMetaMethods) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", classLoader, method, telephonyMetaHook
                )
            } catch (e: Throwable) { /* 部分方法在低版本可能不存在 */ }
        }

        // ── 3. PhoneStateListener 回调拦截 ──
        // 防止应用通过 TelephonyManager.listen() 的 LISTEN_CELL_INFO 回调
        // 绕过 getAllCellInfo() 的 Hook 获取真实基站数据
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", classLoader, "listen",
                "android.telephony.PhoneStateListener",
                Int::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        var events = param.args[1] as Int
                        // 移除 LISTEN_CELL_LOCATION (0x10) 和 LISTEN_CELL_INFO (0x400) 标志位
                        // 这样系统就不会将真实的基站变更回调给应用
                        events = events and 0x10.inv()   // 移除 LISTEN_CELL_LOCATION
                        events = events and 0x400.inv()  // 移除 LISTEN_CELL_INFO
                        param.args[1] = events
                    }
                }
            )
        } catch (e: Throwable) { XposedBridge.log(e) }

        XposedBridge.log("[LocationSpoofer] Cell environment hooks installed")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 网络连接层伪造 — 覆盖 ConnectivityManager / NetworkCapabilities / NetworkInterface
    // ══════════════════════════════════════════════════════════════════════════
    private fun hookConnectivityLayer(classLoader: ClassLoader) {

        // ── 1. ConnectivityManager.getActiveNetworkInfo() ──
        // 不修改返回值结构，NetworkInfo.getExtraInfo() 的 Hook 已在 hookWifiEnvironment 中处理 SSID 泄漏
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.ConnectivityManager", classLoader, "getActiveNetworkInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // 保持返回值不变 — SSID 泄漏由 NetworkInfo.getExtraInfo() Hook 处理
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        // ── 2. NetworkCapabilities — Android 6+ 网络能力查询 ──
        // Android 12+ 的 NetworkCapabilities 包含 WifiInfo 作为 TransportInfo
        // 必须清除以防止通过此路径获取真实 Wi-Fi 信息
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.ConnectivityManager", classLoader,
                "getNetworkCapabilities",
                "android.net.Network",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        val nc = param.result ?: return
                        // 清除 TransportInfo 中可能包含的真实 Wi-Fi 信息
                        try {
                            XposedHelpers.setObjectField(nc, "mTransportInfo", null)
                        } catch (e: Throwable) { /* 字段名可能因版本不同 */ }
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        // ── 3. NetworkInterface.getNetworkInterfaces() ──
        // 过滤 wlan0/p2p 等 Wi-Fi 相关网络接口，防止通过接口名和 MAC 地址泄漏真实信息
        try {
            XposedHelpers.findAndHookMethod(
                "java.net.NetworkInterface", classLoader, "getNetworkInterfaces",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        val result = param.result as? java.util.Enumeration<*> ?: return
                        val filtered = java.util.Collections.list(result).filter { iface ->
                            val name = try {
                                (iface as java.net.NetworkInterface).name
                            } catch (e: Throwable) { "" }
                            // 保留非 Wi-Fi 接口（lo, rmnet 等），过滤 wlan/p2p/swlan
                            !name.startsWith("wlan") && !name.startsWith("p2p") && !name.startsWith("swlan")
                        }
                        param.result = java.util.Collections.enumeration(filtered)
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        XposedBridge.log("[LocationSpoofer] Connectivity layer hooks installed")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 蓝牙 BLE 扫描拦截 — 防止通过 iBeacon / Eddystone 信标定位
    // ══════════════════════════════════════════════════════════════════════════
    private fun hookBluetoothLE(classLoader: ClassLoader) {

        // ── BLE 扫描结果伪造的核心逻辑（复用于不同 startScan 重载）──
        val buildAndDeliverBleResults = { config: JSONObject, callbackObj: Any, cl: ClassLoader ->
            try {
                val bluetoothArray = config.optJSONArray("bluetooth_json")
                if (bluetoothArray != null && bluetoothArray.length() > 0) {
                    val results = java.util.ArrayList<Any>()
                    val scanResultClass = XposedHelpers.findClass("android.bluetooth.le.ScanResult", cl)
                    val bluetoothDeviceClass = XposedHelpers.findClass("android.bluetooth.BluetoothDevice", cl)
                    val scanRecordClass = XposedHelpers.findClass("android.bluetooth.le.ScanRecord", cl)

                    for (i in 0 until bluetoothArray.length()) {
                        try {
                            val obj = bluetoothArray.getJSONObject(i)
                            val address = obj.optString("address", "00:11:22:33:44:55")
                            val rssi = obj.optInt("rssi", -60)
                            val hexRecord = obj.optString("scanRecordHex", "")

                            // 1. 构造 BluetoothDevice
                            val device = XposedHelpers.newInstance(bluetoothDeviceClass, address)

                            // 2. 构造 ScanRecord
                            var scanRecord: Any? = null
                            if (hexRecord.isNotEmpty()) {
                                try {
                                    val bytes = hexStringToByteArray(hexRecord)
                                    scanRecord = XposedHelpers.callStaticMethod(scanRecordClass, "parseFromBytes", bytes)
                                } catch (e: Throwable) { /* 忽略 */ }
                            }

                            // 3. 构造 ScanResult（兼容新旧构造器）
                            val timestampNanos = android.os.SystemClock.elapsedRealtimeNanos()
                            var scanResultObj: Any? = null
                            try {
                                // Android 8.0+ 构造器
                                scanResultObj = XposedHelpers.newInstance(
                                    scanResultClass, device,
                                    0x001B, 1, 0, 255, 127, rssi, 0, scanRecord, timestampNanos
                                )
                            } catch (e: Throwable) {
                                try {
                                    // 旧版本构造器
                                    scanResultObj = XposedHelpers.newInstance(
                                        scanResultClass, device, scanRecord, rssi, timestampNanos
                                    )
                                } catch (e2: Throwable) { /* 忽略 */ }
                            }

                            if (scanResultObj != null) {
                                results.add(scanResultObj)
                                try { XposedHelpers.callMethod(callbackObj, "onScanResult", 1, scanResultObj) } catch (e: Throwable) {}
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] 构建虚拟BLE失败: $e")
                        }
                    }

                    // 批量触发回调
                    if (results.isNotEmpty()) {
                        try { XposedHelpers.callMethod(callbackObj, "onBatchScanResults", results) } catch (e: Throwable) {}
                    }
                }
            } catch (e: Throwable) { XposedBridge.log(e) }
            Unit
        }

        // ── 1. startScan(List<ScanFilter>, ScanSettings, ScanCallback) — 3参数重载 ──
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.le.BluetoothLeScanner", classLoader, "startScan",
                java.util.List::class.java,
                android.bluetooth.le.ScanSettings::class.java,
                android.bluetooth.le.ScanCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = null // 短路原始扫描
                        val callback = param.args[2] ?: return
                        buildAndDeliverBleResults(config, callback, classLoader)
                    }
                }
            )
        } catch (e: Throwable) { XposedBridge.log(e) }

        // ── 2. startScan(ScanCallback) — 1参数重载 ──
        // 部分 App（如微信）使用无 filter 的简化版 startScan
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.le.BluetoothLeScanner", classLoader, "startScan",
                android.bluetooth.le.ScanCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = null
                        val callback = param.args[0] ?: return
                        buildAndDeliverBleResults(config, callback, classLoader)
                    }
                }
            )
        } catch (e: Throwable) { XposedBridge.log(e) }

        // ── 3. BluetoothAdapter.getBondedDevices() → 空集合 ──
        // 防止通过已配对蓝牙设备列表进行指纹识别
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.BluetoothAdapter", classLoader, "getBondedDevices",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = java.util.HashSet<Any>()
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        // ── 4. BluetoothAdapter.startDiscovery() → false ──
        // 阻止经典蓝牙扫描发现周围真实设备
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.BluetoothAdapter", classLoader, "startDiscovery",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        param.result = false
                    }
                }
            )
        } catch (e: Throwable) { /* 忽略 */ }

        // ── 5. 老接口 BluetoothAdapter.startLeScan（Android 4.x）──
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.BluetoothAdapter", classLoader, "startLeScan",
                android.bluetooth.BluetoothAdapter.LeScanCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = readConfig() ?: return
                        if (!config.optBoolean("active", false)) return
                        // 老接口不具备很好的伪造性，直接返回启动失败
                        param.result = false
                    }
                }
            )
        } catch (e: Throwable) { XposedBridge.log(e) }

        XposedBridge.log("[LocationSpoofer] Bluetooth LE hooks installed")
    }
    
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
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
        classLoader: ClassLoader, lat: Double, lng: Double, config: org.json.JSONObject?
    ): java.util.ArrayList<Any> {
        val result = java.util.ArrayList<Any>()
        
        val cellArray = config?.optJSONArray("cell_json")
        if (cellArray != null && cellArray.length() > 0) {
            for (i in 0 until cellArray.length()) {
                try {
                    val obj = cellArray.getJSONObject(i)
                    // 为简化跨版本兼容，统一构造CellInfoLte
                    val cellInfoLteClass = XposedHelpers.findClass("android.telephony.CellInfoLte", classLoader)
                    val cellInfo = XposedHelpers.newInstance(cellInfoLteClass)
                    val isRegistered = obj.optBoolean("isRegistered", i == 0)
                    
                    try { XposedHelpers.setBooleanField(cellInfo, "mRegistered", isRegistered) } 
                    catch (e: Throwable) { try { XposedHelpers.callMethod(cellInfo, "setRegistered", isRegistered) } catch (e2: Throwable) {} }
                    
                    try { XposedHelpers.setLongField(cellInfo, "mTimeStamp", android.os.SystemClock.elapsedRealtimeNanos()) } catch (e: Throwable) {}
                    
                    val mcc = obj.optInt("mcc", 460)
                    val mnc = obj.optInt("mnc", 0)
                    val tac = obj.optInt("tac", 10000)
                    val pci = obj.optInt("pci", 0)
                    val dbm = obj.optInt("dbm", -80)
                    val ci = if (obj.has("ci")) obj.optInt("ci") else if (obj.has("cid")) obj.optInt("cid") else 100000
                    
                    val cellIdentityLteClass = XposedHelpers.findClass("android.telephony.CellIdentityLte", classLoader)
                    val cellIdentity = try {
                        XposedHelpers.newInstance(cellIdentityLteClass, mcc, mnc, ci, pci, tac)
                    } catch (e: Throwable) {
                        val identity = XposedHelpers.newInstance(cellIdentityLteClass)
                        try { XposedHelpers.setIntField(identity, "mMcc", mcc) } catch (e2: Throwable) {}
                        try { XposedHelpers.setIntField(identity, "mMnc", mnc) } catch (e2: Throwable) {}
                        try { XposedHelpers.setIntField(identity, "mCi", ci) } catch (e2: Throwable) {}
                        try { XposedHelpers.setIntField(identity, "mPci", pci) } catch (e2: Throwable) {}
                        try { XposedHelpers.setIntField(identity, "mTac", tac) } catch (e2: Throwable) {}
                        identity
                    }
                    try { XposedHelpers.setObjectField(cellInfo, "mCellIdentityLte", cellIdentity) } catch (e: Throwable) {}
                    
                    try {
                        val cssClass = XposedHelpers.findClass("android.telephony.CellSignalStrengthLte", classLoader)
                        val css = XposedHelpers.newInstance(cssClass)
                        try { XposedHelpers.setIntField(css, "mRsrp", dbm) } catch (e2: Throwable) {}
                        try { XposedHelpers.setIntField(css, "mSignalStrength", dbm + 113) } catch (e2: Throwable) {}
                        XposedHelpers.setObjectField(cellInfo, "mCellSignalStrengthLte", css)
                    } catch (e: Throwable) {}
                    
                    result.add(cellInfo)
                } catch (e: Throwable) {
                    XposedBridge.log("[LocationSpoofer] 解析cell_json失败: $e")
                }
            }
            return result
        }

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
     *    目标App进程通过contentResolver.query()访问com.suseoaa.locationspoofer.fork.provider时,
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
