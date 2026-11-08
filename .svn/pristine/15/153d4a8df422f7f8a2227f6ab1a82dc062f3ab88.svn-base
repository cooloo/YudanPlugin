package com.egogame.plugin.runtime;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** 使用临时证书签出真实 JAR，覆盖验签而非模拟签名结果。 */
final class SignedPluginFixture {
    private final File directory;
    private final File keystore;
    private final String password = UUID.randomUUID().toString();
    final String publisher;
    final String replacementPublisher;

    SignedPluginFixture(File root) throws Exception {
        directory = Files.createTempDirectory(root.toPath(), "signed-packages-").toFile();
        keystore = new File(directory, "test-publishers.p12");
        generateKey("publisher");
        generateKey("replacement");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = new FileInputStream(keystore)) { store.load(input, password.toCharArray()); }
        publisher = sha256(store.getCertificate("publisher").getEncoded());
        replacementPublisher = sha256(store.getCertificate("replacement").getEncoded());
    }

    File signed(JSONObject manifest) throws Exception {
        return archive(manifest, Collections.emptyMap(), true, false, "publisher");
    }

    File signed(JSONObject manifest, Map<String, byte[]> additional) throws Exception {
        return archive(manifest, additional, true, false, "publisher");
    }

    File archive(JSONObject manifest, Map<String, byte[]> additional,
                 boolean signed, boolean corruptChecksum, String alias) throws Exception {
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put("plugin.json", utf8(manifest.toString()));
        if (manifest.getString("packageKind").equals("code")) {
            payload.put("classes.dex", new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0});
        }
        payload.putAll(additional);
        JSONObject hashes = new JSONObject();
        for (Map.Entry<String, byte[]> entry : payload.entrySet()) hashes.put(entry.getKey(), sha256(entry.getValue()));
        if (corruptChecksum) hashes.put("plugin.json", String.join("", Collections.nCopies(64, "0")));
        payload.put("checksums.json", utf8(new JSONObject().put("schemaVersion", 1).put("files", hashes).toString()));
        File archive = writeArchive(payload);
        if (signed) run("jarsigner", "-keystore", keystore.getPath(), "-storetype", "PKCS12",
                "-storepass:env", "YD_PLUGIN_TEST_STORE_PASSWORD", "-digestalg", "SHA-256",
                "-sigalg", "SHA256withRSA", archive.getPath(), alias);
        return archive;
    }

    File rewrite(File source, String path, byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(source, false)) {
            Enumeration<JarEntry> iterator = jar.entries();
            while (iterator.hasMoreElements()) {
                JarEntry entry = iterator.nextElement();
                try (InputStream input = jar.getInputStream(entry)) {
                    entries.put(entry.getName(), readAll(input));
                }
            }
        }
        entries.put(path, content);
        return writeArchive(entries);
    }

    File writeArchive(Map<String, byte[]> entries) throws Exception {
        File file = new File(directory, UUID.randomUUID() + ".ydplugin");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file.toPath()))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return file;
    }

    private void generateKey(String alias) throws Exception {
        run("keytool", "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-validity", "30", "-dname", "CN=Plugin Runtime Test",
                "-storetype", "PKCS12", "-keystore", keystore.getPath(),
                "-storepass:env", "YD_PLUGIN_TEST_STORE_PASSWORD", "-noprompt");
    }

    private void run(String tool, String... arguments) throws Exception {
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        File executable = new File(System.getProperty("java.home"), "bin/" + tool + suffix);
        List<String> command = new ArrayList<>();
        command.add(executable.getPath());
        command.addAll(Arrays.asList(arguments));
        File log = new File(directory, tool + "-" + UUID.randomUUID() + ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log);
        builder.environment().put("YD_PLUGIN_TEST_STORE_PASSWORD", password);
        Process process = builder.start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("测试签名工具超时：" + tool);
        }
        if (process.exitValue() != 0) throw new AssertionError("测试签名工具失败，请查看：" + log);
    }

    static JSONObject codeManifest(String id, long version) throws Exception {
        return manifest(id, version, "code")
                .put("entryClass", "example.music.TestPlugin")
                .put("provides", new JSONArray().put(capability("yudan.music")).put(capability("yudan.ui")))
                .put("requires", new JSONArray().put(new JSONObject().put("id", "yudan.music.host").put("apiMin", 1).put("apiMax", 1)));
    }

    static JSONObject assetManifest(String id, long version) throws Exception {
        return manifest(id, version, "assets").put("provides", new JSONArray().put(capability("test.assets")));
    }

    private static JSONObject manifest(String id, long version, String kind) throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("id", id).put("name", "运行时测试插件")
                .put("versionCode", version).put("versionName", Long.toString(version)).put("packageKind", kind)
                .put("dataSchemaVersion", 1).put("hostApiMin", 1).put("hostApiMax", 1).put("minAndroidApi", 28)
                .put("requires", new JSONArray()).put("runtimeRequirements", new JSONArray()).put("contributions", new JSONObject());
    }

    private static JSONObject capability(String id) throws Exception { return new JSONObject().put("id", id).put("api", 1); }
    static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        return result.toString();
    }
}
