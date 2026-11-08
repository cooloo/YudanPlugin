package com.egogame.plugin.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 发行信任和用户主动导入的信任并存；安装器不自动信任包内证书。 */
public final class PluginTrustStore {
    private final Set<String> publishers;
    private final Map<String, Set<String>> legacyPublishers;
    private final SharedPreferences preferences;
    public PluginTrustStore(Context context, Collection<String> publishers) {
        this(context, publishers, Collections.emptyMap());
    }
    public PluginTrustStore(Context context, Collection<String> publishers,
                            Map<String, ? extends Collection<String>> legacyPublishers) {
        this.publishers = Collections.unmodifiableSet(new HashSet<>(publishers));
        Map<String, Set<String>> legacy = new HashMap<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : legacyPublishers.entrySet()) {
            legacy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }
        this.legacyPublishers = Collections.unmodifiableMap(legacy);
        preferences = context.getSharedPreferences("yd_plugin_trust_v1", Context.MODE_PRIVATE);
    }
    public synchronized boolean isTrusted(String fingerprint) {
        return publishers.contains(fingerprint) || preferences.getStringSet("users", Collections.emptySet()).contains(fingerprint);
    }
    /** 旧发行证书只对明确列出的迁移插件有效，不扩大为全局发行信任。 */
    public synchronized boolean isTrusted(String fingerprint, String pluginId) {
        Set<String> allowed = legacyPublishers.get(fingerprint);
        return isTrusted(fingerprint) || allowed != null && allowed.contains(pluginId);
    }
    /** 仅在用户核对证书指纹并主动确认后调用；不应由导入异常重试自动触发。 */
    public synchronized void trustByUser(String fingerprint) throws PluginException {
        if (!fingerprint.matches("[0-9a-f]{64}")) throw new PluginException("INVALID_CERTIFICATE", "证书指纹格式无效");
        Set<String> users = new HashSet<>(preferences.getStringSet("users", Collections.emptySet()));
        users.add(fingerprint);
        if (!preferences.edit().putStringSet("users", users).commit()) throw new PluginException("STORAGE", "保存用户信任失败");
    }
    public synchronized void revokeUserTrust(String fingerprint) throws PluginException {
        Set<String> users = new HashSet<>(preferences.getStringSet("users", Collections.emptySet()));
        users.remove(fingerprint);
        if (!preferences.edit().putStringSet("users", users).commit()) throw new PluginException("STORAGE", "撤销用户信任失败");
    }
}
