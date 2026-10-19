/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Clearly labeled shell; later phase screens are intentionally absent. */
public final class FoundationPanel extends JPanel {
    public FoundationPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(new JLabel("<html><h1>Cofre Digital</h1>Fase 0: estrutura do projeto pronta.<br>"
                + "Autenticação, banco de dados e acesso a arquivos ainda não implementados.</html>"));
    }
}
