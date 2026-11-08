package com.cloudmusic.car.plugin

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import com.egogame.music.plugin.common.StatefulMusicPlugin
import com.egogame.plugin.api.PluginEnvironment
import com.cloudmusic.car.api.LyricsParser
import com.cloudmusic.car.api.NeteaseClient
import com.cloudmusic.car.data.AccountStore
import com.cloudmusic.car.data.MusicCache
import com.cloudmusic.car.data.Settings
import com.cloudmusic.car.player.PlayerHub
import com.cloudmusic.car.ui.AppRoot
import com.cloudmusic.car.ui.Nav
import com.cloudmusic.car.ui.theme.CloudMusicTheme
import com.paopao.music.nowplaying.KrcSource
import com.paopao.music.nowplaying.METADATA_KEY_KRC_LYRIC
import com.paopao.music.nowplaying.WordLyricsParser

/** 鱼蛋音乐插件入口，账号与播放器随插件运行时管理。 */
class CloudMusicPlugin : StatefulMusicPlugin<Nav>() {
    override fun initializePlatform(environment: PluginEnvironment) {
        NeteaseClient.init(environment.storage)
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
                try { MusicCache.close() } finally { NeteaseClient.close() }
            }
        }
    }

    override fun newPage(savedState: Bundle?): Nav = Nav(savedState)

    @Composable
    override fun Content(page: Nav) {
        CloudMusicTheme { AppRoot(embedded = true, nav = page) }
    }

    override fun mediaExtras(): Bundle = Bundle().apply {
        putString("android.media.metadata.LYRIC", LyricsParser.toLrc(PlayerHub.lyrics.value))
        putString("lyrics", LyricsParser.toLrc(PlayerHub.lyrics.value))
        putInt("audioSessionId", PlayerHub.audioSessionId)
        putString(METADATA_KEY_KRC_LYRIC,
            WordLyricsParser.toKrc(PlayerHub.lyrics.value.map { KrcSource(it.timeMs, it.text, it.words) }))
        PlayerHub.status.value?.let {
            putString("com.paopao.music.STATUS_TEXT", it.text)
            putString("com.paopao.music.STATUS_KIND", it.kind)
            putString("error", it.text)
        }
    }
}
