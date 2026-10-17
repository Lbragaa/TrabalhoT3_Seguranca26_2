package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.auth.*;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;

/** Disposable files/identities; never prints phrases, enrollment secrets or plaintext. */
public final class VaultTestSupport implements AutoCloseable {
    public final Database database;
    public final StartupService startup;
    public final AuthenticationService auth;
    public final AuthenticationTestSupport.MutableClock clock = new AuthenticationTestSupport.MutableClock();
    public final RegistrationTestSupport.Material[] materials = new RegistrationTestSupport.Material[3];
    public final AuthenticationTestSupport.Identity[] identities = new AuthenticationTestSupport.Identity[3];
    public final Path folder, output;
    public final PrivateKey[] keys = new PrivateKey[3];
    public final PublicKey[] publics = new PublicKey[3];
    public VaultTestSupport(Path temp, boolean real) throws Exception {
        folder = Files.createDirectories(temp.resolve("safe")); output = Files.createDirectories(temp.resolve("out"));
        Path root = Path.of(System.getProperty("project.root"));
        Properties properties = new Properties();
        if (real) {
            Path config = Path.of(System.getProperty("fixture.secrets", root.resolve(".local/fixture-secrets.properties").toString()));
            if (!Files.isRegularFile(config)) { throw new IllegalStateException("Ignored fixture credentials required."); }
            try (var reader = new PushbackReader(Files.newBufferedReader(config, StandardCharsets.UTF_8))) {
                int first=reader.read(); if(first!=-1 && first!=0xfeff){reader.unread(first);} properties.load(reader);
            }
        }
        try {
            String[] ids = {"admin", "user01", "user02"};
            for (int i=0;i<3;i++) {
                if (real) {
                    String value = properties.getProperty(ids[i]); if(value==null || value.isEmpty()){throw new IllegalStateException("Missing fixture phrase.");}
                    Path cert=temp.resolve(ids[i]+".crt"), key=temp.resolve(ids[i]+".key");
                    Files.copy(root.resolve("Pacote-T3/Keys/"+ids[i]+"-x509.crt"),cert);
                    Files.copy(root.resolve("Pacote-T3/Keys/"+ids[i]+"-pkcs8-aes.key"),key);
                    char[] phrase=value.toCharArray(); try { materials[i]=new RegistrationTestSupport.Material(cert,key,phrase); } finally{Arrays.fill(phrase,'\0');}
                } else { materials[i]=RegistrationTestSupport.synthetic(temp.resolve("keys"),ids[i]); }
                char[] phrase=materials[i].phrase();
                try { keys[i]=new PrivateKeyService().restore(Files.readAllBytes(materials[i].key),phrase); }
                finally { Arrays.fill(phrase,'\0'); }
                publics[i]=new CertificateService().read(materials[i].certificate).getPublicKey();
            }
        } finally { properties.clear(); }
        database=new Database(temp.resolve("cofre.db")); var registrations=new RegistrationService(database);
        identities[0]=AuthenticationTestSupport.register(registrations,null,true,materials[0],Group.ADMINISTRATOR,8);
        startup=new StartupService(database); startup.begin(); char[] phrase=materials[0].phrase();
        try { startup.unlock(phrase); } finally { Arrays.fill(phrase,'\0'); }
        auth=new AuthenticationService(database,clock); SessionContext admin=AuthenticationTestSupport.login(auth,identities[0],clock);
        var authority=auth.registrationAuthorization(admin);
        identities[1]=AuthenticationTestSupport.register(registrations,authority,false,materials[1],Group.USER,9);
        identities[2]=AuthenticationTestSupport.register(registrations,authority,false,materials[2],Group.USER,10);
        auth.logout(admin);
        if (real) {
            try(var paths=Files.list(root.resolve("Pacote-T3/Files"))){for(Path path:paths.toList()){Files.copy(path,folder.resolve(path.getFileName()));}}
        } else { writeSynthetic(); }
    }
    public SessionContext login(int index) throws Exception {
        if(auth.stage()==AuthenticationService.Stage.AUTHENTICATED){auth.logout(auth.session());}
        return AuthenticationTestSupport.login(auth,identities[index],clock);
    }
    public VaultService vault(int index) throws Exception { return startup.openVault(login(index)); }
    public VaultService.Listing list(VaultService vault,int index) throws Exception {
        char[] phrase=materials[index].phrase();try{return vault.list(folder.toString(),phrase);}finally{Arrays.fill(phrase,'\0');}
    }
    public String email(int index){return identities[index].user.email();}
    public void writeSynthetic() throws Exception {
        String index="A111 alpha.txt "+email(0)+" administrador\nB222 beta.txt "+email(1)+" usuario\nC333 gamma.txt "+email(2)+" usuario\n";
        protect("index",index.getBytes(StandardCharsets.US_ASCII),0,null);
        protect("share",("C333 1 "+email(1)+"\n").getBytes(StandardCharsets.US_ASCII),0,null);
        protect("A111","verified A".getBytes(StandardCharsets.US_ASCII),0,null);
        protect("B222","verified B".getBytes(StandardCharsets.US_ASCII),1,null);
        protect("C333","verified C".getBytes(StandardCharsets.US_ASCII),2,1);
    }
    public void protect(String code,byte[] plaintext,int owner,Integer recipient) throws Exception {
        byte[] seed=new byte[32];new SecureRandom().nextBytes(seed);
        try {
            Files.write(folder.resolve(code+".enc"),new AesCipher().encrypt(plaintext,new LegacyKeyDerivation().fromSeed(seed)));
            Files.write(folder.resolve(code+".asd"),new SignatureService().sign(plaintext,keys[owner]));
            Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,publics[owner]);Files.write(folder.resolve(code+".env"),rsa.doFinal(seed));
            if(recipient!=null){rsa.init(Cipher.ENCRYPT_MODE,publics[recipient]);Files.write(folder.resolve(code+".1.shr"),rsa.doFinal(seed));}
        }finally{Arrays.fill(seed,(byte)0);}
    }
    public List<Integer> events() throws Exception {
        try(var c=database.open();var s=c.prepareStatement("SELECT MID FROM Registros WHERE MID BETWEEN 7001 AND 7021 ORDER BY RID");var r=s.executeQuery()){
            List<Integer> result=new ArrayList<>();while(r.next()){result.add(r.getInt(1));}return result;
        }
    }
    public void clearEvents() throws Exception {database.transaction(c->{try(var s=c.prepareStatement("DELETE FROM Registros WHERE MID BETWEEN 7001 AND 7021")){s.executeUpdate();}return null;});}
    public static void corrupt(Path file) throws Exception {byte[] bytes=Files.readAllBytes(file);bytes[0]^=1;Files.write(file,bytes);Arrays.fill(bytes,(byte)0);}
    public static void noOutputs(Path output) throws Exception {try(var files=Files.list(output)){org.junit.jupiter.api.Assertions.assertEquals(0,files.count());}}
    @Override public void close() throws Exception {
        try{auth.close();}finally{startup.shutdown();for(var i:identities){if(i!=null){i.close();}}for(var m:materials){if(m!=null){m.close();}}Arrays.fill(keys,null);}
    }
}
