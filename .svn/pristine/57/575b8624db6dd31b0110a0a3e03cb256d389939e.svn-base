package com.egogame.plugin.runtime;

import android.os.Looper;
import com.egogame.plugin.api.PluginEnvironment;
import com.egogame.plugin.api.YdPlugin;
import com.egogame.plugin.api.UiPlugin;
import com.egogame.plugin.music.api.MusicPlugin;
import com.egogame.plugin.vehiclemodel.api.VehicleModelPlugin;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;

public final class PluginCodeLoader {
    private static final java.util.Map<String, LoadedVersion> loaders = new java.util.HashMap<>();
    private static final class LoadedVersion {
        final long version;
        final DexClassLoader loader;
        LoadedVersion(long version, DexClassLoader loader) { this.version = version; this.loader = loader; }
    }
    private PluginCodeLoader() { }
    /** 必须在宿主释放旧实例及租约后调用；不强制系统立即回收旧类。 */
    public static void forget(String id) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("插件生命周期必须在主线程调用");
        loaders.remove(id);
    }
    public static YdPlugin load(PluginLease lease, PluginEnvironment environment, ClassLoader parent) throws Exception {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("插件生命周期必须在主线程调用");
        if (!lease.manifest.isCode()) throw new PluginException("ASSET_CODE", "资源插件不创建代码加载器");
        StringBuilder paths = new StringBuilder();
        for (int sequence = 1; ; sequence++) {
            File file = new File(lease.directory, sequence == 1 ? "classes.dex" : "classes" + sequence + ".dex");
            if (!file.exists()) break;
            if ((android.system.Os.stat(file.getPath()).st_mode & 0222) != 0) {
                throw new PluginException("WRITABLE_DEX", "禁止加载可写 DEX");
            }
            if (paths.length() != 0) paths.append(File.pathSeparator);
            paths.append(file.getAbsolutePath());
        }
        if (paths.length() == 0) throw new IOException("插件缺少 DEX");
        File optimized = new File(environment.getApplicationContext().getCodeCacheDir(),
                "yd-plugin-" + environment.getPluginId() + "-" + lease.manifest.versionCode);
        PluginPaths.mkdir(optimized);
        LoadedVersion loaded = loaders.get(lease.manifest.id);
        if (loaded != null && loaded.version != lease.manifest.versionCode) {
            throw new PluginException("IN_USE", "请先关闭旧插件运行时再重新加载");
        }
        DexClassLoader loader;
        if (loaded == null) {
            loader = new DexClassLoader(paths.toString(), optimized.getPath(), null, parent);
            loaders.put(lease.manifest.id, new LoadedVersion(lease.manifest.versionCode, loader));
        } else loader = loaded.loader;
        Class<?> entry = loader.loadClass(lease.manifest.entryClass);
        if (entry.getClassLoader() != loader || !YdPlugin.class.isAssignableFrom(entry)) {
            throw new PluginException("ENTRY_TYPE", "插件入口不是该插件的通用 SDK 实现");
        }
        if (lease.manifest.provides.containsKey("yudan.music") && !MusicPlugin.class.isAssignableFrom(entry)
                || lease.manifest.provides.containsKey("yudan.ui") && !UiPlugin.class.isAssignableFrom(entry)
                || lease.manifest.provides.containsKey("yudan.vehicle-model") && !VehicleModelPlugin.class.isAssignableFrom(entry)) {
            throw new PluginException("ENTRY_TYPE", "插件入口与清单声明能力不符");
        }
        YdPlugin plugin = (YdPlugin) entry.getDeclaredConstructor().newInstance();
        try { plugin.initialize(environment); return plugin; }
        catch (Exception | Error error) {
            try { plugin.close(); } catch (Exception | Error cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
}
