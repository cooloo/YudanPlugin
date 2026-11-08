package com.egogame.plugin.runtime;

import com.egogame.plugin.api.PluginResources;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 固定版本资源租约；释放后不能继续打开资源或由宿主删除活动资源。 */
public final class PluginLease implements AutoCloseable {
    public final PluginManifest manifest;
    public final File directory;
    public final PluginResources resources;
    private final Runnable release;
    private volatile boolean closed;

    PluginLease(PluginManifest manifest, File directory, Runnable release) {
        this.manifest = manifest; this.directory = directory; this.release = release;
        resources = new PluginResources() {
            private File resolve(String path) throws IOException {
                if (closed) throw new PluginException("LEASE_CLOSED", "插件资源租约已释放");
                if (!path.startsWith("assets/") && !path.equals("icon.png")) {
                    throw new PluginException("INVALID_PATH", "只能读取插件公开资源");
                }
                File file = PluginPaths.resolve(directory, path);
                if (!file.isFile()) throw new IOException("插件资源不存在");
                return file;
            }
            @Override public InputStream open(String path) throws IOException { return new FileInputStream(resolve(path)); }
            @Override public long size(String path) throws IOException { return resolve(path).length(); }
            @Override public String sha256(String path) throws IOException { return PluginPackageVerifier.hash(resolve(path)); }
        };
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        release.run();
    }
}
