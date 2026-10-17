package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.AuthenticationService.Stage;
import br.pucrio.inf1416.cofre.auth.VirtualKeypad;
import java.awt.*;
import javax.swing.*;

/** No plaintext personal-password input: the only password input is captured digit pairs. */
public final class AuthenticationPanel extends JPanel {
    final JTextField login = new JTextField(32);
    final JButton identify = new JButton("OK"), clearLogin = new JButton("LIMPAR");
    final VirtualKeypad keypad = new VirtualKeypad();
    final JButton[] pairs = new JButton[5];
    final JPasswordField mask = new JPasswordField(14);
    final JButton verifyPassword = new JButton("OK"), clearPassword = new JButton("LIMPAR");
    final JTextField token = new JTextField(6);
    final JButton verifyToken = new JButton("OK"), clearToken = new JButton("LIMPAR");
    final JButton restart = new JButton("Reiniciar identificação");
    private final JPanel cards = new JPanel(new CardLayout());
    private final JLabel status = new JLabel(" ");
    private Stage stage = Stage.IDENTIFY;
    private boolean busy;

    public AuthenticationPanel() {
        super(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(new JLabel("Cofre Digital — Autenticação"), BorderLayout.NORTH);
        JPanel identification = new JPanel(new BorderLayout(8, 8));
        identification.add(new JLabel("Login name (e-mail):"), BorderLayout.NORTH);
        JPanel loginRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); loginRow.add(login);
        identification.add(loginRow, BorderLayout.CENTER);
        identification.add(buttons(identify, clearLogin), BorderLayout.SOUTH);
        JPanel password = new JPanel(new BorderLayout(8, 8));
        JPanel feedback = new JPanel(new BorderLayout(8, 8));
        feedback.add(new JLabel("Senha pessoal:"), BorderLayout.WEST); mask.setEditable(false); feedback.add(mask);
        password.add(feedback, BorderLayout.NORTH);
        JPanel keys = new JPanel(new GridLayout(2, 3, 8, 8));
        for (int i = 0; i < 5; i++) {
            final int index = i;
            pairs[i] = new JButton(); keys.add(pairs[i]);
            pairs[i].addActionListener(e -> { if (!busy) { keypad.press(index); refreshKeypad(); } });
        }
        password.add(keys, BorderLayout.CENTER); password.add(buttons(verifyPassword, clearPassword), BorderLayout.SOUTH);
        JPanel totp = new JPanel(new BorderLayout(8, 8));
        totp.add(new JLabel("TOTP (6 dígitos do Google Authenticator):"), BorderLayout.NORTH);
        JPanel tokenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); tokenRow.add(token);
        totp.add(tokenRow, BorderLayout.CENTER); totp.add(buttons(verifyToken, clearToken), BorderLayout.SOUTH);
        cards.add(identification, Stage.IDENTIFY.name()); cards.add(password, Stage.PASSWORD.name()); cards.add(totp, Stage.TOKEN.name());
        add(cards, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(status); bottom.add(restart, BorderLayout.EAST); add(bottom, BorderLayout.SOUTH);
        FieldLimits.apply(login, 255); FieldLimits.apply(token, 6);
        clearLogin.addActionListener(e -> login.setText(""));
        clearPassword.addActionListener(e -> { keypad.clear(); refreshKeypad(); });
        clearToken.addActionListener(e -> token.setText(""));
        login.addActionListener(e -> identify.doClick()); token.addActionListener(e -> verifyToken.doClick());
        display(Stage.IDENTIFY);
    }
    private JPanel buttons(JButton... values) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); for (JButton button : values) { panel.add(button); } return panel; }
    void display(Stage value) {
        stage = value; keypad.clear(); mask.setText(""); token.setText("");
        ((CardLayout) cards.getLayout()).show(cards, value.name()); refreshKeypad(); setBusy(false);
        if (value == Stage.IDENTIFY) { login.requestFocusInWindow(); }
        if (value == Stage.TOKEN) { token.requestFocusInWindow(); }
    }
    void setBusy(boolean value) {
        busy = value;
        for (JComponent component : new JComponent[]{login, identify, clearLogin, clearPassword, token, verifyToken, clearToken, restart}) { component.setEnabled(!value); }
        status.setText(value ? "Verificando… aguarde." : " "); refreshKeypad();
    }
    private void refreshKeypad() {
        for (int i = 0; i < 5; i++) { pairs[i].setText(keypad.layout().get(i).toString()); pairs[i].setEnabled(!busy && keypad.length() < 10); }
        mask.setText("*".repeat(keypad.length())); verifyPassword.setEnabled(!busy && keypad.canSubmit());
        restart.setVisible(stage != Stage.IDENTIFY);
    }
    void clearSensitiveInput() { token.setText(""); mask.setText(""); keypad.clear(); }
}
