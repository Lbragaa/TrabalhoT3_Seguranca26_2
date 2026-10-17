package br.pucrio.inf1416.cofre.vault;

/** User-facing categories; causes and plaintext are deliberately not included in messages. */
public final class VaultException extends Exception {
    public enum Reason { PERMISSION, GROUP_ROUTE, KEY_RESTORE, KEY_MISMATCH, CATALOG, UNKNOWN_OWNER,
        MISSING_FILE, DECRYPTION, SIGNATURE, INVALID_PATH, EXISTS, STATE }
    private final Reason reason;
    public VaultException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
