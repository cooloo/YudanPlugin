package com.egogame.plugin.runtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginManifest {
    public static final int SCHEMA = 1;
    public final String id, name, versionName, packageKind, entryClass;
    public final long versionCode;
    public final int dataSchemaVersion, hostApiMin, hostApiMax, minAndroidApi;
    public final Map<String, Integer> provides;
    public final Map<String, ApiRange> requires;
    public final Map<String, String> runtimeRequirements;
    private final String json;

    public static final class ApiRange {
        public final int min, max;
        ApiRange(int min, int max) { this.min = min; this.max = max; }
    }

    public static PluginManifest parse(String json) throws PluginException {
        try { return new PluginManifest(new JSONObject(json)); }
        catch (JSONException | IllegalArgumentException e) {
            throw new PluginException("INVALID_MANIFEST", "插件清单格式错误：" + e.getMessage(), e);
        }
    }

    private PluginManifest(JSONObject obj) throws JSONException {
        if (obj.getInt("schemaVersion") != SCHEMA) throw new JSONException("不支持的清单版本");
        id = obj.getString("id");
        if (!id.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*") || id.length() > 100) {
            throw new JSONException("插件标识无效");
        }
        name = obj.getString("name");
        versionName = obj.getString("versionName");
        if (name.trim().isEmpty() || name.length() > 100 || versionName.length() > 100) {
            throw new JSONException("插件名称或版本名称无效");
        }
        versionCode = obj.getLong("versionCode");
        dataSchemaVersion = obj.getInt("dataSchemaVersion");
        hostApiMin = obj.getInt("hostApiMin");
        hostApiMax = obj.getInt("hostApiMax");
        minAndroidApi = obj.getInt("minAndroidApi");
        if (versionCode <= 0 || dataSchemaVersion < 1 || hostApiMin < 1
                || hostApiMax < hostApiMin || minAndroidApi < 22) throw new JSONException("版本范围无效");
        packageKind = obj.getString("packageKind");
        if (!packageKind.equals("code") && !packageKind.equals("assets")) throw new JSONException("插件模式无效");
        entryClass = obj.optString("entryClass", "");
        if (isCode()) {
            if (!entryClass.matches("[a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+")) {
                throw new JSONException("代码入口无效");
            }
        } else if (obj.has("entryClass")) throw new JSONException("资源包禁止代码入口");
        Map<String, Integer> supplied = new LinkedHashMap<>();
        JSONArray provideArray = obj.getJSONArray("provides");
        for (int i = 0; i < provideArray.length(); i++) {
            JSONObject item = provideArray.getJSONObject(i);
            String key = capabilityId(item);
            int api = item.getInt("api");
            if (api < 1 || supplied.put(key, api) != null) throw new JSONException("提供能力重复或无效");
        }
        if (supplied.isEmpty()) throw new JSONException("未声明插件能力");
        provides = Collections.unmodifiableMap(supplied);
        Map<String, ApiRange> required = new LinkedHashMap<>();
        JSONArray requireArray = obj.getJSONArray("requires");
        for (int i = 0; i < requireArray.length(); i++) {
            JSONObject item = requireArray.getJSONObject(i);
            int min = item.getInt("apiMin"), max = item.getInt("apiMax");
            if (min < 1 || max < min || required.put(capabilityId(item), new ApiRange(min, max)) != null) {
                throw new JSONException("要求能力重复或无效");
            }
        }
        requires = Collections.unmodifiableMap(required);
        Map<String, String> runtimes = new LinkedHashMap<>();
        JSONArray runtimeArray = obj.getJSONArray("runtimeRequirements");
        for (int i = 0; i < runtimeArray.length(); i++) {
            JSONObject item = runtimeArray.getJSONObject(i);
            String key = capabilityId(item), abi = item.getString("abi");
            if (abi.isEmpty() || runtimes.put(key, abi) != null) throw new JSONException("运行库声明无效");
        }
        runtimeRequirements = Collections.unmodifiableMap(runtimes);
        obj.getJSONObject("contributions");
        json = obj.toString();
    }
    private static String capabilityId(JSONObject item) throws JSONException {
        String id = item.getString("id");
        if (!id.matches("[a-z][a-z0-9.-]{0,99}")) throw new JSONException("能力标识无效");
        return id;
    }
    public boolean isCode() { return "code".equals(packageKind); }
    public String toJson() { return json; }
    public JSONObject contributions() {
        try { return new JSONObject(json).getJSONObject("contributions"); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
