package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.SimMode
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.ui.components.AMapView
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

data class RecommendedApp(val name: String, val packageName: String, val icon: ImageVector)

val RECOMMENDED_APPS = listOf(
    RecommendedApp("微信", "com.tencent.mm", Icons.AutoMirrored.Outlined.Chat),
    RecommendedApp("超星学习通", "com.chaoxing.mobile", Icons.Outlined.School),
    RecommendedApp("高德地图", "com.autonavi.minimap", Icons.Outlined.Map),
    RecommendedApp("百度地图", "com.baidu.BaiduMap", Icons.Outlined.Map),
    RecommendedApp("腾讯地图", "com.tencent.map", Icons.Outlined.Map),
    RecommendedApp("美团", "com.sankuai.meituan", Icons.Outlined.LocalDining),
    RecommendedApp("钉钉", "com.alibaba.android.rimet", Icons.Outlined.Work),
    RecommendedApp("Google 服务", "com.google.android.gms", Icons.Outlined.Android),
)

@Composable
fun SpoofingScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onExpandMap: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showSavedLocations by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val topBarBg = AppColors.surface(isDark)

    // 请求当前位置（仅在坐标为空时）
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.fetchCurrentLocation(context)
        }
    }

    // 小地图实例，用于响应坐标更新
    var smallMapRef by remember { mutableStateOf<AMap?>(null) }
    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, smallMapRef) {
        if (lat != null && lng != null) {
            smallMapRef?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBg)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MyLocation, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "LocationSpoofer",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSavedLocations = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Bookmarks, "保存的位置",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

        // ── 地图缩略图 ──
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AMapView(modifier = Modifier.fillMaxSize()) { map ->
                smallMapRef = map
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.setAllGesturesEnabled(false)
                // 初始相机位置（后续由 LaunchedEffect 响应坐标变化更新）
                val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))
            }

            Box(modifier = Modifier.fillMaxSize().clickable { onExpandMap() })

            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fullscreen, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全屏选点", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
            )
        }

        // ── 滚动内容 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(4.dp))

            if (uiState.isSpoofingActive) {
                WifiStatusCard(uiState)
                Spacer(Modifier.height(12.dp))
            }

            CoordinateInputCard(viewModel, uiState, isDark) { showSaveDialog = true }
            Spacer(Modifier.height(12.dp))

            SimulationSettingsCard(viewModel, uiState, isDark)
            Spacer(Modifier.height(12.dp))

            ActionButtons(viewModel, uiState, onExpandMap)
            Spacer(Modifier.height(20.dp))

            SectionHeader(Icons.Outlined.AppRegistration, "LSPosed 作用域", isDark)
            Spacer(Modifier.height(8.dp))
            AppScopeCard(isDark)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSaveDialog) {
        SaveNameDialog(
            title = "保存当前位置",
            onConfirm = { name ->
                viewModel.saveCurrentLocation(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showSavedLocations) {
        SavedLocationsDialog(
            savedLocations = uiState.savedLocations,
            onDismiss = { showSavedLocations = false },
            onSelect = { loc ->
                viewModel.updateLatitude(loc.lat.toString())
                viewModel.updateLongitude(loc.lng.toString())
                showSavedLocations = false
            },
            onDelete = { loc -> viewModel.removeSavedLocation(loc) }
        )
    }
}


// ── Wifi Status Card ──────────────────────────────────────────

private data class StatusStyle(
    val bgColor: Color, val tint: Color, val text: String, val icon: ImageVector
)

@Composable
fun WifiStatusCard(uiState: AppState) {
    val style = when (uiState.wifiLoadStatus) {
        WifiLoadStatus.LOADING -> StatusStyle(
            AccentOrange.copy(alpha = 0.12f), AccentOrange,
            "正在拉取当地 Wi-Fi 指纹数据...", Icons.Outlined.CloudDownload
        )
        WifiLoadStatus.DONE -> StatusStyle(
            AccentGreen.copy(alpha = 0.12f), AccentGreen,
            "Wi-Fi 指纹已就绪（${uiState.wifiApCount} 个热点）", Icons.Outlined.Wifi
        )
        else -> StatusStyle(
            AccentBlue.copy(alpha = 0.12f), AccentBlue,
            "GPS 定位已接管", Icons.Outlined.GpsFixed
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(style.bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.wifiLoadStatus == WifiLoadStatus.LOADING) {
            CircularProgressIndicator(color = style.tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(style.icon, null, tint = style.tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(style.text, color = style.tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Coordinate Input Card ─────────────────────────────────────

@Composable
fun CoordinateInputCard(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onSaveClick: () -> Unit
) {
    val textSecondary = AppColors.textSecondary(isDark)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(Icons.Outlined.PinDrop, "目标坐标", isDark)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSaveClick) {
                    Icon(Icons.Rounded.StarBorder, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = uiState.longitudeInput,
                onValueChange = { viewModel.updateLongitude(it) },
                label = { Text("经度") },
                placeholder = { Text("点击上方地图进入全屏选点", color = textSecondary) },
                leadingIcon = { Icon(Icons.Outlined.East, null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.showCoordinateError,
                enabled = !uiState.isSpoofingActive,
                singleLine = true,
                colors = coordinateFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.latitudeInput,
                onValueChange = { viewModel.updateLatitude(it) },
                label = { Text("纬度") },
                placeholder = { Text("点击上方地图进入全屏选点", color = textSecondary) },
                leadingIcon = { Icon(Icons.Outlined.North, null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.showCoordinateError,
                enabled = !uiState.isSpoofingActive,
                singleLine = true,
                colors = coordinateFieldColors()
            )

            if (uiState.showCoordinateError) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("经纬度数值超出合法范围", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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

// ── Simulation Settings Card ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationSettingsCard(viewModel: MainViewModel, uiState: AppState, isDark: Boolean) {
    val textSecondary = AppColors.textSecondary(isDark)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(Icons.AutoMirrored.Outlined.DirectionsWalk, "轨迹模拟 (速度/步频/抖动)", isDark)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SimMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.simMode == mode,
                        onClick = { viewModel.updateSimMode(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                }
            }

            // 路线模式下方向由路线决定，隐藏方向滑块
            AnimatedVisibility(visible = uiState.simMode != SimMode.STILL && !uiState.isRouteMode) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text("移动方向 (角度: ${uiState.simBearing.toInt()}°)", fontSize = 12.sp, color = textSecondary)
                    Slider(
                        value = uiState.simBearing,
                        onValueChange = { viewModel.updateSimBearing(it) },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentBlue,
                            activeTrackColor = AccentBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Action Buttons ────────────────────────────────────────────

@Composable
fun ActionButtons(viewModel: MainViewModel, uiState: AppState, onOpenMap: () -> Unit) {
    if (uiState.isSpoofingActive) {
        val stopColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.error,
            animationSpec = tween(300), label = "stop_color"
        )
        Button(
            onClick = { viewModel.stopSpoofing() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = stopColor)
        ) {
            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (uiState.isRouteMode) "停止路线模拟" else "停止模拟并恢复系统定位",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        val hasRoute = uiState.routePoints.size >= 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.startSpoofing() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("定点模拟", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    if (hasRoute) viewModel.startRouteSimulation()
                    else { viewModel.setEditingRoute(true); onOpenMap() }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(
                    if (hasRoute) Icons.Rounded.Route else Icons.Rounded.EditLocationAlt,
                    null, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (hasRoute) "路线模拟" else "规划路线",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (uiState.routePoints.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val tagColor = if (hasRoute) AccentGreen else AccentOrange
                Icon(Icons.Outlined.CheckCircle, null, tint = tagColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "已规划 ${uiState.routePoints.size} 个路点",
                    color = tagColor, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.setEditingRoute(true); onOpenMap() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("编辑", fontSize = 12.sp) }
                TextButton(
                    onClick = { viewModel.clearRoute() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("清除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
    }
}

// ── App Scope Card ────────────────────────────────────────────

@Composable
fun AppScopeCard(isDark: Boolean) {
    val textSecondary = AppColors.textSecondary(isDark)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            RECOMMENDED_APPS.forEachIndexed { index, app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(app.icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(app.packageName, color = textSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                }
                if (index < RECOMMENDED_APPS.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// ── Section Header ────────────────────────────────────────────

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

// ── Saved Locations Dialog ────────────────────────────────────

@Composable
fun SavedLocationsDialog(
    savedLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("保存的位置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                if (savedLocations.isEmpty()) {
                    Text("暂无保存的位置", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(savedLocations) { loc ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(loc) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = AccentBlue)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(loc.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Text("${loc.lat}, ${loc.lng}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = { onDelete(loc) }) {
                                    Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
            }
        }
    }
}

// ── Save Name Dialog ──────────────────────────────────────────

@Composable
fun SaveNameDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
