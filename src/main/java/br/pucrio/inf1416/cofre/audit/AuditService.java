/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
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
    /** Cryptographic verification succeeded; publication is a separate structured outcome. */
    public long verifiedPendingPublication(Connection c, User actor, String filename) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO Registros(timestamp,MID,UID,login_context,file_context,publication_context) VALUES(?,7019,?,?,?,'pending') RETURNING RID")){
            s.setLong(1,System.currentTimeMillis());s.setLong(2,actor.uid());s.setString(3,actor.email());s.setString(4,filename);
            try(var rows=s.executeQuery()){if(!rows.next()){throw new SQLException("Registro de verificação ausente.");}return rows.getLong(1);}
        }
    }
    public void publication(Connection c,long rid,String status)throws SQLException{
        if(!"published".equals(status) && !"failed".equals(status)){throw new IllegalArgumentException("Estado de publicação inválido.");}
        try(var s=c.prepareStatement("UPDATE Registros SET publication_context=? WHERE RID=? AND MID=7019 AND publication_context='pending'")){
            s.setString(1,status);s.setLong(2,rid);if(s.executeUpdate()!=1){throw new SQLException("Registro de publicação inconsistente.");}
        }
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
