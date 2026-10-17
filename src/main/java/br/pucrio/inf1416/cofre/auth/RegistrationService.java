package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.app.AdministratorContext;
import br.pucrio.inf1416.cofre.audit.AuditEvent;
import br.pucrio.inf1416.cofre.audit.AuditService;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import static br.pucrio.inf1416.cofre.auth.RegistrationException.Reason.*;

public final class RegistrationService {
    private final Database database;
    private final AuditService audit;
    private final UserRepository users = new UserRepository();
    private final KeyRepository keys = new KeyRepository();
    public RegistrationService(Database database) { this.database = database; this.audit = new AuditService(database); }
    public record FormInfo(User actor, long totalUsers, boolean bootstrap) { }

    public FormInfo showForm(RegistrationAuthorization authorization, boolean bootstrap) throws SQLException, RegistrationException {
        try {
            return database.transaction(c -> {
                User actor = authorize(c, authorization, bootstrap);
                audit.record(c, AuditEvent.E6001, actor);
                return new FormInfo(actor, users.count(c), bootstrap);
            });
        } catch (AccessFailure e) { throw unauthorized(); }
    }

    public void back(RegistrationAuthorization authorization) throws SQLException, RegistrationException {
        try { database.transaction(c -> { audit.record(c, AuditEvent.E6010, authorize(c, authorization, false)); return null; }); }
        catch (AccessFailure e) { throw unauthorized(); }
    }

    /** Expensive validation and preparation only: no account/key/token rows yet. */
    public PendingRegistration prepare(RegistrationAuthorization authorization, boolean bootstrap, RegistrationRequest request)
            throws SQLException, RegistrationException {
        User actor;
        try {
            actor = database.transaction(c -> {
                User user = authorize(c, authorization, bootstrap);
                audit.record(c, AuditEvent.E6002, user); return user;
            });
        } catch (AccessFailure e) { throw unauthorized(); }
        if (!PersonalPasswordPolicy.valid(request.password) || !Arrays.equals(request.password, request.confirmation)) {
            throw rejected(AuditEvent.E6003, actor, PASSWORD, "Senha deve ter 8 a 10 dígitos, sem repetições adjacentes, e confirmação igual.");
        }
        if (request.group == null || (bootstrap && request.group != Group.ADMINISTRATOR)) { throw unauthorized(); }
        X509Certificate certificate;
        CertificateService.Identity identity;
        try {
            Path path = validPath(request.certificatePath);
            certificate = new CertificateService().read(path);
            identity = new CertificateService().identity(certificate);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw rejected(AuditEvent.E6004, actor, CERTIFICATE, "Certificado inválido ou caminho inacessível.");
        }
        byte[] encrypted;
        try { encrypted = Files.readAllBytes(validPath(request.keyPath)); }
        catch (IOException | IllegalArgumentException e) {
            throw rejected(AuditEvent.E6005, actor, KEY_PATH, "Caminho da chave privada inválido ou inacessível.");
        }
        PrivateKey privateKey;
        try {
            if (request.passphrase.length == 0 || request.passphrase.length > 255) { throw new GeneralSecurityException(); }
            privateKey = new PrivateKeyService().restore(encrypted, request.passphrase);
        } catch (GeneralSecurityException e) {
            throw rejected(AuditEvent.E6006, actor, KEY_RESTORE, "Frase secreta incorreta ou chave privada danificada.");
        }
        try {
            new SignatureService().validateKeyPair(privateKey, certificate.getPublicKey(), 10_240);
        } catch (GeneralSecurityException e) {
            throw rejected(AuditEvent.E6007, actor, KEY_MISMATCH, "A chave privada não corresponde ao certificado.");
        }
        try (Connection c = database.open()) {
            if (users.findByEmail(c, identity.email()).isPresent()) { throw duplicate(); }
        }
        try {
            var tokens = new TotpSecretService();
            String secret = tokens.generateSecret();
            return new PendingRegistration(this, authorization, bootstrap, actor, certificate, identity, request.group,
                    encrypted, new PasswordHasher().hash(request.password), tokens.encrypt(secret, request.password), secret,
                    bootstrap ? request.passphrase.clone() : null, bootstrap ? privateKey : null);
        } catch (GeneralSecurityException e) {
            throw new RegistrationException(STATE, "Não foi possível proteger o segredo TOTP.");
        }
    }

    private Path validPath(String value) throws IOException {
        if (value == null || value.isBlank() || value.length() > 255) { throw new IOException("Invalid path"); }
        Path path = Path.of(value);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) { throw new IOException("Unreadable file"); }
        return path;
    }

    public void confirmCertificate(PendingRegistration pending) throws RegistrationException {
        synchronized (pending) { checkPending(pending); pending.confirmed = true; }
    }

    /** Called only after certificate acceptance and enrollment acknowledgement. Audit and account commit together. */
    public Result commit(PendingRegistration pending) throws SQLException, RegistrationException {
        synchronized (pending) {
            checkPending(pending);
            if (!pending.confirmed) { throw new RegistrationException(STATE, "Confirme o certificado antes de concluir."); }
            String pem;
            try { pem = new CertificateService().toPem(pending.certificate); }
            catch (GeneralSecurityException e) { throw new RegistrationException(CERTIFICATE, "Não foi possível armazenar o certificado."); }
            try {
                User created = database.transaction(c -> {
                    User actor = authorize(c, pending.authorization, pending.bootstrap);
                    if (users.findByEmail(c, pending.identity.email()).isPresent()) { throw new DuplicateFailure(); }
                    long uid = users.nextUid(c); long kid = keys.nextKid(c);
                    users.insert(c, uid, kid, pending.identity.email(), pending.identity.name(), pending.group,
                            pending.bootstrap, pending.hash, pending.encryptedToken);
                    keys.insert(c, kid, uid, pending.encryptedKey, pem);
                    User result = users.find(c, uid).orElseThrow();
                    // No invented 'registration success' event: 6008 is the specified accepted confirmation.
                    audit.record(c, AuditEvent.E6008, actor == null ? result : actor);
                    return result;
                });
                AdministratorContext context = pending.bootstrap
                        ? new AdministratorContext(created, pending.passphrase, pending.privateKey) : null;
                pending.close();
                return new Result(database, created, context);
            } catch (AccessFailure e) { throw unauthorized(); }
            catch (DuplicateFailure e) { throw duplicate(); }
        }
    }

    public void cancel(PendingRegistration pending) throws SQLException, RegistrationException {
        synchronized (pending) {
            checkPending(pending);
            try { audit.record(AuditEvent.E6009, pending.actor); }
            finally { pending.close(); }
        }
    }

    private void checkPending(PendingRegistration pending) throws RegistrationException {
        if (pending.service != this || pending.closed) { throw new RegistrationException(STATE, "Cadastro expirado ou já concluído."); }
    }
    private RegistrationException rejected(AuditEvent event, User actor, RegistrationException.Reason reason, String message) throws SQLException {
        audit.record(event, actor); return new RegistrationException(reason, message);
    }
    private RegistrationException unauthorized() { return new RegistrationException(UNAUTHORIZED, "Cadastro permitido somente ao administrador autenticado; cadastro inicial exige banco vazio."); }
    private RegistrationException duplicate() { return new RegistrationException(DUPLICATE, "Este e-mail já está cadastrado."); }
    private User authorize(Connection c, RegistrationAuthorization authorization, boolean bootstrap) throws SQLException {
        if (bootstrap) {
            if (authorization != null || users.count(c) != 0) { throw new AccessFailure(); }
            return null;
        }
        if (authorization == null || !authorization.active() || authorization.database != database) { throw new AccessFailure(); }
        User actor = users.find(c, authorization.uid).orElseThrow(AccessFailure::new);
        if (actor.group() != Group.ADMINISTRATOR) { throw new AccessFailure(); }
        return actor;
    }
    private static final class AccessFailure extends RuntimeException { }
    private static final class DuplicateFailure extends RuntimeException { }

    public static final class Result implements AutoCloseable {
        private final Database database;
        private final User user;
        private AdministratorContext context;
        private Result(Database database, User user, AdministratorContext context) { this.database = database; this.user = user; this.context = context; }
        public boolean belongsTo(Database database) { return this.database == database; }
        public User user() { return user; }
        public synchronized AdministratorContext takeBootstrapContext() {
            AdministratorContext result = context; context = null; return result;
        }
        @Override public synchronized void close() { if (context != null) { context.close(); context = null; } }
    }

    public static final class PendingRegistration implements AutoCloseable {
        private final RegistrationService service;
        private final RegistrationAuthorization authorization;
        private final boolean bootstrap;
        private final User actor;
        private final X509Certificate certificate;
        private final CertificateService.Identity identity;
        private final Group group;
        private final byte[] encryptedKey;
        private final String hash;
        private final byte[] encryptedToken;
        private String secret;
        private char[] passphrase;
        private PrivateKey privateKey;
        private boolean confirmed;
        private boolean closed;
        private PendingRegistration(RegistrationService service, RegistrationAuthorization authorization, boolean bootstrap,
                User actor, X509Certificate certificate, CertificateService.Identity identity, Group group,
                byte[] encryptedKey, String hash, byte[] encryptedToken, String secret, char[] passphrase, PrivateKey privateKey) {
            this.service = service; this.authorization = authorization; this.bootstrap = bootstrap; this.actor = actor;
            this.certificate = certificate; this.identity = identity; this.group = group; this.encryptedKey = encryptedKey;
            this.hash = hash; this.encryptedToken = encryptedToken; this.secret = secret; this.passphrase = passphrase; this.privateKey = privateKey;
        }
        public X509Certificate certificate() { return certificate; }
        public CertificateService.Identity identity() { return identity; }
        public synchronized String enrollmentSecret() {
            if (closed || !confirmed) { throw new IllegalStateException("Confirmação do certificado necessária."); }
            return secret;
        }
        @Override public synchronized void close() {
            if (passphrase != null) { Arrays.fill(passphrase, '\0'); passphrase = null; }
            privateKey = null; secret = null; closed = true;
        }
        @Override public String toString() { return "PendingRegistration[redacted]"; }
    }
}
