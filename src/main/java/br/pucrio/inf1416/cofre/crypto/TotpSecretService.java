/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Secret generation and the assignment's password-derived encryption; no enrollment UI. */
public final class TotpSecretService {
    private final Base32 base32 = new Base32(Base32.Alphabet.BASE32, false, false);

    public String generateSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        try { return base32.toString(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    public byte[] encrypt(String secret, char[] password) throws GeneralSecurityException {
        validate(secret);
        byte[] plaintext = secret.getBytes(StandardCharsets.US_ASCII);
        try {
            return new AesCipher().encrypt(plaintext, new LegacyKeyDerivation().fromPassword(password));
        } finally { Arrays.fill(plaintext, (byte) 0); }
    }

    public String decrypt(byte[] ciphertext, char[] password) throws GeneralSecurityException {
        byte[] plaintext = new AesCipher().decrypt(ciphertext, new LegacyKeyDerivation().fromPassword(password));
        try {
            String secret = new String(plaintext, StandardCharsets.US_ASCII);
            validate(secret);
            return secret;
        } finally { Arrays.fill(plaintext, (byte) 0); }
    }

    private void validate(String secret) throws GeneralSecurityException {
        byte[] bytes = base32.fromString(secret);
        try {
            if (bytes == null || bytes.length != 20 || !base32.toString(bytes).equals(secret)) {
                throw new GeneralSecurityException("Expected a canonical 20-byte Base32 token secret.");
            }
        } finally { if (bytes != null) { Arrays.fill(bytes, (byte) 0); } }
    }
}
