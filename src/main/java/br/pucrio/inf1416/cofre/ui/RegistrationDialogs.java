package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.crypto.CertificateService.Identity;
import java.awt.Component;
import java.security.cert.X509Certificate;
import javax.swing.*;

/** Dialog seam permits tests without displaying real enrollment secrets or modal windows. */
public interface RegistrationDialogs {
    boolean certificate(X509Certificate certificate, Identity identity);
    boolean enrollment(String secret, String email);
    void error(String message);
    static String certificateDetails(X509Certificate c,Identity i){
        return "Versão: "+c.getVersion()+"\nSérie: "+c.getSerialNumber()+"\nValidade: "+c.getNotBefore()+" até "+c.getNotAfter()
                +"\nTipo de assinatura: "+c.getSigAlgName()+"\nEmissor: "+c.getIssuerX500Principal().getName()
                +"\nSujeito: "+c.getSubjectX500Principal().getName()+"\nNome: "+i.name()+"\nE-mail: "+i.email();
    }
    static RegistrationDialogs swing(Component parent){return new RegistrationDialogs(){
        public boolean certificate(X509Certificate c,Identity identity){return JOptionPane.showConfirmDialog(parent,certificateDetails(c,identity),"Confirme o certificado",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
        public boolean enrollment(String secret,String email){
            JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
            panel.add(new JLabel("Cadastre esta conta no Google Authenticator: "+email));
            panel.add(new JLabel("Tipo: baseado no tempo; chave secreta Base32:"));
            JTextField value=new JTextField(secret,34);value.setEditable(false);panel.add(value);
            panel.add(new JLabel("Confirme somente após salvar a chave. Cancelar não cria a conta."));
            try{return JOptionPane.showConfirmDialog(parent,panel,"Configuração do token TOTP",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
            finally{value.setText("");}
        }
        public void error(String message){JOptionPane.showMessageDialog(parent,message,"Cofre Digital",JOptionPane.ERROR_MESSAGE);}
    };}
}
