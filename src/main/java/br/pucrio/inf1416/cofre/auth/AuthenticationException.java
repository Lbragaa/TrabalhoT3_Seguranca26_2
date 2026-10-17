package br.pucrio.inf1416.cofre.auth;

public final class AuthenticationException extends Exception {
    public enum Reason { STATE, BUSY, UNAUTHORIZED, DATA }
    private final Reason reason;
    public AuthenticationException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
