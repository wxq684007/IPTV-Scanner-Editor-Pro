package com.iptv.scanner.editor.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/**
 * 判断当前是否为深色主题。
 * 通过 surface 颜色的亮度判断（luminance < 0.5 = 深色）。
 */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean {
    return MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

/**
 * 播放器控制层覆盖颜色 — 随主题自适应。
 *
 * 视频控制层叠在视频上方，需要遮罩保证对比度：
 * - 深色主题：深色半透明遮罩 + 白色图标/文字
 * - 浅色主题：浅色半透明遮罩 + 深色图标/文字
 * 强调色（进度条、激活态）在两种主题下统一使用蓝色。
 */
data class PlayerOverlayColors(
    val scrim: Color,           // 控制层半透明背景
    val topBarBg: Color,        // 顶部信息条背景
    val infoBarBg: Color,       // 竖屏信息栏背景
    val iconTint: Color,        // 图标默认颜色
    val iconTintActive: Color,  // 激活态图标颜色（如静音/锁定）
    val textPrimary: Color,     // 主文字色
    val textSecondary: Color,   // 次要文字色
    val accent: Color,          // 强调色（进度条/激活按钮）
    val trackInactive: Color,   // 进度条未激活轨道色
    val badgeBg: Color,         // 徽章背景色
    val badgeText: Color,       // 徽章文字色
    val divider: Color,         // 分隔线/边框色
)

/**
 * 获取当前主题下的播放器控制层颜色。
 */
@Composable
@ReadOnlyComposable
fun rememberPlayerOverlayColors(): PlayerOverlayColors {
    val dark = isDarkTheme()
    return if (dark) {
        PlayerOverlayColors(
            scrim = Color(0x66000000),
            topBarBg = Color(0xF0202A40),      // 深蓝（信息栏背景，与纯黑视频区域区分）
            infoBarBg = Color(0xF0202A40),      // 与信息栏同色（控制栏/活动区域背景）
            iconTint = Color.White,
            iconTintActive = Color(0xFFFFA500),
            textPrimary = Color.White,
            textSecondary = Color(0xFFAAAAAA),
            accent = Color(0xFF4A9EFF),
            trackInactive = Color(0xFF3A3A5C),
            badgeBg = Color(0xFF4A9EFF).copy(alpha = 0.15f),
            badgeText = Color(0xFF8AB4F8),
            divider = Color(0xFF2A2A4E),
        )
    } else {
        PlayerOverlayColors(
            scrim = Color(0x66FFFFFF),
            topBarBg = Color(0xCCFFFFFF),
            infoBarBg = Color(0xFFF0F0F0),
            iconTint = Color(0xFF212121),
            iconTintActive = Color(0xFFE65100),
            textPrimary = Color(0xFF212121),
            textSecondary = Color(0xFF666666),
            accent = Color(0xFF1A73E8),
            trackInactive = Color(0xFFCCCCCC),
            badgeBg = Color(0xFF757575).copy(alpha = 0.12f),
            badgeText = Color(0xFF616161),
            divider = Color(0xFFE0E0E0),
        )
    }
}
