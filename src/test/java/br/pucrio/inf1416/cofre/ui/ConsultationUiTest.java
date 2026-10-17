package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.vault.*;
import br.pucrio.inf1416.cofre.crypto.ProtectedFileService;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConsultationUiTest {
    @TempDir Path temp;
    private void idle(ConsultationController controller,AtomicReference<Exception> fatal)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);AtomicBoolean busy=new AtomicBoolean(true);
        while(busy.get()&&System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->busy.set(controller.isBusy()));if(busy.get()){Thread.sleep(10);}}
        assertFalse(busy.get(),"Consultation worker timed out");assertNull(fatal.get(),"Consultation fatal error");
    }
    @Test void actualListExtractAndBackControlsWorkWithoutAdministratorPrivileges()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            AtomicReference<Exception> fatal=new AtomicReference<>();AtomicInteger notices=new AtomicInteger();AtomicBoolean returned=new AtomicBoolean();
            var panelRef=new AtomicReference<ConsultationPanel>();var controllerRef=new AtomicReference<ConsultationController>();
            SwingUtilities.invokeAndWait(()->{
                var panel=new ConsultationPanel(e.identities[1].user);panelRef.set(panel);
                controllerRef.set(new ConsultationController(panel,vault,(parent,output)->output?e.output:e.folder,()->{assertTrue(SwingUtilities.isEventDispatchThread());returned.set(true);},message->{assertTrue(SwingUtilities.isEventDispatchThread());notices.incrementAndGet();},fatal::set));
                panel.browse.doClick(0);assertEquals(e.folder.toString(),panel.folder.getText());
            });
            var panel=panelRef.get();var controller=controllerRef.get();char[] phrase=e.materials[1].phrase();
            try{SwingUtilities.invokeAndWait(()->{panel.phrase.setText(new String(phrase));panel.list.doClick(0);assertFalse(panel.list.isEnabled());assertEquals(0,panel.phrase.getPassword().length);panel.list.doClick(0);});}finally{Arrays.fill(phrase,'\0');}
            var pulse=new CountDownLatch(1);SwingUtilities.invokeLater(pulse::countDown);assertTrue(pulse.await(2,TimeUnit.SECONDS));idle(controller,fatal);
            SwingUtilities.invokeAndWait(()->{assertEquals(2,panel.table.getRowCount());assertTrue(panel.count.getText().endsWith("1"));panel.table.setRowSelectionInterval(1,1);panel.extract.doClick(0);});idle(controller,fatal);
            assertEquals(1,notices.get());assertEquals("verified C",Files.readString(e.output.resolve("gamma.txt")));
            assertThrows(br.pucrio.inf1416.cofre.auth.AuthenticationException.class,()->e.auth.registrationAuthorization(e.auth.session()));
            SwingUtilities.invokeAndWait(()->panel.back.doClick(0));idle(controller,fatal);assertTrue(returned.get());assertTrue(vault.isClosed());assertTrue(e.auth.session().active());assertTrue(e.events().contains(7002));
            try(var reopened=e.startup.openVault(e.auth.session())){assertEquals(1,reopened.user().consultationCount());}
        }
    }
    @Test void failureKeepsInputsClearsTableAndFieldLengthsAreEnforced()throws Exception{
        try(var e=new VaultTestSupport(temp,false);var vault=e.vault(1)){
            var fatal=new AtomicReference<Exception>();var notices=new AtomicInteger();var panelRef=new AtomicReference<ConsultationPanel>();var controllerRef=new AtomicReference<ConsultationController>();
            SwingUtilities.invokeAndWait(()->{var p=new ConsultationPanel(e.identities[1].user);panelRef.set(p);controllerRef.set(new ConsultationController(p,vault,(parent,output)->null,()->{},message->notices.incrementAndGet(),fatal::set));
                p.folder.setText("x".repeat(256));assertTrue(p.folder.getText().isEmpty());p.phrase.setText("x".repeat(256));assertEquals(0,p.phrase.getPassword().length);assertNotEquals(0,p.phrase.getEchoChar());
                p.folder.setText(e.folder.toString());p.phrase.setText(java.util.UUID.randomUUID().toString());p.list.doClick(0);
            });idle(controllerRef.get(),fatal);
            SwingUtilities.invokeAndWait(()->{assertEquals(e.folder.toString(),panelRef.get().folder.getText());assertEquals(0,panelRef.get().table.getRowCount());assertFalse(panelRef.get().extract.isEnabled());assertEquals(0,panelRef.get().phrase.getPassword().length);});assertEquals(1,notices.get());
        }
    }
    @Test void disposedViewRejectsLateListingAndDoesNotBlockEdt()throws Exception{
        try(var e=new VaultTestSupport(temp,false)){
            var session=e.login(1);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            var source=new CatalogService((folder,name)->{
                if(name.equals("index")){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS)){throw new java.io.IOException();}}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new java.io.IOException();}}
                return new ProtectedFileService().decryptAndVerify(folder.resolve(name+".enc"),folder.resolve(name+".env"),folder.resolve(name+".asd"),e.keys[0],e.publics[0]);
            });
            try(var vault=new VaultService(e.database,session,source)){
                var fatal=new AtomicReference<Exception>();var notices=new AtomicInteger();var p=new AtomicReference<ConsultationPanel>();var c=new AtomicReference<ConsultationController>();char[] phrase=e.materials[1].phrase();
                try{SwingUtilities.invokeAndWait(()->{p.set(new ConsultationPanel(e.identities[1].user));c.set(new ConsultationController(p.get(),vault,(parent,output)->null,()->{},message->notices.incrementAndGet(),fatal::set));p.get().folder.setText(e.folder.toString());p.get().phrase.setText(new String(phrase));p.get().list.doClick(0);});}finally{Arrays.fill(phrase,'\0');}
                assertTrue(entered.await(3,TimeUnit.SECONDS));SwingUtilities.invokeAndWait(()->c.get().dispose());
                assertTrue(vault.isClosed());var pulse=new CountDownLatch(1);SwingUtilities.invokeLater(pulse::countDown);assertTrue(pulse.await(2,TimeUnit.SECONDS));release.countDown();
                // Drain the worker through the service monitor, then the EDT completion queue.
                vault.close();Thread.sleep(100);SwingUtilities.invokeAndWait(()->assertEquals(0,p.get().table.getRowCount()));assertEquals(0,notices.get());assertNull(fatal.get());
                assertEquals(0,vaultCount(e));
            }finally{release.countDown();}
        }
    }
    private long vaultCount(VaultTestSupport e)throws Exception{try(var db=e.database.open()){return new br.pucrio.inf1416.cofre.db.UserRepository().find(db,e.identities[1].user.uid()).orElseThrow().consultationCount();}}
    @Test void offscreenConsultationRenderMatchesRequiredFields()throws Exception{
        var user=new br.pucrio.inf1416.cofre.db.UserRepository.User(1,"usuario@example.test","Usuário de demonstração",br.pucrio.inf1416.cofre.db.Group.USER,1,false,0,0,0,2,3);
        SwingUtilities.invokeAndWait(()->{
            var panel=new ConsultationPanel(user);panel.showListing(new VaultService.Listing(user,java.util.List.of(new Catalog.Entry("DEMO","relatorio.txt","usuario@example.test",br.pucrio.inf1416.cofre.db.Group.USER))));
            panel.setSize(1000,520);layout(panel);var image=new BufferedImage(1000,520,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();
            try{ImageIO.write(image,"png",Path.of(System.getProperty("project.root"),"target/phase4-consultation.png").toFile());}catch(Exception ex){throw new IllegalStateException(ex);}
        });
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents()){if(child instanceof Container nested){layout(nested);}}}
}
