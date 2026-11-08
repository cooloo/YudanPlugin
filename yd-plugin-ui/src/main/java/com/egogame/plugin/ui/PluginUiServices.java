package com.egogame.plugin.ui;

import android.app.Activity;
import android.view.View;

/** 窗口实现由宿主注入，资源使用语义令牌，插件不保存宿主资源编号。 */
public interface PluginUiServices {
    String CAPABILITY = "yudan.ui.host";
    float textSizeSp(String token);
    int color(String token, boolean darkTheme);
    interface WindowHandle extends AutoCloseable { @Override void close(); }
    WindowHandle showDialog(Activity activity, View content, boolean dismissOnOutside,
                            Runnable onDismiss);
}
