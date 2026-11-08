package com.egogame.music.plugin.common

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.egogame.plugin.api.PluginEnvironment
import com.egogame.plugin.api.PluginHostState
import com.egogame.plugin.music.api.MusicHostServices
import com.egogame.plugin.music.api.MusicPlugin
import com.egogame.plugin.ui.PluginUiServices
import com.egogame.plugin.ui.PluginComposeUiServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

interface MusicPage {
    fun save(): Bundle
    fun back(): Boolean
}

val LocalPluginUi = staticCompositionLocalOf<PluginUiServices> { error("音乐页面缺少宿主 UI 能力") }
val LocalPluginDarkTheme = staticCompositionLocalOf { false }

/** 字号值来自宿主资源，Compose 按原系统密度保留字体缩放。 */
@Composable
fun pluginTextSize(size: Int): TextUnit = LocalPluginUi.current.textSizeSp("music.text.$size").sp

/** 运行时与页面分开持有；只有 close 释放播放器、账号任务和宿主注册。 */
@androidx.annotation.OptIn(UnstableApi::class)
abstract class StatefulMusicPlugin<P : MusicPage> : MusicPlugin {
    protected lateinit var environment: PluginEnvironment
    protected lateinit var musicHost: MusicHostServices
    private lateinit var uiHost: PluginUiServices
    private lateinit var composeUiHost: PluginComposeUiServices
    private var scope: CoroutineScope? = null
    private var session: MediaSession? = null
    private var registration: MusicHostServices.SessionRegistration? = null
    private val pages = IdentityHashMap<View, Page<P>>()
    private var initialized = false

    private class Page<P>(val navigation: P) {
        var dark by mutableStateOf(false)
    }

    protected abstract fun initializePlatform(environment: PluginEnvironment)
    protected abstract fun preparePlayer(): Player
    protected abstract fun releasePlatform()
    protected abstract fun newPage(savedState: Bundle?): P
    @Composable protected abstract fun Content(page: P)
    protected abstract fun mediaExtras(): Bundle

    final override fun initialize(environment: PluginEnvironment) {
        check(!initialized) { "音乐插件已经初始化" }
        this.environment = environment
        musicHost = environment.requireCapability(MusicHostServices.CAPABILITY, MusicHostServices.API, MusicHostServices::class.java)
        uiHost = environment.requireCapability(PluginUiServices.CAPABILITY, 1, PluginUiServices::class.java)
        composeUiHost = environment.requireCapability(PluginComposeUiServices.CAPABILITY, 1, PluginComposeUiServices::class.java)
        PluginIcons.configure(composeUiHost)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        initialized = true
        try {
            initializePlatform(environment)
        } catch (failure: Throwable) {
            try { close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    final override fun preparePlaybackRuntime() {
        check(initialized) { "音乐插件尚未初始化" }
        if (session != null) return
        val player = preparePlayer()
        val created = MediaSession.Builder(environment.applicationContext, player)
            .setId("yudan.music.${environment.pluginId}")
            .build()
        try {
            registration = musicHost.registerSession(created.sessionCompatToken.token as android.media.session.MediaSession.Token)
            session = created
        } catch (failure: Throwable) {
            created.release()
            throw failure
        }
        scope!!.launch {
            while (true) {
                val extras = mediaExtras()
                created.setSessionExtras(extras)
                val metadata = player.mediaMetadata
                registration?.updateSnapshot(Bundle(extras).apply {
                    putInt("schemaVersion", 1)
                    putString("pluginId", environment.pluginId)
                    putString("title", metadata.title?.toString())
                    putString("mediaId", player.currentMediaItem?.mediaId)
                    putString("artist", metadata.artist?.toString())
                    putString("album", metadata.albumTitle?.toString())
                    putString("artworkUri", metadata.artworkUri?.toString())
                    putBoolean("playing", player.isPlaying)
                    putBoolean("buffering", player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
                    putLong("positionMs", player.currentPosition)
                    putLong("durationMs", player.duration.coerceAtLeast(0))
                })
                delay(1_000)
            }
        }
    }

    final override fun createView(activity: Activity, savedState: Bundle?): View {
        preparePlaybackRuntime()
        val page = Page(newPage(savedState))
        return ComposeView(activity).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            pages[this] = page
            setContent {
                CompositionLocalProvider(LocalPluginUi provides uiHost, LocalPluginDarkTheme provides page.dark,
                    LocalPluginComposeUi provides composeUiHost) {
                    Content(page.navigation)
                }
            }
        }
    }

    final override fun saveViewState(view: View): Bundle = requireNotNull(pages[view]).navigation.save()
    final override fun onBack(view: View): Boolean = pages[view]?.navigation?.back() ?: false
    final override fun onHostState(view: View, state: PluginHostState) {
        pages[view]?.dark = state.darkTheme
    }
    final override fun destroyView(view: View) {
        if (pages.remove(view) != null) (view as ComposeView).disposeComposition()
    }

    final override fun close() {
        if (!initialized) return
        pages.keys.toList().forEach(::destroyView)
        scope?.cancel()
        scope = null
        try { registration?.close() } finally {
            registration = null
            try { session?.release() } finally {
                session = null
                try { releasePlatform() } finally {
                    initialized = false
                }
            }
        }
    }
}
