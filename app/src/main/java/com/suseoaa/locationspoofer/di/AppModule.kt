package com.suseoaa.locationspoofer.di

import com.suseoaa.locationspoofer.data.repository.LocationRepository
import com.suseoaa.locationspoofer.data.repository.SettingsRepository
import com.suseoaa.locationspoofer.data.repository.WifiRepository
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import com.suseoaa.locationspoofer.utils.SettingsManager
import com.suseoaa.locationspoofer.utils.WigleClient
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { RootManager() }
    single { ConfigManager(get()) }
    single { LSPosedManager() }
    single { WigleClient() }
    single { SettingsManager(androidContext()) }

    single { LocationRepository(get(), get(), get()) }
    single { SettingsRepository(get()) }
    single { WifiRepository(get()) }

    viewModel { MainViewModel(get(), get(), get(), androidContext()) }
}
