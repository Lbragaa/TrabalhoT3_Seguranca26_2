package br.pucrio.inf1416.cofre.auth;

public final class RegistrationException extends Exception {
    public enum Reason { UNAUTHORIZED, PASSWORD, CERTIFICATE, KEY_PATH, KEY_RESTORE, KEY_MISMATCH, DUPLICATE, STATE }
    private final Reason reason;
    public RegistrationException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
