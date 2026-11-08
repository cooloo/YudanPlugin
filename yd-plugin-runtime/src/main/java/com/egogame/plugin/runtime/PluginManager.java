package com.egogame.plugin.runtime;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** 单进程管理器；耗时方法由宿主调度至工作线程。索引和版本目录都在私有目录。 */
public final class PluginManager {
    private final File root;
    private final AtomicFile index;
    private final PluginPackageVerifier verifier;
    private final PluginCompatibility compatibility;
    private final PluginTrustStore trust;
    private final Map<String, Integer> extensionLimits;
    private final Map<String, PluginRecord> records = new LinkedHashMap<>();
    private final Map<String, Integer> leases = new HashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean started;

    public PluginManager(Context context, PluginCompatibility compatibility, PluginTrustStore trust) throws IOException {
        this(context, compatibility, trust, java.util.Collections.emptyMap());
    }
    public PluginManager(Context context, PluginCompatibility compatibility, PluginTrustStore trust,
                         Map<String, Integer> extensionLimits) throws IOException {
        root = new File(context.getFilesDir(), "yd-plugins");
        PluginPaths.mkdir(root);
        this.compatibility = compatibility; this.trust = trust;
        this.extensionLimits = new LinkedHashMap<>(extensionLimits);
        for (int limit : this.extensionLimits.values()) {
            if (limit <= 0) throw new IllegalArgumentException("插件扩展数量上限必须大于零");
        }
        verifier = new PluginPackageVerifier(compatibility, trust);
        index = new AtomicFile(new File(root, "index.json"));
        if (index.getBaseFile().exists() || new File(index.getBaseFile().getPath() + ".bak").exists()) {
            try (InputStream input = index.openRead()) {
                byte[] bytes = readBounded(input, 4 * 1024 * 1024);
                JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
                for (int i = 0; i < array.length(); i++) {
                    PluginRecord record = new PluginRecord(array.getJSONObject(i));
                    if (records.put(record.manifest.id, record) != null) throw new JSONException("重复插件标识");
                }
            } catch (JSONException e) { throw new PluginException("INDEX", "插件索引损坏，保留原文件等待恢复", e); }
        }
    }
    public synchronized void startSession() throws IOException {
        if (started) return;
        for (PluginRecord record : records.values()) {
            try {
                if (record.attempting) rollback(record, "上次插件初始化未完成，已恢复上一健康版本");
                if (record.pendingVersion > 0 && record.enabled) activate(record);
            } catch (IOException failure) {
                record.enabled = false;
                record.error = "插件版本恢复失败：" + failure.getMessage();
            }
        }
        save();
        started = true;
        changed();
    }
    public synchronized PluginRecord install(InputStream source, boolean allowSignerChange) throws IOException {
        ensureStarted();
        File stage = new File(root, "staging/" + UUID.randomUUID());
        PluginPaths.mkdir(stage);
        try {
            File archive = new File(stage, "package.ydplugin");
            try (FileOutputStream output = new FileOutputStream(archive)) {
                byte[] buffer = new byte[32768]; long total = 0; int size;
                while ((size = source.read(buffer)) != -1) {
                    total += size;
                    if (total > PluginPackageVerifier.MAX_ARCHIVE) throw new PluginException("PACKAGE_LIMIT", "导入文件大小超限");
                    output.write(buffer, 0, size);
                }
                output.getFD().sync();
            }
            File unpacked = new File(stage, "version");
            PluginPackageVerifier.Result verified = verifier.extract(archive, unpacked);
            PluginManifest manifest = verified.manifest;
            PluginRecord old = records.get(manifest.id);
            for (String extension : manifest.provides.keySet()) {
                Integer limit = extensionLimits.get(extension);
                if (limit == null) continue;
                int count = 0;
                for (PluginRecord installed : records.values()) {
                    if (!installed.manifest.id.equals(manifest.id) && installed.manifest.provides.containsKey(extension)) count++;
                }
                if (count >= limit) throw new PluginException("EXTENSION_LIMIT", "该类插件已达安装数量上限：" + extension);
            }
            long floor = old == null ? 0 : Math.max(Math.max(old.activeVersion, old.pendingVersion), old.failedVersion);
            if (manifest.versionCode <= floor) throw new PluginException("VERSION_ORDER", "正常导入必须使用更高业务版本；回滚由健康事务管理");
            if (old != null && !old.signer.equals(verified.signer) && !allowSignerChange) {
                throw new PluginException("SIGNER_CHANGED", "发布者已变化，需要用户明确授权替换。SHA-256：" + verified.signer);
            }
            if (old != null && !old.manifest.packageKind.equals(manifest.packageKind)) {
                throw new PluginException("PACKAGE_KIND", "同一插件身份不能在代码和资源模式之间切换");
            }
            // 首版没有数据迁移契约；禁止新代码先破坏数据再尝试回滚。
            if (old != null && old.manifest.dataSchemaVersion != manifest.dataSchemaVersion) {
                throw new PluginException("DATA_SCHEMA", "当前版本不支持跨数据结构升级，请先导出数据并明确清除后重新安装");
            }
            if (manifest.provides.containsKey("yudan.vehicle-model")) VehicleModelPackageValidator.validate(manifest, unpacked);
            File target = versionDirectory(manifest.id, manifest.versionCode);
            writeFile(new File(unpacked, "publisher.sha256"), verified.signer.getBytes(StandardCharsets.UTF_8));
            PluginPaths.mkdir(target.getParentFile());
            if (target.exists()) PluginPaths.delete(target);
            if (!unpacked.renameTo(target)) throw new PluginException("STORAGE", "无法原子写入插件版本目录");
            PluginRecord next = old == null ? new PluginRecord(manifest) : old.copy();
            next.manifest = manifest; next.signer = verified.signer;
            next.pendingVersion = manifest.versionCode; next.error = "";
            records.put(manifest.id, next);
            try { save(); }
            catch (IOException failure) {
                if (old == null) records.remove(manifest.id); else records.put(manifest.id, old);
                throw failure;
            }
            if ((!manifest.isCode() || old == null) && leaseCount(manifest.id) == 0 && next.enabled) {
                activatePending(manifest.id);
            }
            changed();
            return required(manifest.id).copy();
        } finally { PluginPaths.delete(stage); }
    }
    public synchronized List<PluginRecord> list() {
        List<PluginRecord> result = new ArrayList<>();
        for (PluginRecord record : records.values()) result.add(record.copy());
        return result;
    }
    /** 完整核对归档但不安装、不建立信任、不加载代码，调用方须在工作线程调用。 */
    public synchronized PluginPackageVerifier.Result inspect(File archive) throws IOException {
        File stage = new File(root, "staging/" + UUID.randomUUID());
        PluginPaths.mkdir(stage);
        try {
            File copy = new File(stage, "package.ydplugin");
            try (InputStream input = new FileInputStream(archive); FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[32768]; long total = 0; int size;
                while ((size = input.read(buffer)) != -1) {
                    total += size;
                    if (total > PluginPackageVerifier.MAX_ARCHIVE) throw new PluginException("PACKAGE_LIMIT", "检查文件大小超限");
                    output.write(buffer, 0, size);
                }
                output.getFD().sync();
            }
            PluginPackageVerifier.Result result = verifier.inspect(copy, new File(stage, "version"));
            if (result.manifest.provides.containsKey("yudan.vehicle-model")) {
                VehicleModelPackageValidator.validate(result.manifest, new File(stage, "version"));
            }
            return result;
        } finally { PluginPaths.delete(stage); }
    }
    /** 读取已验签安装记录的图标，不取得运行租约，停止或禁用状态也可显示。 */
    public synchronized File getInstalledIconFile(String id) throws IOException {
        PluginRecord record = records.get(id);
        if (record == null) return null;
        pluginDirectory(id);
        long version = record.pendingVersion > 0 ? record.pendingVersion : record.activeVersion;
        if (version <= 0) return null;
        File icon = PluginPaths.resolve(root, "installed/" + id + "/versions/" + version + "/icon.png");
        return icon.isFile() ? icon : null;
    }
    /** 调用方先结束该插件的全部页面与运行时，再切换版本。 */
    public synchronized PluginRecord activatePending(String id) throws IOException {
        ensureStarted();
        PluginRecord record = required(id);
        if (leaseCount(id) > 0) throw new PluginException("IN_USE", "请先关闭此插件的页面和播放");
        if (!record.enabled) throw new PluginException("DISABLED", "插件已禁用");
        if (record.pendingVersion <= 0) return record.copy();
        PluginRecord next = record.copy();
        activate(next);
        records.put(id, next);
        try { save(); }
        catch (IOException failure) { records.put(id, record); throw failure; }
        changed();
        return next.copy();
    }
    /** 仅恢复尚未健康的新版本，已健康版本的运行错误不得覆盖账号数据。 */
    public synchronized PluginRecord recoverFailed(String id) throws IOException {
        ensureStarted();
        PluginRecord record = required(id);
        if (leaseCount(id) > 0) throw new PluginException("IN_USE", "失败版本仍有活动实例");
        if (!record.attempting || record.healthy) throw new PluginException("NO_RECOVERY", "没有可恢复的新版本初始化事务");
        PluginRecord next = record.copy();
        rollback(next, record.error.isEmpty() ? "插件初始化失败，已恢复上一健康版本" : record.error);
        records.put(id, next);
        try { save(); }
        catch (IOException failure) { records.put(id, record); throw failure; }
        changed();
        return next.copy();
    }
    public synchronized PluginLease acquire(String id) throws IOException {
        ensureStarted();
        PluginRecord record = required(id);
        if (!record.enabled) throw new PluginException("DISABLED", "插件已禁用");
        if (record.activeVersion <= 0) throw new PluginException("NOT_ACTIVE", "插件尚未启用，请重新加载");
        File directory = versionDirectory(id, record.activeVersion);
        PluginManifest active = readManifest(directory);
        compatibility.check(active);
        if (!trust.isTrusted(readSigner(directory, record), id)) throw new PluginException("UNTRUSTED_SIGNER", "插件证书信任已撤销");
        if (active.isCode()) {
            if (leaseCount(id) == 0) {
                // 只有尚未确认健康的新版本参与回滚，不能因同版本重新打开恢复旧备份。
                record.attempting = !record.healthy;
                save();
            }
        }
        leases.put(id, leaseCount(id) + 1);
        return new PluginLease(active, directory, () -> release(id));
    }
    private synchronized void release(String id) {
        leases.put(id, Math.max(0, leaseCount(id) - 1));
        PluginRecord record = records.get(id);
        if (record != null && !record.manifest.isCode() && record.pendingVersion > 0 && leaseCount(id) == 0 && record.enabled) {
            try { activate(record); save(); changed(); }
            catch (IOException e) { record.error = "资源更新启用失败：" + e.getMessage(); }
        }
    }
    public synchronized void markHealthy(String id) throws IOException {
        PluginRecord record = required(id);
        PluginRecord previous = record.copy();
        record.attempting = false; record.healthy = true; record.error = "";
        try { save(); } catch (IOException failure) { records.put(id, previous); throw failure; }
        changed();
    }
    public synchronized void markFailed(String id, String reason) throws IOException {
        PluginRecord record = required(id);
        record.error = reason == null ? "插件初始化失败" : reason;
        record.failedVersion = Math.max(record.failedVersion, record.activeVersion);
        record.attempting = !record.healthy;
        if (record.healthy) record.enabled = false;
        save(); changed();
    }
    public synchronized void setEnabled(String id, boolean enabled) throws IOException {
        PluginRecord record = required(id);
        if (leaseCount(id) > 0) throw new PluginException("IN_USE", "请先关闭此插件的页面和播放");
        boolean previous = record.enabled;
        record.enabled = enabled;
        try { save(); } catch (IOException failure) { record.enabled = previous; throw failure; }
        changed();
    }
    public synchronized void uninstall(String id, boolean clearData) throws IOException {
        PluginRecord record = required(id);
        if (leaseCount(id) > 0) throw new PluginException("IN_USE", "插件仍有活动实例，不能卸载");
        records.remove(id);
        try { save(); } catch (IOException failure) { records.put(id, record); throw failure; }
        File directory = pluginDirectory(id);
        PluginPaths.delete(new File(directory, "versions"));
        PluginPaths.delete(new File(directory, "backup"));
        PluginPaths.delete(getCacheDirectory(id));
        if (clearData) PluginPaths.delete(getDataDirectory(id));
        changed();
    }
    public synchronized File getDataDirectory(String id) throws IOException {
        File directory = new File(pluginDirectory(id), "data"); PluginPaths.mkdir(directory); return directory;
    }
    public synchronized File getCacheDirectory(String id) throws IOException {
        File directory = new File(pluginDirectory(id), "cache"); PluginPaths.mkdir(directory); return directory;
    }
    public void addListener(Runnable listener) { listeners.addIfAbsent(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() {
        for (Runnable listener : listeners) {
            try { listener.run(); } catch (RuntimeException ignored) { /* 观察者异常不撤销已经落盘的事务。 */ }
        }
    }
    private void activate(PluginRecord record) throws IOException {
        if (leaseCount(record.manifest.id) != 0) throw new PluginException("IN_USE", "版本仍有活动租约");
        String id = record.manifest.id;
        File nextDirectory = versionDirectory(id, record.pendingVersion);
        PluginManifest nextManifest = readManifest(nextDirectory);
        compatibility.check(nextManifest);
        File signerFile = new File(nextDirectory, "publisher.sha256");
        if (!signerFile.exists()) writeFile(signerFile, record.signer.getBytes(StandardCharsets.UTF_8));
        File backup = new File(pluginDirectory(id), "backup");
        PluginPaths.delete(backup);
        copyDirectory(getDataDirectory(id), backup, new long[]{0});
        record.previousVersion = record.healthy ? record.activeVersion : record.previousVersion;
        record.activeVersion = record.pendingVersion; record.pendingVersion = 0;
        record.attempting = false; record.healthy = !nextManifest.isCode();
        record.manifest = nextManifest;
    }
    private void rollback(PluginRecord record, String reason) throws IOException {
        String id = record.manifest.id;
        record.failedVersion = Math.max(record.failedVersion, record.activeVersion);
        File backup = new File(pluginDirectory(id), "backup");
        if (backup.isDirectory()) {
            File data = getDataDirectory(id);
            File restored = new File(pluginDirectory(id), "restore-" + UUID.randomUUID());
            File replaced = new File(pluginDirectory(id), "replaced-" + UUID.randomUUID());
            try {
                // 完整恢复副本落盘前不动现有数据，避免复制失败留下半份账号目录。
                copyDirectory(backup, restored, new long[]{0});
                if (!data.renameTo(replaced)) throw new PluginException("STORAGE", "无法保留失败版本的数据目录");
                if (!restored.renameTo(data)) {
                    if (!replaced.renameTo(data)) throw new PluginException("STORAGE", "恢复数据目录失败，保留备份等待重试");
                    throw new PluginException("STORAGE", "无法切换到恢复的数据目录");
                }
                PluginPaths.delete(replaced);
            } finally { PluginPaths.delete(restored); }
        }
        record.activeVersion = record.previousVersion; record.previousVersion = 0;
        record.attempting = false; record.healthy = record.activeVersion > 0;
        if (record.activeVersion > 0) {
            File previous = versionDirectory(id, record.activeVersion);
            record.manifest = readManifest(previous); record.signer = readSigner(previous, record);
        } else record.enabled = false;
        record.error = reason;
    }
    private String readSigner(File directory, PluginRecord record) throws IOException {
        File file = new File(directory, "publisher.sha256");
        return file.isFile() ? PluginPackageVerifier.readText(file, 128).trim() : record.signer;
    }
    private static PluginManifest readManifest(File directory) throws IOException {
        return PluginManifest.parse(PluginPackageVerifier.readText(new File(directory, "plugin.json"), 65536));
    }
    private PluginRecord required(String id) throws PluginException {
        PluginRecord record = records.get(id);
        if (record == null) throw new PluginException("NOT_INSTALLED", "插件尚未安装");
        return record;
    }
    private File pluginDirectory(String id) throws IOException {
        if (id == null || !id.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*") || id.length() > 100) {
            throw new PluginException("INVALID_ID", "插件标识无效");
        }
        return PluginPaths.resolve(root, "installed/" + id);
    }
    private File versionDirectory(String id, long version) throws IOException {
        if (version <= 0) throw new PluginException("INVALID_VERSION", "插件版本无效");
        return new File(pluginDirectory(id), "versions/" + version);
    }
    private int leaseCount(String id) { return leases.containsKey(id) ? leases.get(id) : 0; }
    private void ensureStarted() throws PluginException {
        if (!started) throw new PluginException("NOT_STARTED", "插件管理器未启动");
    }
    private void save() throws IOException {
        FileOutputStream stream = null;
        try {
            JSONArray array = new JSONArray();
            for (PluginRecord record : records.values()) array.put(record.toJson());
            stream = index.startWrite();
            stream.write(array.toString().getBytes(StandardCharsets.UTF_8));
            index.finishWrite(stream);
        } catch (JSONException | IOException e) {
            if (stream != null) index.failWrite(stream);
            throw new PluginException("INDEX", "插件状态保存失败", e);
        }
    }
    private static byte[] readBounded(InputStream input, int max) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] bytes = new byte[8192]; int read;
        while ((read = input.read(bytes)) != -1) {
            if (out.size() + read > max) throw new IOException("插件索引大小超限");
            out.write(bytes, 0, read);
        }
        return out.toByteArray();
    }
    private static void writeFile(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
    }
    private static void copyDirectory(File source, File destination, long[] size) throws IOException {
        PluginPaths.mkdir(destination);
        File[] children = source.listFiles();
        if (children == null) throw new IOException("无法读取插件数据备份");
        for (File child : children) {
            if (!child.getCanonicalFile().equals(child.getAbsoluteFile())) throw new IOException("插件数据禁止链接");
            File target = new File(destination, child.getName());
            if (child.isDirectory()) copyDirectory(child, target, size);
            else try (FileInputStream input = new FileInputStream(child); FileOutputStream out = new FileOutputStream(target)) {
                byte[] bytes = new byte[8192]; int read;
                while ((read = input.read(bytes)) != -1) {
                    size[0] += read;
                    if (size[0] > 256 * PluginPackageVerifier.MIB) throw new PluginException("DATA_LIMIT", "插件数据备份超过上限");
                    out.write(bytes, 0, read);
                }
                out.getFD().sync();
            }
        }
    }
}
