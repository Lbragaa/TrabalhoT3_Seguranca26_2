package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.auth.*;
import br.pucrio.inf1416.cofre.db.Database;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.*;

/** Startup -> MFA -> role menu. Consultation remains an explicit Phase 4 placeholder. */
public final class ApplicationController {
    private final JFrame frame;
    private final JPanel cards = new JPanel(new CardLayout());
    private volatile StartupService startup;
    private volatile AuthenticationService authentication;
    private RegistrationService registrations;
    private RegistrationController registrationController;
    private AuthenticationController authenticationController;
    private SessionContext session;
    private boolean busy = true;
    private record Initialized(Database database, StartupService startup, RegistrationService service, RegistrationService.FormInfo form) { }

    public ApplicationController(JFrame frame) {
        this.frame = frame; frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); frame.setContentPane(cards);
        frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { exit(0, false); } });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { closeResources(false); } catch (Exception e) { System.err.println("Falha ao persistir encerramento do sistema."); }
        }, "cofre-shutdown"));
    }
    public void start(Path databasePath) {
        show("loading", new JLabel("Inicializando Cofre Digital…", SwingConstants.CENTER));
        BackgroundTask.run(() -> {
            Database database = new Database(databasePath);
            StartupService s = new StartupService(database); s.begin(); startup = s;
            RegistrationService r = new RegistrationService(database);
            return new Initialized(database, s, r, s.state() == StartupService.State.BOOTSTRAP_REQUIRED ? r.showForm(null, true) : null);
        }, initialized -> {
            registrations = initialized.service(); authentication = new AuthenticationService(initialized.database()); busy = false;
            if (initialized.form() != null) { showBootstrap(initialized.form()); } else { showUnlock(); }
        }, this::fatal);
    }
    private void showBootstrap(RegistrationService.FormInfo info) {
        RegistrationPanel panel = new RegistrationPanel(info);
        registrationController = new RegistrationController(panel, registrations, null, true, RegistrationDialogs.swing(frame),
                result -> startup.acceptBootstrap(result), () -> { registrationController = null; showLogin(); }, () -> { }, this::fatal);
        show("registration", panel);
    }
    private void showUnlock() {
        StartupPassphrasePanel panel = new StartupPassphrasePanel();
        panel.submit.addActionListener(e -> {
            if (busy) { return; } busy = true; panel.setBusy(true);
            char[] phrase = panel.phrase.getPassword(); panel.phrase.setText("");
            BackgroundTask.run(() -> { try { startup.unlock(phrase); return null; } finally { Arrays.fill(phrase, '\0'); } },
                    ignored -> { busy = false; showLogin(); }, this::fatal);
        });
        panel.phrase.addActionListener(e -> panel.submit.doClick()); show("unlock", panel);
    }
    private void showLogin() {
        if (authenticationController != null) { authenticationController.dispose(); }
        AuthenticationPanel panel = new AuthenticationPanel();
        authenticationController = new AuthenticationController(panel, authentication, value -> {
            session = value; authenticationController.dispose(); authenticationController = null; showMainMenu();
        }, this::notice, this::fatal);
        show("authentication", panel); authenticationController.begin();
    }
    private void showMainMenu() {
        busy = true;
        BackgroundTask.run(() -> authentication.mainMenu(session), user -> {
            busy = false; MainMenuPanel panel = new MainMenuPanel(user);
            panel.register.addActionListener(e -> showRegistration());
            panel.consult.addActionListener(e -> consultationPlaceholder());
            panel.exit.addActionListener(e -> showExit()); show("menu", panel);
        }, this::fatal);
    }
    private void showRegistration() {
        if (busy) { return; } busy = true;
        BackgroundTask.run(() -> {
            authentication.menu(session, AuthenticationService.Menu.REGISTER);
            var authorization = authentication.registrationAuthorization(session);
            return registrations.showForm(authorization, false);
        }, info -> {
            busy = false; RegistrationPanel panel = new RegistrationPanel(info);
            try {
                registrationController = new RegistrationController(panel, registrations, authentication.registrationAuthorization(session), false,
                        RegistrationDialogs.swing(frame), result -> { }, () -> { },
                        () -> { registrationController = null; showMainMenu(); }, this::fatal);
                show("registration", panel);
            } catch (AuthenticationException e) { fatal(e); }
        }, this::fatal);
    }
    private void consultationPlaceholder() {
        if (busy) { return; } busy = true;
        BackgroundTask.run(() -> { authentication.menu(session, AuthenticationService.Menu.CONSULT); return null; }, ignored -> {
            busy = false; notice("Consulta ao cofre ainda não implementada. Disponível somente após a Fase 4.");
        }, this::fatal);
    }
    private void showExit() {
        if (busy) { return; } busy = true;
        BackgroundTask.run(() -> {
            authentication.menu(session, AuthenticationService.Menu.EXIT); return authentication.exitScreen(session);
        }, user -> {
            busy = false; SessionExitPanel panel = new SessionExitPanel(user);
            panel.logout.addActionListener(e -> logout()); panel.exit.addActionListener(e -> exit(0, true));
            panel.back.addActionListener(e -> {
                if (busy) { return; } busy = true;
                BackgroundTask.run(() -> { authentication.backFromExit(session); return null; }, ignored -> { busy = false; showMainMenu(); }, this::fatal);
            }); show("exit", panel);
        }, this::fatal);
    }
    private void logout() {
        if (busy) { return; } busy = true;
        BackgroundTask.run(() -> { authentication.logout(session); return null; }, ignored -> {
            session = null; registrationController = null; busy = false; showLogin();
        }, this::fatal);
    }
    private void show(String name, JComponent panel) {
        cards.removeAll(); cards.add(panel, name); ((CardLayout) cards.getLayout()).show(cards, name);
        frame.pack(); frame.setMinimumSize(new Dimension(650, 250)); frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
    private void notice(String message) { JOptionPane.showMessageDialog(frame, message, "Cofre Digital", JOptionPane.INFORMATION_MESSAGE); }
    private void fatal(Exception error) {
        busy = false;
        String message = error instanceof java.security.GeneralSecurityException ? error.getMessage()
                : "Não foi possível concluir a operação. O sistema será encerrado para proteger a sessão e os registros.";
        JOptionPane.showMessageDialog(frame, message, "Cofre Digital", JOptionPane.ERROR_MESSAGE); exit(1, false);
    }
    private void exit(int code, boolean explicitButton) {
        if (busy || (registrationController != null && registrationController.isBusy())
                || (authenticationController != null && authenticationController.isBusy())) { return; }
        for (Component card : cards.getComponents()) {
            if (card instanceof RegistrationPanel panel) { panel.clear(); }
            if (card instanceof StartupPassphrasePanel panel) { panel.phrase.setText(""); }
            if (card instanceof AuthenticationPanel panel) { panel.clearSensitiveInput(); }
        }
        if (authenticationController != null) { authenticationController.dispose(); }
        busy = true;
        BackgroundTask.run(() -> { closeResources(explicitButton); return null; }, ignored -> {
            frame.dispose(); System.exit(code);
        }, error -> {
            JOptionPane.showMessageDialog(frame, "Falha ao registrar encerramento. O sistema será encerrado.", "Cofre Digital", JOptionPane.ERROR_MESSAGE);
            frame.dispose(); System.exit(1);
        });
    }
    private void closeResources(boolean explicitButton) throws Exception {
        try {
            AuthenticationService current = authentication;
            if (current != null) {
                if (explicitButton && session != null) { current.exitSystem(session); }
                else { current.close(); }
            }
        } finally { StartupService current = startup; if (current != null) { current.shutdown(); } }
    }
}
