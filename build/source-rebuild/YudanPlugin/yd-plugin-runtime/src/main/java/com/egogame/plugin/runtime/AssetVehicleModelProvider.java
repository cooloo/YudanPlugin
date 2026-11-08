package com.egogame.plugin.runtime;

import android.os.Bundle;
import com.egogame.plugin.vehiclemodel.api.VehicleModelProvider;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.IOException;

/** 纯资源适配器不实例化 DexClassLoader；调用方持有租约至渲染实例完整释放。 */
public final class AssetVehicleModelProvider implements VehicleModelProvider {
    private final PluginLease lease;
    public AssetVehicleModelProvider(PluginLease lease) throws IOException {
        if (lease.manifest.isCode()) throw new PluginException("PACKAGE_KIND", "资源适配器只接收纯资源插件");
        VehicleModelPackageValidator.validate(lease.manifest, lease.directory);
        this.lease = lease;
    }
    @Override public Bundle getModelDescriptor(String modelId) {
        try {
            JSONArray models = lease.manifest.contributions().getJSONArray("vehicleModels");
            for (int i = 0; i < models.length(); i++) {
                org.json.JSONObject model = models.getJSONObject(i);
                if (!model.getString("id").equals(modelId)) continue;
                String path = model.getString("descriptor");
                Bundle descriptor = new Bundle();
                descriptor.putInt("schemaVersion", 1);
                descriptor.putString("modelId", modelId);
                descriptor.putString("pluginId", lease.manifest.id);
                descriptor.putLong("versionCode", lease.manifest.versionCode);
                descriptor.putString("json", PluginPackageVerifier.readText(PluginPaths.resolve(lease.directory, path), 65536));
                return descriptor;
            }
            throw new IllegalArgumentException("模型不存在");
        } catch (JSONException | IOException e) { throw new IllegalStateException("读取模型描述失败", e); }
    }
}
