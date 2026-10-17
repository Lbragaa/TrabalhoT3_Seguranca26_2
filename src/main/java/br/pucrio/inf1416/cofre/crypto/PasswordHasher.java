package br.pucrio.inf1416.cofre.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

/** Required format only; registration's numeric password policy belongs to Phase 2. */
public final class PasswordHasher {
    public String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        try {
            return OpenBSDBCrypt.generate("2y", password, salt, 8);
        } finally {
            Arrays.fill(salt, (byte) 0);
        }
    }

    public boolean verify(String stored, char[] password) {
        if (password == null || stored == null || !stored.matches("\\$2y\\$08\\$[./A-Za-z0-9]{53}")) { return false; }
        try {
            return OpenBSDBCrypt.checkPassword(stored, password);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
