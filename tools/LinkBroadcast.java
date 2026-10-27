import com.estatica.domain.link.LinkCodec;
import com.estatica.domain.link.LinkPacket;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Developer broadcasts: codes anyone can type into the game, signed so players can't make
 * their own. Run through tools/link_broadcast.sh.
 *
 *   keygen [key]         new key pair; prints the public key for balance.json → link.broadcastKey
 *   sign <file> [key]    prints the code for a broadcast described in a .properties file
 *
 * The private key defaults to D:/Android/keys/link_broadcast.pk8 and never goes in the repo.
 */
public final class LinkBroadcast {

    private static final String DEFAULT_KEY = "D:/Android/keys/link_broadcast.pk8";

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && args[0].equals("keygen")) {
            Path out = Path.of(args.length > 1 ? args[1] : DEFAULT_KEY);
            if (Files.exists(out)) {
                System.err.println("Refusing to overwrite " + out + ": every code signed with it would stop working.");
                System.exit(1);
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = gen.generateKeyPair();
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.write(out, pair.getPrivate().getEncoded());
            System.out.println("Private key: " + out);
            System.out.println("broadcastKey: " + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            return;
        }
        if (args.length >= 2 && args[0].equals("sign")) {
            Properties p = new Properties();
            try (InputStreamReader in = new InputStreamReader(new FileInputStream(args[1]), StandardCharsets.UTF_8)) {
                p.load(in);
            }
            Path keyPath = Path.of(args.length > 2 ? args[2] : DEFAULT_KEY);
            PrivateKey key = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyPath)));
            Map<String, Integer> resources = new LinkedHashMap<>();
            for (String part : p.getProperty("resources", "").split(",")) {
                String[] kv = part.trim().split(":");
                if (kv.length == 2) {
                    resources.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
            String expires = p.getProperty("expires", "").trim();
            int expiresDay = expires.isEmpty() ? 0 : (int) LocalDate.parse(expires).toEpochDay();
            String id = p.getProperty("id", "").trim();
            LinkPacket packet = LinkPacket.broadcast(id.isEmpty() ? UUID.randomUUID().toString() : id,
                    System.currentTimeMillis(), p.getProperty("title", ""), p.getProperty("text", ""), resources,
                    p.getProperty("lore", ""), expiresDay);
            System.out.println(LinkCodec.encodeSigned(packet, key));
            return;
        }
        System.err.println("usage: keygen [key] | sign <broadcast.properties> [key]");
        System.exit(2);
    }
}
