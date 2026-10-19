/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.vault;

import br.pucrio.inf1416.cofre.db.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    @Test void reasonsUnionDeduplicatesAndEnvelopeRouteIsSeparate(){
        var user=new UserRepository.User(1,"a@b","Name",Group.USER,1,false,0,0,0,1,0);
        var entry=new Catalog.Entry("ABC","a.txt","a@b",Group.USER);var shared=new Catalog.Entry("DEF","b.txt","b@c",Group.USER);
        var catalog=new Catalog(List.of(entry,shared),List.of(new Catalog.Share("DEF",7,"a@b")));var p=new AccessPolicy();
        assertEquals(Set.of(AccessPolicy.Reason.OWNER,AccessPolicy.Reason.GROUP),p.evaluate(user,entry,catalog).reasons());
        assertEquals("ABC.env",p.evaluate(user,entry,catalog).envelope("ABC"));assertEquals("DEF.7.shr",p.evaluate(user,shared,catalog).envelope("DEF"));
        assertEquals(2,p.listing(user,catalog).size());assertFalse(p.evaluate(null,entry,catalog).visible());
        assertFalse(p.evaluate(user,new Catalog.Entry("FAKE","x.txt","a@b",Group.USER),catalog).visible());
        var groupOnly=new Catalog(List.of(shared),List.of());assertTrue(p.evaluate(user,shared,groupOnly).visible());assertFalse(p.evaluate(user,shared,groupOnly).decryptable());
    }
}
