package com.kugoumusic.car.plugin

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import com.egogame.music.plugin.common.StatefulMusicPlugin
import com.egogame.plugin.api.PluginEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.kugoumusic.car.api.KuGouMusicClient
import com.kugoumusic.car.api.KuGouLoginApi
import com.kugoumusic.car.data.AccountStore
import com.kugoumusic.car.data.MusicCache
import com.kugoumusic.car.data.Settings
import com.kugoumusic.car.player.PlayerHub
import com.kugoumusic.car.ui.AppRoot
import com.kugoumusic.car.ui.Nav
import com.kugoumusic.car.ui.theme.KuGouMusicTheme

/** 鱼蛋音乐插件入口，账号与播放器随插件运行时管理。 */
class KuGouMusicPlugin : StatefulMusicPlugin<Nav>() {
    override fun initializePlatform(environment: PluginEnvironment) {
        KuGouLoginApi.init(environment.transfers)
        KuGouMusicClient.init(environment.storage)
        Settings.init(environment.storage)
        MusicCache.init(environment.applicationContext, environment.storage, environment.transfers)
        AccountStore.init()
    }

    override fun preparePlayer(): Player {
        PlayerHub.init(environment.applicationContext, environment.storage, environment.transfers, musicHost)
        return PlayerHub.player
    }

    override fun releasePlatform() {
        try { AccountStore.close() } finally {
            try { PlayerHub.release() } finally {
                try { MusicCache.close() } finally { KuGouMusicClient.close() }
            }
        }
    }

    override fun newPage(savedState: Bundle?): Nav = Nav(savedState)

    @Composable
    override fun Content(page: Nav) {
        KuGouMusicTheme { AppRoot(embedded = true, nav = page) }
    }

    override fun mediaExtrasUpdates(): Flow<Bundle> = combine(PlayerHub.sessionLyrics, PlayerHub.status) { lyrics, status ->
        Bundle(lyrics).apply {
            status?.let {
                putString("com.paopao.music.STATUS_TEXT", it.text)
                putString("com.paopao.music.STATUS_KIND", it.kind)
                putString("error", it.text)
            }
        }
    }
}
