/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.db.Group;
import br.pucrio.inf1416.cofre.db.UserRepository.User;

/** Issued only after token acceptance; contains no personal password or token secret. */
public final class SessionContext implements AutoCloseable {
    final AuthenticationService issuer;
    final RegistrationAuthorization registration;
    private final User user;
    private final Database database;
    private final java.util.Set<AutoCloseable> consultations = new java.util.HashSet<>();
    private volatile boolean active = true;
    SessionContext(AuthenticationService issuer, Database database, User user) {
        this.issuer = issuer; this.user = user; this.database = database;
        registration = user.group() == Group.ADMINISTRATOR ? new RegistrationAuthorization(database, user.uid()) : null;
    }
    public User user() { return user; }
    public boolean active() { return active; }
    /** Vault operations hold this monitor until completion, making publication and logout ordered. */
    public synchronized User requireActive(Database expected) {
        if (!active || database != expected) { throw new SecurityException("Sessão autenticada necessária."); }
        return user;
    }
    public synchronized void bindConsultation(Database expected, AutoCloseable resource) {
        requireActive(expected); consultations.add(resource);
    }
    public synchronized void releaseConsultation(AutoCloseable resource) { consultations.remove(resource); }
    @Override public synchronized void close() {
        active = false; if (registration != null) { registration.close(); }
        for (AutoCloseable resource : java.util.List.copyOf(consultations)) {
            try { resource.close(); } catch (Exception e) { throw new IllegalStateException("Falha ao liberar consulta.", e); }
        }
        consultations.clear();
    }
    @Override public String toString() { return "SessionContext[uid=" + user.uid() + ",active=" + active + "]"; }
}
