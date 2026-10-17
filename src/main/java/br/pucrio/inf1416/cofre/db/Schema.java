package br.pucrio.inf1416.cofre.db;

import br.pucrio.inf1416.cofre.audit.AuditEvent;
import java.sql.Connection;
import java.sql.SQLException;

final class Schema {
    private Schema() { }
    static void initialize(Connection c) throws SQLException {
        String[] statements = {
            """
            CREATE TABLE IF NOT EXISTS Grupos (
                GID INTEGER PRIMARY KEY, nome TEXT NOT NULL UNIQUE, rotulo TEXT NOT NULL UNIQUE)
            """,
            """
            CREATE TABLE IF NOT EXISTS Usuarios (
                UID INTEGER PRIMARY KEY, email TEXT NOT NULL UNIQUE, nome TEXT NOT NULL,
                GID INTEGER NOT NULL REFERENCES Grupos(GID), KID INTEGER NOT NULL UNIQUE,
                bootstrap INTEGER NOT NULL DEFAULT 0 CHECK(bootstrap IN (0,1)),
                password_hash TEXT NOT NULL CHECK(length(password_hash)=60 AND substr(password_hash,1,7)='$2y$08$'),
                token_encrypted BLOB NOT NULL CHECK(typeof(token_encrypted)='blob' AND length(token_encrypted)>0),
                password_failures INTEGER NOT NULL DEFAULT 0 CHECK(password_failures>=0),
                token_failures INTEGER NOT NULL DEFAULT 0 CHECK(token_failures>=0),
                blocked_until INTEGER NOT NULL DEFAULT 0 CHECK(blocked_until>=0),
                access_count INTEGER NOT NULL DEFAULT 0 CHECK(access_count>=0),
                consultation_count INTEGER NOT NULL DEFAULT 0 CHECK(consultation_count>=0),
                UNIQUE(UID,KID), CHECK(bootstrap=0 OR GID=1),
                FOREIGN KEY(KID,UID) REFERENCES Chaveiro(KID,UID) DEFERRABLE INITIALLY DEFERRED)
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS only_bootstrap_administrator ON Usuarios(bootstrap) WHERE bootstrap=1
            """,
            """
            CREATE TABLE IF NOT EXISTS Chaveiro (
                KID INTEGER PRIMARY KEY, UID INTEGER NOT NULL UNIQUE,
                private_key_encrypted BLOB NOT NULL CHECK(typeof(private_key_encrypted)='blob' AND length(private_key_encrypted)>0),
                certificate_pem TEXT NOT NULL, UNIQUE(KID,UID),
                FOREIGN KEY(UID,KID) REFERENCES Usuarios(UID,KID) DEFERRABLE INITIALLY DEFERRED)
            """,
            """
            CREATE TABLE IF NOT EXISTS Mensagens (MID INTEGER PRIMARY KEY, texto TEXT NOT NULL)
            """,
            """
            CREATE TABLE IF NOT EXISTS Registros (
                RID INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL,
                MID INTEGER NOT NULL REFERENCES Mensagens(MID), UID INTEGER REFERENCES Usuarios(UID),
                login_context TEXT, file_context TEXT,
                publication_context TEXT CHECK(publication_context IS NULL OR
                    (MID=7019 AND publication_context IN ('pending','published','failed'))))
            """
        };
        for (String sql : statements) { try (var statement = c.createStatement()) { statement.execute(sql); } }
        boolean publication=false;
        try(var s=c.createStatement();var rows=s.executeQuery("PRAGMA table_info(Registros)")){
            while(rows.next()){publication|="publication_context".equals(rows.getString("name"));}
        }
        if(!publication){try(var s=c.createStatement()){
            s.execute("ALTER TABLE Registros ADD COLUMN publication_context TEXT CHECK(publication_context IS NULL OR (MID=7019 AND publication_context IN ('pending','published','failed')))");
        }}
        try (var statement = c.prepareStatement("INSERT INTO Grupos(GID,nome,rotulo) VALUES(?,?,?) ON CONFLICT(GID) DO NOTHING")) {
            for (Group group : Group.values()) {
                statement.setInt(1, group.id()); statement.setString(2, group.catalogLabel());
                statement.setString(3, group.displayName()); statement.executeUpdate();
            }
        }
        try (var statement = c.prepareStatement("INSERT INTO Mensagens(MID,texto) VALUES(?,?) ON CONFLICT(MID) DO NOTHING")) {
            for (AuditEvent event : AuditEvent.values()) {
                statement.setInt(1, event.code()); statement.setString(2, event.template()); statement.executeUpdate();
            }
        }
        // Do not silently overwrite a damaged mapping/catalog on subsequent startup.
        try (var statement = c.prepareStatement("SELECT nome,rotulo FROM Grupos WHERE GID=?")) {
            for (Group group : Group.values()) {
                statement.setInt(1, group.id());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !group.catalogLabel().equals(rows.getString(1)) || !group.displayName().equals(rows.getString(2))) {
                        throw new SQLException("Mapeamento de grupos inconsistente.");
                    }
                }
            }
        }
        try (var statement = c.prepareStatement("SELECT texto FROM Mensagens WHERE MID=?")) {
            for (AuditEvent event : AuditEvent.values()) {
                statement.setInt(1, event.code());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !event.template().equals(rows.getString(1))) { throw new SQLException("Catálogo de auditoria inconsistente."); }
                }
            }
        }
    }
}
