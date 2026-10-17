package br.pucrio.inf1416.cofre.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordAndTokenSecretTest {
    @Test void bcryptUsesRequiredVersionCostLengthAndFreshSalt() {
        char[] password = CryptoPrimitivesTest.randomPhrase();
        char[] wrong = CryptoPrimitivesTest.randomPhrase();
        try {
            var hasher = new PasswordHasher();
            String first = hasher.hash(password); String second = hasher.hash(password);
            assertEquals(60, first.length());
            assertTrue(first.matches("\\$2y\\$08\\$[./A-Za-z0-9]{53}"));
            assertNotEquals(first, second);
            assertTrue(hasher.verify(first, password));
            assertFalse(hasher.verify(first, wrong));
        } finally { Arrays.fill(password, '\0'); Arrays.fill(wrong, '\0'); }
    }

    @Test void bcryptRejectsMalformedOrNonAssignmentFormats() {
        var hasher = new PasswordHasher(); char[] password = CryptoPrimitivesTest.randomPhrase();
        try {
            assertFalse(hasher.verify(null, password));
            assertFalse(hasher.verify(hasher.hash(password), null));
            assertFalse(hasher.verify("invalid", password));
            String hash = hasher.hash(password);
            assertFalse(hasher.verify(hash.replace("$2y$", "$2a$"), password));
            assertFalse(hasher.verify(hash.replace("$08$", "$10$"), password));
        } finally { Arrays.fill(password, '\0'); }
    }

    @Test void tokenSecretIsRandom20ByteBase32AndPasswordEncrypted() throws Exception {
        var service = new TotpSecretService();
        String first = service.generateSecret(); String second = service.generateSecret();
        assertEquals(32, first.length()); assertNotEquals(first, second);
        assertEquals(20, new Base32(Base32.Alphabet.BASE32, false, false).fromString(first).length);
        char[] password = CryptoPrimitivesTest.randomPhrase(); char[] wrong = CryptoPrimitivesTest.randomPhrase();
        try {
            byte[] encrypted = service.encrypt(first, password);
            assertTrue(first.equals(service.decrypt(encrypted, password)), "Token-secret round trip failed");
            assertFalse(Arrays.equals(first.getBytes(java.nio.charset.StandardCharsets.US_ASCII), encrypted));
            assertThrows(GeneralSecurityException.class, () -> service.decrypt(encrypted, wrong));
        } finally { Arrays.fill(password, '\0'); Arrays.fill(wrong, '\0'); }
    }

    @Test void tokenSecretRejectsInvalidLengthEncodingAndCorruptCiphertext() throws Exception {
        var service = new TotpSecretService(); char[] password = CryptoPrimitivesTest.randomPhrase();
        try {
            assertThrows(GeneralSecurityException.class, () -> service.encrypt(null, password));
            assertThrows(GeneralSecurityException.class, () -> service.encrypt("INVALID!", password));
            assertThrows(GeneralSecurityException.class, () -> service.encrypt("MY", password));
            assertThrows(GeneralSecurityException.class, () -> service.decrypt(new byte[17], password));
        } finally { Arrays.fill(password, '\0'); }
    }
}
