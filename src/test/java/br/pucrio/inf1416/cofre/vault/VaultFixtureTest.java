package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.crypto.ProtectedFileService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("fixtures")
class VaultFixtureTest {
    @TempDir Path temp;
    private void originalsIntact()throws Exception{
        Path root=Path.of(System.getProperty("project.root"));
        for(String line:Files.readAllLines(root.resolve("src/test/resources/fixtures.sha256"))){
            String[] parts=line.split("  ",2);assertEquals(parts[0],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(parts[1])))),"Professor fixture checksum mismatch");
        }
    }
    @Test void signedProfessorMatrixAllOwnersAndExplicitRecipientExtractSafely()throws Exception{
        originalsIntact();
        try(var e=new VaultTestSupport(temp.resolve("professor"),true)){
            String[] codes={"XXYYZZ00","XXYYZZ11","XXYYZZ22"};var matrix=new StringBuilder("user\tcode\tlisting_reason\textraction\n");
            for(int user=0;user<3;user++){
                try(var vault=e.vault(user)){
                    var listing=e.list(vault,user);var visible=listing.files().stream().map(Catalog.Entry::code).collect(java.util.stream.Collectors.toSet());
                    assertEquals(user==0?Set.of(codes[0]):Set.of(codes[1],codes[2]),visible);
                    for(int file=0;file<3;file++){
                        String selected=codes[file];int owner=file;
                        boolean listed=visible.contains(codes[file]);boolean route=user==file || (user==1 && file==2);
                        String reason=!listed?"denied":user==file?"owner+group":user==1&&file==2?"group+share":"group";
                        matrix.append(user==0?"admin":"user0"+user).append('\t').append(codes[file]).append('\t').append(reason).append('\t').append(route?"verified":"unavailable").append('\n');
                        Path output=Files.createDirectories(e.output.resolve(user+"-"+file));
                        if(!route){
                            var failure=assertThrows(VaultException.class,()->vault.extract(selected,output));
                            assertEquals(listed?VaultException.Reason.GROUP_ROUTE:VaultException.Reason.PERMISSION,failure.reason());VaultTestSupport.noOutputs(output);continue;
                        }
                        var entry=listing.files().stream().filter(x->x.code().equals(selected)).findFirst().orElseThrow();assertEquals(e.email(owner),entry.owner());
                        byte[] expected=new ProtectedFileService().decryptAndVerify(e.folder.resolve(selected+".enc"),e.folder.resolve(selected+".env"),e.folder.resolve(selected+".asd"),e.keys[owner],e.publics[owner]);
                        byte[] actual=null;
                        try{Path result=vault.extract(selected,output);assertEquals(entry.filename(),result.getFileName().toString());actual=Files.readAllBytes(result);assertTrue(Arrays.equals(expected,actual),"Verified owner/recipient bytes differ");}
                        finally{Arrays.fill(expected,(byte)0);if(actual!=null){Arrays.fill(actual,(byte)0);}}
                    }
                }
            }
            Files.writeString(Path.of(System.getProperty("project.root"),"target/phase4-permissions.tsv"),matrix.toString());
            try(var vault=e.vault(1)){
                e.list(vault,1);Path output=Files.createDirectories(e.output.resolve("tampered"));
                byte[] signed=Files.readAllBytes(e.folder.resolve("XXYYZZ22.asd"));
                try{VaultTestSupport.corrupt(e.folder.resolve("XXYYZZ22.asd"));assertEquals(VaultException.Reason.SIGNATURE,assertThrows(VaultException.class,()->vault.extract("XXYYZZ22",output)).reason());VaultTestSupport.noOutputs(output);}
                finally{Files.write(e.folder.resolve("XXYYZZ22.asd"),signed);}
                Files.write(e.folder.resolve("XXYYZZ22.enc"),new byte[3]);assertEquals(VaultException.Reason.DECRYPTION,assertThrows(VaultException.class,()->vault.extract("XXYYZZ22",output)).reason());VaultTestSupport.noOutputs(output);
            }
        }finally{originalsIntact();}
    }
}
