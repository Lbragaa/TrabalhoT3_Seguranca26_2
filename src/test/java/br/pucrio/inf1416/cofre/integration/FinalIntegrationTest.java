package br.pucrio.inf1416.cofre.integration;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinalIntegrationTest {
    @TempDir Path temp;
    @Test void freshDatabaseThroughIndependentAuditReview()throws Exception{IntegrationWorkflow.run(temp,false);}
}
