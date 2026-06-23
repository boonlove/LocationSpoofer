package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
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
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

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
                        .background(MaterialTheme.colorScheme.surface)
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
                    onDismissRequest = { darkModeExpanded = false }
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
                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
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
                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
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

            // Map Engine Selection
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

            // Animated Key Inputs
            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.AMAP) {
                PasswordField(
                    value = localAmapApiKey,
                    onValueChange = { localAmapApiKey = it },
                    label = { Text(stringResource(R.string.custom_amap_key)) },
                    placeholder = { Text(stringResource(R.string.custom_amap_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
                )
            }
            
            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.BAIDU) {
                Column {
                    PasswordField(
                        value = localBaiduApiKey,
                        onValueChange = { localBaiduApiKey = it },
                        label = { Text(stringResource(R.string.custom_baidu_key)) },
                        placeholder = { Text(stringResource(R.string.custom_baidu_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
                    )
                    PasswordField(
                        value = localBaiduStyleId,
                        onValueChange = { localBaiduStyleId = it },
                        label = { Text(stringResource(R.string.custom_baidu_style_id)) },
                        placeholder = { Text(stringResource(R.string.custom_baidu_style_id_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
                    )
                }
            }
            
            AnimatedVisibility(visible = uiState.mapEngine == MapEngine.GOOGLE) {
                PasswordField(
                    value = localGoogleApiKey,
                    onValueChange = { localGoogleApiKey = it },
                    label = { Text(stringResource(R.string.custom_google_key)) },
                    placeholder = { Text(stringResource(R.string.custom_google_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
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
                placeholder = { Text(stringResource(R.string.custom_wigle_token_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
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
                placeholder = { Text(stringResource(R.string.custom_opencellid_token_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedLabelColor = AccentBlue)
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
                    Toast.makeText(context, context.getString(R.string.restart_required_hint), Toast.LENGTH_SHORT).show()
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
