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
        try (var statement = c.prepareStatement("INSERT INTO Registros(timestamp,MID,UID,login_context) VALUES(?,?,?,?)")) {
            statement.setLong(1, System.currentTimeMillis()); statement.setInt(2, event.code());
            if (actor == null) { statement.setNull(3, Types.BIGINT); statement.setNull(4, Types.VARCHAR); }
            else { statement.setLong(3, actor.uid()); statement.setString(4, actor.email()); }
            statement.executeUpdate();
        }
    }
}
