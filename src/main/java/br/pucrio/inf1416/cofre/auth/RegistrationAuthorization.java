package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.db.Database;

/** MFA session capability: package-private issuance, owned and revoked by the authenticated session. */
public final class RegistrationAuthorization implements AutoCloseable {
    final Database database;
    final long uid;
    private volatile boolean active = true;
    RegistrationAuthorization(Database database, long uid) { this.database = database; this.uid = uid; }
    boolean active() { return active; }
    @Override public void close() { active = false; }
}
