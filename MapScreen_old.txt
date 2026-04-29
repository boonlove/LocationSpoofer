package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.model.RoutePlanStage
import com.suseoaa.locationspoofer.data.model.RouteRunMode
import com.suseoaa.locationspoofer.data.model.SimMode
import com.suseoaa.locationspoofer.ui.components.AMapView
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import kotlin.math.*

// 全屏路线规划页面

@Composable
@Suppress("UNUSED_PARAMETER")
fun FullScreenMapPage(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mapRef by remember { mutableStateOf<AMap?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val stage = uiState.routePlanStage
    val isRunning = stage == RoutePlanStage.RUNNING
    val isManual = uiState.routeRunMode == RouteRunMode.MANUAL
    val routePoints = uiState.routePoints

    // 同步路点标记和折线到地图
    var liveMarker by remember { mutableStateOf<Marker?>(null) }
    LaunchedEffect(routePoints, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        map.clear()
        liveMarker = null
        if (routePoints.size >= 2) {
            map.addPolyline(
                PolylineOptions()
                    .color(android.graphics.Color.parseColor("#FF388BFD"))
                    .width(8f)
                    .apply { routePoints.forEach { add(LatLng(it.lat, it.lng)) } }
            )
        }
        routePoints.forEachIndexed { idx, p ->
            val opts = MarkerOptions().position(LatLng(p.lat, p.lng)).title("${idx + 1}")
            when (idx) {
                0 -> opts.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                routePoints.lastIndex -> opts.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }
            map.addMarker(opts)
        }
    }

    // 运行中时跟踪实时位置
    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, isRunning) {
        if (isRunning && lat != null && lng != null) {
            val pos = LatLng(lat, lng)
            mapRef?.animateCamera(CameraUpdateFactory.newLatLng(pos))
            // 更新或创建实时位置标记
            if (liveMarker != null) {
                liveMarker?.position = pos
            } else {
                liveMarker = mapRef?.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                        .title("当前位置")
                )
            }
        }
    }

    // 就绪时弹出配置弹窗
    LaunchedEffect(stage) {
        if (stage == RoutePlanStage.READY) showConfigDialog = true
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 地图
        AMapView(modifier = Modifier.fillMaxSize()) { map ->
            mapRef = map
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isCompassEnabled = false
            val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
            val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))
        }

        // 选点模式的十字准星
        if (stage == RoutePlanStage.SELECTING) {
            Icon(
                Icons.Rounded.AddLocation, null,
                tint = AccentBlue,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }

        // 手动模式运行时的定位标志
        if (isRunning && isManual) {
            Icon(
                Icons.Rounded.PersonPin, null,
                tint = AccentOrange,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            )
        }

        // 顶部栏
        TopBar(
            stage = stage,
            routePointCount = routePoints.size,
            isManual = isManual,
            onBack = onClose,
            canUndo = stage == RoutePlanStage.SELECTING && routePoints.isNotEmpty(),
            onUndo = { viewModel.undoLastRoutePoint() }
        )

        // 右侧定位按钮
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapFab(
                icon = Icons.Rounded.MyLocation,
                contentDescription = "定位到当前位置",
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AccentBlue
            ) {
                val client = AMapLocationClient(context.applicationContext)
                client.setLocationOption(AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                })
                client.setLocationListener { loc ->
                    if (loc != null && loc.errorCode == 0) {
                        viewModel.updateLatitude(String.format("%.6f", loc.latitude))
                        viewModel.updateLongitude(String.format("%.6f", loc.longitude))
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
                        }
                    } else {
                        Toast.makeText(context, "定位失败: ${loc?.errorInfo ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                    client.stopLocation(); client.onDestroy()
                }
                client.startLocation()
            }
        }

        // 摇杆（仅手动模式运行中）
        AnimatedVisibility(
            visible = isRunning && isManual,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp),
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200))
        ) {
            JoystickPanel(viewModel = viewModel, maxSpeedMs = uiState.routeSimMode.speedMs.toFloat())
        }

        // 底部操作栏
        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            stage = stage,
            routePoints = routePoints,
            onConfirmPoint = {
                mapRef?.cameraPosition?.target?.let { t ->
                    viewModel.addRoutePoint(t.latitude, t.longitude)
                }
            },
            onFinishSelecting = { viewModel.finishSelectingPoints() },
            onRestartSelecting = { viewModel.restartSelectingPoints() },
            onStartPlanning = { showConfigDialog = true },
            onStopRoute = { viewModel.stopRoutePlanning(); onClose() }
        )
    }

    // 配置弹窗
    if (showConfigDialog) {
        RoutePlanConfigDialog(
            uiState = uiState,
            onDismiss = {
                showConfigDialog = false
                // 如果还没开始，回退到 READY
                if (stage == RoutePlanStage.READY) {
                    viewModel.restartSelectingPoints()
                }
            },
            onStartRoute = {
                showConfigDialog = false
                viewModel.startRoutePlanning()
            },
            onRunModeChange = viewModel::setRouteRunMode,
            onSpeedChange = viewModel::setRouteSimMode
        )
    }
}

// 顶部栏

@Composable
private fun BoxScope.TopBar(
    stage: RoutePlanStage,
    routePointCount: Int,
    isManual: Boolean,
    onBack: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .align(Alignment.TopCenter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface).shadow(4.dp, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = when (stage) {
                    RoutePlanStage.IDLE -> "路线规划"
                    RoutePlanStage.SELECTING -> "点击确认选点添加路点（已选 $routePointCount 个）"
                    RoutePlanStage.READY -> "路线已就绪，共 $routePointCount 个路点"
                    RoutePlanStage.RUNNING -> if (isManual) "摇杆控制中" else "路线自动循环中"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        AnimatedVisibility(visible = canUndo) {
            MapFab(
                icon = Icons.AutoMirrored.Rounded.Undo,
                contentDescription = "撤销",
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                onClick = onUndo
            )
        }
    }
}

// 底部操作栏（由阶段驱动）

@Composable
private fun BottomActionBar(
    modifier: Modifier,
    stage: RoutePlanStage,
    routePoints: List<RoutePoint>,
    onConfirmPoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    onRestartSelecting: () -> Unit,
    onStartPlanning: () -> Unit,
    onStopRoute: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (stage) {
            RoutePlanStage.IDLE -> { /* 不显示 */ }

            RoutePlanStage.SELECTING -> {
                Text(
                    "已选 ${routePoints.size} 个路点${if (routePoints.size < 2) "（至少需要 2 个）" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onConfirmPoint,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Rounded.AddLocation, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("确认选点", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onFinishSelecting,
                        enabled = routePoints.size >= 2,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("结束选点", fontWeight = FontWeight.Bold)
                    }
                }
            }

            RoutePlanStage.READY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRestartSelecting,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新选点", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStartPlanning,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始规划", fontWeight = FontWeight.Bold)
                    }
                }
            }

            RoutePlanStage.RUNNING -> {
                Button(
                    onClick = onStopRoute,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("结束规划", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 路线配置对话框

@Composable
private fun RoutePlanConfigDialog(
    uiState: AppState,
    onDismiss: () -> Unit,
    onStartRoute: () -> Unit,
    onRunModeChange: (RouteRunMode) -> Unit,
    onSpeedChange: (SimMode) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "路线配置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // 模式选择
                Text(
                    "控制方式",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = uiState.routeRunMode == RouteRunMode.MANUAL,
                        onClick = { onRunModeChange(RouteRunMode.MANUAL) },
                        label = { Text("手动（摇杆）") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                            selectedLabelColor = AccentOrange
                        )
                    )
                    FilterChip(
                        selected = uiState.routeRunMode == RouteRunMode.LOOP,
                        onClick = { onRunModeChange(RouteRunMode.LOOP) },
                        label = { Text("循环（自动）") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                }

                // 循环模式速度选择
                AnimatedVisibility(uiState.routeRunMode == RouteRunMode.LOOP) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "循环路线说明",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            "从起点到终点，再从终点到起点，不断循环",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "移动速度",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SimMode.WALKING, SimMode.RUNNING,
                                SimMode.CYCLING, SimMode.DRIVING
                            ).forEach { mode ->
                                FilterChip(
                                    selected = uiState.routeSimMode == mode,
                                    onClick = { onSpeedChange(mode) },
                                    label = {
                                        Text(
                                            "${mode.label}\n${mode.speedMs.toInt()}m/s",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentGreen.copy(alpha = 0.15f),
                                        selectedLabelColor = AccentGreen
                                    )
                                )
                            }
                        }
                    }
                }

                // 手动模式说明
                AnimatedVisibility(uiState.routeRunMode == RouteRunMode.MANUAL) {
                    Text(
                        "使用屏幕摇杆控制移动方向，实时更新定位",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消") }
                    Button(
                        onClick = onStartRoute,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("开始模拟", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// 摇杆面板

@Composable
fun JoystickPanel(viewModel: MainViewModel, maxSpeedMs: Float = 10f) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 120f
    var joystickState by remember { mutableStateOf(Pair(0.0, 0f)) }

    LaunchedEffect(joystickState) {
        val (angle, intensity) = joystickState
        if (intensity > 0) {
            while (true) {
                val bearing = (Math.toDegrees(angle) + 90 + 360) % 360
                viewModel.moveByJoystick(bearing, intensity, maxSpeedMs)
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { thumbOffset = Offset.Zero; joystickState = Pair(0.0, 0f) },
                    onDragCancel = { thumbOffset = Offset.Zero; joystickState = Pair(0.0, 0f) }
                ) { change, dragAmount ->
                    change.consume()
                    val raw = thumbOffset + dragAmount
                    val dist = sqrt(raw.x * raw.x + raw.y * raw.y)
                    thumbOffset = if (dist <= maxRadius) raw else raw * (maxRadius / dist)
                    val angle = atan2(thumbOffset.y.toDouble(), thumbOffset.x.toDouble())
                    val intensity = (sqrt(thumbOffset.x * thumbOffset.x + thumbOffset.y * thumbOffset.y) / maxRadius).coerceIn(0f, 1f)
                    joystickState = Pair(angle, intensity)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.toInt(), thumbOffset.y.toInt()) }
                .size(52.dp)
                .background(AccentOrange, CircleShape)
        )
    }
}

// 地图悬浮按钮辅助

@Composable
private fun MapFab(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(44.dp),
        containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        shape = CircleShape
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(20.dp))
    }
}

// 保存名称对话框（SpoofingScreen复用）

@Composable
fun SaveNameDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
