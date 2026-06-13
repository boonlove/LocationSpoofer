package com.suseoaa.locationspoofer.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.AppMapProvider
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var localAmapApiKey by remember(uiState.amapApiKey) { mutableStateOf(uiState.amapApiKey) }
    var localBaiduMapsApiKey by remember(uiState.baiduMapsApiKey) { mutableStateOf(uiState.baiduMapsApiKey) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
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

            Text(
                stringResource(R.string.map_provider_api_kry_config),
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
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = localAmapApiKey,
                onValueChange = { localAmapApiKey = it },
                label = { Text(stringResource(R.string.custom_amap_key)) },
                placeholder = { Text(stringResource(R.string.custom_amap_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = localBaiduMapsApiKey,
                onValueChange = { localBaiduMapsApiKey = it },
                label = { Text(stringResource(R.string.custom_baidu_maps_key)) },
                placeholder = { Text(stringResource(R.string.custom_amap_key_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.setAmapApiKey(localAmapApiKey)
                    viewModel.setBaiduMapsKey(localBaiduMapsApiKey)
                    Toast.makeText(context, context.getString(R.string.restart_required_hint), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(stringResource(R.string.save), modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(24.dp))

            // 地图服务切换
            Text(
                stringResource(R.string.map_provider),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            Map_Provider_LIST.forEach { mapProvider ->
                MapProviderItem(
                    option = mapProvider,
                    isSelected = uiState.mapProvider == mapProvider.value,
                    onClick = {
                        when (mapProvider.value) {
                            AppMapProvider.GOOGLE_MAPS -> {
                                if (com.suseoaa.locationspoofer.BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()) {
                                    viewModel.setMapProvider(mapProvider.value)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.google_maps_api_key_not_configured), Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> {
                                viewModel.setMapProvider(mapProvider.value)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

data class MapProviderOption(val nameResId: Int, val value: AppMapProvider)

val Map_Provider_LIST = listOf(
    MapProviderOption(R.string.amap, AppMapProvider.AMAP),
    MapProviderOption(R.string.baidu_maps, AppMapProvider.BAIDU_MAPS),
    MapProviderOption(R.string.google_map, AppMapProvider.GOOGLE_MAPS)
)

@Composable
fun MapProviderItem(
    option: MapProviderOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, AccentBlue) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(option.nameResId),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onBackground
                )
            }
            if (isSelected) {
                RadioButton(selected = true, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AccentBlue))
            }
        }
    }
}
