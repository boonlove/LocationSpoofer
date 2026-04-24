package com.suseoaa.locationspoofer

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.suseoaa.locationspoofer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LocationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        
        startKoin {
            androidLogger()
            androidContext(this@LocationApp)
            modules(appModule)
        }
    }
}
