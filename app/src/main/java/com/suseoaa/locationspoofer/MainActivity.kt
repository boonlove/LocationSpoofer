package com.suseoaa.locationspoofer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.suseoaa.locationspoofer.utils.SavedLocation
import org.koin.androidx.viewmodel.ext.android.viewModel

// ── 品牌色系 (Dark) ──────────────────────────────────────────────
private val DarkBg = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val SurfaceCardDark = Color(0xFF1C2333)
private val DividerColorDark = Color(0xFF30363D)
private val TextPrimaryDark = Color(0xFFE6EDF3)
private val TextSecondaryDark = Color(0xFF8B949E)

// ── 品牌色系 (Light) ──────────────────────────────────────────────
private val LightBg = Color(0xFFF6F8FA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceCardLight = Color(0xFFFFFFFF)
private val DividerColorLight = Color(0xFFD0D7DE)
private val TextPrimaryLight = Color(0xFF24292F)
private val TextSecondaryLight = Color(0xFF57606A)

// ── 通用色 ──────────────────────────────────────────────
private val Accent = Color(0xFF2EA043)        // 绿色：运行中
private val AccentBlue = Color(0xFF388BFD)    // 蓝色：主要操作
private val AccentOrange = Color(0xFFD29922)  // 橙色：警告

private val AppColorSchemeDark = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = DarkBg,
    surface = SurfaceCardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    outline = DividerColorDark,
    error = Color(0xFFF85149)
)

private val AppColorSchemeLight = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = LightBg,
    surface = SurfaceCardLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    outline = DividerColorLight,
    error = Color(0xFFCF222E)
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // 开启沉浸式状态栏与导航栏
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val notGranted = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                requestPermissions(notGranted.toTypedArray(), 100)
            }
        }

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
}

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
fun MainScreen(viewModel: MainViewModel, uiState: AppState, isDark: Boolean) {
    var isFullScreenMap by remember { mutableStateOf(false) }

    // 主内容在非全屏地图时显示，系统栏留白处理通过 WindowInsets 处理
    AnimatedContent(
        targetState = isFullScreenMap,
        transitionSpec = {
            slideInVertically(tween(400)) { height -> height } togetherWith slideOutVertically(tween(400)) { height -> -height }
        }, label = "fullscreen_transition"
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

@Composable
fun InitializingScreen(isDark: Boolean) {
    val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "LocationSpoofer",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
            Text("正在初始化环境...", color = textColorSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun BlockingScreen(icon: ImageVector, title: String, message: String, isDark: Boolean) {
    val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF85149).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFFF85149),
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                message, color = textColorSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun AMapView(modifier: Modifier = Modifier, onMapReady: (AMap) -> Unit) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onDestroy() }
    }

    AndroidView(
        factory = {
            mapView.apply {
                setOnTouchListener { v, _ ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                map.setOnMapLoadedListener { onMapReady(map) }
            }
        },
        modifier = modifier
    )
}

@Composable
fun SpoofingScreen(viewModel: MainViewModel, uiState: AppState, isDark: Boolean, onExpandMap: () -> Unit) {
    val scrollState = rememberScrollState()
    var showSavedLocations by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val topBarBg = if (isDark) SurfaceDark else SurfaceLight
    val dividerCol = MaterialTheme.colorScheme.outline

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()) {

        // ── 顶部 Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBg)
                .padding(horizontal = 20.dp, vertical = 14.dp),
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
                    Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "LocationSpoofer",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            
            // Saved Locations Button
            IconButton(onClick = { showSavedLocations = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Bookmarks, contentDescription = "Saved Locations", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDivider(color = dividerCol, thickness = 0.5.dp)

        // ── 地图区域（小图展示，带有全屏入口）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clickable { onExpandMap() }
        ) {
            AMapView(modifier = Modifier.fillMaxSize()) { map ->
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.setAllGesturesEnabled(false) // 禁用小地图交互，让用户点进去
                val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))
                map.setOnMapClickListener { onExpandMap() }
            }

            // 中心准星
            Icon(
                Icons.Rounded.AddLocationAlt,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )

            // 全屏提示按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全屏选点", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                }
            }

            // 地图底部渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
            )
        }

        // ── 滚动内容区 ──
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

            CoordinateInputCard(viewModel, uiState, isDark) {
                showSaveDialog = true
            }
            Spacer(Modifier.height(12.dp))

            ActionButton(viewModel, uiState)
            Spacer(Modifier.height(20.dp))

            SectionHeader(icon = Icons.Outlined.AppRegistration, title = "LSPosed 作用域", isDark = isDark)
            Spacer(Modifier.height(8.dp))
            AppScopeCard(uiState, isDark)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSaveDialog) {
        var locationName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存当前位置") },
            text = {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("位置名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (locationName.isNotBlank()) {
                        viewModel.saveCurrentLocation(locationName)
                        showSaveDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
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
            onDelete = { loc ->
                viewModel.removeSavedLocation(loc)
            }
        )
    }
}

@Composable
fun FullScreenMapPage(viewModel: MainViewModel, uiState: AppState, isDark: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<AMap?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Tip>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight
    var showSaveDialog by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全屏地图
        AMapView(modifier = Modifier.fillMaxSize()) { map ->
            mapInstance = map
            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isCompassEnabled = false
            
            val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
            val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(initLat, initLng), 15f))
            
            map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(position: CameraPosition) {}
                override fun onCameraChangeFinish(position: CameraPosition) {
                    if (!uiState.isSpoofingActive) {
                        viewModel.updateLongitude(String.format("%.6f", position.target.longitude))
                        viewModel.updateLatitude(String.format("%.6f", position.target.latitude))
                    }
                }
            })
        }

        // 中心准星
        Icon(
            Icons.Rounded.AddLocationAlt,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
        )

        // 顶部搜索和返回按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(Modifier.width(12.dp))

                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it 
                        if (it.isNotBlank()) {
                            val inputtipsQuery = InputtipsQuery(it, "")
                            inputtipsQuery.cityLimit = false
                            val inputtips = Inputtips(context, inputtipsQuery)
                            inputtips.setInputtipsListener { tipList, rCode ->
                                if (rCode == 1000 && tipList != null) {
                                    suggestions = tipList.filter { tip -> tip.point != null }
                                }
                            }
                            inputtips.requestInputtipsAsyn()
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    placeholder = { Text("搜索地点（如：大学）", color = textColorSecondary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = textColorSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                suggestions = emptyList()
                            }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = textColorSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent,
                    )
                )
            }

            // 搜索联想列表
            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(suggestions) { tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = tip.name
                                        suggestions = emptyList()
                                        focusManager.clearFocus()
                                        val lat = tip.point.latitude
                                        val lng = tip.point.longitude
                                        mapInstance?.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f)
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, contentDescription = null, tint = textColorSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(tip.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    if (tip.district.isNotEmpty()) {
                                        Text(tip.district, color = textColorSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // 底部状态卡片及操作按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("当前坐标", fontSize = 12.sp, color = textColorSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${uiState.longitudeInput}, ${uiState.latitudeInput}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    FilledTonalButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("确定选点", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSaveDialog) {
        var locationName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存当前位置") },
            text = {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("位置名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (locationName.isNotBlank()) {
                        viewModel.saveCurrentLocation(locationName)
                        showSaveDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            }
        )
    }
}

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
                Spacer(modifier = Modifier.height(12.dp))
                if (savedLocations.isEmpty()) {
                    Text("暂无保存的位置", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(savedLocations) { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(loc) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, contentDescription = null, tint = AccentBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(loc.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                    Text("${loc.lat}, ${loc.lng}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = { onDelete(loc) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFF85149))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun WifiStatusCard(uiState: AppState) {
    val (bgColor, iconTint, statusText, icon) = when (uiState.wifiLoadStatus) {
        WifiLoadStatus.LOADING -> Quadruple(
            AccentOrange.copy(alpha = 0.12f), AccentOrange,
            "正在拉取当地 Wi-Fi 指纹数据...", Icons.Outlined.CloudDownload
        )

        WifiLoadStatus.DONE -> Quadruple(
            Accent.copy(alpha = 0.12f), Accent,
            "Wi-Fi 指纹已就绪（${uiState.wifiApCount} 个热点）", Icons.Outlined.Wifi
        )

        else -> Quadruple(
            AccentBlue.copy(alpha = 0.12f), AccentBlue,
            "GPS 定位已接管", Icons.Outlined.GpsFixed
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.wifiLoadStatus == WifiLoadStatus.LOADING) {
            CircularProgressIndicator(
                color = iconTint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(statusText, color = iconTint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CoordinateInputCard(viewModel: MainViewModel, uiState: AppState, isDark: Boolean, onSaveClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(icon = Icons.Outlined.PinDrop, title = "目标坐标", isDark = isDark)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSaveClick) {
                    Icon(Icons.Rounded.StarBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
            Spacer(Modifier.height(4.dp))

            val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight

            OutlinedTextField(
                value = uiState.longitudeInput,
                onValueChange = { viewModel.updateLongitude(it) },
                label = { Text("经度") },
                placeholder = { Text("点击上方地图进入全屏选点", color = textColorSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.East,
                        null,
                        tint = textColorSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                placeholder = { Text("点击上方地图进入全屏选点", color = textColorSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.North,
                        null,
                        tint = textColorSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.showCoordinateError,
                enabled = !uiState.isSpoofingActive,
                singleLine = true,
                colors = coordinateFieldColors()
            )
            if (uiState.showCoordinateError) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "经纬度数值超出合法范围",
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
fun ActionButton(viewModel: MainViewModel, uiState: AppState) {
    val activeColor by animateColorAsState(
        targetValue = if (uiState.isSpoofingActive) Color(0xFFF85149) else AccentBlue,
        animationSpec = tween(300), label = "button_color"
    )

    Button(
        onClick = { if (uiState.isSpoofingActive) viewModel.stopSpoofing() else viewModel.startSpoofing() },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
    ) {
        val icon = if (uiState.isSpoofingActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow
        val label = if (uiState.isSpoofingActive) "停止模拟并恢复系统定位" else "启动虚拟定位"
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppScopeCard(uiState: AppState, isDark: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            RECOMMENDED_APPS.forEachIndexed { index, app ->
                AppScopeRow(app, isDark)
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

@Composable
fun AppScopeRow(app: RecommendedApp, isDark: Boolean) {
    val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                app.icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(app.packageName, color = textColorSecondary, fontSize = 11.sp)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = textColorSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String, isDark: Boolean) {
    val textColorSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = textColorSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title.uppercase(),
            color = textColorSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
}
