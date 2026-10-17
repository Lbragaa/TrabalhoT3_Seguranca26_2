package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.auth.SessionContext;
import br.pucrio.inf1416.cofre.audit.*;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import static br.pucrio.inf1416.cofre.audit.AuditEvent.*;
import static br.pucrio.inf1416.cofre.vault.VaultException.Reason.*;

/** One consultation, bound to a live MFA session. No key/password is returned to the UI. */
public final class VaultService implements AutoCloseable {
    public record Listing(User user, List<Catalog.Entry> files) { public Listing { files = List.copyOf(files); } }
    private final Database database;
    private final SessionContext session;
    private final CatalogService catalogs;
    private final AuditService audit;
    private final UserRepository users = new UserRepository();
    private final AccessPolicy policy = new AccessPolicy();
    @FunctionalInterface interface Publisher { void publish(Path target,Path temporary)throws IOException; }
    private final Publisher publisher;
    private Path directory;
    private Catalog listed;
    private PrivateKey key;
    private volatile boolean closed;
    public VaultService(Database database, SessionContext session, CatalogService catalogs) throws SQLException, VaultException {
        this(database,session,catalogs,(target,temporary)->Files.createLink(target,temporary));
    }
    VaultService(Database database, SessionContext session, CatalogService catalogs,Publisher publisher) throws SQLException, VaultException {
        this.database = database; this.session = session; this.catalogs = catalogs; audit = new AuditService(database);
        this.publisher=publisher;
        synchronized (session) {
            current(); record(E7001, null); session.bindConsultation(database, this);
        }
    }
    private User current() throws VaultException, SQLException {
        if (closed) { throw new VaultException(STATE, "Consulta encerrada."); }
        try {
            var identity = session.requireActive(database);
            try (var c = database.open()) {
                var stored = users.find(c, identity.uid()).orElseThrow(() -> new VaultException(PERMISSION, "Usuário não cadastrado."));
                if (!stored.email().equals(identity.email()) || stored.kid() != identity.kid() || stored.group() != identity.group()) {
                    throw new VaultException(PERMISSION, "Identidade da sessão alterada.");
                }
                return stored;
            }
        } catch (SecurityException e) { close(); throw new VaultException(PERMISSION, "Sessão autenticada necessária."); }
    }
    public User user() throws SQLException, VaultException { synchronized (session) { return current(); } }
    private void record(AuditEvent event, String file) throws SQLException {
        try { database.transaction(c -> { audit.recordFile(c, event, session.user(), file); return null; }); }
        catch (SQLException e) { close(); throw e; }
    }
    public Listing list(String folder, char[] phrase) throws SQLException, VaultException {
        synchronized (session) { synchronized (this) {
            User user = current(); clearListing(); record(E7003, null);
            Path selected;
            try { selected = SafePaths.directory(folder); }
            catch (VaultException e) { record(E7004, null); throw e; }
            PrivateKey restored = restore(user, phrase);
            try {
                Catalog catalog = catalogs.load(selected, event -> record(event, null));
                validateReferences(catalog);
                current();
                List<Catalog.Entry> visible = policy.listing(user, catalog);
                User updated = database.transaction(c -> {
                    audit.record(c, E7009, user); audit.record(c, E7014, user);
                    return users.consultationAccepted(c, user.uid());
                });
                directory = selected; listed = catalog; key = restored;
                return new Listing(updated, visible);
            } catch (SQLException e) { close(); throw e; }
        } }
    }
    private PrivateKey restore(User user, char[] phrase) throws SQLException, VaultException {
        KeyRepository.KeyRecord stored;
        try (var c = database.open()) { stored = new KeyRepository().find(c, user.kid()); }
        PrivateKey restored;
        try {
            if (phrase == null || phrase.length == 0 || phrase.length > 255) { throw new GeneralSecurityException(); }
            restored = new PrivateKeyService().restore(stored.encrypted(), phrase);
        } catch (GeneralSecurityException e) {
            record(E6006, null); throw new VaultException(KEY_RESTORE, "Frase secreta incorreta ou chave privada danificada.");
        }
        try {
            var certificate = new CertificateService().parse(stored.certificatePem().getBytes(StandardCharsets.US_ASCII));
            new SignatureService().validateKeyPair(restored, certificate.getPublicKey(), 10_240);
            if (!new CertificateService().identity(certificate).email().equals(user.email())) { throw new GeneralSecurityException(); }
            return restored;
        } catch (GeneralSecurityException e) {
            record(E6007, null); throw new VaultException(KEY_MISMATCH, "Chave privada e certificado cadastrados inconsistentes.");
        }
    }
    private void validateReferences(Catalog catalog) throws SQLException, VaultException {
        try (var c = database.open()) {
            for (var entry : catalog.entries()) {
                if (users.findByEmail(c, entry.owner()).isEmpty()) { throw new VaultException(UNKNOWN_OWNER, "Dono de arquivo sem certificado cadastrado."); }
            }
            for (var share : catalog.shares()) {
                if (users.findByEmail(c, share.recipient()).isEmpty()) { throw new VaultException(CATALOG, "Destinatário do catálogo não cadastrado."); }
            }
        }
    }
    public Path extract(String code, Path outputDirectory) throws SQLException, VaultException {
        synchronized (session) { synchronized (this) {
            User user = current();
            if (key == null || listed == null) { throw new VaultException(STATE, "Valide a frase secreta e liste os arquivos primeiro."); }
            Catalog.Entry shown = listed.entries().stream().filter(e -> e.code().equals(code)).findFirst().orElse(null);
            String fileContext = shown == null ? null : shown.filename();
            record(E7015, fileContext);
            Catalog fresh;
            try { fresh = catalogs.load(directory, event -> record(event, null)); validateReferences(fresh); }
            catch (VaultException e) { clearListing(); throw e; }
            var entry = fresh.entries().stream().filter(e -> e.code().equals(code)).findFirst().orElse(null);
            var access = policy.evaluate(user, entry, fresh);
            if (shown == null || !shown.equals(entry) || !access.visible()) {
                record(E7017, fileContext); throw new VaultException(PERMISSION, "Acesso negado ou atributos alterados. Liste novamente.");
            }
            if (!access.decryptable()) {
                record(E7017, fileContext); throw new VaultException(GROUP_ROUTE, "Arquivo visível pelo grupo, mas não há envelope para este usuário. Extração por grupo aguarda esclarecimento do professor.");
            }
            record(E7016, fileContext);
            Path target = SafePaths.output(outputDirectory, entry.filename(), directory);
            byte[] plaintext = decrypt(entry, access, fileContext);
            Path temporary = null;
            long verification=0;
            try {
                verification=database.transaction(c -> { audit.recordFile(c, E7018, user, fileContext); return audit.verifiedPendingPublication(c,user,fileContext); });
                temporary = Files.createTempFile(target.getParent(), ".cofre-", ".tmp");
                Files.write(temporary, plaintext);
                current();
                // Atomic no-clobber publication on NTFS/other hard-link-capable filesystems.
                publisher.publish(target, temporary);
                publication(verification,"published");
                return target;
            } catch (FileAlreadyExistsException e) { if(verification!=0){publication(verification,"failed");} throw new VaultException(EXISTS, "Arquivo de saída já existe. Nada foi sobrescrito."); }
            catch (IOException | UnsupportedOperationException e) { if(verification!=0){publication(verification,"failed");} throw new VaultException(INVALID_PATH, "A pasta de saída não permite a publicação segura do arquivo verificado."); }
            catch (SQLException e) { close(); throw e; }
            finally {
                Arrays.fill(plaintext, (byte)0);
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); }
                    catch (IOException e) { throw new VaultException(INVALID_PATH, "Não foi possível remover o arquivo temporário verificado."); }
                }
            }
        } }
    }
    private void publication(long rid,String status)throws SQLException{
        try{database.transaction(c->{audit.publication(c,rid,status);return null;});}catch(SQLException e){close();throw e;}
    }
    private byte[] decrypt(Catalog.Entry entry, AccessPolicy.Decision access, String file) throws SQLException, VaultException {
        try {
            PublicKey owner;
            try (var c = database.open()) {
                var user = users.findByEmail(c, entry.owner()).orElseThrow(() -> new VaultException(UNKNOWN_OWNER, "Dono não cadastrado."));
                var stored = new KeyRepository().find(c, user.kid());
                var cert = new CertificateService().parse(stored.certificatePem().getBytes(StandardCharsets.US_ASCII));
                if (!new CertificateService().identity(cert).email().equals(entry.owner())) { throw new VaultException(UNKNOWN_OWNER, "Certificado do dono inconsistente."); }
                owner = cert.getPublicKey();
            }
            return new ProtectedFileService().decryptAndVerify(SafePaths.input(directory, entry.code()+".enc"),
                    SafePaths.input(directory, access.envelope(entry.code())), SafePaths.input(directory, entry.code()+".asd"), key, owner);
        } catch (SignatureException e) {
            record(E7018, file); record(E7021, file); throw new VaultException(SIGNATURE, "Assinatura do arquivo inválida. Nenhum arquivo foi publicado.");
        } catch (GeneralSecurityException e) { record(E7020, file); throw new VaultException(DECRYPTION, "Falha na decriptação do arquivo."); }
        catch (IOException e) { record(E7020, file); throw new VaultException(MISSING_FILE, "Criptograma, envelope ou assinatura ausente, inacessível ou inseguro."); }
    }
    public void back() throws SQLException, VaultException {
        synchronized (session) { synchronized (this) { current(); try { record(E7002, null); } finally { close(); session.releaseConsultation(this); } } }
    }
    private void clearListing() { key = null; listed = null; directory = null; }
    public boolean isClosed() { return closed; }
    /** Nonblocking cancellation marker for a disposed UI; workers clear material on completion. */
    public void invalidate() { closed = true; }
    @Override public void close() { closed = true; synchronized (this) { clearListing(); } }
}
