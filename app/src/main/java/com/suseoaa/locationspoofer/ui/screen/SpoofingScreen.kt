package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SavedLocation
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
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // 首页地图是否已被用户点击（控制确认选点按钮的显示）
    var mapTapped by remember { mutableStateOf(false) }

    // 拦截返回键：如果有搜索结果，按返回键先关闭搜索结果
    BackHandler(enabled = showSearchResults) {
        showSearchResults = false
    }

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
        // 头部
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

        // 搜索栏
        HomeSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {
                focusManager.clearFocus()
                if (searchQuery.isNotBlank()) {
                    performPoiSearch(context, searchQuery) { results ->
                        searchResults = results
                        showSearchResults = results.isNotEmpty()
                    }
                }
            }
        )

        // 搜索结果下拉
        AnimatedVisibility(visible = showSearchResults && searchResults.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-4).dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(searchResults.take(15)) { poi ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val p = poi.latLonPoint
                                    viewModel.updateLatitude(String.format("%.6f", p.latitude))
                                    viewModel.updateLongitude(String.format("%.6f", p.longitude))
                                    smallMapRef?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 16f)
                                    )
                                    showSearchResults = false
                                    searchQuery = poi.title ?: ""
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(poi.title ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Text(poi.snippet ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // 地图缩略图
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AMapView(modifier = Modifier.fillMaxSize()) { map ->
                smallMapRef = map
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.setAllGesturesEnabled(true)
                val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))
                // 点击地图显示确认选点按钮
                map.setOnMapClickListener { latLng ->
                    mapTapped = true
                    map.clear()
                    map.addMarker(MarkerOptions().position(latLng).title("选中位置"))
                }
            }

            // 十字准星（首次打开不显示按钮，点击地图后出现）
            if (!mapTapped) {
                Icon(
                    Icons.Rounded.AddLocationAlt, null,
                    tint = AccentBlue.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
            }

            // 确认选点按钮（点击地图后出现）
            if (mapTapped) {
                Button(
                    onClick = {
                        smallMapRef?.cameraPosition?.target?.let { t ->
                            viewModel.confirmMapPoint(t.latitude, t.longitude)
                            mapTapped = false
                            smallMapRef?.clear()
                            Toast.makeText(context, "已选定坐标", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp).height(44.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("确认选点", fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .clickable { onExpandMap() }
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

        // 滚动内容
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

            ActionButtons(viewModel, uiState, onExpandMap)
            Spacer(Modifier.height(16.dp))

            // 已保存的位置列表（显示在操作按钮下方）
            if (uiState.savedLocations.isNotEmpty()) {
                SectionHeader(Icons.Outlined.Bookmarks, "保存的位置", isDark)
                Spacer(Modifier.height(8.dp))
                SavedLocationsCard(
                    savedLocations = uiState.savedLocations,
                    onSelect = { loc ->
                        viewModel.updateLatitude(loc.lat.toString())
                        viewModel.updateLongitude(loc.lng.toString())
                    },
                    onDelete = { loc -> viewModel.removeSavedLocation(loc) }
                )
                Spacer(Modifier.height(16.dp))
            }

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


// Wi-Fi 状态卡片

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

// 坐标输入卡片

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

// 操作按钮

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
            Text("停止模拟", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
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
                onClick = { viewModel.enterRoutePlanning(); onOpenMap() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Rounded.Route, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("规划路线", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// 应用作用域卡片

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

// 章节标题

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

// 已保存位置对话框

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

// 首页搜索栏

@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(4.dp, RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("搜索地点", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        )
        Spacer(Modifier.width(10.dp))
        FilledIconButton(
            onClick = onSearch,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentBlue)
        ) {
            Icon(Icons.Rounded.Search, "搜索", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// 首页已保存位置卡片（内嵌列表，非弹窗）

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
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSelect(loc) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Place, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(loc.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text("${loc.lat}, ${loc.lng}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { onDelete(loc) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                if (index < savedLocations.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }
}

// 高德 POI 搜索

fun performPoiSearch(
    context: android.content.Context,
    keyword: String,
    onResult: (List<PoiItem>) -> Unit
) {
    try {
        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 10
        query.pageNum = 0
        val search = PoiSearch(context, query)
        search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: com.amap.api.services.poisearch.PoiResult?, rCode: Int) {
                if (rCode == 1000 && result != null) {
                    onResult(result.pois ?: emptyList())
                } else {
                    onResult(emptyList())
                }
            }
            override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
        })
        search.searchPOIAsyn()
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(emptyList())
    }
}
