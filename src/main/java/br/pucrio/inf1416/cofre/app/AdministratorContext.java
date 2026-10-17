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
    public synchronized boolean isClosed() { return key == null; }
    /** Package-scoped fixed catalog operation; the startup private key never leaves its context. */
    synchronized byte[] catalog(java.nio.file.Path directory, String name, java.security.PublicKey certificate)
            throws java.io.IOException, java.security.GeneralSecurityException {
        if (key == null || (!name.equals("index") && !name.equals("share"))) { throw new java.security.GeneralSecurityException("Contexto indisponível."); }
        return new br.pucrio.inf1416.cofre.crypto.ProtectedFileService().decryptAndVerify(
                br.pucrio.inf1416.cofre.vault.SafePaths.input(directory, name+".enc"),
                br.pucrio.inf1416.cofre.vault.SafePaths.input(directory, name+".env"),
                br.pucrio.inf1416.cofre.vault.SafePaths.input(directory, name+".asd"), key, certificate);
    }
    @Override public synchronized void close() {
        if (passphrase != null) { Arrays.fill(passphrase, '\0'); passphrase = null; }
        key = null;
    }
    @Override public String toString() { return "AdministratorContext[redacted]"; }
}
