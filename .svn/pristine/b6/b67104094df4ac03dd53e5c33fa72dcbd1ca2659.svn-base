package com.egogame.plugin.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 声明身份不是二进制兼容证明；发布前仍须生成实际宿主成员 ABI。 */
public final class PluginRuntimeBaseline {
    // 图标实现只由宿主提供，不再承诺完整 Material 图标类集合。
    public static final String COMPOSE_ABI = "1.6.1-host-icons-v1";
    private PluginRuntimeBaseline() { }
    public static Map<String, String> identities() { return declaredRuntimes(); }
    public static Map<String, String> declaredRuntimes() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("yudan.plugin-sdk", "1.0.0");
        values.put("kotlin", "1.8.22");
        values.put("androidx.compose", COMPOSE_ABI);
        values.put("androidx.compose.material3", "1.2.0");
        values.put("androidx.activity", "1.7.0");
        values.put("androidx.lifecycle", "2.6.2");
        values.put("androidx.media3", "1.2.1");
        values.put("kotlinx.coroutines", "1.7.3");
        values.put("okhttp", "4.9.3");
        values.put("coil", "2.5.0");
        values.put("androidx.palette", "1.0.0");
        values.put("androidx.core", "1.12.0");
        values.put("zxing", "3.4.1");
        return Collections.unmodifiableMap(values);
    }
}
