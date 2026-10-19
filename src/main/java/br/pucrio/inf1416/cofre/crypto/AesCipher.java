/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/** Legacy AES mode required for the supplied files and encrypted token secrets. */
public final class AesCipher {
    public byte[] encrypt(byte[] plaintext, SecretKey key) throws GeneralSecurityException {
        return apply(Cipher.ENCRYPT_MODE, plaintext, key);
    }

    public byte[] decrypt(byte[] ciphertext, SecretKey key) throws GeneralSecurityException {
        if (ciphertext == null || ciphertext.length == 0 || ciphertext.length % 16 != 0) {
            throw new GeneralSecurityException("Invalid AES ciphertext length.");
        }
        return apply(Cipher.DECRYPT_MODE, ciphertext, key);
    }

    private byte[] apply(int mode, byte[] input, SecretKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "SunJCE");
        cipher.init(mode, key);
        return cipher.doFinal(input);
    }
}
