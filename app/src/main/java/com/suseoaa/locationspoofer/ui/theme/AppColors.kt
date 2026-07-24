package com.suseoaa.locationspoofer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 动态颜色兼容层。
 *
 * 原本项目中的硬编码颜色常量（AccentBlue=0xFF388BFD 等）已迁移为基于 MaterialKolor 种子色
 * 动态生成的 [ColorScheme]。为最小化对调用方的改动并保证不遗漏，这里将原符号重新定义为
 * **@Composable getter**，其值实时取自 [MaterialTheme.colorScheme]，使所有历史调用点自动获得动态颜色。
 *
 * 真正的颜色生成逻辑见 [LocationSpooferTheme] / [rememberLocationSpooferColorScheme]。
 */

// 强调色：跟随当前 colorScheme 主色/次色/三色/错误色
val AccentBlue: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val OnAccentBlue: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary
val AccentGreen: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
val OnAccentGreen: Color
    @Composable get() = MaterialTheme.colorScheme.onSecondary
val AccentOrange: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
val OnAccentOrange: Color
    @Composable get() = MaterialTheme.colorScheme.onTertiary
val ErrorRed: Color
    @Composable get() = MaterialTheme.colorScheme.error
val OnErrorRed: Color
    @Composable get() = MaterialTheme.colorScheme.onError

// 默认强调色（固定）
val DefaultAccentBlue = Color(0xFF388BFD)
val DefaultAccentGreen = Color(0xFF2EA043)
val DefaultAccentOrange = Color(0xFFD29922)
val DefaultErrorRed = Color(0xFFF85149)
/**
 * 主题辅助方法。保留 `isDark` 参数以兼容历史调用签名，但内部不再依赖它——
 * 所有颜色均来自当前 [MaterialTheme.colorScheme]，由 [LocationSpooferTheme] 统一解析。
 */
object AppColors {
    @Composable
    fun textSecondary(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
        MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun surface(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
        MaterialTheme.colorScheme.surface

    @Composable
    fun cardBackground(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
        MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun topBarBackground(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
        MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun background(@Suppress("UNUSED_PARAMETER") isDark: Boolean): Color =
        MaterialTheme.colorScheme.background

    @Composable
    fun switchColors(@Suppress("UNUSED_PARAMETER") isDark: Boolean): SwitchColors =
        SwitchDefaults.colors(
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
}
