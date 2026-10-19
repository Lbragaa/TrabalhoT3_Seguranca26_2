/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

/** Test-only access to the existing numeric-time seam; never packaged. */
public final class TotpTestClock {
    private TotpTestClock() { }
    public static String code(String secret, long millis) throws Exception {
        try (TOTP totp = new TOTP(secret, 30)) { return totp.generateCodeAt(millis); }
    }
}
