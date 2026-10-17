package br.pucrio.inf1416.cofre.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class UserRepository {
    /** Identity and counters only. No passphrase, hash or token secret in this value's toString. */
    public record User(long uid, String email, String name, Group group, long kid, boolean bootstrap,
                       int passwordFailures, int tokenFailures, long blockedUntil, long accessCount, long consultationCount) { }
    public long count(Connection c) throws SQLException {
        try (var statement = c.prepareStatement("SELECT count(*) FROM Usuarios"); var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
    }
    public Optional<User> find(Connection c, long uid) throws SQLException { return query(c, "SELECT * FROM Usuarios WHERE UID=?", uid); }
    public Optional<User> findByEmail(Connection c, String email) throws SQLException { return query(c, "SELECT * FROM Usuarios WHERE email=?", email); }
    public Optional<User> bootstrap(Connection c) throws SQLException { return query(c, "SELECT * FROM Usuarios WHERE bootstrap=?", 1); }
    private Optional<User> query(Connection c, String sql, Object value) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
        }
    }
    private User map(ResultSet r) throws SQLException {
        return new User(r.getLong("UID"), r.getString("email"), r.getString("nome"), Group.fromId(r.getInt("GID")),
                r.getLong("KID"), r.getBoolean("bootstrap"), r.getInt("password_failures"), r.getInt("token_failures"),
                r.getLong("blocked_until"), r.getLong("access_count"), r.getLong("consultation_count"));
    }
    public long nextUid(Connection c) throws SQLException {
        try (var statement = c.prepareStatement("SELECT COALESCE(MAX(UID),0)+1 FROM Usuarios"); var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
    }
    public void insert(Connection c, long uid, long kid, String email, String name, Group group,
                       boolean bootstrap, String hash, byte[] token) throws SQLException {
        try (var statement = c.prepareStatement("INSERT INTO Usuarios(UID,KID,email,nome,GID,bootstrap,password_hash,token_encrypted) VALUES(?,?,?,?,?,?,?,?)")) {
            statement.setLong(1, uid); statement.setLong(2, kid); statement.setString(3, email); statement.setString(4, name);
            statement.setInt(5, group.id()); statement.setBoolean(6, bootstrap); statement.setString(7, hash); statement.setBytes(8, token);
            statement.executeUpdate();
        }
    }
}
