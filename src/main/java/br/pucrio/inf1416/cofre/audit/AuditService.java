package br.pucrio.inf1416.cofre.audit;

import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/** Event references and explicit identity context only. SQLException always reaches the caller. */
public final class AuditService {
    private final Database database;
    public AuditService(Database database) { this.database = database; }
    public void record(AuditEvent event, User actor) throws SQLException {
        database.transaction(c -> { record(c, event, actor); return null; });
    }
    public void record(Connection c, AuditEvent event, User actor) throws SQLException {
        recordIdentity(c, event, actor, actor == null ? null : actor.email());
    }
    /** Explicit login identity context for an unknown, syntactically valid email; never a token/password. */
    public void recordIdentity(Connection c, AuditEvent event, User actor, String login) throws SQLException {
        recordContext(c, event, actor, login, null);
    }
    public void recordFile(Connection c, AuditEvent event, User actor, String filename) throws SQLException {
        recordContext(c, event, actor, actor == null ? null : actor.email(), filename);
    }
    private void recordContext(Connection c, AuditEvent event, User actor, String login, String filename) throws SQLException {
        try (var statement = c.prepareStatement("INSERT INTO Registros(timestamp,MID,UID,login_context,file_context) VALUES(?,?,?,?,?)")) {
            statement.setLong(1, System.currentTimeMillis()); statement.setInt(2, event.code());
            if (actor == null) { statement.setNull(3, Types.BIGINT); }
            else { statement.setLong(3, actor.uid()); }
            statement.setString(4, login);
            statement.setString(5, filename);
            statement.executeUpdate();
        }
    }
}
