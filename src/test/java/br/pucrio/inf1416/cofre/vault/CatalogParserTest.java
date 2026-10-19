/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.vault;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CatalogParserTest {
    private Catalog parse(String index,String share)throws Exception{return new CatalogParser().parse(index.getBytes(StandardCharsets.UTF_8),share.getBytes(StandardCharsets.UTF_8));}
    @Test void acceptsAsciiPunctuationEmptyAndCrLf()throws Exception{
        assertTrue(parse("","").entries().isEmpty());
        var c=parse("ABC report.doc owner+name@domain usuario\r\n","ABC 2 user@domain\r\n");assertEquals("report.doc",c.entries().get(0).filename());assertEquals(2,c.shares().get(0).id());
    }
    @ParameterizedTest @ValueSource(strings={"../a","C:\\a","a/b","..","NUL.txt","file.","name:stream","a?b"})
    void unsafeNamesRejected(String name){assertThrows(VaultException.class,()->parse("ABC "+name+" a@b usuario\n",""));}
    @ParameterizedTest @ValueSource(strings={"ABC a.txt a@b usuario\nABC b.txt b@c usuario\n","ABC a.txt a@b usuario\nabc b.txt b@c usuario\n","ABC a.txt a@b usuario\nDEF A.TXT b@c usuario\n","index a.txt a@b usuario\n","ABC a.txt a@b unknown\n","ABC a.txt a@b usuario extra\n","ABC a.txt a@b usuario\n\n","ABC a.txt a@b usuárió\n","ABC\ta.txt a@b usuario\n"})
    void malformedAndDuplicateIndexRejected(String index){assertThrows(VaultException.class,()->parse(index,""));}
    @ParameterizedTest @ValueSource(strings={"ABC 0 user@domain\n","ABC -1 user@domain\n","ABC 999999999999999999999 user@domain\n","DEF 1 user@domain\n","ABC 1 a@b\n","ABC 1 user@domain\nABC 1 another@domain\n","ABC 1 user@domain\nABC 2 user@domain\n","ABC 01 user@domain\n"})
    void sharingReferencesAndIdentifiersChecked(String share){assertThrows(VaultException.class,()->parse("ABC a.txt a@b usuario\n",share));}
}
