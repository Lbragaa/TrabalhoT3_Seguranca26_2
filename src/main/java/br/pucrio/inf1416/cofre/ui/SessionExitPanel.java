package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.awt.*;
import javax.swing.*;

/** Minimal required session termination navigation; no vault functionality. */
public final class SessionExitPanel extends JPanel {
    final JButton logout = new JButton("Encerrar Sessão"), exit = new JButton("Encerrar Sistema"), back = new JButton("Voltar de Sair para o Menu Principal");
    public SessionExitPanel(User user) {
        super(new BorderLayout(12, 12)); setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24)); add(MainMenuPanel.header(user), BorderLayout.NORTH);
        add(new JLabel("Pressione o botão Encerrar Sessão ou o botão Encerrar Sistema para confirmar."));
        JPanel buttons = new JPanel(new GridLayout(0, 1, 8, 8)); buttons.add(logout); buttons.add(exit); buttons.add(back); add(buttons, BorderLayout.SOUTH);
    }
}
