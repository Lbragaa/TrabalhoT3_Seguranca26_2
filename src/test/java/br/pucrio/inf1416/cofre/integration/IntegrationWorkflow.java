/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.integration;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.auth.*;
import br.pucrio.inf1416.cofre.crypto.ProtectedFileService;
import br.pucrio.inf1416.cofre.db.*;
import br.pucrio.inf1416.cofre.logview.LogViewService;
import br.pucrio.inf1416.cofre.vault.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class IntegrationWorkflow {
    static void run(Path temp,boolean real)throws Exception{
        try(var e=new VaultTestSupport(temp,real,true)){
            String[] codes=real?new String[]{"XXYYZZ00","XXYYZZ11","XXYYZZ22"}:new String[]{"A111","B222","C333"};
            for(int user=0;user<3;user++){
                var logged=e.login(user);e.auth.mainMenu(logged);e.auth.menu(logged,AuthenticationService.Menu.CONSULT);
                try(var vault=e.startup.openVault(logged)){
                    if(user==1){
                        assertThrows(AuthenticationException.class,()->e.auth.registrationAuthorization(e.auth.session()));
                        char[] phrase=e.materials[user].phrase();try{assertThrows(VaultException.class,()->vault.list(temp.resolve("missing").toString(),phrase));}finally{Arrays.fill(phrase,'\0');}
                        assertThrows(VaultException.class,()->vault.list(e.folder.toString(),UUID.randomUUID().toString().toCharArray()));
                    }
                    var listing=e.list(vault,user);assertEquals(1,listing.user().consultationCount());
                    Path output=Files.createDirectories(e.output.resolve("owner-"+user));String code=codes[user];
                    byte[] expected=new ProtectedFileService().decryptAndVerify(e.folder.resolve(code+".enc"),e.folder.resolve(code+".env"),e.folder.resolve(code+".asd"),e.keys[user],e.publics[user]);
                    byte[] actual=null;
                    try{actual=Files.readAllBytes(vault.extract(code,output));assertTrue(Arrays.equals(expected,actual),"Owner bytes differ");}finally{Arrays.fill(expected,(byte)0);if(actual!=null){Arrays.fill(actual,(byte)0);}}
                    if(user==1){
                        expected=new ProtectedFileService().decryptAndVerify(e.folder.resolve(codes[2]+".enc"),e.folder.resolve(codes[2]+".env"),e.folder.resolve(codes[2]+".asd"),e.keys[2],e.publics[2]);
                        try{actual=Files.readAllBytes(vault.extract(codes[2],Files.createDirectories(e.output.resolve("shared"))));assertTrue(Arrays.equals(expected,actual),"Shared bytes differ");}finally{Arrays.fill(expected,(byte)0);if(actual!=null){Arrays.fill(actual,(byte)0);}}
                        assertEquals(VaultException.Reason.PERMISSION,assertThrows(VaultException.class,()->vault.extract(codes[0],output)).reason());
                    }
                    if(user==2){assertTrue(listing.files().stream().anyMatch(x->x.code().equals(codes[1])));assertEquals(VaultException.Reason.GROUP_ROUTE,assertThrows(VaultException.class,()->vault.extract(codes[1],output)).reason());}
                    vault.back();assertTrue(vault.isClosed());assertTrue(e.startup.hasAdministratorMaterial());
                }
            }
            e.auth.logout(e.auth.session());e.auth.identify(e.email(1));assertEquals(AuthenticationService.PasswordResult.ACCEPTED,e.auth.password(e.identities[1].pairs()));
            assertThrows(AuthenticationException.class,e.auth::session);
            assertEquals(AuthenticationService.TokenResult.REJECTED,e.auth.token("bad"));assertEquals(AuthenticationService.TokenResult.REJECTED,e.auth.token(""));assertEquals(AuthenticationService.TokenResult.BLOCKED,e.auth.token("12345"));
            e.auth.close();e.startup.shutdown();assertFalse(e.startup.hasAdministratorMaterial());
            Database reopened=new Database(e.database.path());var startup=new StartupService(reopened);assertEquals(StartupService.State.UNLOCK_REQUIRED,startup.begin());
            char[] adminPhrase=e.materials[0].phrase();try{startup.unlock(adminPhrase);}finally{Arrays.fill(adminPhrase,'\0');}
            try(var auth=new AuthenticationService(reopened,e.clock)){
                auth.start();assertEquals(AuthenticationService.Identification.BLOCKED,auth.identify(e.email(1)));assertEquals(AuthenticationService.Identification.KNOWN,auth.identify(e.email(2)));
                assertEquals(AuthenticationService.PasswordResult.REJECTED,auth.password(e.identities[2].wrongPairs()));assertEquals(AuthenticationService.PasswordResult.REJECTED,auth.password(e.identities[2].wrongPairs()));assertEquals(AuthenticationService.PasswordResult.BLOCKED,auth.password(e.identities[2].wrongPairs()));
                e.clock.advance(120_000);var session=AuthenticationTestSupport.login(auth,e.identities[1],e.clock);auth.mainMenu(session);
                assertEquals(2,session.user().accessCount());assertEquals(1,session.user().consultationCount());auth.logout(session);assertFalse(session.active());
            }finally{startup.shutdown();}
            var viewer=new LogViewService(reopened.path());char[] phrase=e.materials[0].phrase();List<LogViewService.Line> lines=new ArrayList<>();
            try(var access=viewer.authenticate(e.materials[0].key,phrase)){viewer.read(access,lines::add);}finally{Arrays.fill(phrase,'\0');}
            assertFalse(lines.isEmpty());assertTrue(lines.stream().allMatch(l->l.code()!=null));
            Set<Integer> events=new HashSet<>();lines.forEach(l->events.add(l.code()));
            assertTrue(events.containsAll(Set.of(1001,1002,1003,1004,1005,1006,2001,2004,3007,4007,5001,5002,5003,6001,6006,6008,7001,7002,7003,7004,7005,7006,7009,7010,7011,7014,7015,7016,7017,7018,7019)));
            assertEquals(4,lines.stream().filter(l->"published".equals(l.publication())).count());
            try(var c=reopened.open();var s=c.createStatement();var r=s.executeQuery("SELECT RID FROM Registros ORDER BY timestamp,RID")){for(var line:lines){assertTrue(r.next());assertEquals(r.getLong(1),line.rid());}assertFalse(r.next());}
            char[] ordinary=e.materials[1].phrase();try{assertThrows(java.security.GeneralSecurityException.class,()->viewer.authenticate(e.materials[1].key,ordinary));}finally{Arrays.fill(ordinary,'\0');}
        }
    }
}
