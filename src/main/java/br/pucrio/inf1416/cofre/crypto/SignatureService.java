/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;

public final class SignatureService {
    public byte[] sign(byte[] plaintext, PrivateKey key) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signature.initSign(key);
        signature.update(plaintext);
        return signature.sign();
    }

    public boolean verify(byte[] plaintext, byte[] signed, PublicKey key) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA1withRSA", "SunRsaSign");
        signature.initVerify(key);
        signature.update(plaintext);
        try {
            return signature.verify(signed);
        } catch (SignatureException e) {
            return false;
        }
    }

    public void validateKeyPair(PrivateKey privateKey, PublicKey publicKey, int challengeLength)
            throws GeneralSecurityException {
        if (challengeLength <= 0) { throw new IllegalArgumentException("Challenge length must be positive."); }
        byte[] challenge = new byte[challengeLength];
        new SecureRandom().nextBytes(challenge);
        try {
            if (!verify(challenge, sign(challenge, privateKey), publicKey)) {
                throw new SignatureException("Private key does not match the certificate.");
            }
        } finally {
            Arrays.fill(challenge, (byte) 0);
        }
    }
}
