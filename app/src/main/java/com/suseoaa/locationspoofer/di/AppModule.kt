package com.suseoaa.locationspoofer.di

import com.suseoaa.locationspoofer.data.repository.LocationRepository
import com.suseoaa.locationspoofer.data.repository.SettingsRepository
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import com.suseoaa.locationspoofer.utils.SettingsManager
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { RootManager() }
    single { ConfigManager(get()) }
    single { LSPosedManager() }
    single { SettingsManager(androidContext()) }
    single { com.suseoaa.locationspoofer.utils.EnvironmentScanner(androidContext()) }

    single { LocationRepository(get(), get(), get(), get(), get()) }
    single { SettingsRepository(get()) }

    single { com.suseoaa.locationspoofer.data.db.AppDatabase.getDatabase(androidContext()) }
    single { get<com.suseoaa.locationspoofer.data.db.AppDatabase>().environmentDao() }
    single { get<com.suseoaa.locationspoofer.data.db.AppDatabase>().savedRouteDao() }

    viewModel { MainViewModel(get(), get(), get(), get(), get(), androidContext()) }
    viewModel { com.suseoaa.locationspoofer.viewmodel.UpdateViewModel(androidContext()) }
}
