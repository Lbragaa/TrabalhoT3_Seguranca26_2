/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.db;

import java.sql.Connection;
import java.sql.SQLException;

public final class KeyRepository {
    public record KeyRecord(long kid, long uid, byte[] encrypted, String certificatePem) {
        public KeyRecord { encrypted = encrypted.clone(); }
        @Override public byte[] encrypted() { return encrypted.clone(); }
        @Override public String toString() { return "KeyRecord[kid=" + kid + ",uid=" + uid + "]"; }
    }
    public long nextKid(Connection c) throws SQLException {
        try (var statement = c.prepareStatement("SELECT COALESCE(MAX(KID),0)+1 FROM Chaveiro"); var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
    }
    public KeyRecord find(Connection c, long kid) throws SQLException {
        try (var statement = c.prepareStatement("SELECT * FROM Chaveiro WHERE KID=?")) {
            statement.setLong(1, kid);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) { throw new SQLException("Chaveiro não encontrado."); }
                return new KeyRecord(kid, rows.getLong("UID"), rows.getBytes("private_key_encrypted"), rows.getString("certificate_pem"));
            }
        }
    }
    public void insert(Connection c, long kid, long uid, byte[] encrypted, String pem) throws SQLException {
        try (var statement = c.prepareStatement("INSERT INTO Chaveiro(KID,UID,private_key_encrypted,certificate_pem) VALUES(?,?,?,?)")) {
            statement.setLong(1, kid); statement.setLong(2, uid); statement.setBytes(3, encrypted); statement.setString(4, pem); statement.executeUpdate();
        }
    }
}
