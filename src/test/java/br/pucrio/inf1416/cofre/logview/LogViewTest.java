/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.logview;

import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.vault.VaultTestSupport;
import java.io.*;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LogViewTest {
    @TempDir Path temp;
    @Test void administratorOnlyClosedAndForeignAccessCannotReadRecords()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            var viewer=new LogViewService(e.database.path());
            assertThrows(SecurityException.class,()->viewer.read(null,line->fail("Unauthorized record")));
            for(int i=1;i<3;i++){int user=i;char[] phrase=e.materials[i].phrase();try{assertThrows(GeneralSecurityException.class,()->viewer.authenticate(e.materials[user].key,phrase));}finally{Arrays.fill(phrase,'\0');}}
            char[] phrase=e.materials[0].phrase();try(var access=viewer.authenticate(e.materials[0].key,phrase)){
                List<LogViewService.Line> lines=new ArrayList<>();viewer.read(access,lines::add);assertFalse(lines.isEmpty());assertEquals(e.email(0),access.email());
                var other=new LogViewService(e.database.path());assertThrows(SecurityException.class,()->other.read(access,line->fail()));
                access.close();assertThrows(SecurityException.class,()->viewer.read(access,line->fail()));
            }finally{Arrays.fill(phrase,'\0');}
        }
    }
    @Test void ordinaryKeysWrongPhrasesAndMismatchedCertificatesFailBeforeOutput()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            var viewer=new LogViewService(e.database.path());
            assertThrows(GeneralSecurityException.class,()->viewer.authenticate(e.materials[0].key,UUID.randomUUID().toString().toCharArray()));
            assertThrows(GeneralSecurityException.class,()->viewer.authenticate(temp.resolve("absent.key"),new char[]{'x'}));
            Files.write(temp.resolve("bad.key"),new byte[7]);assertThrows(GeneralSecurityException.class,()->viewer.authenticate(temp.resolve("bad.key"),new char[]{'x'}));
            e.database.transaction(c->{try(var s=c.prepareStatement("UPDATE Chaveiro SET certificate_pem=(SELECT certificate_pem FROM Chaveiro WHERE KID=?) WHERE KID=?")){s.setLong(1,e.identities[1].user.kid());s.setLong(2,e.identities[0].user.kid());s.executeUpdate();}return null;});
            char[] phrase=e.materials[0].phrase();try{assertThrows(GeneralSecurityException.class,()->viewer.authenticate(e.materials[0].key,phrase));}finally{Arrays.fill(phrase,'\0');}
        }
    }
    @Test void roleAndCertificateAreRecheckedBeforeEveryAuditQuery()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            var viewer=new LogViewService(e.database.path());char[] phrase=e.materials[0].phrase();
            try(var access=viewer.authenticate(e.materials[0].key,phrase)){
                e.database.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE Usuarios SET bootstrap=0,GID=2 WHERE UID="+e.identities[0].user.uid());}return null;});
                assertThrows(SecurityException.class,()->viewer.read(access,line->fail("Revoked administrator read a record")));
            }finally{Arrays.fill(phrase,'\0');}
        }
    }
    @Test void orderedRowsResolveOnlyStoredContextAndMalformedRowsAreSafe()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            e.database.transaction(c->{try(var s=c.createStatement()){
                s.executeUpdate("DELETE FROM Registros");
                s.executeUpdate("INSERT INTO Registros(RID,timestamp,MID,login_context,file_context) VALUES(3,2000,7015,'user@example.test','report.txt'),(2,1000,2005,NULL,NULL),(1,1000,1001,NULL,NULL)");
                s.executeUpdate("INSERT INTO Registros(RID,timestamp,MID) VALUES(4,'bad',1001)");
                s.executeUpdate("INSERT INTO Mensagens(MID,texto) VALUES(9999,'untrusted')");s.executeUpdate("INSERT INTO Registros(RID,timestamp,MID) VALUES(5,3000,9999)");
            }return null;});
            var service=new LogViewService(e.database.path());char[] phrase=e.materials[0].phrase();
            try(var access=service.authenticate(e.materials[0].key,phrase)){
                List<LogViewService.Line> lines=new ArrayList<>();service.read(access,lines::add);
                assertEquals(List.of(1L,2L,3L,5L,4L),lines.stream().map(LogViewService.Line::rid).toList());
                assertTrue(lines.get(1).message().contains("<login_name>"));assertTrue(lines.get(2).message().contains("report.txt"));assertTrue(lines.get(2).message().contains("user@example.test"));
                assertNull(lines.get(3).code());assertFalse(lines.get(3).message().contains("untrusted"));assertNull(lines.get(4).code());
            }finally{Arrays.fill(phrase,'\0');}
            assertEquals("x <arq_name> f",LogViewService.resolve("x <login_name> <arq_name>","<arq_name>","f"));
            assertEquals("x\\u001b\\u000ay",LogViewService.resolve("<login_name>","x\u001b\ny",null));
        }
    }
    @Test void viewerUsesReadOnlyExistingDatabaseAndDoesNotSeedMissingFiles()throws Exception{
        Path missing=temp.resolve("missing/cofre.db");assertThrows(IOException.class,()->new LogViewService(missing));assertFalse(Files.exists(missing.getParent()));
        var db=new Database(temp.resolve("cofre.db"));byte[] before=Files.readAllBytes(db.path());
        var read=Database.existingReadOnly(db.path());try(var c=read.open();var s=c.createStatement()){assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM Mensagens"));}
        assertArrayEquals(before,Files.readAllBytes(db.path()));
    }
    @Test void cliFailsClosedWithoutConsoleAndRejectsArgumentsWithoutReadingSecrets()throws Exception{
        var out=new StringWriter();var err=new StringWriter();
        assertEquals(2,LogViewMain.run(new String[]{"key","--database",temp.resolve("missing.db").toString()},null,new PrintWriter(out,true),new PrintWriter(err,true)));assertEquals("",out.toString());assertFalse(Files.exists(temp.resolve("missing.db")));
        assertEquals(2,LogViewMain.run(new String[]{"key","--password","forbidden"},()->{fail("Invalid args read input");return null;},new PrintWriter(out,true),new PrintWriter(err,true)));
        assertEquals(0,LogViewMain.run(new String[]{"--check"},null,new PrintWriter(out,true),new PrintWriter(err,true)));
    }
    @Test void cliAuthenticatedEmptyLogsAndNegativeAttemptsClearInput()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            e.database.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("DELETE FROM Registros");}return null;});
            String[] args={e.materials[0].key.toString(),"--database",e.database.path().toString()};
            var out=new StringWriter();var err=new StringWriter();char[] phrase=e.materials[0].phrase();
            assertEquals(0,LogViewMain.run(args,()->phrase,new PrintWriter(out,true),new PrintWriter(err,true)));assertTrue(out.toString().contains("Nenhum registro"));assertArrayEquals(new char[phrase.length],phrase);
            out.getBuffer().setLength(0);char[] wrong=UUID.randomUUID().toString().toCharArray();
            assertEquals(1,LogViewMain.run(args,()->wrong,new PrintWriter(out,true),new PrintWriter(err,true)));assertEquals("",out.toString());assertArrayEquals(new char[wrong.length],wrong);
            assertEquals(2,LogViewMain.run(args,()->{throw new IOError(new IOException("injected console failure"));},new PrintWriter(out,true),new PrintWriter(err,true)));assertEquals("",out.toString());
            assertEquals(1,LogViewMain.run(new String[]{temp.resolve("absent.key").toString()},()->{fail("Missing key asked for secret");return null;},new PrintWriter(out,true),new PrintWriter(err,true)));
        }
    }
    @Test void legacyDatabaseIsReadableWithoutMigrationAndVaultMigrationPreservesLogs()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            e.database.transaction(c->{try(var s=c.createStatement()){s.execute("ALTER TABLE Registros DROP COLUMN publication_context");}return null;});
            byte[] before=Files.readAllBytes(e.database.path());var viewer=new LogViewService(e.database.path());char[] phrase=e.materials[0].phrase();
            try(var access=viewer.authenticate(e.materials[0].key,phrase)){viewer.read(access,line->assertNull(line.publication()));}finally{Arrays.fill(phrase,'\0');}
            assertArrayEquals(before,Files.readAllBytes(e.database.path()));
            new Database(e.database.path());
            try(var c=e.database.open();var s=c.createStatement();var r=s.executeQuery("SELECT publication_context FROM Registros")){assertTrue(r.next());assertNull(r.getString(1));}
        }
    }
}
