package com.egogame.plugin.music.api;

import com.egogame.plugin.api.UiPlugin;
import com.egogame.plugin.api.YdPlugin;

public interface MusicPlugin extends YdPlugin, UiPlugin {
    void preparePlaybackRuntime();
}
