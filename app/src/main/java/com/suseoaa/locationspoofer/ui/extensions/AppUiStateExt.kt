package com.suseoaa.locationspoofer.ui.extensions

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.suseoaa.locationspoofer.data.model.DarkMode

@Composable
fun DarkMode.isDark(): Boolean = when (this) {
    DarkMode.DARK -> true
    DarkMode.LIGHT -> false
    else -> isSystemInDarkTheme()
}
