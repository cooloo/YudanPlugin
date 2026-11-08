package com.egogame.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily

/** 可选 Compose ABI 单独锁定，不进入通用根接口与纯资源插件。 */
interface PluginComposeUiServices {
    @Composable fun fontFamily(): FontFamily
    fun icon(name: String): ImageVector

    companion object { const val CAPABILITY = "yudan.ui.compose.host" }
}
