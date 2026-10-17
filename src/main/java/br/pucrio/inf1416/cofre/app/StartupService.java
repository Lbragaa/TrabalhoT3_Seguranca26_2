package br.pucrio.inf1416.cofre.app;

import br.pucrio.inf1416.cofre.audit.AuditEvent;
import br.pucrio.inf1416.cofre.audit.AuditService;
import br.pucrio.inf1416.cofre.auth.RegistrationService;
import br.pucrio.inf1416.cofre.crypto.CertificateService;
import br.pucrio.inf1416.cofre.crypto.PrivateKeyService;
import br.pucrio.inf1416.cofre.crypto.SignatureService;
import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.db.KeyRepository;
import br.pucrio.inf1416.cofre.db.UserRepository;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.sql.SQLException;

/** Startup validation only. READY deliberately does not mean an authenticated user session. */
public final class StartupService {
    public enum State { NEW, BOOTSTRAP_REQUIRED, UNLOCK_REQUIRED, READY, TERMINATED }
    private final Database database;
    private final AuditService audit;
    private final UserRepository users = new UserRepository();
    private State state = State.NEW;
    private User bootstrap;
    private AdministratorContext context;
    public StartupService(Database database) { this.database = database; this.audit = new AuditService(database); }
    public synchronized State state() { return state; }
    public synchronized User administrator() { return bootstrap; }

    public synchronized State begin() throws SQLException {
        if (state != State.NEW) { throw new IllegalStateException("Partida já iniciada."); }
        try {
            bootstrap = database.transaction(c -> {
                long count = users.count(c);
                User user = users.bootstrap(c).orElse(null);
                if (count != 0 && user == null) { throw new SQLException("Administrador inicial ausente; inicialização interrompida."); }
                audit.record(c, AuditEvent.E1001, null);
                audit.record(c, count == 0 ? AuditEvent.E1005 : AuditEvent.E1006, user);
                return user;
            });
            state = bootstrap == null ? State.BOOTSTRAP_REQUIRED : State.UNLOCK_REQUIRED;
            return state;
        } catch (SQLException e) { state = State.TERMINATED; throw e; }
    }

    public synchronized void acceptBootstrap(RegistrationService.Result result) throws SQLException {
        if (state != State.BOOTSTRAP_REQUIRED || !result.belongsTo(database) || !result.user().bootstrap()) { throw new IllegalStateException("Cadastro inicial inesperado."); }
        try (var c = database.open()) {
            User stored = users.bootstrap(c).orElseThrow(() -> new SQLException("Administrador inicial ausente."));
            if (stored.uid() != result.user().uid()) { throw new SQLException("Administrador inicial inconsistente."); }
            AdministratorContext candidate = result.takeBootstrapContext();
            if (candidate == null || candidate.isClosed()) { throw new IllegalStateException("Contexto inicial inválido."); }
            bootstrap = stored; context = candidate; state = State.READY;
        }
    }

    public synchronized void unlock(char[] passphrase) throws SQLException, GeneralSecurityException {
        if (state != State.UNLOCK_REQUIRED) { throw new IllegalStateException("Validação inicial inesperada."); }
        KeyRepository.KeyRecord stored;
        try (var c = database.open()) { stored = new KeyRepository().find(c, bootstrap.kid()); }
        PrivateKey key;
        try {
            if (passphrase.length == 0 || passphrase.length > 255) { throw new GeneralSecurityException(); }
            key = new PrivateKeyService().restore(stored.encrypted(), passphrase);
        } catch (GeneralSecurityException e) {
            fail(AuditEvent.E6006);
            throw new GeneralSecurityException("Frase secreta incorreta ou chave privada danificada. Sistema encerrado.");
        }
        try {
            var certificate = new CertificateService().parse(stored.certificatePem().getBytes(StandardCharsets.US_ASCII));
            new SignatureService().validateKeyPair(key, certificate.getPublicKey(), 10_240);
        } catch (GeneralSecurityException e) {
            fail(AuditEvent.E6007);
            throw new GeneralSecurityException("Chave privada e certificado inconsistentes. Sistema encerrado.");
        }
        context = new AdministratorContext(bootstrap, passphrase, key);
        state = State.READY;
    }
    private void fail(AuditEvent event) throws SQLException {
        state = State.TERMINATED;
        database.transaction(c -> { audit.record(c, event, bootstrap); audit.record(c, AuditEvent.E1002, null); return null; });
    }
    public synchronized void shutdown() throws SQLException {
        boolean record = state != State.NEW && state != State.TERMINATED;
        state = State.TERMINATED;
        try { if (record) { audit.record(AuditEvent.E1002, null); } }
        finally { if (context != null) { context.close(); context = null; } }
    }
    public synchronized boolean hasAdministratorMaterial() { return context != null && !context.isClosed(); }
}
