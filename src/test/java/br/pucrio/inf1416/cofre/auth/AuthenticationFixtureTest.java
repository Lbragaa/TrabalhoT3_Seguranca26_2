package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.db.*;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real professor identities; additional-user registrations use the actual post-MFA capability. */
@Tag("fixtures")
class AuthenticationFixtureTest {
    @TempDir Path temp;
    private final Path root=Path.of(System.getProperty("project.root"));
    private RegistrationTestSupport.Material material(String id) throws Exception {
        Path config=Path.of(System.getProperty("fixture.secrets",root.resolve(".local/fixture-secrets.properties").toString()));
        if(!Files.isRegularFile(config)){throw new IllegalStateException("Missing ignored fixture credentials.");}
        Properties properties=new Properties();
        try(var reader=new PushbackReader(Files.newBufferedReader(config,StandardCharsets.UTF_8))){int first=reader.read();if(first!=-1&&first!=0xfeff){reader.unread(first);}properties.load(reader);}
        try{
            String value=properties.getProperty(id);if(value==null||value.isEmpty()){throw new IllegalStateException("Missing fixture credential for "+id);}
            Path cert=temp.resolve(id+".crt"),key=temp.resolve(id+".key");
            Files.copy(root.resolve("Pacote-T3/Keys/"+id+"-x509.crt"),cert);Files.copy(root.resolve("Pacote-T3/Keys/"+id+"-pkcs8-aes.key"),key);
            char[] phrase=value.toCharArray();try{return new RegistrationTestSupport.Material(cert,key,phrase);}finally{Arrays.fill(phrase,'\0');}
        }finally{properties.clear();}
    }
    @Test void actualAdministratorMfaAuthorizesBothUsersAndEachCanAuthenticate() throws Exception {
        Database db=new Database(temp.resolve("cofre.db"));var registrations=new RegistrationService(db);var clock=new AuthenticationTestSupport.MutableClock();
        try(var administrator=material("admin");var one=material("user01");var two=material("user02");
            var admin=AuthenticationTestSupport.register(registrations,null,true,administrator,Group.ADMINISTRATOR,8)){
            var startup=new StartupService(db);assertEquals(StartupService.State.UNLOCK_REQUIRED,startup.begin());
            char[] phrase=administrator.phrase();try{startup.unlock(phrase);}finally{Arrays.fill(phrase,'\0');}
            try(var auth=new AuthenticationService(db,clock)){
                var session=AuthenticationTestSupport.login(auth,admin,clock);var authority=auth.registrationAuthorization(session);
                try(var user01=AuthenticationTestSupport.register(registrations,authority,false,one,Group.USER,9);
                    var user02=AuthenticationTestSupport.register(registrations,authority,false,two,Group.USER,10)){
                    auth.logout(session);assertThrows(RegistrationException.class,()->registrations.showForm(authority,false));
                    for(var user:List.of(user01,user02)){
                        var ordinary=AuthenticationTestSupport.login(auth,user,clock);assertEquals(Group.USER,ordinary.user().group());
                        assertThrows(AuthenticationException.class,()->auth.registrationAuthorization(ordinary));auth.logout(ordinary);
                    }
                }
            }finally{startup.shutdown();}
            Database reopened=new Database(db.path());try(var c=reopened.open();var s=c.prepareStatement("SELECT email,access_count FROM Usuarios ORDER BY UID");var r=s.executeQuery()){
                Set<String> emails=new HashSet<>();while(r.next()){emails.add(r.getString(1));assertEquals(1,r.getLong(2));}
                assertEquals(Set.of("admin@inf1416.puc-rio.br","user01@inf1416.puc-rio.br","user02@inf1416.puc-rio.br"),emails);
            }
        }
    }
}
