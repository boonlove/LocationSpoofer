package com.suseoaa.locationspoofer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.suseoaa.locationspoofer.data.model.AppInfoItem
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCoordinateScreen(
    viewModel: MainViewModel,
    uiState: AppState,
    onBack: () -> Unit
) {
    var showSystemApps by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }

    val appsToShow = uiState.hookedApps.filter { app ->
        showSystemApps || !app.isSystem
    }

    val milkyWhiteColorScheme = lightColorScheme(
        background = androidx.compose.ui.graphics.Color(0xFFFDFBF7),
        surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        onBackground = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
        onSurface = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF5F0E6),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A4A4A),
        primary = androidx.compose.ui.graphics.Color(0xFFC7A27C),
        onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFF4E5D3),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF5E4226)
    )

    MaterialTheme(colorScheme = milkyWhiteColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                title = { 
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.custom_coordinate_algo),
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.show_system_apps),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (appsToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.no_hooked_apps), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(appsToShow, key = { it.packageName }) { app ->
                        val currentSys = uiState.appCoordinateSystems[app.packageName]
                        AppItem(
                            appInfo = app,
                            currentCoordinateSystem = currentSys,
                            onClick = { selectedApp = app }
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
        
        // Selection Dialog
        selectedApp?.let { app ->
            CoordinateSelectionDialog(
                appInfo = app,
                currentSystem = uiState.appCoordinateSystems[app.packageName] ?: "GCJ-02",
                onDismiss = { selectedApp = null },
                onSelect = { sys ->
                    if (sys == "GCJ-02") {
                        // If it's default GCJ-02, we can just remove it from the map to keep config clean
                        viewModel.removeAppCoordinateSystem(app.packageName)
                    } else {
                        viewModel.setAppCoordinateSystem(app.packageName, sys)
                    }
                    selectedApp = null
                }
            )
        }
    }
}
}

@Composable
fun AppItem(
    appInfo: AppInfoItem,
    currentCoordinateSystem: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(
            packageName = appInfo.packageName,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = appInfo.packageName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        if (currentCoordinateSystem != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = currentCoordinateSystem,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageDrawable(null)
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinateSelectionDialog(
    appInfo: AppInfoItem,
    currentSystem: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val systems = listOf(
        "GCJ-02" to androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.gcj02_desc),
        "WGS-84" to androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.wgs84_desc),
        "BD-09"  to androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.bd09_desc)
    )

    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.set_coordinate_algo),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            }
        },
        text = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.select_coord_sys_desc, appInfo.appName),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                systems.forEach { (sys, desc) ->
                    val isSelected = sys == currentSystem
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(sys) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sys,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides currentContext,
                androidx.compose.ui.platform.LocalConfiguration provides currentConfiguration
            ) {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.suseoaa.locationspoofer.R.string.cancel))
            }
            }
        }
    )
}
