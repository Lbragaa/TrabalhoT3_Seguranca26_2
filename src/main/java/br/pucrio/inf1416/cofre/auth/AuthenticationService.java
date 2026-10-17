package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.audit.*;
import br.pucrio.inf1416.cofre.crypto.TotpSecretService;
import br.pucrio.inf1416.cofre.crypto.TotpValidation;
import br.pucrio.inf1416.cofre.db.*;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import static br.pucrio.inf1416.cofre.audit.AuditEvent.*;
import static br.pucrio.inf1416.cofre.auth.AuthenticationException.Reason.*;

/** One local navigation flow; account failure limits are serialized in SQLite across flows/processes. */
public final class AuthenticationService implements AutoCloseable {
    public enum Stage { NEW, IDENTIFY, PASSWORD, TOKEN, AUTHENTICATED, CLOSED }
    public enum Identification { KNOWN, UNKNOWN, BLOCKED }
    public enum PasswordResult { ACCEPTED, REJECTED, BLOCKED, STALE }
    public enum TokenResult { ACCEPTED, REJECTED, BLOCKED }
    public enum Menu { REGISTER, CONSULT, EXIT }
    private final Database database;
    private final Clock clock;
    private final AuditService audit;
    private final UserRepository users = new UserRepository();
    private final KeypadPasswordVerifier verifier = new KeypadPasswordVerifier();
    private Stage stage = Stage.NEW;
    private User identified;
    private TotpValidation token;
    private SessionContext session;
    private long generation;
    private boolean verifying;
    public AuthenticationService(Database database) { this(database, Clock.systemUTC()); }
    public AuthenticationService(Database database, Clock clock) { this.database = database; this.clock = clock; audit = new AuditService(database); }
    public synchronized Stage stage() { return stage; }
    public synchronized User identifiedUser() { return identified; }
    synchronized boolean passwordVerificationInProgress() { return verifying; }
    public synchronized SessionContext session() throws AuthenticationException { require(Stage.AUTHENTICATED); return checkedSession(session); }

    public synchronized void start() throws SQLException, AuthenticationException {
        require(Stage.NEW);
        transact(c -> { audit.record(c, E2001, null); return null; }); stage = Stage.IDENTIFY;
    }
    public synchronized Identification identify(String login) throws SQLException, AuthenticationException {
        require(Stage.IDENTIFY);
        boolean valid = login != null && login.length() <= 255 && login.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
        User[] selected = new User[1];
        Identification result = transact(c -> {
            User user = valid ? users.findByEmail(c, login).orElse(null) : null;
            if (user == null) { audit.recordIdentity(c, E2005, null, valid ? login : null); return Identification.UNKNOWN; }
            user = users.refreshLockout(c, user.uid(), clock.millis()); selected[0] = user;
            if (blocked(user)) { audit.record(c, E2004, user); return Identification.BLOCKED; }
            audit.record(c, E2003, user); audit.record(c, E2002, null); audit.record(c, E3001, user);
            return Identification.KNOWN;
        });
        if (result == Identification.KNOWN) { identified = selected[0]; stage = Stage.PASSWORD; generation++; }
        return result;
    }

    public PasswordResult password(List<DigitPair> captured) throws SQLException, AuthenticationException, InterruptedException {
        final long version;
        final long uid;
        final UserRepository.Credentials credentials;
        synchronized (this) {
            require(Stage.PASSWORD);
            if (verifying) { throw new AuthenticationException(BUSY, "Verificação de senha já em andamento."); }
            User current = transact(c -> users.refreshLockout(c, identified.uid(), clock.millis()));
            if (blocked(current)) { returnBlocked(Stage.PASSWORD, current); return PasswordResult.BLOCKED; }
            credentials = transact(c -> users.credentials(c, current.uid()));
            verifying = true; version = generation; uid = current.uid();
        }
        TotpValidation preparedToken = null;
        try (credentials; var result = verifier.verify(credentials.hash(), captured)) {
            if (result.matched()) {
                char[] recovered = result.passwordCopy();
                try { preparedToken = new TotpValidation(new TotpSecretService().decrypt(credentials.encryptedToken(), recovered)); }
                catch (Exception e) { throw new AuthenticationException(DATA, "Não foi possível recuperar o segredo TOTP armazenado."); }
                finally { Arrays.fill(recovered, '\0'); }
            }
            synchronized (this) {
                if (stage != Stage.PASSWORD || version != generation) { return PasswordResult.STALE; }
                boolean accepted = result.matched();
                PasswordResult outcome = transact(c -> {
                    User user = users.refreshLockout(c, uid, clock.millis());
                    if (blocked(user)) { endToIdentification(c, Stage.PASSWORD, user); audit.record(c, E2004, user); return PasswordResult.BLOCKED; }
                    if (accepted) {
                        users.passwordAccepted(c, uid); audit.record(c, E3003, user); audit.record(c, E3002, user); audit.record(c, E4001, user);
                        return PasswordResult.ACCEPTED;
                    }
                    int failures = user.passwordFailures() + 1;
                    users.failures(c, uid, failures, user.tokenFailures(), failures >= 3 ? clock.millis() + 120_000 : 0);
                    audit.record(c, AuditEvent.fromCode(3003 + Math.min(failures, 3)), user);
                    if (failures >= 3) { audit.record(c, E3007, user); endToIdentification(c, Stage.PASSWORD, user); return PasswordResult.BLOCKED; }
                    return PasswordResult.REJECTED;
                });
                if (outcome == PasswordResult.ACCEPTED) { token = preparedToken; preparedToken = null; stage = Stage.TOKEN; }
                else if (outcome == PasswordResult.BLOCKED) { clearAttempt(); stage = Stage.IDENTIFY; generation++; }
                return outcome;
            }
        } catch (AuthenticationException e) { synchronized (this) { abort(); } throw e; }
        finally {
            if (preparedToken != null) { preparedToken.close(); }
            synchronized (this) { verifying = false; }
        }
    }

    public synchronized TokenResult token(String code) throws SQLException, AuthenticationException {
        require(Stage.TOKEN);
        User[] authenticated = new User[1];
        TokenResult result = transact(c -> {
            User user = users.refreshLockout(c, identified.uid(), clock.millis());
            if (blocked(user)) { endToIdentification(c, Stage.TOKEN, user); audit.record(c, E2004, user); return TokenResult.BLOCKED; }
            if (token.validate(code, clock.millis())) {
                authenticated[0] = users.authenticationAccepted(c, user.uid());
                audit.record(c, E4003, user); audit.record(c, E4002, user); audit.record(c, E1003, user);
                return TokenResult.ACCEPTED;
            }
            int failures = user.tokenFailures() + 1;
            users.failures(c, user.uid(), user.passwordFailures(), failures, failures >= 3 ? clock.millis() + 120_000 : 0);
            audit.record(c, AuditEvent.fromCode(4003 + Math.min(failures, 3)), user);
            if (failures >= 3) { audit.record(c, E4007, user); endToIdentification(c, Stage.TOKEN, user); return TokenResult.BLOCKED; }
            return TokenResult.REJECTED;
        });
        if (result == TokenResult.ACCEPTED) { clearAttempt(); session = new SessionContext(this, database, authenticated[0]); stage = Stage.AUTHENTICATED; }
        else if (result == TokenResult.BLOCKED) { clearAttempt(); stage = Stage.IDENTIFY; generation++; }
        return result;
    }

    public synchronized void restart() throws SQLException, AuthenticationException {
        if (verifying) { throw new AuthenticationException(BUSY, "Aguarde a verificação antes de reiniciar."); }
        if (stage != Stage.IDENTIFY && stage != Stage.PASSWORD && stage != Stage.TOKEN) { throw new AuthenticationException(STATE, "Reinício de autenticação inesperado."); }
        transact(c -> { endToIdentification(c, stage, identified); return null; });
        clearAttempt(); stage = Stage.IDENTIFY; generation++;
    }
    public synchronized User mainMenu(SessionContext value) throws SQLException, AuthenticationException {
        checkedSession(value);
        return transact(c -> { User user = users.find(c, value.user().uid()).orElseThrow(); audit.record(c, E5001, user); return user; });
    }
    public synchronized void menu(SessionContext value, Menu choice) throws SQLException, AuthenticationException {
        checkedSession(value);
        if (choice == null) { throw new AuthenticationException(STATE, "Opção de menu inválida."); }
        if (choice == Menu.REGISTER && value.registration == null) { throw new AuthenticationException(UNAUTHORIZED, "Cadastro permitido somente ao administrador."); }
        transact(c -> { audit.record(c, choice == Menu.REGISTER ? E5002 : choice == Menu.CONSULT ? E5003 : E5004, value.user()); return null; });
    }
    public synchronized RegistrationAuthorization registrationAuthorization(SessionContext value) throws AuthenticationException {
        checkedSession(value);
        if (value.registration == null || !value.registration.active()) { throw new AuthenticationException(UNAUTHORIZED, "Cadastro permitido somente ao administrador autenticado."); }
        return value.registration;
    }
    public synchronized User exitScreen(SessionContext value) throws SQLException, AuthenticationException {
        checkedSession(value);
        return transact(c -> { User user = users.find(c, value.user().uid()).orElseThrow(); audit.record(c, E8001, user); return user; });
    }
    public synchronized void backFromExit(SessionContext value) throws SQLException, AuthenticationException { checkedSession(value); transact(c -> { audit.record(c, E8004, value.user()); return null; }); }
    public synchronized void logout(SessionContext value) throws SQLException, AuthenticationException {
        checkedSession(value);
        try { transact(c -> { audit.record(c, E8002, value.user()); audit.record(c, E1004, value.user()); audit.record(c, E2001, null); return null; }); }
        finally { value.close(); session = null; }
        stage = Stage.IDENTIFY; generation++;
    }
    public synchronized void exitSystem(SessionContext value) throws SQLException, AuthenticationException {
        checkedSession(value); transact(c -> { audit.record(c, E8003, value.user()); return null; }); close();
    }
    private SessionContext checkedSession(SessionContext value) throws AuthenticationException {
        if (stage != Stage.AUTHENTICATED || value == null || value != session || value.issuer != this || !value.active()) {
            throw new AuthenticationException(UNAUTHORIZED, "Sessão autenticada necessária.");
        }
        return value;
    }
    private boolean blocked(User user) { return user.blockedUntil() > clock.millis(); }
    private void returnBlocked(Stage current, User user) throws SQLException {
        transact(c -> { endToIdentification(c, current, user); audit.record(c, E2004, user); return null; });
        clearAttempt(); stage = Stage.IDENTIFY; generation++;
    }
    private void endToIdentification(Connection c, Stage current, User user) throws SQLException {
        audit.record(c, current == Stage.PASSWORD ? E3002 : current == Stage.TOKEN ? E4002 : E2002, user);
        audit.record(c, E2001, null);
    }
    private void require(Stage expected) throws AuthenticationException { if (stage != expected) { throw new AuthenticationException(STATE, "Etapa de autenticação inesperada."); } }
    private <T> T transact(Database.Work<T> work) throws SQLException {
        try { return database.transaction(work); }
        catch (SQLException | RuntimeException e) { abort(); throw e; }
    }
    private void clearAttempt() { if (token != null) { token.close(); token = null; } identified = null; }
    private void abort() { clearAttempt(); if (session != null) { session.close(); session = null; } stage = Stage.CLOSED; generation++; verifier.close(); }
    @Override public synchronized void close() throws SQLException {
        if (stage == Stage.CLOSED) { return; }
        try {
            database.transaction(c -> {
                if (stage == Stage.AUTHENTICATED && session != null) { audit.record(c, E1004, session.user()); }
                else if (stage != Stage.NEW) { audit.record(c, stage == Stage.PASSWORD ? E3002 : stage == Stage.TOKEN ? E4002 : E2002, identified); }
                return null;
            });
        } finally { abort(); }
    }
}
