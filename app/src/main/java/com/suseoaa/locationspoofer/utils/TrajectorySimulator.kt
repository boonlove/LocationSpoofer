package com.suseoaa.locationspoofer.utils

import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.SimulatedLocation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrajectorySimulator {
    /** 地球半径（米） */
    private const val R = 6378137.0

    /**
     * 计算模拟位置（带抖动和步频模拟）
     * @param baseLat 基础纬度
     * @param baseLng 基础经度
     * @param startTimestamp 开始时间戳
     * @param simModeName 模拟模式（步行、跑步等）
     * @param bearingDeg 初始方位角（度）
     * @param currentTime 当前时间戳
     */
    fun calculateSimulatedLocation(
        baseLat: Double,
        baseLng: Double,
        startTimestamp: Long,
        simModeName: String,
        bearingDeg: Float,
        currentTime: Long = System.currentTimeMillis()
    ): SimulatedLocation {
        val elapsedSec = (currentTime - startTimestamp) / 1000.0
        if (elapsedSec <= 0) return SimulatedLocation(baseLat, baseLng, 0f, bearingDeg, 5.0f, 0.0)

        val (speedMs, stepFreqHz, jitterRadius) = getModeParams(simModeName)
        val distance = speedMs * elapsedSec
        val bearingRad = Math.toRadians(bearingDeg.toDouble())

        val latRad = Math.toRadians(baseLat)
        val lngRad = Math.toRadians(baseLng)
        val newLatRad = Math.asin(
            sin(latRad) * cos(distance / R) + cos(latRad) * sin(distance / R) * cos(bearingRad)
        )
        val newLngRad = lngRad + Math.atan2(
            sin(bearingRad) * sin(distance / R) * cos(latRad),
            cos(distance / R) - sin(latRad) * sin(newLatRad)
        )

        var currentLat = Math.toDegrees(newLatRad)
        var currentLng = Math.toDegrees(newLngRad)

        if (stepFreqHz > 0) {
            val stepPhase = elapsedSec * stepFreqHz * 2 * Math.PI
            val stepOffsetMeters = 0.15 * sin(stepPhase)
            val perpBearingRad = bearingRad + Math.PI / 2
            currentLat += Math.toDegrees((stepOffsetMeters * cos(perpBearingRad)) / R)
            currentLng += Math.toDegrees(
                (stepOffsetMeters * sin(perpBearingRad)) / (R * cos(
                    newLatRad
                ))
            )
        }

        currentLat += Math.toDegrees(sin(elapsedSec / 10.0) * (jitterRadius / R))
        currentLng += Math.toDegrees(cos(elapsedSec / 12.0) * (jitterRadius / (R * cos(newLatRad))))

        val accuracy = (jitterRadius + 2.0 + 3.0 * sin(elapsedSec / 5.0)).toFloat()
        val altitude =
            10.0 + if (stepFreqHz > 0) 0.05 * cos(elapsedSec * stepFreqHz * 2 * Math.PI) else 0.0

        return SimulatedLocation(
            currentLat,
            currentLng,
            speedMs.toFloat(),
            bearingDeg,
            accuracy,
            altitude
        )
    }

    /**
     * 计算路线上的当前位置
     * @param points 路点列表
     * @param startTimestamp 开始时间戳
     * @param simModeName 模拟模式
     * @param currentTime 当前时间戳
     */
    fun calculateRoutePosition(
        points: List<RoutePoint>,
        startTimestamp: Long,
        simModeName: String,
        currentTime: Long = System.currentTimeMillis()
    ): SimulatedLocation {
        if (points.size < 2) {
            val p = points.firstOrNull() ?: RoutePoint(0.0, 0.0)
            return SimulatedLocation(p.lat, p.lng, 0f, 0f, 5f, 0.0)
        }

        val elapsedSec = (currentTime - startTimestamp) / 1000.0
        if (elapsedSec <= 0) {
            val p = points.first()
            return SimulatedLocation(p.lat, p.lng, 0f, 0f, 5f, 0.0)
        }

        val (speedMs, stepFreqHz, jitterRadius) = getModeParams(simModeName)
        var remainingDist = speedMs * elapsedSec

        val totalDistance = (0 until points.size - 1).sumOf { i -> haversineDistance(points[i], points[i + 1]) }
        val gapDistance = haversineDistance(points.last(), points.first())
        val isLoop = gapDistance < 100.0

        val from: RoutePoint
        val to: RoutePoint
        var fraction: Double = 1.0

        if (isLoop) {
            val fullLoopDistance = totalDistance + gapDistance
            if (fullLoopDistance > 0) {
                remainingDist %= fullLoopDistance
            }
            
            var segStartIdx = 0
            while (true) {
                val isLastToFirst = (segStartIdx == points.size - 1)
                val fromPoint = points[segStartIdx]
                val toPoint = if (isLastToFirst) points.first() else points[segStartIdx + 1]
                
                val segLen = haversineDistance(fromPoint, toPoint)
                if (remainingDist <= segLen) {
                    from = fromPoint
                    to = toPoint
                    fraction = if (segLen > 0) (remainingDist / segLen).coerceIn(0.0, 1.0) else 1.0
                    break
                }
                remainingDist -= segLen
                segStartIdx++
                if (segStartIdx >= points.size) segStartIdx = 0
            }
        } else {
            var segStartIdx = 0
            while (segStartIdx < points.size - 1) {
                val segLen = haversineDistance(points[segStartIdx], points[segStartIdx + 1])
                if (remainingDist <= segLen) {
                    break
                }
                remainingDist -= segLen
                segStartIdx++
            }

            from = points[segStartIdx]
            to = if (segStartIdx < points.size - 1) points[segStartIdx + 1] else points.last()

            val segLen = haversineDistance(from, to)
            fraction = if (segLen > 0) (remainingDist / segLen).coerceIn(0.0, 1.0) else 1.0
        }

        val bearing = bearing(from, to)
        val bearingRad = Math.toRadians(bearing)
        val segLenFinal = haversineDistance(from, to)
        val interpDist = segLenFinal * fraction
        val fromLatRad = Math.toRadians(from.lat)
        val fromLngRad = Math.toRadians(from.lng)

        val newLatRad = Math.asin(
            sin(fromLatRad) * cos(interpDist / R) + cos(fromLatRad) * sin(interpDist / R) * cos(
                bearingRad
            )
        )
        val newLngRad = fromLngRad + Math.atan2(
            sin(bearingRad) * sin(interpDist / R) * cos(fromLatRad),
            cos(interpDist / R) - sin(fromLatRad) * sin(newLatRad)
        )

        var lat = Math.toDegrees(newLatRad)
        var lng = Math.toDegrees(newLngRad)

        if (stepFreqHz > 0) {
            val stepPhase = elapsedSec * stepFreqHz * 2 * Math.PI
            val stepOffsetMeters = 0.15 * sin(stepPhase)
            val perpBearingRad = bearingRad + Math.PI / 2
            lat += Math.toDegrees((stepOffsetMeters * cos(perpBearingRad)) / R)
            lng += Math.toDegrees((stepOffsetMeters * sin(perpBearingRad)) / (R * cos(newLatRad)))
        }

        lat += Math.toDegrees(sin(elapsedSec / 10.0) * (jitterRadius / R))
        lng += Math.toDegrees(cos(elapsedSec / 12.0) * (jitterRadius / (R * cos(newLatRad))))

        val accuracy = (jitterRadius + 2.0 + 3.0 * sin(elapsedSec / 5.0)).toFloat()
        val altitude =
            10.0 + if (stepFreqHz > 0) 0.05 * cos(elapsedSec * stepFreqHz * 2 * Math.PI) else 0.0

        return SimulatedLocation(lat, lng, speedMs.toFloat(), bearing.toFloat(), accuracy, altitude)
    }

    data class ModeParams(val speedMs: Double, val stepFreqHz: Double, val jitterRadius: Double)

    fun getModeParams(simModeName: String): ModeParams = when (simModeName) {
        "WALKING" -> ModeParams(1.4, 2.0, 5.0)
        "RUNNING" -> ModeParams(3.0, 3.0, 8.0)
        "CYCLING" -> ModeParams(5.5, 0.0, 3.0)
        "DRIVING" -> ModeParams(15.0, 0.0, 2.0)
        else -> ModeParams(0.0, 0.0, 2.0)
    }

    /** 计算两点间的哈弗辛距离（米） */
    private fun haversineDistance(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h =
            sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        return 2 * R * Math.atan2(sqrt(h), sqrt(1 - h))
    }

    /** 计算两点间的方位角（度） */
    private fun bearing(from: RoutePoint, to: RoutePoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }
}
