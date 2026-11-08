package com.egogame.plugin.api;

/** 主线程调用；初始化失败后宿主仍会调用 close，释放已取得的资源。 */
public interface YdPlugin extends AutoCloseable {
    void initialize(PluginEnvironment environment) throws Exception;
    @Override void close();
}
