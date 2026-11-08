package com.egogame.plugin.runtime;

import android.graphics.BitmapFactory;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 先完整读取以触发 JAR 验签，再进行清单、哈希、限额和兼容核对。 */
public final class PluginPackageVerifier {
    static final long MIB = 1024L * 1024;
    static final long MAX_ARCHIVE = 256 * MIB;
    public static final class Result {
        public final PluginManifest manifest;
        public final String signer;
        public final Map<String, String> hashes;
        Result(PluginManifest manifest, String signer, Map<String, String> hashes) {
            this.manifest = manifest; this.signer = signer;
            this.hashes = Collections.unmodifiableMap(new HashMap<>(hashes));
        }
    }
    private final PluginCompatibility compatibility;
    private final PluginTrustStore trust;
    public PluginPackageVerifier(PluginCompatibility compatibility, PluginTrustStore trust) {
        this.compatibility = compatibility; this.trust = trust;
    }

    public Result extract(File archive, File destination) throws IOException {
        return extract(archive, destination, true);
    }
    Result inspect(File archive, File destination) throws IOException {
        return extract(archive, destination, false);
    }
    private Result extract(File archive, File destination, boolean requireTrusted) throws IOException {
        if (archive.length() == 0 || archive.length() > MAX_ARCHIVE) fail("PACKAGE_LIMIT", "插件包大小超限");
        PluginPaths.mkdir(destination);
        Map<String, String> hashes = new HashMap<>();
        Set<String> allNames = new HashSet<>();
        List<Integer> dexNumbers = new ArrayList<>();
        String signer = null;
        long expanded = 0;
        try (JarFile jar = new JarFile(archive, true)) {
            Enumeration<JarEntry> entries = jar.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (++count > 4096) fail("PACKAGE_LIMIT", "插件条目过多");
                String name = entry.getName();
                if (entry.isDirectory()) name = name.substring(0, name.length() - 1);
                PluginPaths.validate(name);
                if (!allNames.add(name.toLowerCase(Locale.ROOT))) fail("DUPLICATE_ENTRY", "插件条目重复");
                if (entry.isDirectory()) continue;
                boolean signature = isSignature(name);
                if (!signature && !isPayload(name)) fail("INVALID_ENTRY", "插件包含不允许的条目：" + name);
                long limit = entryLimit(name);
                MessageDigest digest = digest();
                File output = signature ? null : PluginPaths.resolve(destination, name);
                if (output != null) PluginPaths.mkdir(output.getParentFile());
                long received = 0;
                try (InputStream input = jar.getInputStream(entry);
                     FileOutputStream out = output == null ? null : new FileOutputStream(output)) {
                    // Android 14 要求先持有写入句柄，再将文件设只读，最后完成内容写入。
                    if (output != null && !output.setReadOnly()) fail("READ_ONLY", "无法设置插件文件只读");
                    byte[] buffer = new byte[32768];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        received += size; expanded += size;
                        if (received > limit || expanded > 512 * MIB) fail("PACKAGE_LIMIT", "插件实际解压大小超限");
                        digest.update(buffer, 0, size);
                        if (out != null) out.write(buffer, 0, size);
                    }
                    if (out != null) out.getFD().sync();
                }
                if (signature) continue;
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) fail("UNSIGNED_ENTRY", "插件有效载荷必须由同一证书签名");
                String fingerprint = certificateFingerprint(signers[0]);
                if (signer != null && !signer.equals(fingerprint)) fail("MIXED_SIGNERS", "插件条目签名不一致");
                signer = fingerprint;
                hashes.put(name, hex(digest.digest()));
                if (name.matches("classes(?:(?:[2-9]|[1-9][0-9]+))?\\.dex")) {
                    if (name.length() > 15) fail("DEX_SEQUENCE", "DEX 序号超限");
                    int sequence = name.equals("classes.dex") ? 1 : Integer.parseInt(name.substring(7, name.length() - 4));
                    if (sequence > 4096) fail("DEX_SEQUENCE", "DEX 序号超限");
                    dexNumbers.add(sequence);
                }
            }
        } catch (SecurityException e) {
            throw new PluginException("SIGNATURE", "插件签名校验失败", e);
        }
        if (signer == null) fail("SIGNATURE", "插件缺少有效签名");
        PluginManifest manifest = PluginManifest.parse(readText(new File(destination, "plugin.json"), 65536));
        if (manifest.isCode()) {
            if (archive.length() > 64 * MIB || expanded > 128 * MIB) fail("PACKAGE_LIMIT", "代码插件大小超限");
            Collections.sort(dexNumbers);
            if (dexNumbers.isEmpty()) fail("MISSING_DEX", "代码插件缺少 DEX");
            for (int i = 0; i < dexNumbers.size(); i++) {
                if (dexNumbers.get(i) != i + 1) fail("DEX_SEQUENCE", "DEX 编号必须连续");
                File dex = new File(destination, i == 0 ? "classes.dex" : "classes" + (i + 1) + ".dex");
                checkDex(dex);
            }
        } else if (!dexNumbers.isEmpty()) fail("ASSET_CODE", "资源插件禁止 DEX");
        try {
            JSONObject checksumDocument = new JSONObject(readText(new File(destination, "checksums.json"), 1024 * 1024));
            if (checksumDocument.getInt("schemaVersion") != 1) fail("CHECKSUM", "不支持的哈希目录版本");
            JSONObject checksums = checksumDocument.getJSONObject("files");
            Set<String> expected = new HashSet<>(hashes.keySet());
            expected.remove("checksums.json");
            if (checksums.length() != expected.size()) fail("CHECKSUM", "哈希目录与有效载荷数量不一致");
            Iterator<String> keys = checksums.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!expected.remove(key) || !checksums.getString(key).matches("[0-9a-f]{64}")
                        || !checksums.getString(key).equals(hashes.get(key))) fail("CHECKSUM", "资源哈希校验失败：" + key);
            }
            if (!expected.isEmpty()) fail("CHECKSUM", "插件存在未经哈希核验的资源");
        } catch (JSONException e) { throw new PluginException("CHECKSUM", "哈希目录格式无效", e); }
        File icon = new File(destination, "icon.png");
        if (icon.exists()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(icon.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 2048 || options.outHeight > 2048) {
                fail("ICON_LIMIT", "插件图标尺寸无效或超限");
            }
        }
        compatibility.check(manifest);
        if (requireTrusted && !trust.isTrusted(signer, manifest.id)) {
            fail("UNTRUSTED_SIGNER", "尚未信任此签名证书，SHA-256：" + signer);
        }
        return new Result(manifest, signer, hashes);
    }
    private static boolean isSignature(String path) {
        return path.equals("META-INF/MANIFEST.MF") || path.matches("META-INF/[A-Za-z0-9_-]+\\.(?:SF|RSA|DSA|EC)");
    }
    private static boolean isPayload(String path) {
        return path.equals("plugin.json") || path.equals("checksums.json") || path.equals("icon.png")
                || path.matches("classes(?:(?:[2-9]|[1-9][0-9]+))?\\.dex")
                || (path.startsWith("assets/") && path.matches(".*\\.(?i:json|glb|png|jpe?g|webp|gif|ttf|otf|txt|lrc|krc|mp3|flac|ogg|m4a|wav)"));
    }
    private static long entryLimit(String name) {
        if (name.equals("plugin.json") || name.endsWith("/model.json")) return 65536;
        if (name.equals("checksums.json")) return MIB;
        if (name.equals("icon.png")) return 2 * MIB;
        if (name.endsWith(".dex")) return 48 * MIB;
        if (name.startsWith("META-INF/")) return MIB;
        return 256 * MIB;
    }
    private static void checkDex(File file) throws IOException {
        byte[] magic = new byte[8];
        try (FileInputStream input = new FileInputStream(file)) {
            if (input.read(magic) != 8 || magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x'
                    || magic[3] != '\n' || magic[7] != 0) fail("INVALID_DEX", "DEX 文件头无效");
        }
    }
    static String readText(File file, int max) throws IOException {
        if (!file.isFile() || file.length() > max) throw new PluginException("INVALID_JSON", "配置缺失或大小超限");
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > max) fail("PACKAGE_LIMIT", "配置实际大小超限");
                output.write(buffer, 0, read);
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString();
            } catch (java.nio.charset.CharacterCodingException error) {
                throw new PluginException("INVALID_UTF8", "插件配置必须使用有效 UTF-8", error);
            }
        }
    }
    static String hash(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String certificateFingerprint(CodeSigner signer) throws PluginException {
        try { return hex(digest().digest(signer.getSignerCertPath().getCertificates().get(0).getEncoded())); }
        catch (CertificateEncodingException e) { throw new PluginException("SIGNATURE", "无法读取签名证书", e); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static void fail(String code, String message) throws PluginException { throw new PluginException(code, message); }
}
