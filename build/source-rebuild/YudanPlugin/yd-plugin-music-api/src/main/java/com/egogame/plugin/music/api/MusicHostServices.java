package com.egogame.plugin.music.api;

import android.media.session.MediaSession;
import android.os.Bundle;

/** 每个实例由宿主绑定插件身份；回调主线程执行，过期请求不得恢复播放。 */
public interface MusicHostServices {
    String CAPABILITY = "yudan.music.host";
    int API = 1;
    interface SessionRegistration extends AutoCloseable {
        void updateSnapshot(Bundle snapshot);
        @Override void close();
    }
    interface PlaybackCallback { void onResult(long requestId, boolean allowed, String reason); }
    SessionRegistration registerSession(MediaSession.Token token);
    long requestPlayback(PlaybackCallback callback);
    void cancelPlaybackRequest(long requestId);
    void notifyPlaybackStopped();
}
