/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.*;
import java.util.function.Consumer;

/** One worker at a time, disabled duplicate actions, and view generation checks for stale completions. */
public final class AuthenticationController {
    private final AuthenticationPanel panel;
    private final AuthenticationService authentication;
    private final Consumer<SessionContext> authenticated;
    private final Consumer<String> notify;
    private final Consumer<Exception> fatal;
    private boolean busy;
    private long generation;
    public AuthenticationController(AuthenticationPanel panel, AuthenticationService authentication,
            Consumer<SessionContext> authenticated, Consumer<String> notify, Consumer<Exception> fatal) {
        this.panel = panel; this.authentication = authentication; this.authenticated = authenticated; this.notify = notify; this.fatal = fatal;
        panel.identify.addActionListener(e -> identify());
        panel.verifyPassword.addActionListener(e -> password()); panel.verifyToken.addActionListener(e -> token());
        panel.restart.addActionListener(e -> restart());
    }
    public boolean isBusy() { return busy; }
    private void setBusy(boolean value) { busy = value; panel.setBusy(value); }
    public void begin() {
        setBusy(true); long version = ++generation;
        BackgroundTask.run(() -> { if (authentication.stage() == AuthenticationService.Stage.NEW) { authentication.start(); } return null; },
                ignored -> { if (version == generation) { setBusy(false); panel.display(AuthenticationService.Stage.IDENTIFY); } }, error -> failed(version, error));
    }
    private void identify() {
        if (busy) { return; } setBusy(true); long version = generation; String login = panel.login.getText();
        BackgroundTask.run(() -> authentication.identify(login), result -> {
            if (version != generation) { return; } setBusy(false);
            if (result == AuthenticationService.Identification.KNOWN) { panel.display(AuthenticationService.Stage.PASSWORD); }
            else { notify.accept(result == AuthenticationService.Identification.BLOCKED ? "Acesso bloqueado temporariamente. Aguarde dois minutos após o bloqueio." : "Login name não identificado."); }
        }, error -> failed(version, error));
    }
    private void password() {
        if (busy || !panel.keypad.canSubmit()) { return; } setBusy(true); long version = generation;
        var captured = panel.keypad.captured();
        BackgroundTask.run(() -> authentication.password(captured), result -> {
            if (version != generation) { return; } setBusy(false);
            switch (result) {
                case ACCEPTED -> panel.display(AuthenticationService.Stage.TOKEN);
                case REJECTED -> { panel.display(AuthenticationService.Stage.PASSWORD); notify.accept("Senha pessoal não verificada."); }
                case BLOCKED -> { panel.display(AuthenticationService.Stage.IDENTIFY); notify.accept("Acesso bloqueado por dois minutos."); }
                case STALE -> panel.display(AuthenticationService.Stage.IDENTIFY);
            }
        }, error -> failed(version, error));
    }
    private void token() {
        if (busy) { return; } setBusy(true); long version = generation;
        String code = panel.token.getText(); panel.token.setText("");
        BackgroundTask.run(() -> {
            var result = authentication.token(code);
            return result == AuthenticationService.TokenResult.ACCEPTED ? authentication.session() : null;
        }, session -> {
            if (version != generation) { return; } setBusy(false);
            if (session != null) { panel.clearSensitiveInput(); authenticated.accept(session); }
            else if (authentication.stage() == AuthenticationService.Stage.IDENTIFY) { panel.display(AuthenticationService.Stage.IDENTIFY); notify.accept("Acesso bloqueado por dois minutos."); }
            else { panel.display(AuthenticationService.Stage.TOKEN); notify.accept("Token não verificado."); }
        }, error -> failed(version, error));
    }
    private void restart() {
        if (busy) { return; } setBusy(true); long version = ++generation;
        BackgroundTask.run(() -> { authentication.restart(); return null; }, ignored -> {
            if (version == generation) { panel.clearSensitiveInput(); panel.display(AuthenticationService.Stage.IDENTIFY); setBusy(false); }
        }, error -> failed(version, error));
    }
    public void dispose() { generation++; panel.clearSensitiveInput(); setBusy(true); }
    private void failed(long version, Exception error) {
        if (version != generation) { return; } setBusy(false); panel.clearSensitiveInput();
        if (error instanceof AuthenticationException auth && auth.reason() != AuthenticationException.Reason.DATA) { notify.accept(auth.getMessage()); }
        else { fatal.accept(error); }
    }
}
