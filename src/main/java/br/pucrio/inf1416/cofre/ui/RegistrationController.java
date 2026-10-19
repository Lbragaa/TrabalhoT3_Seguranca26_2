/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.*;
import java.util.function.Consumer;

/** Crypto/SQL on workers; input snapshots and all Swing/dialog changes on the EDT. */
public final class RegistrationController {
    @FunctionalInterface public interface Completion { void accept(RegistrationService.Result result) throws Exception; }
    private final RegistrationPanel panel;
    private final RegistrationService service;
    private final RegistrationAuthorization authorization;
    private final boolean bootstrap;
    private final RegistrationDialogs dialogs;
    private final Completion completion;
    private final Runnable done;
    private final Consumer<Exception> fatal;
    private boolean busy;
    public RegistrationController(RegistrationPanel panel,RegistrationService service,RegistrationAuthorization authorization,
            boolean bootstrap,RegistrationDialogs dialogs,Completion completion,Runnable done,Runnable back,Consumer<Exception> fatal){
        this.panel=panel;this.service=service;this.authorization=authorization;this.bootstrap=bootstrap;this.dialogs=dialogs;
        this.completion=completion;this.done=done;this.fatal=fatal;
        panel.submit.addActionListener(e->submit());
        panel.back.addActionListener(e->{if(busy){return;}setBusy(true);BackgroundTask.run(()->{service.back(authorization);return null;},ignored->{panel.clear();setBusy(false);back.run();},this::failed);});
    }
    public boolean isBusy(){return busy;}
    private void setBusy(boolean value){busy=value;panel.setBusy(value);}
    private void submit(){
        if(busy){return;}setBusy(true);RegistrationRequest request=panel.snapshot();
        BackgroundTask.run(()->{try(request){return service.prepare(authorization,bootstrap,request);}},this::prepared,this::failed);
    }
    private void prepared(RegistrationService.PendingRegistration pending){
        try{
            if(!dialogs.certificate(pending.certificate(),pending.identity())){cancel(pending);return;}
            service.confirmCertificate(pending);
            if(!dialogs.enrollment(pending.enrollmentSecret(),pending.identity().email())){cancel(pending);return;}
            BackgroundTask.run(()->{
                try(pending;var result=service.commit(pending)){
                    completion.accept(result);
                    return bootstrap?null:service.showForm(authorization,false);
                }
            },info->{panel.clear();if(info!=null){panel.updateInfo(info);}setBusy(false);done.run();},this::failed);
        }catch(RegistrationException|RuntimeException e){pending.close();failed(e);}
    }
    private void cancel(RegistrationService.PendingRegistration pending){
        BackgroundTask.run(()->{service.cancel(pending);return null;},ignored->setBusy(false),this::failed);
    }
    private void failed(Exception error){
        setBusy(false);
        if(error instanceof RegistrationException registration){dialogs.error(registration.getMessage());}
        else{dialogs.error("Falha de persistência ou processamento. Operação interrompida; verifique o banco antes de reiniciar.");fatal.accept(error);}
    }
}
