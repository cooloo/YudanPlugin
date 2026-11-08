package com.egogame.music.plugin.common

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.egogame.plugin.music.api.MusicHostServices

/** 页面、通知和控制器共享同一入口；新的播放请求会撤销旧回调。 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackPermitPlayer(
    private val delegate: ExoPlayer,
    private val host: MusicHostServices,
    private val onError: (String) -> Unit,
) : ForwardingPlayer(delegate) {
    private var generation = 0L
    private var pending: Long? = null
    private var closed = false
    val audioSessionId: Int get() = delegate.audioSessionId

    init {
        delegate.addListener(object : HostPlayerListener() {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) host.notifyPlaybackStopped()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) host.notifyPlaybackStopped()
            }
            override fun onPlayerError(error: PlaybackException) { host.notifyPlaybackStopped() }
        })
    }

    override fun play() = requestPlay()

    // Media3 1.2 不支持构建器阈值配置，所有控制入口统一保持直接切上一首。
    override fun getMaxSeekToPreviousPosition(): Long = Long.MAX_VALUE

    override fun seekToPrevious() {
        if (delegate.hasPreviousMediaItem()) delegate.seekToPreviousMediaItem()
        else if (delegate.isCurrentMediaItemSeekable) delegate.seekTo(0L)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) requestPlay() else pause()
    }

    private fun requestPlay() {
        if (closed) return
        val requestGeneration = ++generation
        pending?.let(host::cancelPlaybackRequest)
        pending = null
        var delivered = false
        val request = host.requestPlayback { _, allowed, reason ->
            delivered = true
            if (closed || requestGeneration != generation) return@requestPlayback
            pending = null
            if (allowed) delegate.play() else {
                delegate.pause()
                onError(reason.ifBlank { "宿主未允许播放" })
            }
        }
        if (!delivered) pending = request
    }

    override fun pause() {
        invalidate()
        delegate.pause()
        host.notifyPlaybackStopped()
    }

    override fun stop() {
        invalidate()
        delegate.stop()
        host.notifyPlaybackStopped()
    }

    private fun invalidate() {
        generation++
        pending?.let(host::cancelPlaybackRequest)
        pending = null
    }

    override fun release() {
        closed = true
        invalidate()
        try { delegate.release() } finally { host.notifyPlaybackStopped() }
    }
}
