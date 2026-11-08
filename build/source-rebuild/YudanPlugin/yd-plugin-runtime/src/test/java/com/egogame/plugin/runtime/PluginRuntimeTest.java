package com.egogame.plugin.runtime;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static com.egogame.plugin.runtime.SignedPluginFixture.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class PluginRuntimeTest {
    private static File validationDirectory;
    private static SignedPluginFixture packages;
    private Context context;
    private PluginCompatibility compatibility;
    private PluginTrustStore trust;
    private PluginManager manager;

    @BeforeClass public static void 准备临时签名发布者() throws Exception {
        validationDirectory = new File(System.getProperty("plugin.validation.dir"));
        Files.createDirectories(validationDirectory.toPath());
        packages = new SignedPluginFixture(validationDirectory);
    }

    @Before public void 准备独立私有目录和宿主能力() throws Exception {
        File files = Files.createTempDirectory(validationDirectory.toPath(), "private-data-").toFile();
        Application application = RuntimeEnvironment.getApplication();
        application.getSharedPreferences("yd_plugin_trust_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context = new ContextWrapper(application) { @Override public File getFilesDir() { return files; } };
        Map<String, Integer> extensions = new LinkedHashMap<>();
        extensions.put("yudan.music", 1);
        extensions.put("yudan.ui", 1);
        extensions.put("test.assets", 1);
        compatibility = new PluginCompatibility(1, 28, Collections.singletonMap("yudan.music.host", 1),
                extensions, Collections.singletonMap("test.runtime", "test-abi-v1"));
        trust = new PluginTrustStore(context, Arrays.asList(packages.publisher, packages.replacementPublisher));
        manager = restart();
    }

    @Test public void 清理目录允许父路径别名但不把普通子目录当链接() throws Exception {
        File base = Files.createTempDirectory(validationDirectory.toPath(), "path-alias-").toFile();
        PluginPaths.mkdir(new File(base, "parent"));
        File target = new File(base, "parent/../stage");
        PluginPaths.mkdir(new File(target, "version/assets"));
        Files.write(new File(target, "version/assets/readme.txt").toPath(), utf8("测试资源"));
        PluginPaths.delete(target);
        assertFalse(target.exists());
        PluginPaths.delete(base);
    }

    @Test public void 完整签名的代码包返回可信发布者及全部有效载荷哈希() throws Exception {
        byte[] text = utf8("中文资源内容");
        PluginPackageVerifier.Result verified = verify(packages.signed(codeManifest("netease", 1),
                Collections.singletonMap("assets/readme.txt", text)), trust);
        assertEquals("netease", verified.manifest.id);
        assertEquals(packages.publisher, verified.signer);
        assertEquals(4, verified.hashes.size());
        assertEquals(sha256(text), verified.hashes.get("assets/readme.txt"));
    }

    @Test public void 旧证书仅信任明确指定的插件且不扩大为全局信任() throws Exception {
        PluginTrustStore legacy = new PluginTrustStore(context, Collections.emptySet(),
                Collections.singletonMap(packages.publisher, Collections.singleton("netease")));
        assertFalse(legacy.isTrusted(packages.publisher));
        assertTrue(legacy.isTrusted(packages.publisher, "netease"));
        assertFalse(legacy.isTrusted(packages.publisher, "unrelated"));
        assertEquals("netease", verify(packages.signed(codeManifest("netease", 1)), legacy).manifest.id);
        assertCode("UNTRUSTED_SIGNER", () -> verify(packages.signed(codeManifest("unrelated", 1)), legacy));
    }

    @Test public void 只读检查完整验签但不信任未知证书或写入安装记录() throws Exception {
        PluginTrustStore unknown = new PluginTrustStore(context, Collections.emptySet());
        PluginManager inspector = new PluginManager(context, compatibility, unknown);
        PluginPackageVerifier.Result inspected = inspector.inspect(packages.signed(codeManifest("netease", 1)));
        assertEquals("netease", inspected.manifest.id);
        assertEquals(packages.publisher, inspected.signer);
        assertFalse(unknown.isTrusted(packages.publisher));
        assertTrue(inspector.list().isEmpty());
        assertEquals(0, new File(context.getFilesDir(), "yd-plugins/staging").list().length);
    }

    @Test public void 只读检查拒绝篡改并清理暂存目录() throws Exception {
        File original = packages.signed(codeManifest("netease", 1));
        File tampered = packages.rewrite(original, "classes.dex", utf8("错误内容"));
        assertCode("SIGNATURE", () -> manager.inspect(tampered));
        assertTrue(manager.list().isEmpty());
        assertEquals(0, new File(context.getFilesDir(), "yd-plugins/staging").list().length);
    }

    @Test public void 已安装图标不需要运行租约且禁用后仍可读取() throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0WQAAAAASUVORK5CYII=");
        File archive = packages.signed(codeManifest("netease", 1), Collections.singletonMap("icon.png", png));
        try (InputStream input = new FileInputStream(archive)) { manager.install(input, false); }
        assertTrue(manager.getInstalledIconFile("netease").isFile());
        manager.setEnabled("netease", false);
        assertTrue(manager.getInstalledIconFile("netease").isFile());
        assertNull(manager.getInstalledIconFile("absent"));
    }

    @Test public void 未签名代码包不能导入() throws Exception {
        File archive = packages.archive(codeManifest("netease", 1), Collections.emptyMap(), false, false, "publisher");
        assertCode("UNSIGNED_ENTRY", () -> verify(archive, trust));
    }

    @Test public void 签名后篡改有效载荷会被拒绝() throws Exception {
        File archive = packages.signed(codeManifest("netease", 1), Collections.singletonMap("assets/readme.txt", utf8("原文")));
        File tampered = packages.rewrite(archive, "assets/readme.txt", utf8("已篡改"));
        assertCode("SIGNATURE", () -> verify(tampered, trust));
    }

    @Test public void 完整签名之外追加资源不能搭便车通过验证() throws Exception {
        File archive = packages.signed(codeManifest("netease", 1));
        File appended = packages.rewrite(archive, "assets/extra.txt", utf8("未签名资源"));
        assertCode("UNSIGNED_ENTRY", () -> verify(appended, trust));
    }

    @Test public void 即使签名有效哈希目录错误仍会被拒绝() throws Exception {
        File archive = packages.archive(codeManifest("netease", 1), Collections.emptyMap(), true, true, "publisher");
        assertCode("CHECKSUM", () -> verify(archive, trust));
    }

    @Test public void 有效签名不会自动信任未知发布者() throws Exception {
        File archive = packages.signed(codeManifest("netease", 1));
        PluginTrustStore unknown = new PluginTrustStore(context, Collections.emptyList());
        assertCode("UNTRUSTED_SIGNER", () -> verify(archive, unknown));
        assertFalse(unknown.isTrusted(packages.publisher));
    }

    @Test public void 目录穿越在写入目录外之前被拒绝() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../escape.txt", utf8("越界"));
        File archive = packages.writeArchive(entries);
        assertCode("INVALID_PATH", () -> verify(archive, trust));
        assertFalse(new File(context.getFilesDir(), "escape.txt").exists());
    }

    @Test public void 大小写冲突的资源不能重复覆盖() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("assets/readme.txt", utf8("原文"));
        entries.put("assets/README.txt", utf8("冲突"));
        assertCode("DUPLICATE_ENTRY", () -> verify(packages.signed(codeManifest("netease", 1), entries), trust));
    }

    @Test public void 代码包缺号的多个DEX会被拒绝() throws Exception {
        byte[] dex = {'d', 'e', 'x', '\n', '0', '3', '5', 0};
        File archive = packages.signed(codeManifest("netease", 1), Collections.singletonMap("classes3.dex", dex));
        assertCode("DEX_SEQUENCE", () -> verify(archive, trust));
    }

    @Test public void 非DEX有效载荷不会因文件扩展名通过() throws Exception {
        File archive = packages.signed(codeManifest("netease", 1), Collections.singletonMap("classes.dex", utf8("invalid dex")));
        assertCode("INVALID_DEX", () -> verify(archive, trust));
    }

    @Test public void 资源包不需要DEX但禁止夹带代码入口和DEX() throws Exception {
        assertFalse(verify(packages.signed(assetManifest("vehicle", 1)), trust).manifest.isCode());
        File embeddedDex = packages.signed(assetManifest("vehicle", 1), Collections.singletonMap("classes.dex", new byte[8]));
        assertCode("ASSET_CODE", () -> verify(embeddedDex, trust));
        File entryClass = packages.signed(assetManifest("vehicle", 1).put("entryClass", "example.HiddenCode"));
        assertCode("INVALID_MANIFEST", () -> verify(entryClass, trust));
    }

    @Test public void Android版本和运行库不兼容会阻止安装() throws Exception {
        File android = packages.signed(codeManifest("netease", 1).put("minAndroidApi", 29));
        assertCode("ANDROID_API", () -> verify(android, trust));
        JSONObject manifest = codeManifest("netease", 1).put("runtimeRequirements", new JSONArray()
                .put(new JSONObject().put("id", "test.runtime").put("abi", "unknown-abi")));
        assertCode("RUNTIME_ABI", () -> verify(packages.signed(manifest), trust));
    }

    @Test public void 代码初装立即启用且健康确认后能再次获取() throws Exception {
        PluginRecord installed = install(packages.signed(codeManifest("netease", 1)));
        assertEquals(1, installed.activeVersion);
        assertEquals(0, installed.pendingVersion);
        try (PluginLease lease = manager.acquire("netease")) {
            assertEquals(1, lease.manifest.versionCode);
            manager.markHealthy("netease");
        }
        writeData("netease", "已健康数据");
        manager.acquire("netease").close();
        manager = restart();
        assertEquals(1, record("netease").activeVersion);
        assertEquals("已健康数据", readData("netease"));
        assertTrue(record("netease").enabled);
    }

    @Test public void 代码更新须释放旧租约再显式启用且无需重启管理器() throws Exception {
        prepareHealthyVersion(1);
        PluginLease old = manager.acquire("netease");
        install(packages.signed(codeManifest("netease", 2)));
        assertCode("IN_USE", () -> manager.activatePending("netease"));
        assertEquals(1, record("netease").activeVersion);
        old.close();
        PluginRecord activated = manager.activatePending("netease");
        assertEquals(2, activated.activeVersion);
        assertEquals(0, activated.pendingVersion);
        try (PluginLease next = manager.acquire("netease")) {
            assertEquals(2, next.manifest.versionCode);
            manager.markHealthy("netease");
        }
    }

    @Test public void 新版初始化失败可在当前进程恢复旧健康版本及数据() throws Exception {
        prepareHealthyVersion(1);
        writeData("netease", "旧健康数据");
        install(packages.signed(codeManifest("netease", 2)));
        manager.activatePending("netease");
        PluginLease next = manager.acquire("netease");
        writeData("netease", "失败新版数据");
        manager.markFailed("netease", "新版初始化失败");
        assertCode("IN_USE", () -> manager.recoverFailed("netease"));
        next.close();
        PluginRecord recovered = manager.recoverFailed("netease");
        assertEquals(1, recovered.activeVersion);
        assertEquals(2, recovered.failedVersion);
        assertEquals("旧健康数据", readData("netease"));
        assertTrue(recovered.enabled);
        try (PluginLease restored = manager.acquire("netease")) {
            assertEquals(1, restored.manifest.versionCode);
        }
    }

    @Test public void 已健康版本运行失败不能被即时回退覆盖用户数据() throws Exception {
        prepareHealthyVersion(1);
        writeData("netease", "当前账号数据");
        manager.markFailed("netease", "已健康版本发生异常");
        assertCode("NO_RECOVERY", () -> manager.recoverFailed("netease"));
        assertEquals("当前账号数据", readData("netease"));
        assertFalse(record("netease").enabled);
    }

    @Test public void 未健康新版本失败后回滚到健康版本并恢复数据备份() throws Exception {
        prepareHealthyVersion(1);
        writeData("netease", "旧版健康数据");
        install(packages.signed(codeManifest("netease", 2)));
        try (PluginLease old = manager.acquire("netease")) { assertEquals(1, old.manifest.versionCode); }
        manager = restart();
        PluginLease next = manager.acquire("netease");
        assertEquals(2, next.manifest.versionCode);
        writeData("netease", "新版尚未健康的数据");
        manager.markFailed("netease", "初始化异常");
        next.close();
        manager = restart();
        assertEquals(1, record("netease").activeVersion);
        assertEquals(0, record("netease").pendingVersion);
        assertEquals("旧版健康数据", readData("netease"));
        assertTrue(record("netease").enabled);
        try (PluginLease restored = manager.acquire("netease")) { assertEquals(1, restored.manifest.versionCode); }
    }

    @Test public void 未完成健康确认就终止的初装版本下次启动禁用() throws Exception {
        install(packages.signed(codeManifest("netease", 1)));
        manager = restart();
        manager.acquire("netease").close();
        manager = restart();
        assertEquals(0, record("netease").activeVersion);
        assertFalse(record("netease").enabled);
        assertCode("DISABLED", () -> manager.acquire("netease"));
    }

    @Test public void 已健康版本后续失败只禁用不恢复陈旧备份() throws Exception {
        prepareHealthyVersion(1);
        writeData("netease", "第一版数据");
        install(packages.signed(codeManifest("netease", 2)));
        manager = restart();
        try (PluginLease next = manager.acquire("netease")) { manager.markHealthy("netease"); }
        writeData("netease", "第二版健康后的用户数据");
        manager.markFailed("netease", "后续运行异常");
        manager = restart();
        assertEquals(2, record("netease").activeVersion);
        assertFalse(record("netease").enabled);
        assertEquals("第二版健康后的用户数据", readData("netease"));
    }

    @Test public void 资源包更新等待旧租约结束且重复释放不会破坏版本() throws Exception {
        install(packages.signed(assetManifest("vehicle", 1), Collections.singletonMap("assets/model.txt", utf8("旧资源"))));
        PluginLease old = manager.acquire("vehicle");
        PluginRecord waiting = install(packages.signed(assetManifest("vehicle", 2), Collections.singletonMap("assets/model.txt", utf8("新资源"))));
        assertEquals(1, waiting.activeVersion);
        assertEquals(2, waiting.pendingVersion);
        assertEquals("旧资源", readResource(old, "assets/model.txt"));
        assertCode("IN_USE", () -> manager.setEnabled("vehicle", false));
        assertCode("IN_USE", () -> manager.uninstall("vehicle", false));
        old.close();
        old.close();
        assertCode("LEASE_CLOSED", () -> old.resources.open("assets/model.txt"));
        try (PluginLease next = manager.acquire("vehicle")) {
            assertEquals(2, next.manifest.versionCode);
            assertEquals("新资源", readResource(next, "assets/model.txt"));
        }
        assertEquals(0, record("vehicle").pendingVersion);
    }

    @Test public void 发布者变更必须显式授权且失败导入保留旧版本() throws Exception {
        install(packages.signed(codeManifest("netease", 1)));
        File replacement = packages.archive(codeManifest("netease", 2), Collections.emptyMap(), true, false, "replacement");
        assertCode("SIGNER_CHANGED", () -> install(replacement));
        assertEquals(packages.publisher, record("netease").signer);
        assertEquals(1, record("netease").activeVersion);
        assertEquals(0, record("netease").pendingVersion);
        try (InputStream input = new FileInputStream(replacement)) {
            assertEquals(packages.replacementPublisher, manager.install(input, true).signer);
        }
    }

    @Test public void 拒绝降级和跨数据结构升级并保留现有版本() throws Exception {
        prepareHealthyVersion(2);
        assertCode("VERSION_ORDER", () -> install(packages.signed(codeManifest("netease", 1))));
        assertCode("DATA_SCHEMA", () -> install(packages.signed(codeManifest("netease", 3).put("dataSchemaVersion", 2))));
        assertEquals(2, record("netease").activeVersion);
        assertEquals(0, record("netease").pendingVersion);
    }

    @Test public void 按扩展限制安装数量但允许原身份升级() throws Exception {
        manager = new PluginManager(context, compatibility, trust, Collections.singletonMap("yudan.music", 2));
        manager.startSession();
        install(packages.signed(codeManifest("first", 1)));
        install(packages.signed(codeManifest("second", 1)));
        assertCode("EXTENSION_LIMIT", () -> install(packages.signed(codeManifest("third", 1))));
        install(packages.signed(codeManifest("first", 2)));
        assertEquals(2, manager.list().size());
        assertEquals(2, record("first").pendingVersion);
    }

    @Test public void 撤销用户信任后已安装资源也不能继续获取() throws Exception {
        trust = new PluginTrustStore(context, Collections.emptyList());
        trust.trustByUser(packages.publisher);
        manager = restart();
        install(packages.signed(assetManifest("vehicle", 1)));
        manager.acquire("vehicle").close();
        trust.revokeUserTrust(packages.publisher);
        assertCode("UNTRUSTED_SIGNER", () -> manager.acquire("vehicle"));
    }

    @Test public void 卸载默认保留数据且明确清除才删除() throws Exception {
        install(packages.signed(assetManifest("vehicle", 1)));
        writeData("vehicle", "账号与配置");
        manager.uninstall("vehicle", false);
        assertEquals("账号与配置", readData("vehicle"));
        install(packages.signed(assetManifest("vehicle", 1)));
        manager.uninstall("vehicle", true);
        File data = new File(context.getFilesDir(), "yd-plugins/installed/vehicle/data/user.txt");
        assertFalse(data.exists());
        assertTrue(manager.list().isEmpty());
    }

    @Test public void 索引写入失败保留健康事务与启用状态() throws Exception {
        install(packages.signed(codeManifest("netease", 1)));
        manager = restart();
        PluginLease lease = manager.acquire("netease");
        File directory = new File(context.getFilesDir(), "yd-plugins");
        File retained = new File(context.getFilesDir(), "retained-plugins");
        assertTrue(directory.renameTo(retained));
        Files.write(directory.toPath(), utf8("阻止索引写入"));
        try {
            assertCode("INDEX", () -> manager.markHealthy("netease"));
            assertFalse(record("netease").healthy);
            assertTrue(record("netease").attempting);
            lease.close();
            assertCode("INDEX", () -> manager.setEnabled("netease", false));
            assertTrue(record("netease").enabled);
        } finally {
            assertTrue(directory.delete());
            assertTrue(retained.renameTo(directory));
            lease.close();
        }
        manager.markHealthy("netease");
        assertTrue(record("netease").healthy);
    }

    private PluginPackageVerifier.Result verify(File archive, PluginTrustStore store) throws Exception {
        File destination = new File(context.getFilesDir(), "extract-" + UUID.randomUUID());
        return new PluginPackageVerifier(compatibility, store).extract(archive, destination);
    }

    private PluginManager restart() throws Exception {
        PluginManager next = new PluginManager(context, compatibility, trust);
        next.startSession();
        return next;
    }

    private PluginRecord install(File archive) throws Exception {
        try (InputStream input = new FileInputStream(archive)) { return manager.install(input, false); }
    }

    private void prepareHealthyVersion(long version) throws Exception {
        install(packages.signed(codeManifest("netease", version)));
        manager = restart();
        try (PluginLease lease = manager.acquire("netease")) { manager.markHealthy("netease"); }
    }

    private PluginRecord record(String id) {
        for (PluginRecord record : manager.list()) if (record.manifest.id.equals(id)) return record;
        throw new AssertionError("缺少插件：" + id);
    }

    private void writeData(String id, String value) throws Exception {
        Files.write(new File(manager.getDataDirectory(id), "user.txt").toPath(), utf8(value));
    }

    private String readData(String id) throws Exception {
        return new String(Files.readAllBytes(new File(manager.getDataDirectory(id), "user.txt").toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String readResource(PluginLease lease, String path) throws Exception {
        try (InputStream input = lease.resources.open(path)) {
            return new String(readAll(input), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void assertCode(String code, ThrowingRunnable operation) {
        PluginException error = assertThrows(PluginException.class, operation);
        assertEquals(code, error.code);
    }
}
