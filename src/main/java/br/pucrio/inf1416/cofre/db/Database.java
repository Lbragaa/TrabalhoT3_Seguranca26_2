package br.pucrio.inf1416.cofre.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;

/** File-backed connections. No connection or secret is kept globally. */
public final class Database {
    private final Path path;
    public Database(Path path) throws IOException, SQLException {
        this.path = path.toAbsolutePath().normalize();
        Files.createDirectories(this.path.getParent());
        transaction(connection -> { Schema.initialize(connection); return null; });
    }
    public Path path() { return path; }
    public Connection open() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection("jdbc:sqlite:" + path, config.toProperties());
    }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    public <T> T transaction(Work<T> work) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
