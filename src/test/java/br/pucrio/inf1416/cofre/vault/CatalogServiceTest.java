package br.pucrio.inf1416.cofre.vault;

import java.nio.file.Path;
import java.security.SignatureException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogServiceTest {
    @Test void bothSignaturesPrecedeParsingAndOwnedBuffersAreCleared()throws Exception{
        byte[] index="not a valid record".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var names=new ArrayList<String>();var events=new ArrayList<Integer>();
        var loader=new CatalogService((directory,name)->{names.add(name);if(name.equals("share")){throw new SignatureException();}return index;});
        assertEquals(VaultException.Reason.SIGNATURE,assertThrows(VaultException.class,()->loader.load(Path.of("."),e->events.add(e.code()))).reason());
        assertEquals(List.of("index","share"),names);assertEquals(List.of(7005,7006,7010,7013),events);assertArrayEquals(new byte[index.length],index);
    }
    @Test void auditFailureClearsReturnedBytes()throws Exception{
        byte[] bytes={1,2,3};var service=new CatalogService((directory,name)->bytes);
        assertThrows(java.sql.SQLException.class,()->service.load(Path.of("."),e->{throw new java.sql.SQLException("injected");}));
        assertArrayEquals(new byte[3],bytes);
    }
}
