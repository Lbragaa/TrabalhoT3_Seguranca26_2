package br.pucrio.inf1416.cofre.vault;

import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.sql.SQLException;
import java.util.Arrays;
import static br.pucrio.inf1416.cofre.audit.AuditEvent.*;
import br.pucrio.inf1416.cofre.audit.AuditEvent;
import static br.pucrio.inf1416.cofre.vault.VaultException.Reason.*;

/** The source owns bootstrap key access; no key is returned to this class or callers. */
public final class CatalogService {
    @FunctionalInterface public interface Source { byte[] verified(Path directory, String name) throws IOException, GeneralSecurityException, SQLException; }
    @FunctionalInterface public interface Events { void record(AuditEvent event) throws SQLException; }
    private final Source source;
    public CatalogService(Source source) { this.source = source; }
    public Catalog load(Path directory, Events events) throws VaultException, SQLException {
        byte[] index = null, share = null;
        try {
            index = read(directory, "index", events);
            share = read(directory, "share", events);
            return new CatalogParser().parse(index, share);
        } finally { if (index != null) { Arrays.fill(index, (byte)0); } if (share != null) { Arrays.fill(share, (byte)0); } }
    }
    private byte[] read(Path directory, String name, Events events) throws SQLException, VaultException {
        boolean index = name.equals("index");
        byte[] bytes;
        try { bytes = source.verified(directory, name); }
        catch (SignatureException e) {
            events.record(index ? E7005 : E7010); events.record(index ? E7008 : E7013);
            throw new VaultException(SIGNATURE, "Assinatura do catálogo "+name+" inválida. Lista indisponível.");
        } catch (GeneralSecurityException e) {
            events.record(index ? E7007 : E7012); throw new VaultException(DECRYPTION, "Falha na decriptação do catálogo "+name+".");
        } catch (IOException e) {
            events.record(index ? E7007 : E7012); throw new VaultException(MISSING_FILE, "Metarquivo do catálogo "+name+" ausente, inacessível ou inseguro.");
        }
        try { events.record(index ? E7005 : E7010); events.record(index ? E7006 : E7011); return bytes; }
        catch (SQLException | RuntimeException e) { Arrays.fill(bytes, (byte)0); throw e; }
    }
}
