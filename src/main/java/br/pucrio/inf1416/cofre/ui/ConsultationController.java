package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.vault.*;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.JFileChooser;

/** All I/O/crypto/SQL stays in workers; generation guards disposed views. */
public final class ConsultationController {
    public interface Directories { Path choose(Component parent, boolean output); }
    public static Directories swingDirectories() {
        return (parent, output) -> {
            JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(output ? "Pasta de saída (fora de Pacote-T3 e da pasta segura)" : "Pasta segura");
            return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
        };
    }
    private final ConsultationPanel panel;
    private final VaultService vault;
    private final Directories directories;
    private final Runnable back;
    private final Consumer<String> notify;
    private final Consumer<Exception> fatal;
    private boolean busy, disposed;
    private long generation;
    public ConsultationController(ConsultationPanel panel, VaultService vault, Directories directories,
            Runnable back, Consumer<String> notify, Consumer<Exception> fatal) {
        this.panel = panel; this.vault = vault; this.directories = directories; this.back = back; this.notify = notify; this.fatal = fatal;
        panel.browse.addActionListener(e -> { if (!busy && !disposed) { Path path = directories.choose(panel, false); if (path != null) { panel.folder.setText(path.toString()); } } });
        panel.list.addActionListener(e -> list()); panel.phrase.addActionListener(e -> list()); panel.extract.addActionListener(e -> extract());
        panel.back.addActionListener(e -> goBack());
    }
    public boolean isBusy() { return busy; }
    private void setBusy(boolean value) { busy = value; panel.setBusy(value); }
    private void list() {
        if (busy || disposed) { return; }
        setBusy(true); panel.clearListing(); long version = generation;
        String path = panel.folder.getText(); char[] phrase = panel.phrase.getPassword(); panel.phrase.setText("");
        BackgroundTask.run(() -> { try { return vault.list(path, phrase); } finally { Arrays.fill(phrase, '\0'); } }, result -> {
            if (!live(version)) { return; } setBusy(false); panel.showListing(result);
        }, error -> failed(version, error));
    }
    private void extract() {
        if (busy || disposed) { return; } String code = panel.selectedCode(); if (code == null) { return; }
        Path output = directories.choose(panel, true); if (output == null) { return; }
        setBusy(true); long version = generation;
        BackgroundTask.run(() -> vault.extract(code, output), path -> {
            if (!live(version)) { return; } setBusy(false); notify.accept("Arquivo verificado e gravado em: "+path);
        }, error -> failed(version, error));
    }
    private void goBack() {
        if (busy || disposed) { return; } setBusy(true); long version = generation;
        BackgroundTask.run(() -> { vault.back(); return null; }, ignored -> {
            if (!live(version)) { return; } disposed = true; generation++; panel.clear(); busy = false; back.run();
        }, error -> failed(version, error));
    }
    private boolean live(long version) { return !disposed && generation == version; }
    private void failed(long version, Exception error) {
        if (!live(version)) { return; } setBusy(false); panel.phrase.setText("");
        if (error instanceof VaultException vaultError) {
            if (vaultError.reason() == VaultException.Reason.SIGNATURE || vaultError.reason() == VaultException.Reason.CATALOG) { panel.clearListing(); }
            notify.accept(error.getMessage());
        } else { panel.clear(); fatal.accept(error); }
    }
    public void dispose() {
        disposed = true; generation++; vault.invalidate(); panel.clear(); setBusy(true);
        BackgroundTask.run(() -> { vault.close(); return null; }, ignored -> { }, fatal);
    }
}
