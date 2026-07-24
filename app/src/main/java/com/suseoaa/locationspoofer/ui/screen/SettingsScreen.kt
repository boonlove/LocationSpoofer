package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.DarkMode
import com.suseoaa.locationspoofer.data.model.MapEngine
import com.suseoaa.locationspoofer.ui.extensions.isEnable
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.keyColorOptions
import com.suseoaa.locationspoofer.ui.screen.spoofing.SpoofingIntent
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    isDark: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = koinViewModel()
    var darkModeExpanded by remember { mutableStateOf(false) }
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduApiKey by remember(uiState.baiduApiKey) { mutableStateOf(uiState.baiduApiKey) }
    var localGoogleApiKey by remember(uiState.googleApiKey) { mutableStateOf(uiState.googleApiKey) }
    var localWigleToken by remember(uiState.wigleToken) { mutableStateOf(uiState.wigleToken) }
    var localOpencellidToken by remember(uiState.opencellidToken) { mutableStateOf(uiState.opencellidToken) }
    var localBaiduStyleId by remember { mutableStateOf(settingsViewModel.getBaiduStyleId()) }
    val clipboardManager = LocalClipboardManager.current
    val spoofingUiState by viewModel.spoofingUiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.topBarBackground(isDark))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
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
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            // 软件设置
            Text(
                "软件设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            // 深色模式
            val darkModeOptions = listOf(DarkMode.SYSTEM to "跟随系统", DarkMode.LIGHT to "浅色", DarkMode.DARK to "深色")
            val darkModeLabel = darkModeOptions.firstOrNull { it.first == uiState.darkMode }?.second ?: "跟随系统"
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { darkModeExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("深色模式", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(darkModeLabel, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }

                DropdownMenu(
                    expanded = darkModeExpanded,
                    onDismissRequest = { darkModeExpanded = false},
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    darkModeOptions.forEach { (darkMode, label) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        color = if (darkMode == uiState.darkMode) AccentBlue else MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (uiState.darkMode == darkMode) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                darkModeExpanded = false
                                viewModel.setDarkMode(darkMode)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 主题颜色（种子色调色板，由 MaterialKolor 派生全套 M3 调色板）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text("主题颜色", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uiState.keyColor == 0) "跟随系统壁纸动态色" else "自定义种子色生成调色板",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 跟随系统动态色（keyColor == 0）
                    KeyColorSwatch(
                        swatchColor = null,
                        isSelected = uiState.keyColor == 0,
                        onClick = { viewModel.setKeyColor(0) }
                    )
                    // 15 色预设种子色
                    keyColorOptions.forEach { colorArgb ->
                        KeyColorSwatch(
                            swatchColor = Color(colorArgb),
                            isSelected = uiState.keyColor == colorArgb,
                            onClick = { viewModel.setKeyColor(colorArgb) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 屏幕密度（本应用 UI 缩放，80%~110%）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // 本地拖动值：拖动时只更新预览，松手(onValueChangeFinished)才落库
                var sliderValue by remember(uiState.screenDensity) {
                    mutableStateOf(uiState.screenDensity.toFloat())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "界面缩放",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "调整全局显示比例",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${sliderValue.toInt()}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.setScreenDensity(sliderValue.toInt())
                    },
                    valueRange = 80f..110f,
                    steps = 29
                )
            }
            Spacer(Modifier.height(8.dp))

            // 首页地图模式
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { viewModel.handleSpoofingIntent(SpoofingIntent.SetMapFullscreen(!spoofingUiState.isMapFullscreen)) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "首页地图全屏",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (spoofingUiState.isMapFullscreen) "首页地图覆盖全屏显示" else "首页地图跟随底部面板自适应",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = spoofingUiState.isMapFullscreen,
                    onCheckedChange = null,
                    colors = AppColors.switchColors(isDark)
                )
            }
            Spacer(Modifier.height(24.dp))

            Text(
                stringResource(R.string.map_config),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = context.packageName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_package_name)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied_to_clipboard),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.appSha1,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_sha1)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(uiState.appSha1))
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied_to_clipboard),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
            Spacer(Modifier.height(16.dp))

            // 地图引擎选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val engines = listOf(
                    MapEngine.AUTO to "自动匹配",
                    MapEngine.AMAP to "高德",
                    MapEngine.BAIDU to "百度",
                    MapEngine.GOOGLE to "谷歌"
                )
                engines.forEach { (engine, label) ->
                    if (!engine.isEnable()) return@forEach  // 地图引擎不可用时不显示该地图引擎
                    val isSelected = uiState.mapEngine == engine
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (isSelected) AccentBlue else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setMapEngine(engine) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 动画 Key 输入
            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.AMAP) {
                PasswordField(
                    value = localAmapApiKey,
                    onValueChange = { localAmapApiKey = it },
                    label = { Text(stringResource(R.string.custom_amap_key)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.custom_amap_key_hint),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = AccentBlue
                    )
                )
            }

            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.BAIDU) {
                Column {
                    PasswordField(
                        value = localBaiduApiKey,
                        onValueChange = { localBaiduApiKey = it },
                        label = { Text(stringResource(R.string.custom_baidu_key)) },
                        placeholder = {
                            Text(
                                stringResource(R.string.custom_baidu_key_hint),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = AccentBlue
                        )
                    )

                    PasswordField(
                        value = localBaiduStyleId,
                        onValueChange = { localBaiduStyleId = it },
                        label = {Text(stringResource(R.string.custom_baidu_style_id)) },
                        placeholder = {
                            Text(stringResource(R.string.custom_baidu_style_id_hint),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = AccentBlue
                        )
                    )
                }
            }

            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.GOOGLE) {
                PasswordField(
                    value = localGoogleApiKey,
                    onValueChange = { localGoogleApiKey = it },
                    label = { Text(stringResource(R.string.custom_google_key)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.custom_google_key_hint),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = AccentBlue
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.wigle_config),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            PasswordField(
                value = localWigleToken,
                onValueChange = { localWigleToken = it },
                label = { Text(stringResource(R.string.custom_wigle_token)) },
                placeholder = {
                    Text(
                        stringResource(R.string.custom_wigle_token_hint),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue
                )
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.opencellid_config),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            PasswordField(
                value = localOpencellidToken,
                onValueChange = { localOpencellidToken = it },
                label = { Text(stringResource(R.string.custom_opencellid_token)) },
                placeholder = {
                    Text(
                        stringResource(R.string.custom_opencellid_token_hint),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue
                )
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.setAmapApiKey(localAmapApiKey)
                    viewModel.setBaiduApiKey(localBaiduApiKey)
                    viewModel.setGoogleApiKey(localGoogleApiKey)
                    viewModel.setWigleApiToken(localWigleToken)
                    viewModel.setOpencellidApiToken(localOpencellidToken)
                    settingsViewModel.setBaiduStyleId(localBaiduStyleId)
                    Toast.makeText(
                        context,
                        context.getString(R.string.restart_required_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(stringResource(R.string.save), modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var privacyEnabled by remember { mutableStateOf(true) }
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused; if (!it.isFocused) privacyEnabled = true },
        label = label,
        placeholder = placeholder,
        visualTransformation =
            if (!privacyEnabled || isFocused) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        trailingIcon = {
            IconButton(
                enabled = !isFocused,
                onClick = { privacyEnabled = !privacyEnabled }
            ) {
                Icon(
                    imageVector = if (!privacyEnabled || isFocused) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        },
        singleLine = singleLine,
        colors = colors
    )
}

/**
 * 主题种子色色块。
 * - [swatchColor] == null 表示「跟随系统动态色」，显示 AutoMode 图标
 * - [swatchColor] != null 为自定义种子色，选中时显示白色对勾
 */
@Composable
private fun KeyColorSwatch(
    swatchColor: Color?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(swatchColor ?: MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 3.dp else if (swatchColor == null) 1.dp else 0.dp,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    swatchColor == null -> MaterialTheme.colorScheme.outline
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (swatchColor == null) {
            // 跟随系统：显示自动模式图标
            Icon(
                Icons.Rounded.AutoMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else if (isSelected) {
            // 自定义色选中：白色对勾
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
