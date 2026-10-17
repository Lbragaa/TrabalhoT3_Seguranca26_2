package br.pucrio.inf1416.cofre.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoPrimitivesTest {
    private static KeyPair owner;
    private static KeyPair other;
    @BeforeAll static void keyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        owner = generator.generateKeyPair();
        other = generator.generateKeyPair();
    }

    static byte[] randomBytes(int size) {
        byte[] result = new byte[size];
        new SecureRandom().nextBytes(result);
        return result;
    }

    static char[] randomPhrase() { return java.util.UUID.randomUUID().toString().toCharArray(); }

    @Test void derivationIsDeterministic256BitAndUsesFreshState() throws Exception {
        var derivation = new LegacyKeyDerivation();
        byte[] seed = randomBytes(20);
        byte[] original = seed.clone();
        assertTrue(Arrays.equals(derivation.fromSeed(seed).getEncoded(), derivation.fromSeed(seed).getEncoded()));
        assertArrayEquals(original, seed);
        assertEquals(32, derivation.fromSeed(seed).getEncoded().length);
        byte[] different = seed.clone(); different[0] ^= 1;
        assertFalse(Arrays.equals(derivation.fromSeed(seed).getEncoded(), derivation.fromSeed(different).getEncoded()));
    }

    @Test void derivationMatchesExplicitJavaProviderProcedure() throws Exception {
        byte[] seed = randomBytes(20);
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        random.setSeed(seed);
        KeyGenerator generator = KeyGenerator.getInstance("AES", "SunJCE");
        generator.init(256, random);
        assertTrue(Arrays.equals(generator.generateKey().getEncoded(), new LegacyKeyDerivation().fromSeed(seed).getEncoded()));
    }

    @Test void passwordEncodingIsUtf8AndCallerBufferIsUntouched() throws Exception {
        char[] phrase = randomPhrase();
        phrase[0] = '\u00e7'; phrase[1] = '\u03a9';
        char[] copy = phrase.clone();
        try {
            byte[] utf8 = new String(phrase).getBytes(StandardCharsets.UTF_8);
            try {
                assertTrue(Arrays.equals(new LegacyKeyDerivation().fromSeed(utf8).getEncoded(),
                        new LegacyKeyDerivation().fromPassword(phrase).getEncoded()));
                assertTrue(Arrays.equals(copy, phrase));
            } finally { Arrays.fill(utf8, (byte) 0); }
        } finally { Arrays.fill(phrase, '\0'); Arrays.fill(copy, '\0'); }
    }

    @Test void missingOrInvalidSeedAndInvalidUnicodeAreRejected() {
        var derivation = new LegacyKeyDerivation();
        assertThrows(GeneralSecurityException.class, () -> derivation.fromSeed(null));
        assertThrows(GeneralSecurityException.class, () -> derivation.fromSeed(new byte[0]));
        assertThrows(GeneralSecurityException.class, () -> derivation.fromPassword(new char[0]));
        assertThrows(GeneralSecurityException.class, () -> derivation.fromPassword(new char[]{'\ud800'}));
    }

    @Test void aesMatchesNist256BitEcbBlockVector() throws Exception {
        // Public NIST SP 800-38A F.1.5 test material, not a fixture credential.
        var hex = HexFormat.of();
        var key = new SecretKeySpec(hex.parseHex("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4"), "AES");
        byte[] input = hex.parseHex("6bc1bee22e409f96e93d7e117393172a");
        byte[] ciphertext = new AesCipher().encrypt(input, key);
        assertEquals("f3eed1bdb5d2a03c064b5a7e3db181f8", hex.formatHex(Arrays.copyOf(ciphertext, 16)));
        assertEquals(32, ciphertext.length); // Full-block plaintext requires a padding block.
        assertArrayEquals(input, new AesCipher().decrypt(ciphertext, key));
    }

    @Test void aesEmptyAndMultiblockRoundTripsAndRejectsInvalidLengths() throws Exception {
        var aes = new AesCipher();
        var key = new LegacyKeyDerivation().fromSeed(randomBytes(20));
        for (int size : new int[]{0,1,15,16,17,10240}) {
            byte[] plain = randomBytes(size);
            assertArrayEquals(plain, aes.decrypt(aes.encrypt(plain, key), key));
        }
        assertThrows(GeneralSecurityException.class, () -> aes.decrypt(new byte[0], key));
        assertThrows(GeneralSecurityException.class, () -> aes.decrypt(new byte[17], key));
    }

    @Test void restoresSyntheticPkcs8AndRejectsWrongPhraseOrNonPem() throws Exception {
        char[] phrase = randomPhrase(); char[] wrong = randomPhrase();
        byte[] pem = ("-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(owner.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n").getBytes(StandardCharsets.US_ASCII);
        try {
            var aesKey = new LegacyKeyDerivation().fromPassword(phrase);
            byte[] encrypted = new AesCipher().encrypt(pem, aesKey);
            var restored = new PrivateKeyService().restore(encrypted, phrase);
            new SignatureService().validateKeyPair(restored, owner.getPublic(), 10_240);
            assertThrows(GeneralSecurityException.class, () -> new PrivateKeyService().restore(encrypted, wrong));
            byte[] nonPem = new AesCipher().encrypt(randomBytes(100), aesKey);
            assertThrows(GeneralSecurityException.class, () -> new PrivateKeyService().restore(nonPem, phrase));
        } finally { Arrays.fill(phrase, '\0'); Arrays.fill(wrong, '\0'); Arrays.fill(pem, (byte) 0); }
    }

    @Test void signatureChallengesAndTampering() throws Exception {
        var service = new SignatureService();
        service.validateKeyPair(owner.getPrivate(), owner.getPublic(), 10_240);
        assertThrows(GeneralSecurityException.class, () -> service.validateKeyPair(owner.getPrivate(), other.getPublic(), 10_240));
        byte[] plain = randomBytes(128); byte[] signature = service.sign(plain, owner.getPrivate());
        assertTrue(service.verify(plain, signature, owner.getPublic()));
        plain[0] ^= 1;
        assertFalse(service.verify(plain, signature, owner.getPublic()));
        assertFalse(service.verify(plain, new byte[1], owner.getPublic()));
        assertThrows(IllegalArgumentException.class, () -> service.validateKeyPair(owner.getPrivate(), owner.getPublic(), 0));
    }

    @Test void envelopeRecoversRawBinarySeedAndRejectsWrongOrEmptySeed() throws Exception {
        byte[] seed = randomBytes(20); seed[0] = 0; seed[1] = (byte) 0xff;
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, owner.getPublic());
        byte[] encrypted = rsa.doFinal(seed);
        var service = new EnvelopeService();
        assertArrayEquals(seed, service.recoverSeed(encrypted, owner.getPrivate()));
        assertTrue(Arrays.equals(new LegacyKeyDerivation().fromSeed(seed).getEncoded(), service.recoverKey(encrypted, owner.getPrivate()).getEncoded()));
        assertThrows(GeneralSecurityException.class, () -> service.recoverSeed(encrypted, other.getPrivate()));
        assertThrows(GeneralSecurityException.class, () -> service.recoverSeed(new byte[257], owner.getPrivate()));
        byte[] empty = rsa.doFinal(new byte[0]);
        assertThrows(GeneralSecurityException.class, () -> service.recoverSeed(empty, owner.getPrivate()));
    }

    @Test void verifiedPayloadAllowsOnlyCorrectSignerAndRecipient() throws Exception {
        byte[] seed = randomBytes(20); byte[] plain = randomBytes(2048);
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding"); rsa.init(Cipher.ENCRYPT_MODE, other.getPublic());
        byte[] envelope = rsa.doFinal(seed);
        byte[] encrypted = new AesCipher().encrypt(plain, new LegacyKeyDerivation().fromSeed(seed));
        byte[] signature = new SignatureService().sign(plain, owner.getPrivate());
        var service = new ProtectedFileService();
        assertArrayEquals(plain, service.decryptAndVerify(encrypted, envelope, signature, other.getPrivate(), owner.getPublic()));
        assertThrows(GeneralSecurityException.class, () -> service.decryptAndVerify(encrypted, envelope, signature, owner.getPrivate(), owner.getPublic()));
        assertThrows(GeneralSecurityException.class, () -> service.decryptAndVerify(encrypted, envelope, signature, other.getPrivate(), other.getPublic()));
        signature[0] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> service.decryptAndVerify(encrypted, envelope, signature, other.getPrivate(), owner.getPublic()));
    }

    @Test void parsesAllActualCertificatePrefixesAndRoundTripsPem() throws Exception {
        var service = new CertificateService();
        Path root = Path.of(System.getProperty("project.root"));
        for (String id : new String[]{"admin", "user01", "user02"}) {
            var certificate = service.read(root.resolve("Pacote-T3/Keys/" + id + "-x509.crt"));
            assertEquals(id + "@inf1416.puc-rio.br", service.identity(certificate).email());
            assertFalse(service.identity(certificate).name().isBlank());
            assertEquals(certificate, service.parse(service.toPem(certificate).getBytes(StandardCharsets.US_ASCII)));
            assertEquals(3, certificate.getVersion());
        }
    }

    @Test void malformedOrDuplicateCertificateBlocksAreRejected() throws Exception {
        var service = new CertificateService();
        assertThrows(GeneralSecurityException.class, () -> service.parse(new byte[0]));
        assertThrows(GeneralSecurityException.class, () -> service.parse("-----BEGIN CERTIFICATE-----\n!\n-----END CERTIFICATE-----".getBytes(StandardCharsets.US_ASCII)));
        byte[] certificate = Files.readAllBytes(Path.of(System.getProperty("project.root"), "Pacote-T3/Keys/admin-x509.crt"));
        byte[] duplicated = new byte[certificate.length * 2];
        System.arraycopy(certificate, 0, duplicated, 0, certificate.length);
        System.arraycopy(certificate, 0, duplicated, certificate.length, certificate.length);
        assertThrows(GeneralSecurityException.class, () -> service.parse(duplicated));
    }
}
