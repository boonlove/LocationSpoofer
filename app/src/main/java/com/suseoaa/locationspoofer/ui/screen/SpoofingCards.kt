package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiSearch
import androidx.compose.ui.res.stringResource
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.GithubRelease
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.data.model.AppMapType
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import androidx.compose.material.icons.rounded.Layers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithCache
import com.suseoaa.locationspoofer.data.model.RoutePlanStage
import com.suseoaa.locationspoofer.ui.theme.*


private data class StatusStyle(
    val bgColor: Color, val tint: Color, val text: String, val icon: ImageVector
)

@Composable
fun WifiStatusCard(uiState: AppState) {
    val style = when (uiState.wifiLoadStatus) {
        WifiLoadStatus.LOADING -> StatusStyle(
            AccentOrange.copy(alpha = 0.12f), AccentOrange,
            stringResource(R.string.fetching_wifi), Icons.Outlined.CloudDownload
        )

        WifiLoadStatus.DONE -> StatusStyle(
            AccentGreen.copy(alpha = 0.12f), AccentGreen,
            stringResource(R.string.wifi_ready, uiState.wifiApCount), Icons.Outlined.Wifi
        )

        else -> StatusStyle(
            AccentBlue.copy(alpha = 0.12f), AccentBlue,
            stringResource(R.string.gps_taken_over), Icons.Outlined.GpsFixed
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .drawWithCache { onDrawBehind { drawRect(style.bgColor) } }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.wifiLoadStatus == WifiLoadStatus.LOADING) {
            CircularProgressIndicator(
                color = style.tint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(style.icon, null, tint = style.tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(style.text, color = style.tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// 坐标输入卡片

@Composable
fun CoordinateInputCard(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onSaveClick: () -> Unit,
    onCustomClick: () -> Unit
) {
    val textSecondary = AppColors.textSecondary(isDark)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    Icons.Outlined.PinDrop,
                    stringResource(R.string.target_coordinates),
                    isDark
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCustomClick) {
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.custom))
                }
                TextButton(onClick = onSaveClick) {
                    Icon(Icons.Rounded.StarBorder, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.longitude)}: ${uiState.longitudeInput.ifEmpty { "0.0" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.latitude)}: ${uiState.latitudeInput.ifEmpty { "0.0" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (uiState.showCoordinateError) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.invalid_coordinates),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun coordinateFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    cursorColor = AccentBlue
)

@Composable
fun ActionButtons(
    viewModel: MainViewModel,
    uiState: AppState,
    onOpenMap: () -> Unit,
    onStartFixedSpoofing: () -> Unit
) {
    if (uiState.isSpoofingActive) {
        val stopColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.error,
            animationSpec = tween(300), label = "stop_color"
        )
        val isPlanning = uiState.routePlanStage == RoutePlanStage.RUNNING
        val onClickAction: () -> Unit = {
            if (isPlanning) {
                viewModel.stopRoutePlanning()
            } else {
                viewModel.stopSpoofing()
            }
        }
        val buttonText = stringResource(if (isPlanning) R.string.stop_planning else R.string.stop_simulation)
        Button(
            onClick = onClickAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = stopColor)
        ) {
            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                buttonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartFixedSpoofing,
                enabled = !uiState.isSavingConfig,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                if (uiState.isSavingConfig) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Starting...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.fixed_simulation),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Button(
                onClick = { viewModel.enterRoutePlanning(); onOpenMap() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Rounded.Route, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.route_planning),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun UpdateCheckCard(isDark: Boolean, onCheckClick: () -> Unit) {
    val textSecondary = AppColors.textSecondary(isDark)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.clickable { onCheckClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.SystemUpdateAlt,
                    null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.check_updates),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.check_updates_desc),
                    color = textSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String, isDark: Boolean) {
    val textSecondary = AppColors.textSecondary(isDark)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title.uppercase(),
            color = textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun SavedLocationsCard(
    savedLocations: List<SavedLocation>,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            savedLocations.forEachIndexed { index, loc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(loc) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Place,
                        null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            loc.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "${loc.lat}, ${loc.lng}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { onDelete(loc) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (index < savedLocations.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppCoordinateConfigCard(isDark: Boolean, onClick: () -> Unit) {
    SectionHeader(Icons.Rounded.Extension, androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.custom_coordinate_algo), isDark)
    Spacer(Modifier.height(8.dp))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBackground(isDark)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Extension, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.config_app_coordinate), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.config_app_coordinate_desc), color = AppColors.textSecondary(isDark), fontSize = 11.sp)
            }
            Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ScannerMapCard(isDark: Boolean, uiState: com.suseoaa.locationspoofer.data.model.AppState, onClick: () -> Unit) {
    SectionHeader(Icons.Rounded.Radar, androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.spatial_env_collection), isDark)
    Spacer(Modifier.height(8.dp))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBackground(isDark)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(AccentGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Map, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.env_map_scan), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                val statusText = if (uiState.isContinuousScanning) androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.scanning_reference_points, uiState.environmentRecordCount) else androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.view_heatmap_start_scan)
                Text(statusText, color = AppColors.textSecondary(isDark), fontSize = 11.sp)
            }
            if (uiState.isContinuousScanning) {
                Box(
                    modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AccentGreen)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ManageDataCard(isDark: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBackground(isDark)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.title_manage_data), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.manage_collected_data_desc), color = AppColors.textSecondary(isDark), fontSize = 11.sp)
            }
            Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ImportExportDataCard(isDark: Boolean, onImportClick: () -> Unit, onExportClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBackground(isDark)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(androidx.compose.material.icons.Icons.Rounded.ImportExport, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.env_data_sharing), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.env_data_sharing_desc), color = AppColors.textSecondary(isDark), fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onImportClick) {
                    Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.import_data), color = AccentBlue)
                }
                TextButton(onClick = onExportClick) {
                    Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.export_data), color = AccentBlue)
                }
            }
        }
    }
}

@Composable
fun FooterLinks(isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        
        // GitHub 图标
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF181717))
                .clickable { uriHandler.openUri("https://github.com/boonlove/LocationSpoofer") }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.suseoaa.locationspoofer.R.drawable.ic_github),
                    contentDescription = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.brand_github),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.brand_github),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Telegram 图标
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .drawWithCache { onDrawBehind { drawRect(if (isDark) Color(0xFF24A1DE).copy(alpha = 0.2f) else Color(0xFFE8F4FA)) } }
                .clickable { uriHandler.openUri("https://t.me/+CsxZGItXdW40ZWVl") }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.suseoaa.locationspoofer.R.drawable.ic_telegram),
                    contentDescription = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.brand_telegram),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.brand_telegram),
                    color = Color(0xFF24A1DE),
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SearchModeCard(
    isDark: Boolean,
    searchMode: com.suseoaa.locationspoofer.data.model.SearchMode,
    onSearchModeChange: (com.suseoaa.locationspoofer.data.model.SearchMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBackground(isDark)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Search, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("搜索源", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text("选择地理信息检索方式", color = AppColors.textSecondary(isDark), fontSize = 11.sp)
            }
            
            val isNetwork = searchMode == com.suseoaa.locationspoofer.data.model.SearchMode.NETWORK
            val activeColor = AccentBlue
            val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

            Row(modifier = Modifier.weight(1.5f), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSearchModeChange(com.suseoaa.locationspoofer.data.model.SearchMode.NETWORK) },
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isNetwork) activeColor else inactiveColor),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        "网络检索",
                        fontSize = 11.sp,
                        color = if (isNetwork) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = { onSearchModeChange(com.suseoaa.locationspoofer.data.model.SearchMode.LOCAL) },
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (!isNetwork) activeColor else inactiveColor),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        "本地采集",
                        fontSize = 11.sp,
                        color = if (!isNetwork) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
