/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static br.pucrio.inf1416.cofre.vault.VaultException.Reason.*;

class VaultServiceTest {
    @TempDir Path temp;
    @Test void ownerSharedGroupUnionCountersAndAuditContext()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            try(var admin=e.vault(0)){
                e.clearEvents();assertEquals(List.of("A111"),e.list(admin,0).files().stream().map(Catalog.Entry::code).toList());
                assertEquals(List.of(7003,7005,7006,7010,7011,7009,7014),e.events());
                assertEquals(2,e.list(admin,0).user().consultationCount());
                Path output=admin.extract("A111",e.output);assertArrayEquals("verified A".getBytes(StandardCharsets.US_ASCII),Files.readAllBytes(output));
                assertEquals(PERMISSION,assertThrows(VaultException.class,()->admin.extract("B222",e.output)).reason());
                admin.back();assertTrue(admin.isClosed());assertTrue(e.startup.hasAdministratorMaterial());
            }
            try(var one=e.vault(1)){
                assertEquals(Set.of("B222","C333"),new HashSet<>(e.list(one,1).files().stream().map(Catalog.Entry::code).toList()));
                Path shared=one.extract("C333",e.output);assertArrayEquals("verified C".getBytes(StandardCharsets.US_ASCII),Files.readAllBytes(shared));
                try(var c=e.database.open();var s=c.prepareStatement("SELECT login_context,file_context,UID FROM Registros WHERE MID=7019 ORDER BY RID DESC LIMIT 1");var r=s.executeQuery()){
                    assertTrue(r.next());assertEquals(e.email(1),r.getString(1));assertEquals("gamma.txt",r.getString(2));assertEquals(e.identities[1].user.uid(),r.getLong(3));
                }
            }
            try(var two=e.vault(2)){
                assertEquals(2,e.list(two,2).files().size());assertEquals(GROUP_ROUTE,assertThrows(VaultException.class,()->two.extract("B222",e.output)).reason());
            }
        }
    }
    @Test void wrongPhraseMismatchAndBadPathClearPriorListing()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.list(vault,1);assertEquals(KEY_RESTORE,assertThrows(VaultException.class,()->vault.list(e.folder.toString(),UUID.randomUUID().toString().toCharArray())).reason());
            assertEquals(STATE,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());
            char[] phrase=e.materials[1].phrase();try{
                assertEquals(INVALID_PATH,assertThrows(VaultException.class,()->vault.list("x".repeat(256),phrase)).reason());assertTrue(e.events().contains(7004));
                e.database.transaction(c->{try(var s=c.prepareStatement("UPDATE Chaveiro SET certificate_pem=? WHERE KID=?")){
                    s.setString(1,new CertificateService().toPem(new CertificateService().read(e.materials[2].certificate)));s.setLong(2,e.identities[1].user.kid());s.executeUpdate();
                }catch(java.security.GeneralSecurityException|java.io.IOException ex){throw new SQLException(ex);}return null;});
                assertEquals(KEY_MISMATCH,assertThrows(VaultException.class,()->vault.list(e.folder.toString(),phrase)).reason());
            }finally{Arrays.fill(phrase,'\0');}
            VaultTestSupport.noOutputs(e.output);
        }
    }
    @ParameterizedTest @ValueSource(strings={"index.enc","index.env","index.asd","share.enc","share.env","share.asd"})
    void invalidCatalogsNeverExposeListingOrIncrementCount(String file)throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            if(file.endsWith(".enc")){Files.write(e.folder.resolve(file),new byte[3]);}else{VaultTestSupport.corrupt(e.folder.resolve(file));}
            var failure=assertThrows(VaultException.class,()->e.list(vault,1));
            assertEquals(file.endsWith(".asd")?SIGNATURE:DECRYPTION,failure.reason());
            assertFalse(e.events().contains(7009));assertFalse(e.events().contains(7014));
            try(var c=e.database.open()){assertEquals(0,new UserRepository().find(c,e.identities[1].user.uid()).orElseThrow().consultationCount());}
            assertEquals(STATE,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());VaultTestSupport.noOutputs(e.output);
        }
    }
    @ParameterizedTest @ValueSource(strings={"B222.enc","B222.env","B222.asd","C333.1.shr"})
    void damagedPayloadRoutesPublishNothing(String file)throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.list(vault,1);if(file.endsWith(".enc")){Files.write(e.folder.resolve(file),new byte[3]);}else{VaultTestSupport.corrupt(e.folder.resolve(file));}
            var failure=assertThrows(VaultException.class,()->vault.extract(file.startsWith("B")?"B222":"C333",e.output));
            assertEquals(file.endsWith(".asd")?SIGNATURE:DECRYPTION,failure.reason());VaultTestSupport.noOutputs(e.output);
            assertTrue(e.events().contains(file.endsWith(".asd")?7021:7020));
        }
    }
    @Test void missingEnvelopeOwnerKeyVerificationAndNoOverwrite()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.list(vault,1);Files.delete(e.folder.resolve("B222.env"));assertEquals(MISSING_FILE,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());
            // A recipient's signature must never validate a shared owner's content.
            Files.write(e.folder.resolve("C333.asd"),new SignatureService().sign("verified C".getBytes(StandardCharsets.US_ASCII),e.keys[1]));
            assertEquals(SIGNATURE,assertThrows(VaultException.class,()->vault.extract("C333",e.output)).reason());VaultTestSupport.noOutputs(e.output);
            e.writeSynthetic();Files.writeString(e.output.resolve("beta.txt"),"keep");
            assertEquals(EXISTS,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());assertEquals("keep",Files.readString(e.output.resolve("beta.txt")));
            assertEquals(INVALID_PATH,assertThrows(VaultException.class,()->vault.extract("B222",e.folder)).reason());
            assertEquals(INVALID_PATH,assertThrows(VaultException.class,()->vault.extract("B222",Path.of(System.getProperty("project.root"),"Pacote-T3"))).reason());
        }
    }
    @Test void rechecksChangedMetadataShareRevocationAndUnknownOwners()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.list(vault,1);e.protect("share",new byte[0],0,null);
            assertEquals(GROUP_ROUTE,assertThrows(VaultException.class,()->vault.extract("C333",e.output)).reason());
            String changed="B222 renamed.txt "+e.email(1)+" usuario\n";e.protect("index",changed.getBytes(StandardCharsets.US_ASCII),0,null);
            assertEquals(PERMISSION,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());
            e.protect("index","ABC a.txt unknown@example.test usuario\n".getBytes(StandardCharsets.US_ASCII),0,null);
            assertEquals(UNKNOWN_OWNER,assertThrows(VaultException.class,()->e.list(vault,1)).reason());VaultTestSupport.noOutputs(e.output);
        }
    }
    @Test void auditFailuresRollbackCounterAndPreventPublication()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.database.transaction(c->{try(var s=c.createStatement()){s.execute("CREATE TRIGGER fail_list BEFORE INSERT ON Registros WHEN NEW.MID=7014 BEGIN SELECT RAISE(ABORT,'injected'); END");}return null;});
            assertThrows(SQLException.class,()->e.list(vault,1));assertTrue(vault.isClosed());
            try(var c=e.database.open()){assertEquals(0,new UserRepository().find(c,e.identities[1].user.uid()).orElseThrow().consultationCount());}
            e.database.transaction(c->{try(var s=c.createStatement()){s.execute("DROP TRIGGER fail_list");}return null;});
            try(var second=e.startup.openVault(e.auth.session())){
                e.list(second,1);e.database.transaction(c->{try(var s=c.createStatement()){s.execute("CREATE TRIGGER fail_extract BEFORE INSERT ON Registros WHEN NEW.MID=7019 BEGIN SELECT RAISE(ABORT,'injected'); END");}return null;});
                assertThrows(SQLException.class,()->second.extract("B222",e.output));assertTrue(second.isClosed());VaultTestSupport.noOutputs(e.output);
            }
        }
    }
    @Test void liveSessionRequiredForeignDatabaseRejectedAndLogoutClearsKeys()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            e.list(vault,1);var session=e.auth.session();
            assertThrows(SecurityException.class,()->session.requireActive(new Database(temp.resolve("other.db"))));
            assertThrows(VaultException.class,()->e.startup.openVault(null));e.auth.logout(session);
            assertTrue(vault.isClosed());assertEquals(STATE,assertThrows(VaultException.class,()->vault.extract("B222",e.output)).reason());
            assertThrows(SecurityException.class,()->e.startup.openVault(session));assertTrue(e.startup.hasAdministratorMaterial());VaultTestSupport.noOutputs(e.output);
        }
    }
}
