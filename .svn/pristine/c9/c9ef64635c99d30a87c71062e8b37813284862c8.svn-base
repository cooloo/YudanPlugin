package com.egogame.plugin.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 提供能力也须经宿主识别，防止安装后没有任何对应扩展处理器。 */
public final class PluginCompatibility {
    public final int hostApi, androidApi;
    private final Map<String, Integer> capabilities, extensions;
    private final Map<String, String> runtimes;

    public PluginCompatibility(int hostApi, int androidApi, Map<String, Integer> capabilities,
                               Map<String, Integer> extensions, Map<String, String> runtimes) {
        this.hostApi = hostApi;
        this.androidApi = androidApi;
        this.capabilities = Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
        this.extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
        this.runtimes = Collections.unmodifiableMap(new LinkedHashMap<>(runtimes));
    }
    public void check(PluginManifest manifest) throws PluginException {
        if (hostApi < manifest.hostApiMin || hostApi > manifest.hostApiMax) {
            throw new PluginException("HOST_API", "宿主插件协议版本不兼容");
        }
        if (androidApi < manifest.minAndroidApi) throw new PluginException("ANDROID_API", "Android 版本低于插件要求");
        for (Map.Entry<String, Integer> entry : manifest.provides.entrySet()) {
            if (!entry.getValue().equals(extensions.get(entry.getKey()))) {
                throw new PluginException("EXTENSION_API", "不支持插件扩展：" + entry.getKey());
            }
        }
        for (Map.Entry<String, PluginManifest.ApiRange> entry : manifest.requires.entrySet()) {
            Integer api = capabilities.get(entry.getKey());
            if (api == null || api < entry.getValue().min || api > entry.getValue().max) {
                throw new PluginException("MISSING_CAPABILITY", "宿主缺少能力：" + entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : manifest.runtimeRequirements.entrySet()) {
            if (!entry.getValue().equals(runtimes.get(entry.getKey()))) {
                throw new PluginException("RUNTIME_ABI", "运行库不兼容：" + entry.getKey());
            }
        }
        if (manifest.provides.containsKey("yudan.music") && (!manifest.isCode()
                || !manifest.provides.containsKey("yudan.ui")
                || !manifest.requires.containsKey("yudan.music.host"))) {
            throw new PluginException("INVALID_CAPABILITIES", "音乐插件必须声明代码、页面和宿主媒体服务");
        }
    }
}
