package com.suseoaa.locationspoofer.viewmodel

import androidx.lifecycle.ViewModel
import com.suseoaa.locationspoofer.data.repository.SettingsRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    fun getBaiduStyleId(): String = settingsRepository.getBaiduStyleId()
    fun setBaiduStyleId(styleId: String) = settingsRepository.setBaiduStyleId(styleId)
}
