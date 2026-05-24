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

class LocationApp : Application() {
    override fun onCreate() {
        super.onCreate()

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

        if (!Places.isInitialized()) {
            Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY)
        }

        startKoin {
            androidLogger()
            androidContext(this@LocationApp)
            modules(appModule)
        }
    }
}
