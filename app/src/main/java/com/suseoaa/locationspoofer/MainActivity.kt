package com.suseoaa.locationspoofer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import org.koin.androidx.viewmodel.ext.android.viewModel

// ── 品牌色系 ──────────────────────────────────────────────
private val DarkBg = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val SurfaceCard = Color(0xFF1C2333)
private val Accent = Color(0xFF2EA043)        // 绿色：运行中
private val AccentBlue = Color(0xFF388BFD)    // 蓝色：主要操作
private val AccentOrange = Color(0xFFD29922)  // 橙色：警告
private val DividerColor = Color(0xFF30363D)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

private val AppColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = DarkBg,
    surface = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = DividerColor,
    error = Color(0xFFF85149)
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    val uiState by viewModel.uiState.collectAsState()
                    MainScreen(viewModel = viewModel, uiState = uiState)
                }
            }
        }
    }
}

data class RecommendedApp(val name: String, val packageName: String, val icon: ImageVector)

val RECOMMENDED_APPS = listOf(
    RecommendedApp("微信", "com.tencent.mm", Icons.Outlined.Chat),
    RecommendedApp("超星学习通", "com.chaoxing.mobile", Icons.Outlined.School),
    RecommendedApp("高德地图", "com.autonavi.minimap", Icons.Outlined.Map),
    RecommendedApp("百度地图", "com.baidu.BaiduMap", Icons.Outlined.Map),
    RecommendedApp("腾讯地图", "com.tencent.map", Icons.Outlined.Map),
    RecommendedApp("美团", "com.sankuai.meituan", Icons.Outlined.LocalDining),
    RecommendedApp("钉钉", "com.alibaba.android.rimet", Icons.Outlined.Work),
    RecommendedApp("Google 服务", "com.google.android.gms", Icons.Outlined.Android),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, uiState: AppState) {
    when {
        uiState.isInitializing -> InitializingScreen()
        !uiState.hasRootAccess -> BlockingScreen(
            icon = Icons.Rounded.Lock,
            title = "需要 Root 权限",
            message = "本应用需要 KernelSU Root 权限才能运行。\n请在 KernelSU 中授权后重启应用。"
        )
        !uiState.isLSPosedActive -> BlockingScreen(
            icon = Icons.Rounded.Extension,
            title = "LSPosed 模块未激活",
            message = "请在 LSPosed 管理器中启用本模块，\n勾选需要欺骗的应用后重启目标应用。"
        )
        else -> SpoofingScreen(viewModel = viewModel, uiState = uiState)
    }
}

@Composable
fun InitializingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(36.dp))
            }
            Text("LocationSpoofer", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            Text("正在初始化环境...", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun BlockingScreen(icon: ImageVector, title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBg).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape)
                    .background(Color(0xFFF85149).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFF85149), modifier = Modifier.size(40.dp))
            }
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            // ★ 关键：阻止父级 ScrollView 拦截地图的触摸事件
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
fun SpoofingScreen(viewModel: MainViewModel, uiState: AppState) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {

        // ── 顶部 Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("LocationSpoofer", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            // 运行状态指示
            AnimatedContent(
                targetState = uiState.isSpoofingActive,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
            ) { active ->
                if (active) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Accent))
                        Spacer(Modifier.width(6.dp))
                        Text("运行中", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(TextSecondary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TextSecondary))
                        Spacer(Modifier.width(6.dp))
                        Text("待机", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Divider(color = DividerColor, thickness = 0.5.dp)

        // ── 地图区域（固定高度，不在滚动容器内，彻底解决冲突）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            AMapView(modifier = Modifier.fillMaxSize()) { map ->
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.9042, 116.4074), 15f))
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
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )

            // 地图底部渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, DarkBg)))
            )
        }

        // ── 滚动内容区 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Wi-Fi 状态卡
            if (uiState.isSpoofingActive) {
                WifiStatusCard(uiState)
                Spacer(Modifier.height(12.dp))
            }

            // 坐标输入卡
            CoordinateInputCard(viewModel, uiState)
            Spacer(Modifier.height(12.dp))

            // 操作按钮
            ActionButton(viewModel, uiState)
            Spacer(Modifier.height(20.dp))

            // LSPosed 应用列表
            SectionHeader(icon = Icons.Outlined.AppRegistration, title = "LSPosed 作用域")
            Spacer(Modifier.height(8.dp))
            AppScopeCard()
            Spacer(Modifier.height(24.dp))
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
            CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(statusText, color = iconTint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CoordinateInputCard(viewModel: MainViewModel, uiState: AppState) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Outlined.PinDrop, title = "目标坐标")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.longitudeInput,
                onValueChange = { viewModel.updateLongitude(it) },
                label = { Text("经度") },
                placeholder = { Text("拖动地图自动获取", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Outlined.East, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
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
                placeholder = { Text("拖动地图自动获取", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Outlined.North, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
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
    unfocusedBorderColor = DividerColor,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    disabledBorderColor = DividerColor.copy(alpha = 0.5f),
    disabledTextColor = TextSecondary,
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
        modifier = Modifier.fillMaxWidth().height(52.dp),
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
fun AppScopeCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            RECOMMENDED_APPS.forEachIndexed { index, app ->
                AppScopeRow(app)
                if (index < RECOMMENDED_APPS.lastIndex) {
                    Divider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun AppScopeRow(app: RecommendedApp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(AccentBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(app.icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(app.packageName, color = TextSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(title.uppercase(), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    }
}
