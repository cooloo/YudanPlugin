package com.egogame.plugin.runtime;

import java.io.IOException;

public final class PluginException extends IOException {
    public final String code;
    public PluginException(String code, String message) { super(message); this.code = code; }
    public PluginException(String code, String message, Throwable cause) {
        super(message, cause); this.code = code;
    }
}
