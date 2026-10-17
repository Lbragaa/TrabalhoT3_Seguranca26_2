package br.pucrio.inf1416.cofre.app;

import br.pucrio.inf1416.cofre.ui.FoundationPanel;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Phase 0 launcher. No registration, authentication, database, or vault operations yet. */
public final class VaultMain {
    private VaultMain() { }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "--check".equals(args[0]))) {
            System.out.println("Cofre Digital — Phase 0 foundation. Usage: java -jar cofre-digital.jar [--help|--check]");
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Unknown arguments; use --help.");
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A graphical desktop is required. Use --check for a launcher smoke check.");
        }
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cofre Digital — Fundação");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new FoundationPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
