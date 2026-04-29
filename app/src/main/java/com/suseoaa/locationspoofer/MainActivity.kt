package com.suseoaa.locationspoofer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.suseoaa.locationspoofer.ui.screen.BlockingScreen
import com.suseoaa.locationspoofer.ui.screen.FullScreenMapPage
import com.suseoaa.locationspoofer.ui.screen.InitializingScreen
import com.suseoaa.locationspoofer.ui.screen.SpoofingScreen
import com.suseoaa.locationspoofer.ui.theme.AppColorSchemeDark
import com.suseoaa.locationspoofer.ui.theme.AppColorSchemeLight
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) AppColorSchemeDark else AppColorSchemeLight

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel, uiState = uiState, isDark = isDark)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissions(notGranted.toTypedArray(), 100)
        } else {
            // 如果已授予位置权限，检查后台位置权限 (Android 10+)
            checkBackgroundLocation()
        }

        // 悬浮窗权限（特殊）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun checkBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Android 11+ 需要单独申请后台位置权限
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // 检查是否已授予位置权限以触发后台位置权限请求
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBackgroundLocation()
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    uiState: com.suseoaa.locationspoofer.data.model.AppState,
    isDark: Boolean
) {
    var isFullScreenMap by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = isFullScreenMap,
        transitionSpec = {
            slideInVertically(tween(400)) { it } togetherWith slideOutVertically(tween(400)) { -it }
        },
        label = "fullscreen_transition"
    ) { fullScreen ->
        if (fullScreen) {
            FullScreenMapPage(
                viewModel = viewModel,
                uiState = uiState,
                isDark = isDark,
                onClose = { isFullScreenMap = false }
            )
        } else {
            when {
                uiState.isInitializing -> InitializingScreen(isDark)
                !uiState.hasRootAccess -> BlockingScreen(
                    icon = Icons.Rounded.Lock,
                    title = "需要 Root 权限",
                    message = "本应用需要 KernelSU Root 权限才能运行。\n请在 KernelSU 中授权后重启应用。",
                    isDark = isDark
                )
                !uiState.isLSPosedActive -> BlockingScreen(
                    icon = Icons.Rounded.Extension,
                    title = "LSPosed 模块未激活",
                    message = "请在 LSPosed 管理器中启用本模块，\n勾选需要欺骗的应用后重启目标应用。",
                    isDark = isDark
                )
                else -> SpoofingScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    isDark = isDark,
                    onExpandMap = { isFullScreenMap = true }
                )
            }
        }
    }
}
