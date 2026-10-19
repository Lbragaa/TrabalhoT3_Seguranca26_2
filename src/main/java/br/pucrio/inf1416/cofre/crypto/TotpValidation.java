/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.crypto;

/** Controlled-time adapter; the assignment TOTP implementation and its allowed imports are unchanged. */
public final class TotpValidation implements AutoCloseable {
    private final TOTP totp;
    public TotpValidation(String base32Secret) throws Exception { totp = new TOTP(base32Secret, 30); }
    public boolean validate(String code, long epochMillis) { return totp.validateCodeAt(code, epochMillis); }
    @Override public void close() { totp.close(); }
    @Override public String toString() { return "TotpValidation[redacted]"; }
}
