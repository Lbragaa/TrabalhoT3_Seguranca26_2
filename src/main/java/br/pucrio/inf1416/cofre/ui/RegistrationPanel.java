package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.RegistrationRequest;
import br.pucrio.inf1416.cofre.auth.RegistrationService.FormInfo;
import br.pucrio.inf1416.cofre.db.Group;
import java.awt.*;
import java.util.Arrays;
import javax.swing.*;

/** A reusable form; owning a panel does not grant service-layer registration authorization. */
public final class RegistrationPanel extends JPanel {
    final JTextField certificate = new JTextField(30);
    final JTextField key = new JTextField(30);
    final JPasswordField phrase = new JPasswordField(30);
    final JPasswordField password = new JPasswordField(10);
    final JPasswordField confirmation = new JPasswordField(10);
    final JComboBox<Group> group = new JComboBox<>(Group.values());
    final JButton submit = new JButton("Cadastrar");
    final JButton back = new JButton("Voltar de Cadastrar para o Menu Principal");
    private final JButton browseCertificate = new JButton("Selecionar…");
    private final JButton browseKey = new JButton("Selecionar…");
    private final JTextArea header = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final boolean bootstrap;

    public RegistrationPanel(FormInfo info) {
        super(new BorderLayout(12,12));
        bootstrap = info.bootstrap();
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        header.setEditable(false); header.setOpaque(false); header.setFocusable(false);
        updateInfo(info); add(header,BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        row(form,0,"Caminho do certificado digital:",certificate,browseCertificate);
        row(form,1,"Caminho da chave privada:",key,browseKey);
        row(form,2,"Frase secreta:",phrase,null);
        row(form,3,"Grupo:",group,null);
        row(form,4,"Senha pessoal:",password,null);
        row(form,5,"Confirmação da senha pessoal:",confirmation,null);
        GridBagConstraints hint = new GridBagConstraints(); hint.gridx=0;hint.gridy=6;hint.gridwidth=3;hint.anchor=GridBagConstraints.WEST;hint.insets=new Insets(12,0,0,0);
        form.add(new JLabel("Senha: 8 a 10 dígitos, sem números iguais adjacentes."),hint);
        add(form,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout()); JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(submit); if(!bootstrap){buttons.add(back);} bottom.add(status,BorderLayout.NORTH);bottom.add(buttons,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        FieldLimits.apply(certificate,255);FieldLimits.apply(key,255);FieldLimits.apply(phrase,255);
        FieldLimits.apply(password,10);FieldLimits.apply(confirmation,10);
        group.setSelectedItem(bootstrap?Group.ADMINISTRATOR:Group.USER);group.setEnabled(!bootstrap);
        browseCertificate.addActionListener(e->choose(certificate));browseKey.addActionListener(e->choose(key));
    }
    private void row(JPanel panel,int y,String label,JComponent field,JButton browse){
        GridBagConstraints c=new GridBagConstraints();c.gridy=y;c.insets=new Insets(5,5,5,5);c.anchor=GridBagConstraints.WEST;
        c.gridx=0;panel.add(new JLabel(label),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(field,c);
        if(browse!=null){c.gridx=2;c.weightx=0;c.fill=GridBagConstraints.NONE;panel.add(browse,c);}
    }
    private void choose(JTextField field){JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){field.setText(chooser.getSelectedFile().getAbsolutePath());}}
    public RegistrationRequest snapshot(){
        char[] p=phrase.getPassword(),a=password.getPassword(),b=confirmation.getPassword();
        try{return new RegistrationRequest(certificate.getText(),key.getText(),(Group)group.getSelectedItem(),p,a,b);}
        finally{Arrays.fill(p,'\0');Arrays.fill(a,'\0');Arrays.fill(b,'\0');}
    }
    public void updateInfo(FormInfo info){
        String identity=info.actor()==null?"Cadastro inicial do administrador":
                "Login: "+info.actor().email()+"\nGrupo: "+info.actor().group().displayName()+"\nNome: "+info.actor().name();
        header.setText(identity+"\n\nTotal de usuários do sistema: "+info.totalUsers());
    }
    public void clear(){certificate.setText("");key.setText("");phrase.setText("");password.setText("");confirmation.setText("");group.setSelectedItem(bootstrap?Group.ADMINISTRATOR:Group.USER);}
    public void setBusy(boolean busy){
        for(JComponent component:new JComponent[]{certificate,key,phrase,password,confirmation,submit,back,browseCertificate,browseKey}){component.setEnabled(!busy);}
        group.setEnabled(!busy&&!bootstrap);status.setText(busy?"Processando…":" ");
    }
}
