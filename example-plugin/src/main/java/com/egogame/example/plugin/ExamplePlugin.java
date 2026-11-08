package com.egogame.example.plugin;

import com.egogame.plugin.api.PluginEnvironment;
import com.egogame.plugin.api.YdPlugin;
import com.egogame.plugin.api.UiPlugin;
import com.egogame.plugin.api.PluginHostState;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** 独立示例只依赖公开 SDK，不读取桌面私有实现。 */
public final class ExamplePlugin implements YdPlugin, UiPlugin {
    @Override public void initialize(PluginEnvironment environment) { }
    @Override public void close() { }
    @Override public View createView(Activity activity, Bundle savedState) {
        TextView view = new TextView(activity);
        view.setGravity(Gravity.CENTER);
        view.setText("独立插件示例");
        return view;
    }
    @Override public Bundle saveViewState(View view) { return new Bundle(); }
    @Override public boolean onBack(View view) { return false; }
    @Override public void onHostState(View view, PluginHostState state) { }
    @Override public void destroyView(View view) { }
}
