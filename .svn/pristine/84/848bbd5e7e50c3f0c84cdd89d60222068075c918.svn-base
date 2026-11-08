package com.egogame.plugin.runtime;

import org.json.JSONException;
import org.json.JSONObject;

/** 管理器返回记录副本，调用方修改副本不会变更持久状态。 */
public final class PluginRecord {
    public PluginManifest manifest;
    public long activeVersion, pendingVersion, previousVersion;
    public boolean enabled = true;
    public String error = "", signer = "";
    boolean attempting, healthy;
    long failedVersion;

    PluginRecord(PluginManifest manifest) { this.manifest = manifest; }
    PluginRecord(JSONObject obj) throws JSONException, PluginException {
        manifest = PluginManifest.parse(obj.getString("manifest"));
        activeVersion = obj.optLong("activeVersion");
        pendingVersion = obj.optLong("pendingVersion");
        previousVersion = obj.optLong("previousVersion");
        enabled = obj.optBoolean("enabled", true);
        error = obj.optString("error"); signer = obj.getString("signer");
        attempting = obj.optBoolean("attempting"); healthy = obj.optBoolean("healthy");
        failedVersion = obj.optLong("failedVersion");
    }
    JSONObject toJson() throws JSONException {
        return new JSONObject().put("manifest", manifest.toJson()).put("activeVersion", activeVersion)
                .put("pendingVersion", pendingVersion).put("previousVersion", previousVersion)
                .put("enabled", enabled).put("error", error).put("signer", signer)
                .put("attempting", attempting).put("healthy", healthy).put("failedVersion", failedVersion);
    }
    PluginRecord copy() {
        try { return new PluginRecord(toJson()); }
        catch (JSONException | PluginException e) { throw new IllegalStateException(e); }
    }
}
