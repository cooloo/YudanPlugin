package com.egogame.plugin.api;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** 页面实例独立管理；销毁页面不意味着关闭插件运行时。所有方法在主线程调用。 */
public interface UiPlugin {
    View createView(Activity activity, Bundle savedState);
    Bundle saveViewState(View view);
    boolean onBack(View view);
    void onHostState(View view, PluginHostState state);
    void destroyView(View view);
}
