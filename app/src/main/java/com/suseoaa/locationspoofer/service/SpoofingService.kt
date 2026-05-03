package com.suseoaa.locationspoofer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.SimulatedLocation
import com.suseoaa.locationspoofer.provider.SpooferProvider
import com.suseoaa.locationspoofer.utils.CoordinateUtils
import com.suseoaa.locationspoofer.utils.TrajectorySimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

class SpoofingService : Service() {

    private lateinit var locationManager: LocationManager
    private var spoofingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "SpoofingServiceChannel"

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                startSpoofing(lat, lng)
            }
            ACTION_STOP -> stopSpoofing()
        }
        return START_STICKY
    }

    private fun startSpoofing(lat: Double, lng: Double) {
        if (isRunning) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("位置模拟器")
            .setContentText("正在模拟位置: $lat, $lng")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        setupTestProvider(LocationManager.GPS_PROVIDER)
        setupTestProvider(LocationManager.NETWORK_PROVIDER)

        spoofingJob = serviceScope.launch {
            while (isActive) {
                val currentLoc = computeCurrentLocation()
                pushLocation(LocationManager.GPS_PROVIDER, currentLoc)
                pushLocation(LocationManager.NETWORK_PROVIDER, currentLoc)
                delay(1000)
            }
        }
    }

    private fun computeCurrentLocation(): SimulatedLocation {
        val routePoints = parseRoutePoints(SpooferProvider.routeJson)
        return if (SpooferProvider.isRouteMode && routePoints.size >= 2) {
            TrajectorySimulator.calculateRoutePosition(
                routePoints,
                SpooferProvider.startTimestamp,
                SpooferProvider.simMode
            )
        } else {
            TrajectorySimulator.calculateSimulatedLocation(
                SpooferProvider.latitude,
                SpooferProvider.longitude,
                SpooferProvider.startTimestamp,
                SpooferProvider.simMode,
                SpooferProvider.simBearing
            )
        }
    }

    private fun parseRoutePoints(json: String): List<RoutePoint> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RoutePoint(obj.getDouble("lat"), obj.getDouble("lng"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun stopSpoofing() {
        spoofingJob?.cancel()
        isRunning = false
        removeTestProvider(LocationManager.GPS_PROVIDER)
        removeTestProvider(LocationManager.NETWORK_PROVIDER)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupTestProvider(provider: String) {
        try {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                provider, false, false, false, false,
                true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(provider, true)
        } catch (e: Exception) {
            Log.e("SpoofingService", "设置TestProvider失败: $provider", e)
        }
    }

    private fun removeTestProvider(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            Log.e("SpoofingService", "移除TestProvider失败: $provider", e)
        }
    }

    private fun pushLocation(provider: String, loc: SimulatedLocation) {
        try {
            // SpooferProvider 中存储的是 GCJ-02（高德坐标系），
            // setTestProviderLocation 要求 WGS-84（GPS坐标系），必须转换
            val wgs84 = CoordinateUtils.gcj02ToWgs84(loc.lat, loc.lng)
            val location = Location(provider).apply {
                latitude = wgs84.lat
                longitude = wgs84.lng
                accuracy = loc.accuracy
                altitude = loc.altitude
                speed = loc.speed
                bearing = loc.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(provider, location)
        } catch (e: Exception) {
            Log.e("SpoofingService", "推送位置失败: $provider", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "位置模拟服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
