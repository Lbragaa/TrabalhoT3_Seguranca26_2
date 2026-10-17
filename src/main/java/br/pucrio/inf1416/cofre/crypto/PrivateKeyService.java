package br.pucrio.inf1416.cofre.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

public final class PrivateKeyService {
    public static final int REGISTRATION_CHALLENGE_BYTES = 10_240;

    public PrivateKey read(Path file, char[] passphrase) throws IOException, GeneralSecurityException {
        return restore(Files.readAllBytes(file), passphrase);
    }

    public PrivateKey restore(byte[] ciphertext, char[] passphrase) throws GeneralSecurityException {
        byte[] plaintext = null;
        byte[] der = null;
        try {
            plaintext = new AesCipher().decrypt(ciphertext, new LegacyKeyDerivation().fromPassword(passphrase));
            der = PemSupport.decode(plaintext, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            // A corrupt ciphertext and a wrong phrase cannot reliably be distinguished.
            throw new GeneralSecurityException("Unable to restore private key: wrong passphrase or invalid key data.");
        } finally {
            if (plaintext != null) { Arrays.fill(plaintext, (byte) 0); }
            if (der != null) { Arrays.fill(der, (byte) 0); }
        }
    }

    public PrivateKey readAndValidate(Path file, char[] passphrase, X509Certificate certificate)
            throws IOException, GeneralSecurityException {
        PrivateKey key = read(file, passphrase);
        new SignatureService().validateKeyPair(key, certificate.getPublicKey(), REGISTRATION_CHALLENGE_BYTES);
        return key;
    }
}
