package com.suseoaa.locationspoofer

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.google.android.libraries.places.api.Places
import com.suseoaa.locationspoofer.data.model.AppMapProvider
import com.suseoaa.locationspoofer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationApp : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private val _isModuleActive = MutableStateFlow(false)
        val isModuleActive: StateFlow<Boolean> = _isModuleActive
        var mService: XposedService? = null
    }

    override fun onCreate() {
        super.onCreate()

        try {
            XposedServiceHelper.registerListener(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // 高德定位 SDK 隐私声明（定位功能始终需要）
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        // 高德地图 SDK
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        val customApiKey = prefs.getString("amap_api_key", "")
        if (!customApiKey.isNullOrEmpty()) {
            MapsInitializer.setApiKey(customApiKey)
            ServiceSettings.getInstance().setApiKey(customApiKey)

            // 高德定位 API Key（定位功能始终需要）
            AMapLocationClient.setApiKey(customApiKey)
        }

        // 谷歌地图 SDK
        if (!Places.isInitialized() && BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()) {
            Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY)
        }

        startKoin {
            androidLogger()
            androidContext(this@LocationApp)
            modules(appModule)
        }
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        _isModuleActive.value = true
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        _isModuleActive.value = false
    }
}
