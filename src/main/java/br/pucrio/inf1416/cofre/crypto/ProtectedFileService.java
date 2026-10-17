package br.pucrio.inf1416.cofre.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;

/** Returns plaintext only after verification. Never writes or publishes decrypted files. */
public final class ProtectedFileService {
    public byte[] decryptAndVerify(Path ciphertext, Path envelope, Path signature,
            PrivateKey recipient, PublicKey owner) throws IOException, GeneralSecurityException {
        return decryptAndVerify(Files.readAllBytes(ciphertext), Files.readAllBytes(envelope),
                Files.readAllBytes(signature), recipient, owner);
    }

    public byte[] decryptAndVerify(byte[] ciphertext, byte[] envelope, byte[] signature,
            PrivateKey recipient, PublicKey owner) throws GeneralSecurityException {
        var key = new EnvelopeService().recoverKey(envelope, recipient);
        byte[] plaintext = new AesCipher().decrypt(ciphertext, key);
        boolean verified = false;
        try {
            if (!new SignatureService().verify(plaintext, signature, owner)) {
                throw new SignatureException("Protected content signature is invalid.");
            }
            verified = true;
            return plaintext;
        } finally {
            if (!verified) { Arrays.fill(plaintext, (byte) 0); }
        }
    }
}
