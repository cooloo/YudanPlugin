package com.qqmusic.car.plugin

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import com.egogame.music.plugin.common.StatefulMusicPlugin
import com.egogame.plugin.api.PluginEnvironment
import com.qqmusic.car.api.LyricsParser
import com.qqmusic.car.api.QQMusicClient
import com.qqmusic.car.data.AccountStore
import com.qqmusic.car.data.MusicCache
import com.qqmusic.car.data.Settings
import com.qqmusic.car.player.PlayerHub
import com.qqmusic.car.ui.AppRoot
import com.qqmusic.car.ui.Nav
import com.qqmusic.car.ui.theme.QQMusicTheme

/** 鱼蛋音乐插件入口，账号与播放器随插件运行时管理。 */
class QQMusicPlugin : StatefulMusicPlugin<Nav>() {
    override fun initializePlatform(environment: PluginEnvironment) {
        QQMusicClient.init(environment.storage)
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
                try { MusicCache.close() } finally { QQMusicClient.close() }
            }
        }
    }

    override fun newPage(savedState: Bundle?): Nav = Nav(savedState)

    @Composable
    override fun Content(page: Nav) {
        QQMusicTheme { AppRoot(embedded = true, nav = page) }
    }

    override fun mediaExtras(): Bundle = Bundle().apply {
        putString("android.media.metadata.LYRIC", LyricsParser.toLrc(PlayerHub.lyrics.value))
        putString("lyrics", LyricsParser.toLrc(PlayerHub.lyrics.value))
        putInt("audioSessionId", PlayerHub.audioSessionId)
        PlayerHub.status.value?.let {
            putString("com.paopao.music.STATUS_TEXT", it.text)
            putString("com.paopao.music.STATUS_KIND", it.kind)
            putString("error", it.text)
        }
    }
}
