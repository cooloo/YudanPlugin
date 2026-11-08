package com.egogame.plugin.api;

import android.content.SharedPreferences;
import java.io.File;

/** 数据随业务更新保留；缓存可清理，调用者不得保存其他插件的路径。 */
public interface PluginStorage {
    File getDataDirectory();
    File getCacheDirectory();
    SharedPreferences preferences(String name);
    long getAudioCacheBudgetBytes();
    long getImageCacheBudgetBytes();
}
