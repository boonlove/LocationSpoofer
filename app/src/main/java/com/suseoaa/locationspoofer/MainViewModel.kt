package com.suseoaa.locationspoofer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suseoaa.locationspoofer.provider.SpooferProvider
import com.suseoaa.locationspoofer.service.SpoofingService
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import com.suseoaa.locationspoofer.utils.WigleClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WifiLoadStatus { IDLE, LOADING, DONE }

data class AppState(
    // 初始化阶段（含环境检测 + Token 验证），全程转圈
    val isInitializing: Boolean = true,
    val hasRootAccess: Boolean = false,
    val isLSPosedActive: Boolean = false,
    val longitudeInput: String = "",
    val latitudeInput: String = "",
    val showCoordinateError: Boolean = false,
    val isSpoofingActive: Boolean = false,
    val wifiLoadStatus: WifiLoadStatus = WifiLoadStatus.IDLE,
    val wifiApCount: Int = 0
)

class MainViewModel(
    private val rootManager: RootManager,
    private val configManager: ConfigManager,
    private val lsposedManager: LSPosedManager,
    private val wigleClient: WigleClient,
    private val context: Context
) : ViewModel() {

    // Token 硬编码在 ViewModel 内，不暴露给 UI
    private val wigleToken =
        "QUlEODRhYjYwNzVjYjI4MTY5ZDU4Yjk2NzQxM2ZiYTFiMDA6YmY2NWE5M2RiYWQ1YzYwNmYwNzdkOTQ2NjE2NmI4MzM="

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    /**
     * 启动时串行执行：环境检测 → Token 验证
     * 两步全部完成后，isInitializing 才变 false，转圈停止
     */
    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 环境检测
            val root = rootManager.checkRootAccess()
            val lsposed = lsposedManager.isModuleActive()

            // 2. Token 验证（失败也继续，只是 Wi-Fi 会降级到 Fallback）
            wigleClient.validateToken(wigleToken) // 结果仅内部使用，不影响 UI 状态

            _uiState.update {
                it.copy(
                    isInitializing = false,
                    hasRootAccess = root,
                    isLSPosedActive = lsposed,
                    isSpoofingActive = SpoofingService.isRunning
                )
            }
        }
    }

    fun updateLongitude(value: String) {
        if (isValidInput(value)) _uiState.update {
            it.copy(
                longitudeInput = value,
                showCoordinateError = false
            )
        }
    }

    fun updateLatitude(value: String) {
        if (isValidInput(value)) _uiState.update {
            it.copy(
                latitudeInput = value,
                showCoordinateError = false
            )
        }
    }

    private fun isValidInput(value: String): Boolean {
        if (value.isEmpty() || value == "-") return true
        return value.toDoubleOrNull() != null
    }

    fun startSpoofing() {
        val lng = _uiState.value.longitudeInput.toDoubleOrNull()
        val lat = _uiState.value.latitudeInput.toDoubleOrNull()

        if (lng == null || lat == null || lng !in -180.0..180.0 || lat !in -90.0..90.0) {
            _uiState.update { it.copy(showCoordinateError = true) }
            return
        }

        viewModelScope.launch {
            // ── 第一步：立即激活 GPS 欺骗（同步，不等 Wi-Fi）──
            SpooferProvider.isActive = true
            SpooferProvider.latitude = lat
            SpooferProvider.longitude = lng
            SpooferProvider.wifiJson = "[]"
            configManager.saveConfig(lat, lng, true)

            val granted = rootManager.grantMockLocation()
            if (granted) {
                context.startForegroundService(
                    Intent(context, SpoofingService::class.java).apply {
                        action = SpoofingService.ACTION_START
                        putExtra(SpoofingService.EXTRA_LAT, lat)
                        putExtra(SpoofingService.EXTRA_LNG, lng)
                    }
                )
            }
            _uiState.update {
                it.copy(
                    isSpoofingActive = true,
                    wifiLoadStatus = WifiLoadStatus.LOADING,
                    wifiApCount = 0
                )
            }

            // ── 第二步：后台异步拉取当地 Wi-Fi 指纹 ──
            val wifiJson = wigleClient.fetchWifiData(lat, lng, wigleToken)
            val apCount = try {
                org.json.JSONArray(wifiJson).length()
            } catch (e: Exception) {
                0
            }
            SpooferProvider.wifiJson = wifiJson
            _uiState.update { it.copy(wifiLoadStatus = WifiLoadStatus.DONE, wifiApCount = apCount) }
        }
    }

    fun stopSpoofing() {
        viewModelScope.launch {
            SpooferProvider.isActive = false
            SpooferProvider.wifiJson = "[]"
            configManager.saveConfig(0.0, 0.0, false)
            context.startService(Intent(context, SpoofingService::class.java).apply {
                action = SpoofingService.ACTION_STOP
            })
            rootManager.revokeMockLocation()
            _uiState.update {
                it.copy(
                    isSpoofingActive = false,
                    wifiLoadStatus = WifiLoadStatus.IDLE,
                    wifiApCount = 0
                )
            }
        }
    }
}
