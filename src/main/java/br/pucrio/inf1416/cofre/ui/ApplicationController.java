package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.auth.RegistrationService;
import br.pucrio.inf1416.cofre.db.Database;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import javax.swing.*;

/** Phase 2 navigation. There is no route issuing a registration authorization before MFA exists. */
public final class ApplicationController {
    private final JFrame frame;
    private final JPanel cards=new JPanel(new CardLayout());
    private volatile StartupService startup;
    private RegistrationService registrations;
    private RegistrationController registrationController;
    private boolean busy=true;
    private record Initialized(StartupService startup,RegistrationService service,RegistrationService.FormInfo form) { }
    public ApplicationController(JFrame frame){
        this.frame=frame;frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);frame.setContentPane(cards);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent event){exit(0);}});
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            var current=startup;if(current!=null){try{current.shutdown();}catch(SQLException e){System.err.println("Falha ao persistir encerramento do sistema.");}}
        },"cofre-shutdown"));
    }
    public void start(Path databasePath){
        show("loading",new JLabel("Inicializando Cofre Digital…",SwingConstants.CENTER));
        BackgroundTask.run(()->{
            Database database=new Database(databasePath);
            StartupService s=new StartupService(database);s.begin();startup=s;
            RegistrationService r=new RegistrationService(database);
            return new Initialized(s,r,s.state()==StartupService.State.BOOTSTRAP_REQUIRED?r.showForm(null,true):null);
        },initialized->{
            registrations=initialized.service();busy=false;
            if(initialized.form()!=null){showBootstrap(initialized.form());}else{showUnlock();}
        },this::fatal);
    }
    private void showBootstrap(RegistrationService.FormInfo info){
        RegistrationPanel panel=new RegistrationPanel(info);
        registrationController=new RegistrationController(panel,registrations,null,true,RegistrationDialogs.swing(frame),
                result->startup.acceptBootstrap(result),()->{registrationController=null;showReady();},()->{},this::fatal);
        show("registration",panel);
    }
    private void showUnlock(){
        StartupPassphrasePanel panel=new StartupPassphrasePanel();
        panel.submit.addActionListener(e->{
            if(busy){return;}busy=true;panel.setBusy(true);char[] phrase=panel.phrase.getPassword();panel.phrase.setText("");
            BackgroundTask.run(()->{try{startup.unlock(phrase);return null;}finally{Arrays.fill(phrase,'\0');}},ignored->{busy=false;showReady();},this::fatal);
        });
        panel.phrase.addActionListener(e->panel.submit.doClick());show("unlock",panel);
    }
    private void showReady(){
        JPanel panel=new JPanel(new BorderLayout(16,16));panel.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        JTextArea message=new JTextArea("Partida do sistema concluída.\n\nA autenticação multifator será implementada na Fase 3.\nNenhuma sessão de usuário foi iniciada.\nO cadastro de outros usuários exige uma sessão administrativa autenticada.");
        message.setEditable(false);message.setOpaque(false);panel.add(message,BorderLayout.CENTER);
        JButton close=new JButton("Encerrar sistema");close.addActionListener(e->exit(0));panel.add(close,BorderLayout.SOUTH);show("ready",panel);
    }
    private void show(String name,JComponent panel){
        cards.removeAll();cards.add(panel,name);((CardLayout)cards.getLayout()).show(cards,name);
        frame.pack();frame.setMinimumSize(new Dimension(650,250));frame.setLocationRelativeTo(null);frame.setVisible(true);
    }
    private void fatal(Exception error){
        busy=false;
        String message=error instanceof java.security.GeneralSecurityException?error.getMessage():"Não foi possível concluir a partida ou persistir a operação. Sistema encerrado.";
        JOptionPane.showMessageDialog(frame,message,"Cofre Digital",JOptionPane.ERROR_MESSAGE);exit(1);
    }
    private void exit(int code){
        if(busy||(registrationController!=null&&registrationController.isBusy())){return;}
        for(Component card:cards.getComponents()){
            if(card instanceof RegistrationPanel panel){panel.clear();}
            if(card instanceof StartupPassphrasePanel panel){panel.phrase.setText("");}
        }
        busy=true;BackgroundTask.run(()->{if(startup!=null){startup.shutdown();}return null;},ignored->{frame.dispose();System.exit(code);},error->{
            JOptionPane.showMessageDialog(frame,"Falha ao registrar encerramento. O sistema será encerrado.","Cofre Digital",JOptionPane.ERROR_MESSAGE);
            frame.dispose();System.exit(1);
        });
    }
}
