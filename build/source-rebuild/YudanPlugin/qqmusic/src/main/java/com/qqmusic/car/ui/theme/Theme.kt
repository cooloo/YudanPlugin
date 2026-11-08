package com.qqmusic.car.ui.theme

import com.egogame.music.plugin.common.LocalPluginDarkTheme
import com.egogame.music.plugin.common.LocalPluginUi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlin.math.min

/** 页面语义色由宿主主题提供。 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val sidebar: Color,
    val card: Color,
    val fill: Color,
    val label: Color,
    val secondary: Color,
    val separator: Color,
    val glass: Color,
    val glassBorder: Color,
    val accent: Color,
)

private val Light = AppColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    sidebar = Color(0xFFF5F5F7),
    card = Color(0xFFFFFFFF),
    fill = Color(0x1F787880),
    label = Color(0xFF000000),
    secondary = Color(0x993C3C43),
    separator = Color(0x2E3C3C43),
    glass = Color(0xF2FAFAFC),
    glassBorder = Color(0xFFFFFFFF),
    accent = Color(0xFF00B578),
)

private val Dark = AppColors(
    isDark = true,
    background = Color(0xFF000000),
    sidebar = Color(0xFF1C1C1E),
    card = Color(0xFF1C1C1E),
    fill = Color(0x3D787880),
    label = Color(0xFFFFFFFF),
    secondary = Color(0x99EBEBF5),
    separator = Color(0x99545458),
    glass = Color(0xF22A2A2E),
    glassBorder = Color(0x1FFFFFFF),
    accent = Color(0xFF16D991),
)

val LocalAppColors = staticCompositionLocalOf { Light }

/** 是否横屏布局（侧边栏）。 */
val LocalLandscape = staticCompositionLocalOf { true }

object K {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

/** 采用宿主主题状态和系统原始密度，保留系统字体缩放。 */
@Composable
fun QQMusicTheme(content: @Composable () -> Unit) {
    val dark = LocalPluginDarkTheme.current
    val host = LocalPluginUi.current
    val surface = Color(host.color("music.surface", dark))
    val text = Color(host.color("music.text", dark))
    val colors = (if (dark) Dark else Light).copy(
        background = Color(host.color("music.background", dark)),
        sidebar = surface,
        card = surface,
        fill = text.copy(alpha = if (dark) .16f else .08f),
        label = text,
        secondary = Color(host.color("music.secondary", dark)),
        separator = text.copy(alpha = .16f),
        glass = surface.copy(alpha = .95f),
        glassBorder = text.copy(alpha = .12f),
        accent = Color(host.color("music.primary", dark)),
    )
    val scheme = if (colors.isDark) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.background)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.background)
    }
    BoxWithConstraints {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val landscape = widthPx >= heightPx
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalLandscape provides landscape,
        ) {
            MaterialTheme(colorScheme = scheme, typography = com.egogame.music.plugin.common.pluginTypography()) {
                // 去掉 Material 默认的固定行高，大字号多行文本才不会重叠
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = com.egogame.music.plugin.common.pluginFontFamily()), content = content)
            }
        }
    }
}
