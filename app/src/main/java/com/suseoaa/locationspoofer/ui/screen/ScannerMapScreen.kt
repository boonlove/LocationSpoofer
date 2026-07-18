package com.suseoaa.locationspoofer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.data.model.AppMapType
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

@Composable
fun ScannerMapScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    var mapController by remember { mutableStateOf<AppMapController?>(null) }
    var showMapTypeDialog by remember { mutableStateOf(false) }

    // 同步地图类型
    LaunchedEffect(mapController, uiState.mapType) {
        mapController?.setMapType(uiState.mapType)
    }

    // 当地图准备就绪并且记录数量发生变化时绘制热力图圆圈
    LaunchedEffect(mapController, uiState.environmentRecordCount) {
        val controller = mapController ?: return@LaunchedEffect
        val locations = viewModel.getAllLocations()
        controller.clear()

        // 绘制覆盖范围圆圈
        com.suseoaa.locationspoofer.utils.MapCoverageHelper.drawCoverage(controller, locations)

        // 如果存在，则将相机移动到最新记录
        if (locations.isNotEmpty()) {
            val last = locations.last()
            controller.animateCamera(last.lat, last.lng, 17f)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        AppMapView(
            mapEngine = uiState.mapEngine,
            isDomestic = uiState.currentLanguage == "zh",
            isDark = isDark,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller ->
                mapController = controller
                controller.disableUiControls()
            }
        )

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Icon(Icons.Rounded.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
            }

            // Status Chip
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isContinuousScanning) AccentGreen else AppColors.textSecondary(
                                    isDark
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.isContinuousScanning) androidx.compose.ui.res.stringResource(
                            com.suseoaa.locationspoofer.R.string.scanning_status_active,
                            uiState.environmentRecordCount
                        ) else androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.scanning_status_inactive),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Bottom Action
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            FloatingActionButton(
                onClick = { viewModel.toggleContinuousScanning() },
                containerColor = if (uiState.isContinuousScanning) MaterialTheme.colorScheme.surface else AccentGreen,
                contentColor = if (uiState.isContinuousScanning) AccentGreen else Color.White
            ) {
                Icon(Icons.Rounded.Radar, null)
            }
        }

        // 右侧悬浮按钮
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { showMapTypeDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .size(44.dp)
            ) {
                Icon(Icons.Rounded.Layers, "Map Type", tint = AccentGreen)
            }
        }
    }

    if (showMapTypeDialog) {
        MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = { viewModel.setMapType(it) },
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = { viewModel.setMapEngine(it) },
            onDismiss = { showMapTypeDialog = false }
        )
    }
}
