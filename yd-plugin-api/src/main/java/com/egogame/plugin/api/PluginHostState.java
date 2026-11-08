package com.egogame.plugin.api;

public final class PluginHostState {
    public final int schemaVersion = 1;
    public final boolean started;
    public final boolean visible;
    public final boolean windowFocused;
    public final boolean inputAllowed;
    public final boolean darkTheme;

    public PluginHostState(boolean started, boolean visible, boolean windowFocused,
                           boolean inputAllowed, boolean darkTheme) {
        this.started = started;
        this.visible = visible;
        this.windowFocused = windowFocused;
        this.inputAllowed = inputAllowed;
        this.darkTheme = darkTheme;
    }
}
