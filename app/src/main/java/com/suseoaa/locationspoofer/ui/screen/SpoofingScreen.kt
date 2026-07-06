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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
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
import com.suseoaa.locationspoofer.ui.components.BottomSheetValue
import com.suseoaa.locationspoofer.ui.components.DraggableBottomSheet
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.rememberBottomSheetState
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.ui.draw.drawWithCache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.BuildConfig
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.data.model.RoutePlanStage
import com.suseoaa.locationspoofer.ui.extensions.activeEngine

data class AppPoiItem(val title: String, val snippet: String, val lat: Double, val lng: Double)

data class RecommendedApp(val nameRes: Int, val packageName: String, val icon: ImageVector)

val RECOMMENDED_APPS = listOf(
    RecommendedApp(R.string.app_wechat, "com.tencent.mm", Icons.AutoMirrored.Outlined.Chat),
    RecommendedApp(R.string.app_chaoxing, "com.chaoxing.mobile", Icons.Outlined.School),
    RecommendedApp(R.string.app_amap, "com.autonavi.minimap", Icons.Outlined.Map),
    RecommendedApp(R.string.app_baidumap, "com.baidu.BaiduMap", Icons.Outlined.Map),
    RecommendedApp(R.string.app_tencentmap, "com.tencent.map", Icons.Outlined.Map),
    RecommendedApp(R.string.app_meituan, "com.sankuai.meituan", Icons.Outlined.LocalDining),
    RecommendedApp(R.string.app_dingtalk, "com.alibaba.android.rimet", Icons.Outlined.Work),
    RecommendedApp(R.string.app_google, "com.google.android.gms", Icons.Outlined.Android),
    RecommendedApp(R.string.app_settings, "com.android.settings", Icons.Outlined.Settings),
)

@Composable
fun SpoofingScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onExpandMap: () -> Unit,
    onExpandScannerMap: () -> Unit,
    onExpandSettings: () -> Unit,
    initialSheetValue: BottomSheetValue = BottomSheetValue.HALF,
    onSheetValueChanged: (BottomSheetValue) -> Unit = {},
    scrollState: ScrollState = ScrollState(0),
    updateViewModel: com.suseoaa.locationspoofer.viewmodel.UpdateViewModel = org.koin.androidx.compose.koinViewModel()
) {
    var showSavedLocations by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showAppCoordinateScreen by remember { mutableStateOf(false) }
    var showEnvironmentDialog by remember { mutableStateOf(false) }
    var showStartSpoofingDialog by remember { mutableStateOf(false) }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { 
            viewModel.exportEnvironmentData(it)
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            viewModel.importEnvironmentData(it) {
                Toast.makeText(context, "导入合并成功", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showCustomCoordDialog by remember { mutableStateOf(false) }
    val updateUiState by updateViewModel.uiState.collectAsState()
    val topBarBg = AppColors.topBarBackground(isDark)
    val isDomestic = viewModel.isDomesticEnvironment()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppPoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var showApiKeyWarning by remember { mutableStateOf(false) }

    var hasAutoCheckedUpdates by remember { mutableStateOf(false) }

    val activeEngine = uiState.mapEngine.activeEngine(isDomestic)

    LaunchedEffect(Unit) {
        if (!hasAutoCheckedUpdates) {
            updateViewModel.fetchReleases()
        }
    }

    LaunchedEffect(updateUiState.releases, updateUiState.isLoading) {
        if (!hasAutoCheckedUpdates && !updateUiState.isLoading && updateUiState.releases.isNotEmpty()) {
            val latestRelease = updateUiState.releases.firstOrNull()
            if (latestRelease != null) {
                val latestVersion = latestRelease.versionName
                val currentVersion = BuildConfig.VERSION_NAME
                
                fun isNewer(current: String, latest: String): Boolean {
                    try {
                        val curClean = current.trim().removePrefix("v").removePrefix("V")
                        val latClean = latest.trim().removePrefix("v").removePrefix("V")
                        val curParts = curClean.split(".")
                        val latParts = latClean.split(".")
                        val maxLen = maxOf(curParts.size, latParts.size)
                        for (i in 0 until maxLen) {
                            val curVal = curParts.getOrNull(i)?.toIntOrNull() ?: 0
                            val latVal = latParts.getOrNull(i)?.toIntOrNull() ?: 0
                            if (latVal > curVal) return true
                            if (curVal > latVal) return false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return false
                }

                val ignoredVersion = viewModel.getIgnoredVersion()
                if (isNewer(currentVersion, latestVersion) && latestVersion != ignoredVersion) {
                    showUpdateDialog = true
                }
            }
            hasAutoCheckedUpdates = true
        }
    }

    // 拦截返回键：如果有搜索结果，按返回键先关闭搜索结果
    BackHandler(enabled = showSearchResults) {
        showSearchResults = false
    }

    // 请求当前位置（仅在坐标为空时），并确保本地采集数据已加载
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.fetchCurrentLocation(context)
        }
        // 强制在界面展示时再加载一次数据，防止因启动时序导致的数据未及时注入
        viewModel.loadManageData()
    }

    // 小地图实例，用于响应坐标更新
    var smallMapRef by remember { mutableStateOf<AppMapController?>(null) }
    // 标记坐标更新是否来自地图手势(拖拽/滑动)，若是则不触发 animateCamera 避免打断惯性动画
    var fromMapGesture by remember { mutableStateOf(false) }
    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, smallMapRef) {
        if (lat != null && lng != null) {
            if (fromMapGesture) {
                fromMapGesture = false // 手势引发的坐标更新，不做 animateCamera 以免打断惯性
            } else {
                smallMapRef?.animateCamera(lat, lng)
            }
        }
    }

    LaunchedEffect(smallMapRef, uiState.mapType) {
        smallMapRef?.setMapType(uiState.mapType)
    }

    LaunchedEffect(smallMapRef, uiState.manageDataList) {
        val map = smallMapRef ?: return@LaunchedEffect
        map.clear()
        val locations = uiState.manageDataList.map { it.location }
        com.suseoaa.locationspoofer.utils.MapCoverageHelper.drawCoverage(map, locations)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val expandedFraction = 0.70f
        val halfFraction = 0.35f
        val expandedHeightPx = screenHeightPx * expandedFraction
        val halfHeightPx = screenHeightPx * halfFraction

        val initialAnchors = remember(expandedHeightPx, halfHeightPx) {
            DraggableAnchors {
                BottomSheetValue.EXPANDED at 0f
                BottomSheetValue.HALF at (expandedHeightPx - halfHeightPx)
            }
        }
        // 用外部传入的初始值创建 sheetState，使页面切换返回后能恢复到原状态
        val sheetState = rememberBottomSheetState(initialSheetValue, initialAnchors)
        // 同步 currentValue 到外部，由 MainScreen 持久化（不受 AnimatedContent 切换影响）
        LaunchedEffect(sheetState.currentValue) {
            onSheetValueChanged(sheetState.currentValue)
        }

        // 全屏地图背景
        AppMapView(
            mapEngine = uiState.mapEngine,
            isDomestic = isDomestic,
            isDark = isDark,
            modifier = Modifier.fillMaxSize()
        ) { map ->
            smallMapRef = map
            map.disableUiControls()
            val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
            val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
            map.moveCamera(initLat, initLng, 15f)

            // 移动地图即选点
            map.setOnCameraChangeListener { lat, lng ->
                fromMapGesture = true
                viewModel.confirmMapPoint(lat, lng)
            }
        }

        // 十字准星（始终显示在中间）
        Icon(
            Icons.Rounded.AddLocationAlt, null,
            tint = AccentBlue.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.Center).size(32.dp).padding(bottom = 16.dp) // 准星底部对齐中心
        )

        // 浮动按钮组（全屏/图层/定位），竖向排列，避让 Sheet 可见高度
        // EXPANDED 时隐藏（Sheet 占半屏，按钮区域被遮挡）
        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
                .offset {
                    val o = sheetState.offset
                    val visible = if (o.isNaN()) expandedHeightPx else (expandedHeightPx - o)
                    IntOffset(0, -(visible + 16.dp.toPx()).roundToInt())
                }
        ) {
            AnimatedVisibility(visible = !sheetState.isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { onExpandMap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Fullscreen, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { showMapTypeDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Layers, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { viewModel.fetchCurrentLocation(context) { _, _ -> } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MyLocation, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Top UI：头部 + 搜索栏 + 搜索结果，渐变背景保证在地图上可读
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Transparent)
        ) {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(topBarBg.copy(alpha = 0.5f))
                    .padding(horizontal = 20.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Icon(Icons.Rounded.MyLocation, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    Icon(
                        painter = painterResource(R.mipmap.icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSavedLocations = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Bookmarks, stringResource(R.string.collection_list),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onExpandSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Settings, stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))

            // 搜索栏
            HomeSearchBar(
                query = searchQuery,
                searchMode = uiState.searchMode,
                onSearchModeChange = { mode ->
                    viewModel.setSearchMode(mode)
                    if (mode == com.suseoaa.locationspoofer.data.model.SearchMode.LOCAL) {
                        focusManager.clearFocus()
                        // Perform local search immediately
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            val results = viewModel.performLocalSearch()
                            searchResults = results
                            showSearchResults = results.isNotEmpty()
                        }
                    } else {
                        searchResults = emptyList()
                        showSearchResults = false
                    }
                },
                onQueryChange = { searchQuery = it },
                onSearch = {
                    focusManager.clearFocus()
                    if (uiState.searchMode == com.suseoaa.locationspoofer.data.model.SearchMode.LOCAL) {
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            val results = viewModel.performLocalSearch()
                            searchResults = results
                            showSearchResults = results.isNotEmpty()
                        }
                    } else if ( (uiState.amapApiKey.isBlank() && activeEngine == MapEngine.AMAP) ||
                        (uiState.baiduApiKey.isBlank() && activeEngine == MapEngine.BAIDU) ||
                        (uiState.googleApiKey.isBlank() && activeEngine == MapEngine.GOOGLE)
                    ) {
                        showApiKeyWarning = true
                    } else if (searchQuery.isNotBlank()) {
                        performPoiSearch(context, activeEngine, searchQuery, isDomestic) { results ->
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(searchResults.take(15)) { poi ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        val pLat = poi.lat
                                        val pLng = poi.lng
                                        viewModel.updateLatitude(String.format("%.6f", pLat))
                                        viewModel.updateLongitude(String.format("%.6f", pLng))
                                        smallMapRef?.animateCamera(pLat, pLng, 16f)
                                        showSearchResults = false
                                        searchQuery = poi.title
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(poi.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Text(poi.snippet, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // BottomSheet
        DraggableBottomSheet(
            sheetState = sheetState,
            modifier = Modifier.fillMaxSize(),
            scrimColor = MaterialTheme.colorScheme.background.copy(0.8f),
            expandedFraction = expandedFraction,
            halfFraction = halfFraction,
            scrollState = scrollState,
            collapsedContent = {
                AnimatedVisibility (uiState.isSpoofingActive) {
                    Column {
                        WifiStatusCard(uiState)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                CoordinateInputCard(
                    viewModel = viewModel,
                    uiState = uiState,
                    isDark = isDark,
                    onSaveClick = { showSaveDialog = true },
                    onCustomClick = { showCustomCoordDialog = true }
                )
            }
        ) {
            Spacer(Modifier.height(12.dp))

            ActionButtons(viewModel, uiState, onExpandMap, onStartFixedSpoofing = { showStartSpoofingDialog = true })
            Spacer(Modifier.height(16.dp))

            // 已保存的位置列表（显示在操作按钮下方）
            if (uiState.savedLocations.isNotEmpty()) {
                SectionHeader(Icons.Outlined.Bookmarks, stringResource(R.string.collection_list), isDark)
                Spacer(Modifier.height(8.dp))
                SavedLocationsCard(
                    savedLocations = uiState.savedLocations,
                    onSelect = { loc ->
                        viewModel.loadSavedLocation(loc)
                    },
                    onDelete = { loc -> viewModel.removeSavedLocation(loc) }
                )
                Spacer(Modifier.height(16.dp))
            }

            SectionHeader(Icons.Rounded.Extension, stringResource(R.string.custom_coordinate_algo), isDark)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.clickable { showAppCoordinateScreen = true }
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
                        Text(stringResource(R.string.config_app_coordinate), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.config_app_coordinate_desc), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))


            SectionHeader(Icons.Rounded.Radar, stringResource(R.string.spatial_env_collection), isDark)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.clickable { 
                    onExpandScannerMap()
                }
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
                        Text(stringResource(R.string.env_map_scan), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        val statusText = if (uiState.isContinuousScanning) stringResource(R.string.scanning_reference_points, uiState.environmentRecordCount) else stringResource(R.string.view_heatmap_start_scan)
                        Text(statusText, color = AppColors.textSecondary(isDark), fontSize = 11.sp)
                    }
                    if (uiState.isContinuousScanning) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AccentGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.clickable { 
                    viewModel.toggleManageDataScreen(true)
                }
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
                        Text(stringResource(R.string.title_manage_data), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.manage_collected_data_desc), color = AppColors.textSecondary(isDark), fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.textSecondary(isDark), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Icon(Icons.Rounded.ImportExport, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.env_data_sharing), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.env_data_sharing_desc), color = AppColors.textSecondary(isDark), fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                            Text(stringResource(R.string.import_data), color = AccentBlue)
                        }
                        TextButton(onClick = { exportLauncher.launch("environment_data.json") }) {
                            Text(stringResource(R.string.export_data), color = AccentBlue)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 检查更新卡片
            SectionHeader(Icons.Outlined.SystemUpdateAlt, stringResource(R.string.check_updates), isDark)
            Spacer(Modifier.height(8.dp))
            UpdateCheckCard(isDark, onCheckClick = {
                updateViewModel.fetchReleases()
                showUpdateDialog = true
            })
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                
                // GitHub Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF181717)) // Official GitHub brand color background
                        .clickable { uriHandler.openUri("https://github.com/boonlove/LocationSpoofer") }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                            contentDescription = stringResource(R.string.brand_github),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.brand_github),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                // Telegram Button
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
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_telegram),
                            contentDescription = stringResource(R.string.brand_telegram),
                            tint = Color.Unspecified, // Use natural brand color
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.brand_telegram),
                            color = Color(0xFF24A1DE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showApiKeyWarning) {
        ApiKeyWarningDialog(isDomestic ,uiState.mapEngine) { showApiKeyWarning = false }
    }

    if (showSaveDialog) {
        SaveNameDialog(
            title = stringResource(R.string.save_current_location),
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
                viewModel.loadSavedLocation(loc)
                showSavedLocations = false
            },
            onDelete = { loc -> viewModel.removeSavedLocation(loc) }
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            uiState = updateUiState,
            onDismiss = { showUpdateDialog = false },
            onDownload = { url, version -> updateViewModel.startDownload(url, version) },
            onCancel = { updateViewModel.cancelDownload() },
            onInstall = { updateViewModel.installApk() },
            onIgnore = { version ->
                viewModel.setIgnoredVersion(version)
                showUpdateDialog = false
            }
        )
    }

    
    if (showAppCoordinateScreen) {
        LocalizedDialog(
            onDismissRequest = { showAppCoordinateScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppCoordinateScreen(
                viewModel = viewModel,
                uiState = uiState,
                onBack = { showAppCoordinateScreen = false }
            )
        }
    }


    if (showStartSpoofingDialog) {
        StartSpoofingDialog(
            uiState = uiState,
            isDark = isDark,
            onDismiss = { showStartSpoofingDialog = false },
            onConfirm = {
                viewModel.startSpoofing()
                showStartSpoofingDialog = false
            },
            onToggleWifi = { viewModel.toggleMockWifi() },
            onToggleCell = { viewModel.toggleMockCell() },
            onToggleBluetooth = { viewModel.toggleMockBluetooth() },
            onToggleJitter = { viewModel.toggleEnableJitter() },
            onAltitudeChange = { viewModel.setAltitude(it) },
            onSatelliteCountChange = { viewModel.setSatelliteCount(it) }
        )
    }

    if (showCustomCoordDialog) {
        CustomCoordinateDialog(
            initialLat = uiState.latitudeInput,
            initialLng = uiState.longitudeInput,
            isDark = isDark,
            onDismiss = { showCustomCoordDialog = false },
            onConfirm = { lat, lng ->
                viewModel.updateLatitude(lat)
                viewModel.updateLongitude(lng)
                showCustomCoordDialog = false
            }
        )
    }

    if (showEnvironmentDialog) {
        LocalizedDialog(onDismissRequest = { showEnvironmentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.local_env_data), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(16.dp))
                    
                    if (uiState.wifiLoadStatus == com.suseoaa.locationspoofer.data.model.WifiLoadStatus.LOADING) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item {
                                Text(stringResource(R.string.wifi_hotspots, uiState.wifiApCount), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            val wifiObj = try { org.json.JSONObject(uiState.collectedWifiJson) } catch (e: Exception) { null }
                            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                            val connectedWifi = if (isConnected) wifiObj?.optJSONObject("connectedWifi") else null
                            val nearbyArray = if (wifiObj != null) {
                                wifiObj.optJSONArray("nearbyWifi") ?: org.json.JSONArray()
                            } else {
                                try { org.json.JSONArray(uiState.collectedWifiJson) } catch (e: Exception) { org.json.JSONArray() }
                            }

                            // 已连接 Wi-Fi 部分
                            item {
                                Text(
                                    text = if (isConnected) "已连接 Wi-Fi" else "已连接 Wi-Fi: 未连接",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) AccentGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(6.dp))
                            }

                            if (connectedWifi != null) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = AccentGreen.copy(alpha = 0.08f)
                                        ),
                                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Wifi,
                                                    contentDescription = null,
                                                    tint = AccentGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = connectedWifi.optString("ssid", "Unknown"),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            
                                            val details = listOf(
                                                "BSSID" to connectedWifi.optString("bssid", "Unknown"),
                                                "厂商" to connectedWifi.optString("vendor", "Unknown"),
                                                "客户端 MAC" to connectedWifi.optString("macAddress", "Unknown"),
                                                "信道" to "${connectedWifi.optInt("channel", 0)} (${connectedWifi.optInt("frequency", 0)} MHz)",
                                                "连接速度" to "${connectedWifi.optInt("linkSpeed", 0)} Mbps",
                                                "信号强度" to "${connectedWifi.optInt("level", 0)} dBm"
                                            )
                                            
                                            details.forEach { (label, value) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (!isConnected) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }

                            // 周边 Wi-Fi 部分
                            item {
                                Text(
                                    text = stringResource(R.string.wifi_hotspots, nearbyArray.length()),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            for (i in 0 until Math.min(nearbyArray.length(), 15)) {
                                val obj = nearbyArray.optJSONObject(i)
                                if (obj != null) {
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = obj.optString("ssid", "Unknown"),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                    Text(
                                                        text = "${obj.optInt("level", 0)} dBm",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "BSSID: ${obj.optString("bssid", "Unknown")} (${obj.optString("vendor", "Unknown")})",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                    Text(
                                                        text = "信道: ${obj.optInt("channel", 0)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (nearbyArray.length() > 15) {
                                item {
                                    Text(
                                        text = stringResource(R.string.and_n_more, nearbyArray.length() - 15),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.cell_info), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccentOrange)
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            val cellArray = try { org.json.JSONArray(uiState.collectedCellJson) } catch (e: Exception) { org.json.JSONArray() }
                            for (i in 0 until cellArray.length()) {
                                val obj = cellArray.optJSONObject(i)
                                item {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.type_is, obj?.optString("type", "Unknown") ?: "Unknown"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                        Text("${obj?.optInt("dbm", 0)} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f))
                                    }
                                }
                            }
                            if (cellArray.length() == 0) {
                                item {
                                    Text(stringResource(R.string.no_cell_data), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.5f))
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEnvironmentDialog = false }) {
                            Text(stringResource(R.string.confirm), color = AccentBlue)
                        }
                    }
                }
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


// Wi-Fi 状态卡片

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
                SectionHeader(Icons.Outlined.PinDrop, stringResource(R.string.target_coordinates), isDark)
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

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
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
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.invalid_coordinates), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
fun ActionButtons(viewModel: MainViewModel, uiState: AppState, onOpenMap: () -> Unit, onStartFixedSpoofing: () -> Unit) {
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = stopColor)
        ) {
            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartFixedSpoofing,
                enabled = !uiState.isSavingConfig,
                modifier = Modifier.weight(1f).height(52.dp),
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
                    Text(stringResource(R.string.fixed_simulation), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = { viewModel.enterRoutePlanning(); onOpenMap() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Rounded.Route, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.route_planning), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// 检查更新卡片

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SystemUpdateAlt, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.check_updates), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.check_updates_desc), color = textSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

// 检查更新弹窗
@Composable
fun UpdateDialog(
    uiState: com.suseoaa.locationspoofer.viewmodel.UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: (String, String) -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onIgnore: (String) -> Unit
) {
    val context = LocalContext.current
    val currentVersion = BuildConfig.VERSION_NAME

    // Find missed versions (newer than current version)
    val missed = remember(uiState.releases) {
        uiState.releases.filter { isNewerVersion(it.versionName, currentVersion) }
    }

    val displayList = remember(uiState.releases, missed) {
        if (missed.size > 1) {
            val latest = missed.first()
            val grouped = parseAndCategorizeReleaseNotes(missed)
            val mergedBody = generateMergedMarkdown(context, grouped)
            val mergedRelease = latest.copy(body = mergedBody)
            val historical = uiState.releases.filter { it !in missed }
            listOf(mergedRelease) + historical
        } else {
            uiState.releases
        }
    }

    LocalizedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.update_dialog_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (uiState.error != null) {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                } else if (uiState.releases.isEmpty()) {
                    Text(stringResource(R.string.no_updates_available))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(displayList) { release ->
                            val isCurrentVersion = release.versionName.contains(BuildConfig.VERSION_NAME) || 
                                                   BuildConfig.VERSION_NAME.contains(release.versionName)
                            val isMergedRelease = missed.size > 1 && release == displayList.first()
                            
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.version, release.versionName), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    if (isCurrentVersion) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentGreen.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(R.string.current_version), fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isMergedRelease) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentBlue.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(R.string.merged_updates_badge, missed.size), fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = parseMarkdown(release.body),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                if (release.downloadUrl != null) {
                                    if (uiState.activeDownloadId != null && uiState.activeDownloadUrl == release.downloadUrl) {
                                        if (uiState.downloadStatus == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                            Button(onClick = onInstall, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) { 
                                                Text(stringResource(R.string.install)) 
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(stringResource(R.string.downloading, uiState.downloadProgress), color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    LinearProgressIndicator(progress = uiState.downloadProgress / 100f, modifier = Modifier.fillMaxWidth(), color = AccentBlue)
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Rounded.Cancel, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    } else if (uiState.activeDownloadId == null) {
                                        Button(onClick = { onDownload(release.downloadUrl, release.versionName) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                                            Text(stringResource(R.string.download))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val latestRelease = uiState.releases.firstOrNull()
                    if (latestRelease != null) {
                        val isCurrentVersion = latestRelease.versionName.contains(BuildConfig.VERSION_NAME) || 
                                               BuildConfig.VERSION_NAME.contains(latestRelease.versionName)
                        if (!isCurrentVersion) {
                            TextButton(
                                onClick = { onIgnore(latestRelease.versionName) }
                            ) {
                                Text(stringResource(R.string.ignore_this_version), color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    TextButton(onClick = onDismiss) { 
                        Text(stringResource(R.string.close)) 
                    }
                }
            }
        }
    }
}

data class GroupedReleaseNotes(
    val features: List<String>,
    val fixes: List<String>,
    val others: List<String>
)

fun parseAndCategorizeReleaseNotes(releases: List<GithubRelease>): GroupedReleaseNotes {
    val features = mutableListOf<String>()
    val fixes = mutableListOf<String>()
    val others = mutableListOf<String>()
    
    val featureHeaderKeywords = listOf(
        "feature", "feat", "add", "new", "improve", "optimize", "enhancement",
        "功能", "新增", "特性", "新功能", "优化", "改进", "提速", "增强"
    )
    val fixHeaderKeywords = listOf(
        "fix", "bug", "crash", "issue", "solve", "repair",
        "修复", "解决", "崩溃", "问题", "纠正", "故障"
    )
    
    for (release in releases) {
        val lines = release.body.split("\n")
        var currentSection = "other" // "feature", "fix", "other"
        
        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty()) continue
            
            // Check if it is a heading
            if (cleanLine.startsWith("#")) {
                val headingText = cleanLine.replace(Regex("^#+\\s*"), "").lowercase()
                
                // Determine section by header keywords
                if (featureHeaderKeywords.any { headingText.contains(it) }) {
                    currentSection = "feature"
                } else if (fixHeaderKeywords.any { headingText.contains(it) }) {
                    currentSection = "fix"
                } else {
                    currentSection = "other"
                }
                continue
            }
            
            // Check if it is a list item
            val isListItem = cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith("+ ") || cleanLine.matches(Regex("""^\d+\.\s+.*"""))
            
            if (isListItem) {
                // Remove list prefix
                var itemContent = cleanLine
                if (cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith("+ ")) {
                    itemContent = cleanLine.substring(2).trim()
                } else {
                    itemContent = cleanLine.replace(Regex("""^\d+\.\s*"""), "").trim()
                }
                
                if (itemContent.isEmpty()) continue
                
                val itemLower = itemContent.lowercase()
                var category = currentSection
                
                if (category == "other") {
                    if (fixHeaderKeywords.any { itemLower.contains(it) }) {
                        category = "fix"
                    } else if (featureHeaderKeywords.any { itemLower.contains(it) }) {
                        category = "feature"
                    }
                }
                
                when (category) {
                    "feature" -> features.add(itemContent)
                    "fix" -> fixes.add(itemContent)
                    else -> others.add(itemContent)
                }
            } else {
                if (!cleanLine.startsWith("```") && !cleanLine.startsWith(">")) {
                    others.add(cleanLine)
                }
            }
        }
    }
    
    return GroupedReleaseNotes(
        features = features.distinct(),
        fixes = fixes.distinct(),
        others = others.distinct()
    )
}

fun generateMergedMarkdown(
    context: android.content.Context,
    grouped: GroupedReleaseNotes
): String {
    val sb = java.lang.StringBuilder()
    
    if (grouped.features.isNotEmpty()) {
        sb.append("### 🌟 ").append(context.getString(R.string.features_header)).append("\n")
        grouped.features.forEach { item ->
            sb.append("- ").append(item).append("\n")
        }
        sb.append("\n")
    }
    
    if (grouped.fixes.isNotEmpty()) {
        sb.append("### 🛠 ").append(context.getString(R.string.fixes_header)).append("\n")
        grouped.fixes.forEach { item ->
            sb.append("- ").append(item).append("\n")
        }
        sb.append("\n")
    }
    
    if (grouped.others.isNotEmpty()) {
        sb.append("### 📝 ").append(context.getString(R.string.others_header)).append("\n")
        grouped.others.forEach { item ->
            if (item.length < 100) {
                sb.append("- ").append(item).append("\n")
            } else {
                sb.append(item).append("\n\n")
            }
        }
    }
    
    return sb.toString().trim()
}

fun isNewerVersion(versionStr: String, currentStr: String): Boolean {
    val v1 = versionStr.lowercase().removePrefix("v").trim()
    val v2 = currentStr.lowercase().removePrefix("v").trim()
    
    val parts1 = v1.split(".")
    val parts2 = v2.split(".")
    
    val length = maxOf(parts1.size, parts2.size)
    for (i in 0 until length) {
        val p1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
        val p2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
        if (p1 > p2) return true
        if (p1 < p2) return false
    }
    return false
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

// API Key 警告弹窗

@Composable
fun ApiKeyWarningDialog(
    isDomestic: Boolean,
    mapEngine: MapEngine,
    onDismiss: () -> Unit
) {
    val mapProviderName = when (mapEngine) {
        MapEngine.AMAP -> stringResource(R.string.app_amap)
        MapEngine.BAIDU -> stringResource(R.string.app_baidumap)
        MapEngine.GOOGLE -> stringResource(R.string.app_googlemap)
        MapEngine.AUTO -> { if (isDomestic) stringResource(R.string.app_amap) else stringResource(R.string.app_googlemap) }
    }

    LocalizedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    stringResource(R.string.api_key_required_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.api_key_required_message, mapProviderName, stringResource(R.string.map_config))
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
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
    LocalizedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.saved_locations), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                if (savedLocations.isEmpty()) {
                    Text(stringResource(R.string.no_saved_locations), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 14.sp)
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
                                    Text("${loc.lat}, ${loc.lng}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                                IconButton(onClick = { onDelete(loc) }) {
                                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

// 首页搜索栏

@Composable
fun HomeSearchBar(
    query: String,
    searchMode: com.suseoaa.locationspoofer.data.model.SearchMode,
    onSearchModeChange: (com.suseoaa.locationspoofer.data.model.SearchMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            val isNetwork = searchMode == com.suseoaa.locationspoofer.data.model.SearchMode.NETWORK
            val activeColor = AccentBlue
            val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
            
            Button(
                onClick = { onSearchModeChange(com.suseoaa.locationspoofer.data.model.SearchMode.NETWORK) },
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isNetwork) activeColor else inactiveColor),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("网络检索", fontSize = 13.sp, color = if (isNetwork) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = { onSearchModeChange(com.suseoaa.locationspoofer.data.model.SearchMode.LOCAL) },
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (!isNetwork) activeColor else inactiveColor),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("本地采集", fontSize = 13.sp, color = if (!isNetwork) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            Text(stringResource(R.string.search_location_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
            Icon(Icons.Rounded.Search, stringResource(R.string.search), tint = Color.White, modifier = Modifier.size(20.dp))
        }
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
                        Text("${loc.lat}, ${loc.lng}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    IconButton(onClick = { onDelete(loc) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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

private var cachedPlacesClient: com.google.android.libraries.places.api.net.PlacesClient? = null

fun performPoiSearch(
    context: android.content.Context,
    activeEngine: com.suseoaa.locationspoofer.data.model.MapEngine,
    keyword: String,
    isDomestic: Boolean,
    onResult: (List<AppPoiItem>) -> Unit
) {
    if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.BAIDU) {
        try {
            val mPoiSearch = com.baidu.mapapi.search.poi.PoiSearch.newInstance()
            mPoiSearch.setOnGetPoiSearchResultListener(object : com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener {
                override fun onGetPoiResult(result: com.baidu.mapapi.search.poi.PoiResult?) {
                    if (result == null || result.error != com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ERROR) {
                        onResult(emptyList())
                        mPoiSearch.destroy()
                        return
                    }
                    val items = result.allPoi?.map {
                        AppPoiItem(it.name ?: "", it.address ?: "", it.location.latitude, it.location.longitude)
                    } ?: emptyList()
                    onResult(items)
                    mPoiSearch.destroy()
                }
                override fun onGetPoiDetailResult(p0: com.baidu.mapapi.search.poi.PoiDetailResult?) {}
                override fun onGetPoiDetailResult(p0: com.baidu.mapapi.search.poi.PoiDetailSearchResult?) {}
                override fun onGetPoiIndoorResult(p0: com.baidu.mapapi.search.poi.PoiIndoorResult?) {}
            })
            val option = com.baidu.mapapi.search.poi.PoiCitySearchOption()
                .city("全国")
                .keyword(keyword)
                .pageNum(0)
                .pageCapacity(20)
            mPoiSearch.searchInCity(option)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(emptyList())
        }
    } else if (activeEngine == com.suseoaa.locationspoofer.data.model.MapEngine.AMAP) {
        try {
            val query = PoiSearch.Query(keyword, "", "")
            query.pageSize = 10
            query.pageNum = 0
            val search = PoiSearch(context, query)
            search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: com.amap.api.services.poisearch.PoiResult?, rCode: Int) {
                    if (rCode == 1000 && result != null) {
                        onResult(result.pois?.map { AppPoiItem(it.title ?: "", it.snippet ?: "", it.latLonPoint.latitude, it.latLonPoint.longitude) } ?: emptyList())
                    } else {
                        if (rCode == 10003 || rCode == 10012 || rCode == 10013 || rCode == 10014 || rCode == 1800 || rCode == 18000) {
                            android.widget.Toast.makeText(context, "高德搜索API调用失败(可能是额度耗尽)，请检查控制台或更换Key！", android.widget.Toast.LENGTH_LONG).show()
                        } else if (rCode != 1000) {
                            android.widget.Toast.makeText(context, "高德搜索失败(错误码:$rCode)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        onResult(emptyList())
                    }
                }
                override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
            })
            search.searchPOIAsyn()
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: ""
            if (e is com.amap.api.services.core.AMapException || msg.contains("limit", ignoreCase = true) || msg.contains("额度")) {
                android.widget.Toast.makeText(context, "高德搜索异常(可能是额度耗尽)：$msg", android.widget.Toast.LENGTH_LONG).show()
            }
            onResult(emptyList())
        }
    } else {
        try {
            val placesClient = cachedPlacesClient ?: com.google.android.libraries.places.api.Places.createClient(context.applicationContext).also {
                cachedPlacesClient = it
            }
            // 使用 Autocomplete 接口：全球关键词搜索，不限制国家/地区
            // 通过 sessionToken 分组请求，避免重复计费；不设置 country 限制以支持全球搜索
            val sessionToken = com.google.android.libraries.places.api.model.AutocompleteSessionToken.newInstance()
            // 设置覆盖全球的矩形偏移，阻止服务器根据 IP 推断区域，实现真正全球搜索
            val worldBounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                com.google.android.gms.maps.model.LatLng(-90.0, -180.0),
                com.google.android.gms.maps.model.LatLng(90.0, 180.0)
            )
            val autocompleteRequest = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                .setQuery(keyword)
                .setLocationBias(worldBounds)
                .setSessionToken(sessionToken)
                .build()

            placesClient.findAutocompletePredictions(autocompleteRequest)
                .addOnSuccessListener { autocompleteResponse ->
                    val predictions = autocompleteResponse.autocompletePredictions
                    if (predictions.isEmpty()) {
                        android.widget.Toast.makeText(context, "No predictions found for: $keyword", android.widget.Toast.LENGTH_SHORT).show()
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }
                    // 批量获取前5条预测结果的详情（坐标）
                    val fetchFields = listOf(
                        com.google.android.libraries.places.api.model.Place.Field.ID,
                        com.google.android.libraries.places.api.model.Place.Field.NAME,
                        com.google.android.libraries.places.api.model.Place.Field.LAT_LNG,
                        com.google.android.libraries.places.api.model.Place.Field.ADDRESS
                    )
                    val resultList = mutableListOf<AppPoiItem>()
                    val topPredictions = predictions.take(5)
                    var completedCount = 0
                    topPredictions.forEach { prediction ->
                        val fetchRequest = com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(prediction.placeId, fetchFields)
                        placesClient.fetchPlace(fetchRequest)
                            .addOnSuccessListener { fetchResponse ->
                                val place = fetchResponse.place
                                val latLng = place.latLng
                                if (latLng != null) {
                                    resultList.add(AppPoiItem(
                                        title = place.name ?: prediction.getPrimaryText(null).toString(),
                                        snippet = place.address ?: prediction.getSecondaryText(null).toString(),
                                        lat = latLng.latitude,
                                        lng = latLng.longitude
                                    ))
                                }
                            }
                            .addOnCompleteListener {
                                completedCount++
                                if (completedCount == topPredictions.size) {
                                    android.widget.Toast.makeText(context, "Search Success: ${resultList.size} results", android.widget.Toast.LENGTH_SHORT).show()
                                    onResult(resultList)
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    android.widget.Toast.makeText(context, "Search Error: ${exception.message}", android.widget.Toast.LENGTH_LONG).show()
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Search Catch Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            onResult(emptyList())
        }
    }
}

@Composable
fun CustomCoordinateDialog(
    initialLat: String,
    initialLng: String,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var lat by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialLat) }
    var lng by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialLng) }
    val textSecondary = AppColors.textSecondary(isDark)

    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        title = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides currentContext, androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.custom_coordinate_title), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        },
        text = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides currentContext, androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration) {
                Column {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.custom_coord_desc), color = textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = lng,
                        onValueChange = { lng = it },
                        label = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.longitude)) },
                        placeholder = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.coordinate_hint), color = textSecondary) },
                        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.East, null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = coordinateFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.latitude)) },
                        placeholder = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.coordinate_hint), color = textSecondary) },
                        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.North, null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = coordinateFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides currentContext, androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration) {
                androidx.compose.material3.TextButton(onClick = { onConfirm(lat, lng) }) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.confirm), color = AccentBlue)
                }
            }
        },
        dismissButton = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides currentContext, androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.cancel), color = textSecondary)
                }
            }
        }
    )
}

@Composable
fun LocalizedDialog(
    onDismissRequest: () -> Unit,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
    content: @Composable () -> Unit
) {
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides currentContext,
            androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
        ) {
            content()
        }
    }
}

@Composable
fun StartSpoofingDialog(
    uiState: AppState,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onToggleWifi: () -> Unit,
    onToggleCell: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onToggleJitter: () -> Unit,
    onAltitudeChange: (String) -> Unit,
    onSatelliteCountChange: (String) -> Unit
) {
    LocalizedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.cardBackground(isDark)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.spoofing_options_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.spoofing_options_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))

                if (uiState.canMockWifi || uiState.wigleToken.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Wifi, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.mock_wifi_data), modifier = Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = uiState.mockWifi, onCheckedChange = { onToggleWifi() })
                    }
                }
                
                if (uiState.canMockCell || uiState.opencellidToken.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CellTower, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.mock_cell_data), modifier = Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = uiState.mockCell, onCheckedChange = { onToggleCell() })
                    }
                }
                
                if (uiState.canMockBluetooth) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bluetooth, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.mock_bluetooth_data), modifier = Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = uiState.mockBluetooth, onCheckedChange = { onToggleBluetooth() })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.enable_slight_jitter), modifier = Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                    Switch(checked = uiState.enableJitter, onCheckedChange = { onToggleJitter() })
                }
                
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = uiState.altitudeInput,
                        onValueChange = onAltitudeChange,
                        label = { Text("海拔 (米)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            focusedLabelColor = AccentBlue
                        )
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = uiState.satelliteCountInput,
                        onValueChange = onSatelliteCountChange,
                        label = { Text("卫星数", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            focusedLabelColor = AccentBlue
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text(stringResource(R.string.start_simulation))
                    }
                }
            }
        }
    }
}

// Markdown 简易解析函数，渲染 Release Notes 内容
@Composable
fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var isInCodeBlock = false
        
        lines.forEachIndexed { index, line ->
            val cleanLine = line.trim()
            
            // Check for code block boundary
            if (cleanLine.startsWith("```")) {
                isInCodeBlock = !isInCodeBlock
                return@forEachIndexed
            }
            
            if (isInCodeBlock) {
                withStyle(style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    color = AccentBlue
                )) {
                    append("    ")
                    append(line)
                }
            } else {
                val headerMatch = Regex("""^(#{1,6})\s+(.*)""").matchEntire(cleanLine)
                if (headerMatch != null) {
                    val hashCount = headerMatch.groupValues[1].length
                    val rest = headerMatch.groupValues[2]
                    val fontSize = when (hashCount) {
                        1 -> 18.sp
                        2 -> 16.sp
                        3 -> 14.sp
                        4 -> 13.sp
                        else -> 12.sp
                    }
                    val fontWeight = FontWeight.Bold
                    val fontStyle = if (hashCount >= 6) FontStyle.Italic else FontStyle.Normal
                    
                    withStyle(style = SpanStyle(
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onBackground
                    )) {
                        parseInlineFormatting(rest)
                    }
                } else if (cleanLine.startsWith(">")) {
                    val rest = if (cleanLine.startsWith("> ")) cleanLine.substring(2) else cleanLine.substring(1)
                    withStyle(style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )) {
                        append("▎ ")
                        parseInlineFormatting(rest)
                    }
                } else if (cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith("+ ")) {
                    append("  • ")
                    val rest = cleanLine.substring(2).trim()
                    parseInlineFormatting(rest)
                } else if (cleanLine.matches(Regex("""^\d+\.\s+.*"""))) {
                    val matchResult = Regex("""^(\d+\.\s+)(.*)""").find(cleanLine)
                    if (matchResult != null) {
                        val prefix = matchResult.groupValues[1]
                        val rest = matchResult.groupValues[2]
                        append("  $prefix")
                        parseInlineFormatting(rest)
                    } else {
                        parseInlineFormatting(cleanLine)
                    }
                } else {
                    parseInlineFormatting(cleanLine)
                }
            }
            
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

@Composable
private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineFormatting(text: String) {
    var i = 0
    while (i < text.length) {
        var tokenType = ""
        var minIdx = Int.MAX_VALUE
        
        val bold1 = text.indexOf("**", i)
        val bold2 = text.indexOf("__", i)
        val italic1 = text.indexOf("*", i)
        val italic2 = text.indexOf("_", i)
        val code = text.indexOf("`", i)
        val strike = text.indexOf("~~", i)
        val link = text.indexOf("[", i)
        
        if (bold1 in i until minIdx) { minIdx = bold1; tokenType = "bold1" }
        if (bold2 in i until minIdx) { minIdx = bold2; tokenType = "bold2" }
        if (italic1 in i until minIdx && bold1 != italic1) { minIdx = italic1; tokenType = "italic1" }
        if (italic2 in i until minIdx && bold2 != italic2) { minIdx = italic2; tokenType = "italic2" }
        if (code in i until minIdx) { minIdx = code; tokenType = "code" }
        if (strike in i until minIdx) { minIdx = strike; tokenType = "strike" }
        if (link in i until minIdx) { minIdx = link; tokenType = "link" }
        
        if (minIdx == Int.MAX_VALUE) {
            append(text.substring(i))
            break
        }
        
        if (minIdx > i) {
            append(text.substring(i, minIdx))
        }
        
        i = minIdx
        var parsed = false
        
        when (tokenType) {
            "bold1", "bold2" -> {
                val delim = if (tokenType == "bold1") "**" else "__"
                val end = text.indexOf(delim, i + 2)
                if (end != -1) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        parseInlineFormatting(text.substring(i + 2, end))
                    }
                    i = end + 2
                    parsed = true
                }
            }
            "italic1", "italic2" -> {
                val delim = if (tokenType == "italic1") "*" else "_"
                val end = text.indexOf(delim, i + 1)
                if (end != -1) {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        parseInlineFormatting(text.substring(i + 1, end))
                    }
                    i = end + 1
                    parsed = true
                }
            }
            "code" -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        color = AccentBlue
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    parsed = true
                }
            }
            "strike" -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        parseInlineFormatting(text.substring(i + 2, end))
                    }
                    i = end + 2
                    parsed = true
                }
            }
            "link" -> {
                val closeBracket = text.indexOf("]", i + 1)
                if (closeBracket != -1) {
                    val openParen = closeBracket + 1
                    if (openParen < text.length && text[openParen] == '(') {
                        val closeParen = text.indexOf(")", openParen + 1)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val linkUrl = text.substring(openParen + 1, closeParen)
                            
                            withStyle(style = SpanStyle(
                                color = AccentBlue,
                                textDecoration = TextDecoration.Underline
                            )) {
                                pushStringAnnotation(tag = "URL", annotation = linkUrl)
                                parseInlineFormatting(linkText)
                                pop()
                            }
                            i = closeParen + 1
                            parsed = true
                        }
                    }
                }
            }
        }
        
        if (!parsed) {
            append(text[i])
            i++
        }
    }
}
