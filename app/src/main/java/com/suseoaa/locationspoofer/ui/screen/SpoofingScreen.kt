package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.suseoaa.locationspoofer.ui.screen.performPoiSearch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.suseoaa.locationspoofer.BuildConfig
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.data.model.SearchMode
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.components.BottomSheetValue
import com.suseoaa.locationspoofer.ui.components.DraggableBottomSheet
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import com.suseoaa.locationspoofer.ui.components.rememberBottomSheetState
import com.suseoaa.locationspoofer.ui.extensions.activeEngine
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingUiState
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SpoofingScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onExpandMap: () -> Unit,
    onExpandScannerMap: () -> Unit,
    onExpandSettings: () -> Unit,
    updateViewModel: com.suseoaa.locationspoofer.viewmodel.UpdateViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val activeEngine = uiState.mapEngine.activeEngine(viewModel.isDomesticEnvironment())

    val spoofingUiState by viewModel.spoofingUiState.collectAsState()
    val updateUiState by updateViewModel.uiState.collectAsState()

    val onIntent = { intent: SpoofingIntent -> viewModel.handleSpoofingIntent(intent) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

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

    var hasAutoCheckedUpdates by remember { mutableStateOf(false) }

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
                val ignoredVersion = viewModel.getIgnoredVersion()
                if (isNewerVersion(
                        latestVersion,
                        currentVersion
                    ) && latestVersion != ignoredVersion
                ) {
                    onIntent(SpoofingIntent.SetUpdateDialogVisible(true))
                }
            }
            hasAutoCheckedUpdates = true
        }
    }

    BackHandler(enabled = spoofingUiState.showSearchResults) {
        onIntent(SpoofingIntent.ClearSearchResults(false))
    }

    var smallMapRef by remember { mutableStateOf<AppMapController?>(null) }
    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()

    // 同步地图类型，地图引擎为高德地图时，需要额外并延迟设置一次地图中心点，否则地图中心点会定位在北京
    LaunchedEffect(smallMapRef, uiState.mapType) {
        smallMapRef?.setMapType(uiState.mapType)
        if (activeEngine == MapEngine.AMAP && lat != null && lng != null) {
            kotlinx.coroutines.delay(200)
            smallMapRef?.moveCamera(lat, lng)
        }
    }

    LaunchedEffect(smallMapRef, uiState.manageDataList) {
        val map = smallMapRef ?: return@LaunchedEffect
        map.clear()
        val locations = uiState.manageDataList.map { it.location }
        com.suseoaa.locationspoofer.utils.MapCoverageHelper.drawCoverage(map, locations)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.fetchCurrentLocation(context) {lat, lng ->
                smallMapRef?.moveCamera(lat, lng)
            }
        }
        viewModel.loadManageData()
    }

    // Bottom Sheet
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    /**
    val screenHeightDp = configuration.screenHeightDp.dp
    val expandedMapHeight = screenHeightDp * 0.25f
    val collapsedMapHeight = screenHeightDp - (if (uiState.isSpoofingActive) 380.dp else 320.dp)

    val animatedMapHeight by animateDpAsState(
        targetValue = if (spoofingUiState.isSearchActive) screenHeightDp else if (spoofingUiState.isSheetExpanded) expandedMapHeight else collapsedMapHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    var isDragging by remember { mutableStateOf(false) }
    var wasAtTopBeforeDrag by remember { mutableStateOf(true) }
    val consumeAllUpwardScroll = remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        if (!isDragging) {
            wasAtTopBeforeDrag = (scrollState.value <= 0)
            consumeAllUpwardScroll.value = false
        }
    }

    val nestedScrollConnection = remember(spoofingUiState.isSearchActive, wasAtTopBeforeDrag) {
        var consumeNextUpwardFling = false

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (spoofingUiState.isSearchActive) return Offset.Zero
                if (available.y < 0f) {
                    if (!spoofingUiState.isSheetExpanded || consumeAllUpwardScroll.value) {
                        if (!consumeAllUpwardScroll.value) consumeAllUpwardScroll.value = true
                        consumeNextUpwardFling = true
                        if (!spoofingUiState.isSheetExpanded) {
                            onIntent(SpoofingIntent.SetSheetExpanded(true))
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (consumeNextUpwardFling) {
                    consumeNextUpwardFling = false
                    if (available.y < 0f) {
                        return androidx.compose.ui.unit.Velocity(0f, available.y)
                    }
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (spoofingUiState.isSearchActive) return Offset.Zero
                if (spoofingUiState.isSheetExpanded && available.y > 10f) {
                    if (wasAtTopBeforeDrag) {
                        onIntent(SpoofingIntent.SetSheetExpanded(false))
                    }
                }
                return Offset.Zero
            }
        }
    }
    */

    val density = LocalDensity.current
    // configuration.screenHeightDp 为系统原始 dp（不受界面缩放影响）：先用系统 density 转物理 px，
    // 再用当前(缩放后) density 转回 dp —— 得到"缩放视觉 dp"，使 Modifier.height 渲染出正确物理高度
    val screenHeightPx = configuration.screenHeightDp.dp.value * LocalContext.current.resources.displayMetrics.density
    val screenHeightDp = with(density) { screenHeightPx.toDp() }
    val expandedFraction = 0.75f
    val halfFraction = 0.375f
    val expandedHeightPx = screenHeightPx * expandedFraction
    val halfHeightPx = screenHeightPx * halfFraction

    var topBarHeightPx by remember { mutableFloatStateOf(0f) }

    val initialAnchors = remember(expandedHeightPx, halfHeightPx) {
        DraggableAnchors {
            BottomSheetValue.EXPANDED at 0f
            BottomSheetValue.HALF at (expandedHeightPx - halfHeightPx)
        }
    }
    val sheetState = rememberBottomSheetState(spoofingUiState.sheetState, initialAnchors)
    val scrollState = rememberScrollState(initial = spoofingUiState.scrollOffset)

    LaunchedEffect(sheetState.currentValue) {
        onIntent(SpoofingIntent.SetSheetState(sheetState.currentValue))
        // onIntent(SpoofingIntent.SetSheetExpanded(sheetState.isExpanded))
    }
    LaunchedEffect(scrollState.value) {
        onIntent(SpoofingIntent.SetScrollOffset(scrollState.value))
    }

    BackHandler(enabled = spoofingUiState.isSearchActive || spoofingUiState.showSearchResults) {
        onIntent(SpoofingIntent.SetSearchActive(false))
        onIntent(SpoofingIntent.ClearSearchResults(false))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 地图（可跟随底部Sheet自适应）、搜索栏及搜索结果、浮动按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    if (spoofingUiState.isMapFullscreen) {
                        screenHeightDp
                    } else {
                         with(density) { (screenHeightPx - topBarHeightPx - (expandedHeightPx - sheetState.offset)).toDp() }
                    }
                )
                .offset{
                    if (!spoofingUiState.isMapFullscreen) {
                        IntOffset(0, topBarHeightPx.roundToInt())
                    } else {
                        IntOffset(0, 0)
                    }
                }
        ) {
            AppMapView(
                mapEngine = uiState.mapEngine,
                isDomestic = viewModel.isDomesticEnvironment(),
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            ) { map ->
                smallMapRef = map
                map.disableUiControls()
                val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                map.moveCamera(initLat, initLng, 15f)

                        map.setOnCameraChangeListener { lat, lng ->
                            onIntent(SpoofingIntent.ConfirmMapPoint(lat, lng))
                        }
                        map.setOnCameraMoveListener { lat, lng ->
                            onIntent(SpoofingIntent.MapPointMoved(lat, lng))
                        }
                    }

            // 十字准星
            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .padding(bottom = 16.dp)
            )

            // 浮动按钮组（全屏/图层/定位），竖向排列，避让 Sheet 可见高度
            // EXPANDED 时隐藏（Sheet 占半屏，按钮区域被遮挡）
            androidx.compose.animation.AnimatedVisibility(
                visible = !sheetState.isExpanded,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        if (spoofingUiState.isMapFullscreen) {
                            val o = sheetState.offset
                            val visible = if (o.isNaN()) expandedHeightPx else (expandedHeightPx - o)
                            IntOffset(0, -visible.roundToInt())
                        } else {
                            IntOffset(0,0)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fullscreen Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { onExpandMap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Select Map Type Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { onIntent(SpoofingIntent.SetMapTypeDialogVisible(true)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Locate Current Position Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable {
                                viewModel.fetchCurrentLocation(context) { lat, lng ->
                                    smallMapRef?.animateCamera(lat, lng)
                                }
                                       },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Floating Search Bar & Results Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .offset{
                        if (spoofingUiState.isMapFullscreen) {
                            IntOffset(0, topBarHeightPx.roundToInt())
                        } else {
                            IntOffset(0, 0)
                        }
                    }
            ) {
                Box(modifier = Modifier.clickable {
                    if (!spoofingUiState.isSearchActive) onIntent(
                        SpoofingIntent.SetSearchActive(true)
                    )
                }) {
                    HomeSearchBar(
                        query = spoofingUiState.searchQuery,
                        searchMode = uiState.searchMode,
                        onSearchModeChange = { mode -> viewModel.setSearchMode(mode) },
                        onSearch = {
                            focusManager.clearFocus()
                            if (uiState.searchMode == SearchMode.LOCAL) {
                                GlobalScope.launch(Dispatchers.Main) {
                                    val results = viewModel.performLocalSearch()
                                    onIntent(
                                        SpoofingIntent.SetSearchResults(
                                            results,
                                            true
                                        )
                                    )
                                }
                            }else if (
                                (activeEngine == MapEngine.AMAP && uiState.amapApiKey.isBlank()) ||
                                (activeEngine == MapEngine.BAIDU && uiState.baiduApiKey.isBlank())
                            ) {
                                onIntent(
                                    SpoofingIntent.SetApiKeyWarningVisible(
                                        true
                                    )
                                )
                            } else if (spoofingUiState.searchQuery.isNotBlank()) {
                                performPoiSearch(
                                    context,
                                    uiState.mapEngine,
                                    spoofingUiState.searchQuery,
                                    viewModel.isDomesticEnvironment()
                                ) { results ->
                                    onIntent(
                                        SpoofingIntent.SetSearchResults(
                                            results,
                                            true
                                        )
                                    )
                                }
                            }
                        },
                        onQueryChange = { onIntent(SpoofingIntent.UpdateSearchQuery(it)) }
                    )
                }

                // 搜索结果
                AnimatedVisibility(visible = spoofingUiState.showSearchResults && spoofingUiState.searchResults.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(spoofingUiState.searchResults.take(15)) { poi ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            /**
                                            viewModel.updateLatitude(
                                                String.format(
                                                    "%.6f",
                                                    poi.lat
                                                )
                                            )
                                            viewModel.updateLongitude(
                                                String.format(
                                                    "%.6f",
                                                    poi.lng
                                                )
                                            )
                                            */
                                            smallMapRef?.animateCamera(
                                                poi.lat,
                                                poi.lng,
                                                16f
                                            )
                                            onIntent(
                                                SpoofingIntent.ConfirmMapPoint(
                                                    poi.lat,
                                                    poi.lng
                                                )
                                            )
                                            onIntent(
                                                SpoofingIntent.ClearSearchResults(
                                                    false
                                                )
                                            )
                                            onIntent(
                                                SpoofingIntent.SetSearchActive(
                                                    false
                                                )
                                            )
                                            onIntent(
                                                SpoofingIntent.UpdateSearchQuery(
                                                    poi.title
                                                )
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Place,
                                        null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            poi.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            poi.snippet,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 地图底部渐变遮罩
            if (!spoofingUiState.isMapFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(0.8f)
                                )
                            )
                        ))
            }
        }

        // 顶部栏
        Column {
            Row (
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        topBarHeightPx = coordinates.size.height.toFloat()
                    }
                    .background(if (spoofingUiState.isMapFullscreen) AppColors.topBarBackground(isDark).copy(alpha = 0.5f) else AppColors.topBarBackground(isDark))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.mipmap.icon),
                        null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
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
                IconButton(
                    onClick = { onIntent(SpoofingIntent.SetSavedLocationsVisible(true)) },
                    modifier = Modifier.size(36.dp)
                ) {
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
        }

        // Bottom Sheet
        DraggableBottomSheet(
            sheetState = sheetState,
            modifier = Modifier.fillMaxSize(),
            sheetShape = if (spoofingUiState.isMapFullscreen) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RectangleShape,
            scrimColor = if (spoofingUiState.isMapFullscreen) MaterialTheme.colorScheme.background.copy(0.8f) else MaterialTheme.colorScheme.background,
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
                    onSaveClick = { onIntent(SpoofingIntent.SetSaveDialogVisible(true)) },
                    onCustomClick = {
                        onIntent(
                            SpoofingIntent.SetCustomCoordDialogVisible(
                                true
                            )
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))

                ActionButtons(
                    viewModel,
                    uiState,
                    onExpandMap,
                    onStartFixedSpoofing = {
                        onIntent(
                            SpoofingIntent.SetStartSpoofingDialogVisible(
                                true
                            )
                        )
                    })
                Spacer(Modifier.height(16.dp))
            }
        ) {
            SectionHeader(
                Icons.Rounded.Search,
                "搜索源",
                isDark
            )
            Spacer(Modifier.height(8.dp))
            SearchModeCard(isDark, uiState.searchMode) { mode ->
                viewModel.setSearchMode(mode)
                if (mode == SearchMode.LOCAL) {
                    focusManager.clearFocus()
                    GlobalScope.launch(Dispatchers.Main) {
                        val results = viewModel.performLocalSearch()
                        onIntent(SpoofingIntent.SetSearchResults(results, true))
                    }
                } else {
                    onIntent(SpoofingIntent.ClearSearchResults(clearAll = true))
                }
            }
            Spacer(Modifier.height(16.dp))

            if (uiState.savedLocations.isNotEmpty()) {
                SectionHeader(
                    Icons.Rounded.Bookmarks,
                    stringResource(R.string.collection_list),
                    isDark
                )
                Spacer(Modifier.height(8.dp))
                SavedLocationsCard(
                    savedLocations = uiState.savedLocations,
                    onSelect = { loc ->
                        // viewModel.loadSavedLocation(loc)
                        smallMapRef?.animateCamera(loc.lat, loc.lng)
                        onIntent(SpoofingIntent.ConfirmMapPoint(loc.lat, loc.lng))
                               },
                    onDelete = { loc -> viewModel.removeSavedLocation(loc) }
                )
                Spacer(Modifier.height(16.dp))
            }

            AppCoordinateConfigCard(isDark) {
                onIntent(SpoofingIntent.SetAppCoordinateScreenVisible(true))
            }
            Spacer(Modifier.height(16.dp))

            ScannerMapCard(isDark, uiState) {
                onExpandScannerMap()
            }
            Spacer(Modifier.height(8.dp))

            ManageDataCard(isDark) {
                viewModel.toggleManageDataScreen(true)
            }
            Spacer(Modifier.height(8.dp))

            ImportExportDataCard(
                isDark = isDark,
                onImportClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/json",
                            "*/*"
                        )
                    )
                },
                onExportClick = { exportLauncher.launch("environment_data.json") }
            )
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                Icons.Rounded.SystemUpdateAlt,
                stringResource(R.string.check_updates),
                isDark
            )
            Spacer(Modifier.height(8.dp))
            UpdateCheckCard(isDark, onCheckClick = {
                updateViewModel.fetchReleases()
                onIntent(SpoofingIntent.SetUpdateDialogVisible(true))
            })
            Spacer(Modifier.height(16.dp))

            FooterLinks(isDark)
        }
    }

    // Dialogs
    if (spoofingUiState.showApiKeyWarningDialog) {
        ApiKeyWarningDialog(
            isDomestic = viewModel.isDomesticEnvironment(),
            mapEngine = activeEngine,
            onDismiss = { onIntent(SpoofingIntent.SetApiKeyWarningVisible(false)) }
        )
    }

    if (spoofingUiState.showSaveDialog) {
        SaveNameDialog(
            title = stringResource(R.string.save_current_location),
            onConfirm = { name ->
                viewModel.saveCurrentLocation(name)
                onIntent(SpoofingIntent.SetSaveDialogVisible(false))
            },
            onDismiss = { onIntent(SpoofingIntent.SetSaveDialogVisible(false)) }
        )
    }

    if (spoofingUiState.showSavedLocationsDialog) {
        SavedLocationsDialog(
            savedLocations = uiState.savedLocations,
            onDismiss = { onIntent(SpoofingIntent.SetSavedLocationsVisible(false)) },
            onSelect = { loc ->
                // viewModel.loadSavedLocation(loc)
                smallMapRef?.animateCamera(loc.lat, loc.lng)
                onIntent(SpoofingIntent.ConfirmMapPoint(loc.lat, loc.lng))
                onIntent(SpoofingIntent.SetSavedLocationsVisible(false))
            },
            onDelete = { loc -> viewModel.removeSavedLocation(loc) }
        )
    }

    if (spoofingUiState.showUpdateDialog) {
        UpdateDialog(
            uiState = updateUiState,
            onDismiss = { onIntent(SpoofingIntent.SetUpdateDialogVisible(false)) },
            onDownload = { url, version -> updateViewModel.startDownload(url, version) },
            onCancel = { updateViewModel.cancelDownload() },
            onInstall = { updateViewModel.installApk() },
            onIgnore = { version ->
                viewModel.setIgnoredVersion(version)
                onIntent(SpoofingIntent.SetUpdateDialogVisible(false))
            }
        )
    }

    if (spoofingUiState.showStartSpoofingDialog) {
        StartSpoofingDialog(
            uiState = uiState,
            isDark = isDark,
            onDismiss = { onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false)) },
            onConfirm = {
                viewModel.startSpoofing()
                onIntent(SpoofingIntent.SetStartSpoofingDialogVisible(false))
            },
            onToggleWifi = { viewModel.toggleMockWifi() },
            onToggleCell = { viewModel.toggleMockCell() },
            onToggleBluetooth = { viewModel.toggleMockBluetooth() },
            onToggleJitter = { viewModel.toggleEnableJitter() },
            onAltitudeChange = { viewModel.setAltitude(it) },
            onSatelliteCountChange = { viewModel.setSatelliteCount(it) }
        )
    }

    if (spoofingUiState.showCustomCoordDialog) {
        CustomCoordinateDialog(
            initialLat = uiState.latitudeInput,
            initialLng = uiState.longitudeInput,
            isDark = isDark,
            onDismiss = { onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false)) },
            onConfirm = { lat, lng ->
                /**
                viewModel.updateLatitude(lat)
                viewModel.updateLongitude(lng)
                */
                val Lat = lat.toDoubleOrNull()
                val Lng = lng.toDoubleOrNull()
                if (Lat != null && Lng != null) {
                    smallMapRef?.animateCamera(Lat, Lng)
                    onIntent(SpoofingIntent.ConfirmMapPoint(Lat, Lng))
                }
                onIntent(SpoofingIntent.SetCustomCoordDialogVisible(false))
            }
        )
    }

    if (spoofingUiState.showMapTypeDialog) {
        MapTypeDialog(
            currentMapType = uiState.mapType,
            onMapTypeSelected = { viewModel.setMapType(it) },
            currentMapEngine = uiState.mapEngine,
            onMapEngineSelected = { viewModel.setMapEngine(it) },
            onDismiss = { onIntent(SpoofingIntent.SetMapTypeDialogVisible(false)) }
        )
    }

    AnimatedVisibility(
        visible = spoofingUiState.showAppCoordinateScreen,
        enter = androidx.compose.animation.slideInVertically(tween(400)) { it },
        exit = androidx.compose.animation.slideOutVertically(tween(400)) { it }
    ) {
        AppCoordinateScreen(
            viewModel = viewModel,
            uiState = uiState,
            onBack = { onIntent(SpoofingIntent.SetAppCoordinateScreenVisible(false)) }
        )
    }
}
