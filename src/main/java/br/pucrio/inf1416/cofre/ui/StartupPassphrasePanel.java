package br.pucrio.inf1416.cofre.ui;

import java.awt.*;
import javax.swing.*;

public final class StartupPassphrasePanel extends JPanel {
    final JPasswordField phrase=new JPasswordField(30);
    final JButton submit=new JButton("Validar e continuar");
    public StartupPassphrasePanel(){
        super(new BorderLayout(12,12));setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        add(new JLabel("Informe a frase secreta da chave privada do administrador inicial."),BorderLayout.NORTH);
        add(phrase,BorderLayout.CENTER);add(submit,BorderLayout.SOUTH);FieldLimits.apply(phrase,255);
    }
    void setBusy(boolean value){phrase.setEnabled(!value);submit.setEnabled(!value);}
}
