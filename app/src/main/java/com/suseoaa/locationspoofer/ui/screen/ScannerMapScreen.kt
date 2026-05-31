package com.suseoaa.locationspoofer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radar
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
    
    // Draw heat map circles when map is ready and records count changes
    LaunchedEffect(mapController, uiState.environmentRecordCount) {
        val controller = mapController ?: return@LaunchedEffect
        val locations = viewModel.getAllLocations()
        controller.clear()
        
        // Draw circles for coverage
        val fillColor = android.graphics.Color.argb(50, 46, 204, 113) // AccentGreen with alpha
        val strokeColor = android.graphics.Color.argb(100, 46, 204, 113)
        locations.forEach { loc ->
            controller.addCircle(loc.lat, loc.lng, 20.0, fillColor, strokeColor, 2f)
        }
        
        // Move camera to latest record if exists
        if (locations.isNotEmpty()) {
            val last = locations.last()
            controller.animateCamera(last.lat, last.lng, 17f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppMapView(
            isDomestic = uiState.currentLanguage == "zh",
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
                            .background(if (uiState.isContinuousScanning) AccentGreen else AppColors.textSecondary(isDark))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.isContinuousScanning) androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.scanning_status_active, uiState.environmentRecordCount) else androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.scanning_status_inactive),
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
    }
}
