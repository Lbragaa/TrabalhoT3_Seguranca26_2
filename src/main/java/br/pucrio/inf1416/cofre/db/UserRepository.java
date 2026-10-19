/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
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

    public static final class Credentials implements AutoCloseable {
        private final String hash;
        private final byte[] token;
        private Credentials(String hash, byte[] token) { this.hash = hash; this.token = token; }
        public String hash() { return hash; }
        public byte[] encryptedToken() { return token.clone(); }
        @Override public void close() { java.util.Arrays.fill(token, (byte) 0); }
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    public Credentials credentials(Connection c, long uid) throws SQLException {
        try (var s = c.prepareStatement("SELECT password_hash,token_encrypted FROM Usuarios WHERE UID=?")) {
            s.setLong(1, uid);
            try (var r = s.executeQuery()) {
                if (!r.next()) { throw new SQLException("Credenciais não encontradas."); }
                return new Credentials(r.getString(1), r.getBytes(2));
            }
        }
    }
    public User refreshLockout(Connection c, long uid, long now) throws SQLException {
        try (var s = c.prepareStatement("UPDATE Usuarios SET password_failures=0,token_failures=0,blocked_until=0 WHERE UID=? AND blocked_until>0 AND blocked_until<=?")) {
            s.setLong(1, uid); s.setLong(2, now); s.executeUpdate();
        }
        return find(c, uid).orElseThrow(() -> new SQLException("Usuário não encontrado."));
    }
    public void failures(Connection c, long uid, int password, int token, long blockedUntil) throws SQLException {
        try (var s = c.prepareStatement("UPDATE Usuarios SET password_failures=?,token_failures=?,blocked_until=? WHERE UID=?")) {
            s.setInt(1, password); s.setInt(2, token); s.setLong(3, blockedUntil); s.setLong(4, uid); s.executeUpdate();
        }
    }
    public void passwordAccepted(Connection c, long uid) throws SQLException {
        try (var s = c.prepareStatement("UPDATE Usuarios SET password_failures=0 WHERE UID=?")) { s.setLong(1, uid); s.executeUpdate(); }
    }
    public User authenticationAccepted(Connection c, long uid) throws SQLException {
        try (var s = c.prepareStatement("UPDATE Usuarios SET token_failures=0,access_count=access_count+1 WHERE UID=?")) { s.setLong(1, uid); s.executeUpdate(); }
        return find(c, uid).orElseThrow();
    }
    public User consultationAccepted(Connection c, long uid) throws SQLException {
        try (var s = c.prepareStatement("UPDATE Usuarios SET consultation_count=consultation_count+1 WHERE UID=?")) { s.setLong(1, uid); s.executeUpdate(); }
        return find(c, uid).orElseThrow();
    }
}
