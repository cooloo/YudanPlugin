package com.egogame.plugin.api;

/** 不得记录 Cookie、令牌、账号密码或带签名的完整网络地址。 */
public interface PluginLogger {
    void log(int priority, String message, Throwable error);
}
