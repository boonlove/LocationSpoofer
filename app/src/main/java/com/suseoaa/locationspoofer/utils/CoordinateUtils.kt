package com.suseoaa.locationspoofer.utils

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 国家测绘局标准坐标偏移算法（GCJ-02 ↔ WGS-84）
 *
 * 坐标系约定：
 * - AMap SDK（高德）返回 GCJ-02
 * - Android LocationManager / GPS 硬件 / Google Play Services 使用 WGS-84
 * - android.location.Location.getLatitude() 标准返回 WGS-84
 * - AMapLocation.getLatitude() 返回 GCJ-02
 *
 * 注入规则：
 * - TestProvider (setTestProviderLocation) → 注入 WGS-84
 * - Xposed Hook (Location.getLatitude) → 注入 WGS-84
 * - Xposed Hook (AMapLocation.getLatitude) → 注入 GCJ-02
 */
object CoordinateUtils {

    private const val PI = Math.PI
    private const val A = 6378245.0           // 克拉索夫斯基椭球体长半轴
    private const val EE = 0.00669342162296594 // 偏心率平方

    data class LatLng(val lat: Double, val lng: Double)

    /**
     * GCJ-02 → WGS-84（逆向偏移）
     * 用于将高德坐标转为 GPS 坐标，注入 TestProvider 和标准 Location API
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): LatLng {
        if (outOfChina(gcjLat, gcjLng)) return LatLng(gcjLat, gcjLng)
        val d = delta(gcjLat, gcjLng)
        return LatLng(gcjLat - d.lat, gcjLng - d.lng)
    }

    /**
     * WGS-84 → GCJ-02（正向偏移）
     * 用于将 GPS 坐标转为高德坐标
     */
    fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): LatLng {
        if (outOfChina(wgsLat, wgsLng)) return LatLng(wgsLat, wgsLng)
        val dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
        val dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val newLat = wgsLat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val newLng = wgsLng + (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(newLat, newLng)
    }

    private fun delta(lat: Double, lng: Double): LatLng {
        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        return LatLng(
            (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI),
            (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        )
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }
}
