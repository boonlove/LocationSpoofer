package com.suseoaa.locationspoofer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 深色调色板
val DarkBg = Color(0xFF0D1117)
val SurfaceDark = Color(0xFF161B22)
val SurfaceCardDark = Color(0xFF1C2333)
val DividerColorDark = Color(0xFF30363D)
val TextPrimaryDark = Color(0xFFE6EDF3)
val TextSecondaryDark = Color(0xFF8B949E)

// 浅色调色板
val LightBg = Color(0xFFF6F8FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFFFFFFF)
val DividerColorLight = Color(0xFFD0D7DE)
val TextPrimaryLight = Color(0xFF24292F)
val TextSecondaryLight = Color(0xFF57606A)

// 强调色
val AccentBlue = Color(0xFF388BFD)
val AccentGreen = Color(0xFF2EA043)
val AccentOrange = Color(0xFFD29922)
val ErrorRed = Color(0xFFF85149)

val AppColorSchemeDark = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentGreen,
    onSecondary = Color.White,
    background = DarkBg,
    surface = SurfaceCardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    outline = DividerColorDark,
    error = ErrorRed
)

val AppColorSchemeLight = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentGreen,
    onSecondary = Color.White,
    background = LightBg,
    surface = SurfaceCardLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    outline = DividerColorLight,
    error = Color(0xFFCF222E)
)

object AppColors {
    fun textSecondary(isDark: Boolean) = if (isDark) TextSecondaryDark else TextSecondaryLight
    fun surface(isDark: Boolean) = if (isDark) SurfaceDark else SurfaceLight
}
