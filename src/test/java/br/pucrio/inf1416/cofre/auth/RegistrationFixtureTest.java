/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real professor pairs registered through the same transactional service used by the GUI. */
@Tag("fixtures")
class RegistrationFixtureTest {
    @TempDir Path temp;
    final Path root=Path.of(System.getProperty("project.root"));
    private void integrity() throws Exception {
        for(String line:Files.readAllLines(root.resolve("src/test/resources/fixtures.sha256"))){
            String[] parts=line.split("  ",2);assertEquals(parts[0],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(parts[1])))));
        }
    }
    @BeforeEach void before() throws Exception {integrity();}
    @AfterEach void after() throws Exception {integrity();}
    private RegistrationTestSupport.Material actual(String id) throws Exception {
        Properties p=new Properties();Path config=Path.of(System.getProperty("fixture.secrets",root.resolve(".local/fixture-secrets.properties").toString()));
        if(!Files.isRegularFile(config)){throw new IllegalStateException("Missing ignored fixture credentials.");}
        try(PushbackReader reader=new PushbackReader(Files.newBufferedReader(config,StandardCharsets.UTF_8))){int first=reader.read();if(first!=-1&&first!=0xfeff){reader.unread(first);}p.load(reader);}
        try{
            String value=p.getProperty(id);if(value==null||value.isEmpty()){throw new IllegalStateException("Missing fixture credential for "+id);}
            Path folder=temp.resolve("copies");Files.createDirectories(folder);
            Path cert=folder.resolve(id+".crt"),key=folder.resolve(id+".key");
            Files.copy(root.resolve("Pacote-T3/Keys/"+id+"-x509.crt"),cert);
            Files.copy(root.resolve("Pacote-T3/Keys/"+id+"-pkcs8-aes.key"),key);
            char[] phrase=value.toCharArray();try{return new RegistrationTestSupport.Material(cert,key,phrase);}finally{Arrays.fill(phrase,'\0');}
        }finally{p.clear();}
    }
    @Test void registersAllThreeFixtureIdentitiesAndRecoversPersistedCredentialsAfterRestart() throws Exception {
        Database db=new Database(temp.resolve("runtime/cofre.db"));RegistrationService service=new RegistrationService(db);
        StartupService startup=new StartupService(db);assertEquals(StartupService.State.BOOTSTRAP_REQUIRED,startup.begin());
        try(var admin=actual("admin");var user01=actual("user01");var user02=actual("user02")){
            Map<String,char[]> passwords=new HashMap<>();Map<String,String> enrollment=new HashMap<>();
            try{
                for(var entry:Map.of("admin",admin,"user01",user01,"user02",user02).entrySet()){passwords.put(entry.getKey(),RegistrationTestSupport.password());}
                try(var request=admin.request(Group.ADMINISTRATOR,passwords.get("admin"));var pending=service.prepare(null,true,request)){
                    service.confirmCertificate(pending);enrollment.put("admin",pending.enrollmentSecret());
                    try(var result=service.commit(pending)){startup.acceptBootstrap(result);}
                }
                try(var authorization=new RegistrationAuthorization(db,1)){
                    for(var entry:List.of(Map.entry("user01",user01),Map.entry("user02",user02))){
                        try(var request=entry.getValue().request(Group.USER,passwords.get(entry.getKey()));var pending=service.prepare(authorization,false,request)){
                            service.confirmCertificate(pending);enrollment.put(entry.getKey(),pending.enrollmentSecret());try(var result=service.commit(pending)){assertEquals(Group.USER,result.user().group());}
                        }
                    }
                }
                startup.shutdown();Database reopened=new Database(db.path());
                try(var c=reopened.open();var s=c.prepareStatement("SELECT * FROM Usuarios ORDER BY UID");var rows=s.executeQuery()){
                    int count=0;
                    while(rows.next()){
                        String id=rows.getString("email").split("@")[0];char[] password=passwords.get(id);
                        assertTrue(new PasswordHasher().verify(rows.getString("password_hash"),password));
                        assertTrue(enrollment.get(id).equals(new TotpSecretService().decrypt(rows.getBytes("token_encrypted"),password)),"Persisted TOTP mismatch");
                        var record=new KeyRepository().find(c,rows.getLong("KID"));assertEquals(rows.getLong("UID"),record.uid());
                        assertTrue(Arrays.equals(record.encrypted(),Files.readAllBytes(temp.resolve("copies/"+id+".key"))));count++;
                    }
                    assertEquals(3,count);
                }
                var second=new StartupService(reopened);assertEquals(StartupService.State.UNLOCK_REQUIRED,second.begin());
                char[] phrase=admin.phrase();try{second.unlock(phrase);}finally{Arrays.fill(phrase,'\0');}
                assertEquals(StartupService.State.READY,second.state());second.shutdown();
                try(var c=reopened.open();var s=c.prepareStatement("SELECT count(*) FROM Registros WHERE MID=6008");var rows=s.executeQuery()){rows.next();assertEquals(3,rows.getInt(1));}
            }finally{passwords.values().forEach(p->Arrays.fill(p,'\0'));enrollment.clear();}
        }
    }
}
