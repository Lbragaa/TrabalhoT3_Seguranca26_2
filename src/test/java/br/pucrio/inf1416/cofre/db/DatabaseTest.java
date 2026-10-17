package br.pucrio.inf1416.cofre.db;

import br.pucrio.inf1416.cofre.audit.AuditEvent;
import br.pucrio.inf1416.cofre.audit.AuditService;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir Path temp;
    @Test void exactlyFiveTablesAndReopeningSeedsIdempotently() throws Exception {
        Path path = temp.resolve("runtime/cofre.db");
        var db = new Database(path); new Database(path);
        try (var c = db.open(); var s = c.prepareStatement("SELECT name FROM sqlite_master WHERE type='table'"); var r = s.executeQuery()) {
            Set<String> tables = new HashSet<>(); while (r.next()) { tables.add(r.getString(1)); }
            assertEquals(Set.of("Usuarios", "Chaveiro", "Grupos", "Mensagens", "Registros"), tables);
        }
        try (var c = db.open(); var s = c.prepareStatement("SELECT MID,texto FROM Mensagens"); var r = s.executeQuery()) {
            int count = 0; while (r.next()) { assertEquals(AuditEvent.fromCode(r.getInt(1)).template(), r.getString(2)); count++; }
            assertEquals(64, count);
        }
    }
    @Test void foreignKeysEnabledOnEveryConnectionAndInvalidAuditReferenceRejected() throws Exception {
        var db = new Database(temp.resolve("cofre.db"));
        for (int i = 0; i < 3; i++) {
            try (var c = db.open(); var s = c.prepareStatement("PRAGMA foreign_keys"); var r = s.executeQuery()) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
        }
        assertThrows(SQLException.class, () -> db.transaction(c -> {
            try (var s = c.prepareStatement("INSERT INTO Registros(timestamp,MID) VALUES(0,9999)")) { s.executeUpdate(); } return null;
        }));
    }
    @Test void groupMappingMatchesVerifiedCatalogAndDisplayNames() throws Exception {
        var db = new Database(temp.resolve("cofre.db"));
        for (Group group : Group.values()) {
            assertSame(group, Group.fromCatalogLabel(group.catalogLabel()));
            try (var c = db.open(); var s = c.prepareStatement("SELECT nome,rotulo FROM Grupos WHERE GID=?")) {
                s.setInt(1, group.id()); try (var r = s.executeQuery()) { assertTrue(r.next()); assertEquals(group.catalogLabel(), r.getString(1)); assertEquals(group.displayName(), r.getString(2)); }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Group.fromCatalogLabel("invented"));
    }
    @Test void catalogDamageIsNotSilentlyOverwrittenAndAuditHasNoMessageTextColumn() throws Exception {
        Path path = temp.resolve("cofre.db"); var db = new Database(path);
        try (var c = db.open(); var s = c.createStatement()) { s.executeUpdate("UPDATE Mensagens SET texto='damaged' WHERE MID=1001"); }
        assertThrows(SQLException.class, () -> new Database(path));
        try (var c = db.open(); var s = c.prepareStatement("PRAGMA table_info(Registros)"); var r = s.executeQuery()) {
            Set<String> columns = new HashSet<>(); while (r.next()) { columns.add(r.getString("name")); }
            assertEquals(Set.of("RID", "timestamp", "MID", "UID", "login_context", "file_context"), columns);
        }
    }
    @Test void auditPersistenceFailurePropagates() throws Exception {
        var db = new Database(temp.resolve("cofre.db"));
        try (var c = db.open(); var s = c.createStatement()) { s.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON Registros BEGIN SELECT RAISE(ABORT,'injected audit failure'); END"); }
        assertThrows(SQLException.class, () -> new AuditService(db).record(AuditEvent.E1001, null));
    }
}
