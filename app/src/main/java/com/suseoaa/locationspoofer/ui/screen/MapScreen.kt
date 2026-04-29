package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SavedRoute
import com.suseoaa.locationspoofer.ui.components.AMapView
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

@Composable
fun FullScreenMapPage(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<AMap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Tip>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    val textSecondary = AppColors.textSecondary(isDark)
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSaveRouteDialog by remember { mutableStateOf(false) }

    // 始终持有最新 uiState，避免 stale closure 问题
    val currentState by rememberUpdatedState(uiState)

    BackHandler { onClose() }

    // 路线路点变化时刷新地图标注和折线
    LaunchedEffect(uiState.routePoints, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        map.clear()
        if (uiState.routePoints.size >= 2) {
            val polylineOptions = PolylineOptions()
                .color(android.graphics.Color.parseColor("#FF388BFD"))
                .width(8f)
            uiState.routePoints.forEach { p -> polylineOptions.add(LatLng(p.lat, p.lng)) }
            map.addPolyline(polylineOptions)
        }
        uiState.routePoints.forEachIndexed { idx, p ->
            val markerOpts = MarkerOptions()
                .position(LatLng(p.lat, p.lng))
                .title("${idx + 1}")
                .snippet("路点 ${idx + 1}")
            when (idx) {
                0 -> markerOpts.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                uiState.routePoints.lastIndex -> markerOpts.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }
            map.addMarker(markerOpts)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 全屏地图 ──
        AMapView(modifier = Modifier.fillMaxSize()) { map ->
            mapInstance = map
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isCompassEnabled = false

            val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
            val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))

            // 使用 currentState 引用，确保回调读取最新状态
            map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition) {}
                override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition) {
                    val state = currentState
                    if (!state.isSpoofingActive && !state.isEditingRoute) {
                        viewModel.updateLongitude(String.format("%.6f", position.target.longitude))
                        viewModel.updateLatitude(String.format("%.6f", position.target.latitude))
                    }
                }
            })

            map.setOnMapClickListener { latLng ->
                val state = currentState
                if (state.isEditingRoute && !state.isSpoofingActive) {
                    viewModel.addRoutePoint(latLng.latitude, latLng.longitude)
                }
            }
        }

        // 准星（非路线编辑模式显示）
        if (!uiState.isEditingRoute) {
            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }

        // ── 顶部：返回 + 搜索 + 路线编辑切换 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) {
                            val q = InputtipsQuery(query, "").apply { cityLimit = false }
                            Inputtips(context, q).apply {
                                setInputtipsListener { tipList, rCode ->
                                    if (rCode == 1000) {
                                        suggestions = tipList?.filter { it.point != null } ?: emptyList()
                                        if (suggestions.isEmpty()) Toast.makeText(context, "未找到坐标", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "搜索失败 $rCode", Toast.LENGTH_SHORT).show()
                                        suggestions = emptyList()
                                    }
                                }
                                requestInputtipsAsyn()
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    modifier = Modifier.weight(1f).shadow(4.dp, RoundedCornerShape(24.dp)),
                    placeholder = { Text("搜索地点", color = textSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = textSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; suggestions = emptyList() }) {
                                Icon(Icons.Rounded.Clear, null, tint = textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                if (!uiState.isSpoofingActive) {
                    Spacer(Modifier.width(8.dp))
                    val editBg = if (uiState.isEditingRoute) AccentBlue else MaterialTheme.colorScheme.surface
                    val editTint = if (uiState.isEditingRoute) Color.White else MaterialTheme.colorScheme.onBackground
                    IconButton(
                        onClick = { viewModel.toggleRouteEditing() },
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(editBg)
                            .shadow(4.dp, CircleShape)
                    ) {
                        Icon(Icons.Rounded.EditLocationAlt, "路线编辑", tint = editTint)
                    }
                }
            }

            // 搜索建议列表
            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).shadow(6.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(suggestions) { tip ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        searchQuery = tip.name
                                        suggestions = emptyList()
                                        focusManager.clearFocus()
                                        mapInstance?.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(tip.point.latitude, tip.point.longitude), 16f
                                            )
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(tip.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    if (tip.district.isNotEmpty()) Text(tip.district, color = textSecondary, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // ── 右侧工具栏：路线编辑工具 + 定位按钮 ──
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = uiState.isEditingRoute,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapFab(
                        icon = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "撤销",
                        enabled = uiState.routePoints.isNotEmpty(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) { viewModel.removeLastRoutePoint() }

                    MapFab(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = "清空",
                        enabled = uiState.routePoints.isNotEmpty(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) { viewModel.clearRoute() }

                    MapFab(
                        icon = Icons.Rounded.Bookmarks,
                        contentDescription = "加载路线",
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) { viewModel.setShowRoutesDialog(true) }

                    if (uiState.routePoints.size >= 2) {
                        MapFab(
                            icon = Icons.Rounded.Save,
                            contentDescription = "保存路线",
                            containerColor = AccentGreen,
                            contentColor = Color.White
                        ) { showSaveRouteDialog = true }
                    }
                }
            }

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
                            mapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f)
                            )
                        }
                    }
                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()
            }
        }

        // ── 底部操作卡 ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    )
                )
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (uiState.isEditingRoute && uiState.routePoints.isNotEmpty()) {
                            Text("路线路点", fontSize = 12.sp, color = textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text("${uiState.routePoints.size} 个路点", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        } else {
                            Text("当前坐标", fontSize = 12.sp, color = textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text("${uiState.longitudeInput}, ${uiState.latitudeInput}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    FilledTonalButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存位置")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (uiState.routePoints.size >= 2 && !uiState.isSpoofingActive) {
                Button(
                    onClick = { viewModel.startRouteSimulation(); onClose() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Icon(Icons.Rounded.Route, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开始路线模拟", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("确定选点", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSaveDialog) {
        SaveNameDialog(
            title = "保存当前位置",
            onConfirm = { name -> viewModel.saveCurrentLocation(name); showSaveDialog = false },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showSaveRouteDialog) {
        SaveNameDialog(
            title = "保存当前路线",
            onConfirm = { name -> viewModel.saveCurrentRoute(name); showSaveRouteDialog = false },
            onDismiss = { showSaveRouteDialog = false }
        )
    }

    if (uiState.showRoutesDialog) {
        SavedRoutesDialog(
            savedRoutes = uiState.savedRoutes,
            onDismiss = { viewModel.setShowRoutesDialog(false) },
            onSelect = { route -> viewModel.loadRoute(route) },
            onDelete = { route -> viewModel.deleteRoute(route) }
        )
    }
}

// ── Map FAB（工具栏浮动按钮）────────────────────────────────────
// 明确传入 containerColor 和 contentColor，避免 Color 对象相等性判断

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

// ── Saved Routes Dialog ───────────────────────────────────────

@Composable
fun SavedRoutesDialog(
    savedRoutes: List<SavedRoute>,
    onDismiss: () -> Unit,
    onSelect: (SavedRoute) -> Unit,
    onDelete: (SavedRoute) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("保存的路线", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                if (savedRoutes.isEmpty()) {
                    Text("暂无保存的路线", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(savedRoutes) { route ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(route) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Route, null, tint = AccentBlue)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Text("${route.points.size} 个路点", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = { onDelete(route) }) {
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
