package com.egogame.plugin.api;

import android.content.Context;

public interface PluginEnvironment {
    String getPluginId();
    long getVersionCode();
    Context getApplicationContext();
    PluginStorage getStorage();
    PluginResources getResources();
    PluginTransfers getTransfers();
    PluginLogger getLogger();
    <T> T requireCapability(String id, int api, Class<T> contract);
}
