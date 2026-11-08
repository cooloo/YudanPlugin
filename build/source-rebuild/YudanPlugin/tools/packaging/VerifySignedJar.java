import java.io.InputStream;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 读取全部条目后才取签名信息，禁止未签名、重复条目或混合签名者。 */
class VerifySignedJar {
    public static void main(String[] args) throws Exception {
        String identity = null;
        int count = 0;
        Set<String> names = new HashSet<>();
        try (JarFile file = new JarFile(Path.of(args[0]).toFile(), true)) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!names.add(entry.getName())) throw new SecurityException("重复包条目");
                if (entry.isDirectory()) continue;
                try (InputStream input = file.getInputStream(entry)) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                }
                String name = entry.getName();
                if (name.matches("(?i)META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))")) continue;
                if (name.startsWith("META-INF/")) throw new SecurityException("非签名元数据");
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) throw new SecurityException("有效载荷未完整单签名");
                String current = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    signers[0].getSignerCertPath().getCertificates().get(0).getEncoded()));
                if (identity != null && !identity.equals(current)) throw new SecurityException("签名者不一致");
                identity = current;
                count++;
            }
        }
        if (count == 0 || identity == null) throw new SecurityException("空签名包");
        System.out.println(identity);
    }
}
