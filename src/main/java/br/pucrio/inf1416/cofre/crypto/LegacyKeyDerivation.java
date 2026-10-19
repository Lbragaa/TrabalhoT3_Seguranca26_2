/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** The professor's deterministic derivation, not a general-purpose password KDF. */
public final class LegacyKeyDerivation {
    public SecretKey fromSeed(byte[] seed) throws GeneralSecurityException {
        if (seed == null || seed.length == 0) {
            throw new GeneralSecurityException("An encryption seed is required.");
        }
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        random.setSeed(seed);
        KeyGenerator generator = KeyGenerator.getInstance("AES", "SunJCE");
        generator.init(256, random);
        return generator.generateKey();
    }

    public SecretKey fromPassword(char[] password) throws GeneralSecurityException {
        if (password == null || password.length == 0) {
            throw new GeneralSecurityException("A passphrase is required.");
        }
        ByteBuffer encoded = null;
        byte[] seed = null;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            seed = new byte[encoded.remaining()];
            encoded.get(seed);
            return fromSeed(seed);
        } catch (CharacterCodingException e) {
            throw new GeneralSecurityException("Passphrase contains invalid Unicode.");
        } finally {
            if (seed != null) { Arrays.fill(seed, (byte) 0); }
            if (encoded != null && encoded.hasArray()) { Arrays.fill(encoded.array(), (byte) 0); }
        }
    }
}
