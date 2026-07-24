package com.suseoaa.locationspoofer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.suseoaa.locationspoofer.data.model.DarkMode
import com.suseoaa.locationspoofer.ui.extensions.isDark

/**
 * 预设主题种子色调色板。
 * keyColor == 0 表示「跟随系统动态色」；非 0 即用户选定种子色。
 */
val keyColorOptions: List<Int> = listOf(
    0xFF388BFD.toInt(), // 默认蓝
    0xFFF44336.toInt(),
    0xFFE91E63.toInt(),
    0xFF9C27B0.toInt(),
    0xFF673AB7.toInt(),
    0xFF3F51B5.toInt(),
    0xFF2196F3.toInt(),
    0xFF00BCD4.toInt(),
    0xFF009688.toInt(),
    0xFF4FAF50.toInt(),
    0xFFFFEB3B.toInt(),
    0xFFFFC107.toInt(),
    0xFFFF9800.toInt(),
    0xFF795548.toInt(),
    0xFF607D8F.toInt(),
    0xFFFF9CA8.toInt(),
)

/**
 * 基于种子色动态生成 Material 3 [ColorScheme]。
 *
 * - seedColor == [Color.Unspecified]（keyColor == 0）→ 取系统动态色 primary 作为种子
 * - 否则用用户选定的 keyColor
 *
 * 使用 MaterialKolor 从单一种子色派生全套 M3 调色板，固定 [PaletteStyle.TonalSpot]。
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun rememberLocationSpooferColorScheme(
    seedColor: Color,
    isDark: Boolean,
): ColorScheme {
    val context = LocalContext.current
    val seed = if (seedColor == Color.Unspecified) {
        (if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        seedColor
    }
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = false,
        style = PaletteStyle.TonalSpot,
    )
}

/**
 * 对 [ColorScheme] 每个 color 字段做 [animateColorAsState] 平滑过渡，
 * 实现主题切换/种子色切换时的颜色动画。
 */
@Composable
fun ColorScheme.animateAsState(): ColorScheme {
    @Composable
    fun animateColor(color: Color): Color = animateColorAsState(
        targetValue = color,
        animationSpec = spring(),
        label = "theme_color_animation"
    ).value

    return ColorScheme(
        primary = animateColor(primary),
        onPrimary = animateColor(onPrimary),
        primaryContainer = animateColor(primaryContainer),
        onPrimaryContainer = animateColor(onPrimaryContainer),
        inversePrimary = animateColor(inversePrimary),
        secondary = animateColor(secondary),
        onSecondary = animateColor(onSecondary),
        secondaryContainer = animateColor(secondaryContainer),
        onSecondaryContainer = animateColor(onSecondaryContainer),
        tertiary = animateColor(tertiary),
        onTertiary = animateColor(onTertiary),
        tertiaryContainer = animateColor(tertiaryContainer),
        onTertiaryContainer = animateColor(onTertiaryContainer),
        background = animateColor(background),
        onBackground = animateColor(onBackground),
        surface = animateColor(surface),
        onSurface = animateColor(onSurface),
        surfaceVariant = animateColor(surfaceVariant),
        onSurfaceVariant = animateColor(onSurfaceVariant),
        surfaceTint = animateColor(surfaceTint),
        inverseSurface = animateColor(inverseSurface),
        inverseOnSurface = animateColor(inverseOnSurface),
        error = animateColor(error),
        onError = animateColor(onError),
        errorContainer = animateColor(errorContainer),
        onErrorContainer = animateColor(onErrorContainer),
        outline = animateColor(outline),
        outlineVariant = animateColor(outlineVariant),
        scrim = animateColor(scrim),
        surfaceBright = animateColor(surfaceBright),
        surfaceDim = animateColor(surfaceDim),
        surfaceContainer = animateColor(surfaceContainer),
        surfaceContainerHigh = animateColor(surfaceContainerHigh),
        surfaceContainerHighest = animateColor(surfaceContainerHighest),
        surfaceContainerLow = animateColor(surfaceContainerLow),
        surfaceContainerLowest = animateColor(surfaceContainerLowest),
        primaryFixed = animateColor(primaryFixed),
        primaryFixedDim = animateColor(primaryFixedDim),
        onPrimaryFixed = animateColor(onPrimaryFixed),
        onPrimaryFixedVariant = animateColor(onPrimaryFixedVariant),
        secondaryFixed = animateColor(secondaryFixed),
        secondaryFixedDim = animateColor(secondaryFixedDim),
        onSecondaryFixed = animateColor(onSecondaryFixed),
        onSecondaryFixedVariant = animateColor(onSecondaryFixedVariant),
        tertiaryFixed = animateColor(tertiaryFixed),
        tertiaryFixedDim = animateColor(tertiaryFixedDim),
        onTertiaryFixed = animateColor(onTertiaryFixed),
        onTertiaryFixedVariant = animateColor(onTertiaryFixedVariant)
    )
}

/**
 * LocationSpoofer 主题入口。
 *
 * 组合 [DarkMode]（SYSTEM/LIGHT/DARK）与 keyColor（0=跟随系统动态色，非 0=用户种子色），
 * 经 [rememberLocationSpooferColorScheme] 生成 ColorScheme，再经 [animateAsState] 平滑过渡，
 * 最终套用 [MaterialTheme]，并通过 [SideEffect] 同步状态栏/导航栏图标明暗。
 */
@Composable
fun LocationSpooferTheme(
    darkMode: DarkMode,
    keyColor: Int,
    content: @Composable () -> Unit,
) {
    val isDark = darkMode.isDark()

    val seedColor = if (keyColor == 0) Color.Unspecified else Color(keyColor)
    val colorScheme = rememberLocationSpooferColorScheme(
        seedColor = seedColor,
        isDark = isDark,
    )
    val animatedColorScheme = colorScheme.animateAsState()

    // 根据当前主题切换状态栏图标与导航栏颜色
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content,
    )
}
