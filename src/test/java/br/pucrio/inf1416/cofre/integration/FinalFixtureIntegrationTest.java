/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.integration;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("fixtures")
class FinalFixtureIntegrationTest {
    @TempDir Path temp;
    private void integrity()throws Exception{
        Path root=Path.of(System.getProperty("project.root"));for(String line:Files.readAllLines(root.resolve("src/test/resources/fixtures.sha256"))){String[] parts=line.split("  ",2);assertEquals(parts[0],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(parts[1])))),"Original fixture changed");}
    }
    @Test void professorFixturesThroughIndependentAuditReview()throws Exception{integrity();try{IntegrationWorkflow.run(temp,true);}finally{integrity();}}
}
