package com.suseoaa.locationspoofer.di

import com.suseoaa.locationspoofer.MainViewModel
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import com.suseoaa.locationspoofer.utils.SettingsManager
import com.suseoaa.locationspoofer.utils.WigleClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { RootManager() }
    single { ConfigManager(get()) }
    single { LSPosedManager() }
    single { WigleClient() }
    single { SettingsManager(androidContext()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get(), androidContext()) }
}
