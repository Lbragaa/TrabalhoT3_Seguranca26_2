package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.db.Group;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.awt.*;
import javax.swing.*;

public final class MainMenuPanel extends JPanel {
    final JButton register = new JButton("1 — Cadastrar um novo usuário");
    final JButton consult = new JButton("2 — Consultar pasta de arquivos secretos do usuário");
    final JButton exit = new JButton("3 — Sair do Sistema");
    public MainMenuPanel(User user) {
        super(new BorderLayout(16, 16)); setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(header(user), BorderLayout.NORTH);
        JPanel menu = new JPanel(new GridLayout(0, 1, 8, 8));
        if (user.group() == Group.ADMINISTRATOR) { menu.add(register); }
        else { register.setEnabled(false); }
        menu.add(consult); menu.add(exit); add(menu);
    }
    static JTextArea header(User user) {
        JTextArea header = new JTextArea("Login: " + user.email() + "\nGrupo: " + user.group().displayName()
                + "\nNome: " + user.name() + "\n\nTotal de acessos do usuário: " + user.accessCount());
        header.setEditable(false); header.setOpaque(false); return header;
    }
}
