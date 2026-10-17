package br.pucrio.inf1416.cofre.app;

import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.security.PrivateKey;
import java.util.Arrays;

/** Startup key material, not an authenticated user session or registration authorization. */
public final class AdministratorContext implements AutoCloseable {
    private final User administrator;
    private char[] passphrase;
    private PrivateKey key;
    public AdministratorContext(User administrator, char[] passphrase, PrivateKey key) {
        this.administrator = administrator; this.passphrase = passphrase.clone(); this.key = key;
    }
    public User administrator() { return administrator; }
    public boolean isClosed() { return key == null; }
    @Override public void close() {
        if (passphrase != null) { Arrays.fill(passphrase, '\0'); passphrase = null; }
        key = null;
    }
    @Override public String toString() { return "AdministratorContext[redacted]"; }
}
