package com.iptv.scanner.editor.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * IPTV 应用主题。支持深色/浅色/跟随系统三种模式。
 *
 * 颜色对应（colors.xml → Compose）：
 * - primary #1a1a2e → 深紫蓝（背景）
 * - surface #16213e → 表面色
 * - accent #4CAF50 → 主强调色（成功/激活）
 * - text_primary #E0E0E0 → 主文字色
 * - text_secondary #9E9E9E → 次要文字色
 * - error #F44336 → 错误色
 * - warning #FF9800 → 警告色
 */

// 主色板（深色）
private val DarkPrimary = Color(0xFF1A1A2E)
private val DarkPrimaryDark = Color(0xFF0F0F1E)
private val DarkSurface = Color(0xFF16213E)
private val DarkSurfaceVariant = Color(0xFF1F2D4D)
private val DarkBackground = Color(0xFF0A0A14)

// 主色板（浅色）
private val LightPrimary = Color(0xFF3F51B5)
private val LightPrimaryDark = Color(0xFF303F9F)
private val LightSurface = Color(0xFFF5F5F5)
private val LightSurfaceVariant = Color(0xFFEEEEEE)
private val LightBackground = Color(0xFFFAFAFA)

// 强调色
private val Accent = Color(0xFF4CAF50)        // 绿色（激活/成功）
private val AccentDim = Color(0xFF2E7D32)    // 暗绿
private val OnAccent = Color(0xFFFFFFFF)

// 文字色（深色）
private val DarkTextPrimary = Color(0xFFE0E0E0)
private val DarkTextSecondary = Color(0xFF9E9E9E)

// 文字色（浅色）
private val LightTextPrimary = Color(0xFF212121)
private val LightTextSecondary = Color(0xFF757575)

// 状态色
private val ErrorColor = Color(0xFFF44336)
private val WarningColor = Color(0xFFFF9800)
private val InfoColor = Color(0xFF4A9EFF)

// 播放器专用色
val PlayerScrim = Color(0xAA000000)          // 控制层半透明背景
val PlayerScrimSolid = Color(0xF0000000)      // 控制层不透明背景
val PlayerAccent = Color(0xFF4A9EFF)          // 进度条/激活按钮色
val PlayerBadgeLive = Color(0xFFE53935)       // 直播 LIVE 徽章
val PlayerBadgeCatchup = Color(0xFFFF9800)    // 回看徽章

private val IptvDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDim,
    onPrimaryContainer = OnAccent,
    secondary = InfoColor,
    onSecondary = OnAccent,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = WarningColor,
    onTertiary = OnAccent,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = ErrorColor,
    onError = OnAccent,
    outline = DarkTextSecondary,
)

private val IptvLightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDim,
    onPrimaryContainer = OnAccent,
    secondary = InfoColor,
    onSecondary = OnAccent,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = WarningColor,
    onTertiary = OnAccent,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = ErrorColor,
    onError = OnAccent,
    outline = LightTextSecondary,
)

/**
 * 应用主题入口。
 * @param themeMode "dark"=深色, "light"=浅色, "system"=跟随系统
 */
@Composable
fun IptvTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true  // "dark" 或默认
    }
    MaterialTheme(
        colorScheme = if (useDark) IptvDarkColorScheme else IptvLightColorScheme,
        content = content
    )
}
