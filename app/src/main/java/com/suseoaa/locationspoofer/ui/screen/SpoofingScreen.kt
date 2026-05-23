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
import androidx.compose.ui.res.stringResource
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.components.AppMapController
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.BuildConfig

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
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val topBarBg = AppColors.surface(isDark)
    val isDomestic = viewModel.isDomesticEnvironment()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppPoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current


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
    var smallMapRef by remember { mutableStateOf<AppMapController?>(null) }
    val lat = uiState.latitudeInput.toDoubleOrNull()
    val lng = uiState.longitudeInput.toDoubleOrNull()
    LaunchedEffect(lat, lng, smallMapRef) {
        if (lat != null && lng != null) {
            smallMapRef?.animateCamera(lat, lng)
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
            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Settings, stringResource(R.string.settings),
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
                    performPoiSearch(context, searchQuery, isDomestic) { results ->
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
                                Text(poi.snippet, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // 地图缩略图
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AppMapView(isDomestic = isDomestic, modifier = Modifier.fillMaxSize()) { map ->
                smallMapRef = map
                map.disableUiControls()
                val initLat = uiState.latitudeInput.toDoubleOrNull() ?: 39.9042
                val initLng = uiState.longitudeInput.toDoubleOrNull() ?: 116.4074
                map.moveCamera(initLat, initLng, 15f)

                // 移动地图即选点
                map.setOnCameraChangeListener { lat, lng ->
                    viewModel.confirmMapPoint(lat, lng)
                }
            }

            // 十字准星（始终显示在中间）
            Icon(
                Icons.Rounded.AddLocationAlt, null,
                tint = AccentBlue.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(32.dp).padding(bottom = 16.dp) // 准星底部对齐中心
            )

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
                    Text(stringResource(R.string.fullscreen_selection), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
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
                SectionHeader(Icons.Outlined.Bookmarks, stringResource(R.string.collection_list), isDark)
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

            SectionHeader(Icons.Outlined.AppRegistration, stringResource(R.string.lsposed_scope), isDark)
            Spacer(Modifier.height(8.dp))
            AppScopeCard(isDark)
            Spacer(Modifier.height(24.dp))
        }
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
                viewModel.updateLatitude(loc.lat.toString())
                viewModel.updateLongitude(loc.lng.toString())
                showSavedLocations = false
            },
            onDelete = { loc -> viewModel.removeSavedLocation(loc) }
        )
    }

    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.settings),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        stringResource(R.string.select_language),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    LANGUAGES.forEach { lang ->
                        LanguageItem(
                            option = lang,
                            isSelected = viewModel.getSavedLanguage() == lang.code,
                            onClick = {
                                viewModel.selectLanguage(lang.code)
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.forLanguageTags(lang.code)
                                )
                                showSettings = false
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        stringResource(R.string.amap_config),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    OutlinedTextField(
                        value = uiState.appSha1,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.app_sha1)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.appSha1))
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
                            }
                        },
                        colors = coordinateFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
                    
                    OutlinedTextField(
                        value = localAmapApiKey,
                        onValueChange = { localAmapApiKey = it },
                        label = { Text(stringResource(R.string.custom_amap_key)) },
                        placeholder = { Text(stringResource(R.string.custom_amap_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = coordinateFieldColors()
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSettings = false }) {
                            Text(stringResource(R.string.close))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { 
                                viewModel.setAmapApiKey(localAmapApiKey)
                                Toast.makeText(context, context.getString(R.string.restart_required_hint), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
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
                SectionHeader(Icons.Outlined.PinDrop, stringResource(R.string.target_coordinates), isDark)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSaveClick) {
                    Icon(Icons.Rounded.StarBorder, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = uiState.longitudeInput,
                onValueChange = { viewModel.updateLongitude(it) },
                label = { Text(stringResource(R.string.longitude)) },
                placeholder = { Text(stringResource(R.string.coordinate_hint), color = textSecondary) },
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
                label = { Text(stringResource(R.string.latitude)) },
                placeholder = { Text(stringResource(R.string.coordinate_hint), color = textSecondary) },
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
            Text(stringResource(R.string.stop_simulation), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.fixed_simulation), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                        Text(stringResource(app.nameRes), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                Text(stringResource(R.string.saved_locations), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                if (savedLocations.isEmpty()) {
                    Text(stringResource(R.string.no_saved_locations), color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
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
    keyword: String,
    isDomestic: Boolean,
    onResult: (List<AppPoiItem>) -> Unit
) {
    if (isDomestic) {
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
                    android.util.Log.d("SpoofingScreen", "Autocomplete got ${predictions.size} predictions")
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
                    android.util.Log.e("SpoofingScreen", "Autocomplete failed: ${exception.message}", exception)
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Search Catch Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.e("SpoofingScreen", "Places API exception: ${e.message}", e)
            onResult(emptyList())
        }
    }
}
