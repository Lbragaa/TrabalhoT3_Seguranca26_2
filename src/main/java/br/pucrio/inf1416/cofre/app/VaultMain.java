package br.pucrio.inf1416.cofre.app;

import br.pucrio.inf1416.cofre.ui.ApplicationController;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Startup, MFA and session launcher; encrypted vault consultation awaits Phase 4. */
public final class VaultMain {
    private VaultMain() { }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "--check".equals(args[0]))) {
            System.out.println("Cofre Digital — Phase 4. Usage: java -jar cofre-digital.jar [--help|--check]");
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Unknown arguments; use --help.");
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A graphical desktop is required. Use --check for a launcher smoke check.");
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cofre Digital");
            new ApplicationController(frame).start(Path.of("runtime", "cofre.db"));
        });
    }
}
