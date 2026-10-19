/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TOTPTest {
    // Public RFC 4226/6238 standard test material, never a professor credential.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @ParameterizedTest @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037", "20000000000,353130"})
    void rfc6238Sha1VectorsReducedToRequiredSixDigits(long seconds, String expected) throws Exception {
        try (TOTP totp = new TOTP(RFC_SECRET, 30)) { assertEquals(expected, totp.generateCodeAt(seconds * 1000)); }
    }

    @ParameterizedTest @CsvSource({"0,755224", "1,287082", "2,359152", "3,969429", "4,338314", "5,254676", "6,287922", "7,162583", "8,399871", "9,520489"})
    void rfc4226CounterVectors(long counter, String expected) throws Exception {
        try (TOTP totp = new TOTP(RFC_SECRET, 30)) { assertEquals(expected, totp.generateCodeAt(counter * 30_000)); }
    }

    @Test void exactThirtySecondBoundariesAndPlusMinusOneWindow() throws Exception {
        try (TOTP totp = new TOTP(RFC_SECRET, 30)) {
            assertEquals(totp.generateCodeAt(29_999), totp.generateCodeAt(0));
            assertEquals("287082", totp.generateCodeAt(30_000));
            for (long now : new long[]{300_000, 329_999}) {
                assertTrue(totp.validateCodeAt(totp.generateCodeAt(270_000), now));
                assertTrue(totp.validateCodeAt(totp.generateCodeAt(300_000), now));
                assertTrue(totp.validateCodeAt(totp.generateCodeAt(330_000), now));
                assertFalse(totp.validateCodeAt(totp.generateCodeAt(240_000), now));
                assertFalse(totp.validateCodeAt(totp.generateCodeAt(360_000), now));
            }
            assertTrue(totp.validateCodeAt("755224", 0));
        }
    }

    @Test void malformedTokensAndNegativeTimeFail() throws Exception {
        try (TOTP totp = new TOTP(RFC_SECRET, 30)) {
            for (String input : new String[]{null, "", "12345", "1234567", " 755224", "abcdef", "１２３４５６"}) {
                assertFalse(totp.validateCodeAt(input, 0));
            }
            assertFalse(totp.validateCodeAt("755224", -1));
            assertThrows(IllegalArgumentException.class, () -> totp.generateCodeAt(-1));
        }
    }

    @Test void invalidSecretsAndWrongTimeStepAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TOTP(null, 30));
        assertThrows(IllegalArgumentException.class, () -> new TOTP("!", 30));
        assertThrows(IllegalArgumentException.class, () -> new TOTP("MY", 30));
        assertThrows(IllegalArgumentException.class, () -> new TOTP(RFC_SECRET, 60));
    }

    @Test void closedSecretCannotGenerateOrValidateAgain() throws Exception {
        TOTP totp = new TOTP(RFC_SECRET, 30); totp.close(); totp.close();
        assertThrows(IllegalStateException.class, totp::generateCode);
        assertThrows(IllegalStateException.class, () -> totp.validateCode("755224"));
    }

    @Test void publicDateBasedMethodsWorkWithoutExternalClockLibrary() throws Exception {
        try (TOTP totp = new TOTP(RFC_SECRET, 30)) {
            assertTrue(totp.validateCode(totp.generateCode()));
        }
    }
}
