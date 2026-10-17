package br.pucrio.inf1416.cofre.crypto;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public final class EnvelopeService {
    public byte[] recoverSeed(byte[] envelope, PrivateKey recipient) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "SunJCE");
        cipher.init(Cipher.DECRYPT_MODE, recipient);
        byte[] seed = cipher.doFinal(envelope);
        if (seed.length == 0) { throw new GeneralSecurityException("Envelope seed is empty."); }
        return seed;
    }

    public SecretKey recoverKey(byte[] envelope, PrivateKey recipient) throws GeneralSecurityException {
        byte[] seed = recoverSeed(envelope, recipient);
        try {
            return new LegacyKeyDerivation().fromSeed(seed);
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }
}
