package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.db.Group;
import br.pucrio.inf1416.cofre.db.UserRepository.User;

/** Issued only after token acceptance; contains no personal password or token secret. */
public final class SessionContext implements AutoCloseable {
    final AuthenticationService issuer;
    final RegistrationAuthorization registration;
    private final User user;
    private volatile boolean active = true;
    SessionContext(AuthenticationService issuer, Database database, User user) {
        this.issuer = issuer; this.user = user;
        registration = user.group() == Group.ADMINISTRATOR ? new RegistrationAuthorization(database, user.uid()) : null;
    }
    public User user() { return user; }
    public boolean active() { return active; }
    @Override public void close() { active = false; if (registration != null) { registration.close(); } }
    @Override public String toString() { return "SessionContext[uid=" + user.uid() + ",active=" + active + "]"; }
}
