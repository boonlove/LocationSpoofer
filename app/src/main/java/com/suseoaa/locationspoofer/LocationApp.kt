package com.suseoaa.locationspoofer

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.google.android.libraries.places.api.Places
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
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        val customApiKey = prefs.getString("amap_api_key", "")
        if (!customApiKey.isNullOrEmpty()) {
            MapsInitializer.setApiKey(customApiKey)
            AMapLocationClient.setApiKey(customApiKey)
            ServiceSettings.getInstance().setApiKey(customApiKey)
        }

        val baiduApiKey = prefs.getString("baidu_api_key", "")
        if (!baiduApiKey.isNullOrEmpty()) {
            com.baidu.mapapi.SDKInitializer.setApiKey(baiduApiKey)
            com.baidu.location.LocationClient.setKey(baiduApiKey)
        }

        try {
            com.baidu.mapapi.SDKInitializer.setAgreePrivacy(this, true)
            com.baidu.location.LocationClient.setAgreePrivacy(true)
            com.baidu.mapapi.SDKInitializer.setCoordType(com.baidu.mapapi.CoordType.GCJ02)
            com.baidu.mapapi.SDKInitializer.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val googleApiKey = prefs.getString("google_api_key", "")
        if (!Places.isInitialized() && (!googleApiKey.isNullOrEmpty() || BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank())) {
            val keyToUse =
                if (!googleApiKey.isNullOrEmpty()) googleApiKey else BuildConfig.GOOGLE_MAPS_API_KEY
            Places.initialize(this, keyToUse)
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
