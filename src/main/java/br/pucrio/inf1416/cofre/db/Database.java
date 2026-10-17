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
    private final boolean readOnly;
    public Database(Path path) throws IOException, SQLException {
        this.path = path.toAbsolutePath().normalize();
        this.readOnly = false;
        Files.createDirectories(this.path.getParent());
        transaction(connection -> { Schema.initialize(connection); return null; });
    }
    private Database(Path path, boolean readOnly) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) { throw new IOException("Banco de dados existente necessário."); }
        this.path = path.toRealPath(); this.readOnly = readOnly;
    }
    /** Viewer: never initializes, migrates, seeds, creates a directory or creates a database. */
    public static Database existingReadOnly(Path path) throws IOException { return new Database(path, true); }
    public Path path() { return path; }
    public Connection open() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setReadOnly(readOnly);
        config.setTransactionMode(readOnly ? SQLiteConfig.TransactionMode.DEFERRED : SQLiteConfig.TransactionMode.IMMEDIATE);
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
