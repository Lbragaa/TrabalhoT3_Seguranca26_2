/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre;

import br.pucrio.inf1416.cofre.app.VaultMain;
import br.pucrio.inf1416.cofre.logview.LogViewMain;
import br.pucrio.inf1416.cofre.ui.FoundationPanel;
import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundationTest {
    @Test void entryPointsOfferNonMutatingSmokeChecks() {
        assertDoesNotThrow(() -> VaultMain.main(new String[]{"--check"}));
        assertDoesNotThrow(() -> LogViewMain.main(new String[]{"--check"}));
        assertThrows(IllegalArgumentException.class, () -> VaultMain.main(new String[]{"--unknown"}));
    }

    @Test void swingShellConstructsOnEventDispatchThread() throws Exception {
        AtomicReference<FoundationPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new FoundationPanel()));
        assertEquals(1, panel.get().getComponentCount());
    }

    @Test void sqliteDriverLoadsAndExecutesInMemory() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test void bouncyCastleDependencyIsPresent() throws Exception {
        // API availability only; password hashing belongs to Phase 1.
        assertNotNull(OpenBSDBCrypt.class.getMethod("generate", String.class, char[].class, byte[].class, int.class));
    }
}
