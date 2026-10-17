package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.crypto.ProtectedFileService;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PublicationAuditTest {
    @TempDir Path temp;
    private CatalogService source(VaultTestSupport e){return new CatalogService((folder,name)->new ProtectedFileService().decryptAndVerify(folder.resolve(name+".enc"),folder.resolve(name+".env"),folder.resolve(name+".asd"),e.keys[0],e.publics[0]));}
    @Test void publicationFailureIsNotReportedAsCryptographicFailure()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=new VaultService(e.database,e.login(1),source(e),(target,temporary)->{throw new IOException("injected publication failure");})){
            e.list(vault,1);e.clearEvents();assertThrows(VaultException.class,()->vault.extract("B222",e.output));VaultTestSupport.noOutputs(e.output);
            assertTrue(e.events().contains(7018));assertTrue(e.events().contains(7019));assertFalse(e.events().contains(7020));assertFalse(e.events().contains(7021));
            try(var c=e.database.open();var s=c.createStatement();var r=s.executeQuery("SELECT publication_context FROM Registros WHERE MID=7019")){assertTrue(r.next());assertEquals("failed",r.getString(1));}
            var viewer=new br.pucrio.inf1416.cofre.logview.LogViewService(e.database.path());char[] phrase=e.materials[0].phrase();
            try(var access=viewer.authenticate(e.materials[0].key,phrase)){viewer.read(access,line->{if(Integer.valueOf(7019).equals(line.code())){assertTrue(line.display().contains("publicação=failed"));}});}finally{java.util.Arrays.fill(phrase,'\0');}
        }
    }
    @Test void failureToRecordPublicationOutcomeIsSurfaced()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=new VaultService(e.database,e.login(1),source(e),(target,temporary)->{throw new IOException("injected");})){
            e.list(vault,1);e.database.transaction(c->{try(var s=c.createStatement()){s.execute("CREATE TRIGGER fail_outcome BEFORE UPDATE ON Registros BEGIN SELECT RAISE(ABORT,'injected'); END");}return null;});
            assertThrows(java.sql.SQLException.class,()->vault.extract("B222",e.output));assertTrue(vault.isClosed());VaultTestSupport.noOutputs(e.output);
        }
    }
}
