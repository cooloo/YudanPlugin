package com.egogame.music.plugin.common

import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/** 显式提供固定版监听契约，避免独立 AAR 的默认方法转换上下文缺失。 */
@Suppress("OVERRIDE_DEPRECATION")
open class HostPlayerListener : Player.Listener {
    override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = Unit
    override fun onAudioSessionIdChanged(audioSessionId: Int) = Unit
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = Unit
    override fun onCues(cues: List<Cue>) = Unit
    override fun onCues(cueGroup: CueGroup) = Unit
    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = Unit
    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = Unit
    override fun onEvents(player: Player, events: Player.Events) = Unit
    override fun onIsLoadingChanged(isLoading: Boolean) = Unit
    override fun onIsPlayingChanged(isPlaying: Boolean) = Unit
    override fun onLoadingChanged(isLoading: Boolean) = Unit
    override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = Unit
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = Unit
    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = Unit
    override fun onMetadata(metadata: Metadata) = Unit
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = Unit
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = Unit
    override fun onPlaybackStateChanged(playbackState: Int) = Unit
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = Unit
    override fun onPlayerError(error: PlaybackException) = Unit
    override fun onPlayerErrorChanged(error: PlaybackException?) = Unit
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = Unit
    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = Unit
    override fun onPositionDiscontinuity(reason: Int) = Unit
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = Unit
    override fun onRenderedFirstFrame() = Unit
    override fun onRepeatModeChanged(repeatMode: Int) = Unit
    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = Unit
    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = Unit
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = Unit
    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = Unit
    override fun onSurfaceSizeChanged(width: Int, height: Int) = Unit
    override fun onTimelineChanged(timeline: Timeline, reason: Int) = Unit
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = Unit
    override fun onTracksChanged(tracks: Tracks) = Unit
    override fun onVideoSizeChanged(videoSize: VideoSize) = Unit
    override fun onVolumeChanged(volume: Float) = Unit
}
