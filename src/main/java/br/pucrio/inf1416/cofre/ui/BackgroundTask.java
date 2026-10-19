/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

final class BackgroundTask {
    private BackgroundTask() { }
    static <T> void run(Callable<T> work,Consumer<T> completed,Consumer<Exception> failed){
        new SwingWorker<T,Void>(){
            protected T doInBackground() throws Exception{return work.call();}
            protected void done(){
                try{completed.accept(get());}
                catch(InterruptedException e){Thread.currentThread().interrupt();failed.accept(e);}
                catch(ExecutionException e){failed.accept(e.getCause() instanceof Exception cause?cause:new IllegalStateException("Operação interrompida."));}
                catch(RuntimeException e){failed.accept(e);}
            }
        }.execute();
    }
}
