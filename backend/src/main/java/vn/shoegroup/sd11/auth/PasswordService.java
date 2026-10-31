package vn.shoegroup.sd11.auth;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class PasswordService {
    private static final int N = 16384, R = 8, P = 1, KEY_LENGTH = 64;
    private final PasswordEncoder bcrypt;
    private final SecureRandom secureRandom = new SecureRandom();
    public PasswordService(PasswordEncoder bcrypt) { this.bcrypt = bcrypt; }

    public String hash(String password) {
        byte[] salt = new byte[16]; secureRandom.nextBytes(salt);
        byte[] derived = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, N, R, P, KEY_LENGTH);
        return "scrypt$" + N + "$" + R + "$" + P + "$" + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(derived);
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null || stored.isBlank()) return false;
        if (stored.startsWith("scrypt$")) {
            try {
                String[] parts = stored.split("\\$");
                int n = Integer.parseInt(parts[1]), r = Integer.parseInt(parts[2]), p = Integer.parseInt(parts[3]);
                byte[] salt = HexFormat.of().parseHex(parts[4]);
                byte[] expected = HexFormat.of().parseHex(parts[5]);
                byte[] actual = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt, n, r, p, expected.length);
                return MessageDigest.isEqual(actual, expected);
            } catch (Exception ex) { return false; }
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            try { return bcrypt.matches(password, stored); } catch (Exception ex) { return false; }
        }
        // Backward compatibility for a small number of pre-hash demo accounts.
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }
}
