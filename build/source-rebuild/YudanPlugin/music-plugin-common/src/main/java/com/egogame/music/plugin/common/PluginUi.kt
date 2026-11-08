package com.egogame.music.plugin.common

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import com.egogame.plugin.ui.PluginComposeUiServices

val LocalPluginComposeUi = staticCompositionLocalOf<PluginComposeUiServices> { error("缺少宿主 Compose UI 能力") }

@Composable
fun pluginFontFamily(): FontFamily = LocalPluginComposeUi.current.fontFamily()

@Composable
fun pluginTypography(): Typography {
    val family = pluginFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** 非组合页面导航元数据也需要读取图标；提供者始终是宿主共享实例。 */
object PluginIcons {
    private lateinit var services: PluginComposeUiServices
    fun configure(value: PluginComposeUiServices) { services = value }
    object Rounded {
        val ArrowBackIosNew get() = services.icon("ArrowBackIosNew")
        val BarChart get() = services.icon("BarChart")
        val Cancel get() = services.icon("Cancel")
        val CheckCircle get() = services.icon("CheckCircle")
        val ChevronRight get() = services.icon("ChevronRight")
        val Close get() = services.icon("Close")
        val Cloud get() = services.icon("Cloud")
        val DeleteSweep get() = services.icon("DeleteSweep")
        val FastForward get() = services.icon("FastForward")
        val Favorite get() = services.icon("Favorite")
        val FavoriteBorder get() = services.icon("FavoriteBorder")
        val GraphicEq get() = services.icon("GraphicEq")
        val Home get() = services.icon("Home")
        val KeyboardArrowDown get() = services.icon("KeyboardArrowDown")
        val LibraryMusic get() = services.icon("LibraryMusic")
        val MusicNote get() = services.icon("MusicNote")
        val NewReleases get() = services.icon("NewReleases")
        val Pause get() = services.icon("Pause")
        val Person get() = services.icon("Person")
        val PlayArrow get() = services.icon("PlayArrow")
        val QueueMusic get() = services.icon("QueueMusic")
        val Radio get() = services.icon("Radio")
        val Refresh get() = services.icon("Refresh")
        val Repeat get() = services.icon("Repeat")
        val RepeatOne get() = services.icon("RepeatOne")
        val Schedule get() = services.icon("Schedule")
        val Search get() = services.icon("Search")
        val Settings get() = services.icon("Settings")
        val Shuffle get() = services.icon("Shuffle")
        val SkipNext get() = services.icon("SkipNext")
        val SkipPrevious get() = services.icon("SkipPrevious")
        val ThumbDown get() = services.icon("ThumbDown")
    }
    object AutoMirrored { val Rounded get() = PluginIcons.Rounded }
}
