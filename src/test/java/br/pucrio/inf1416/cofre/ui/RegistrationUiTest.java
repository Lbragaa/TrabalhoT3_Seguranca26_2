package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.*;
import br.pucrio.inf1416.cofre.crypto.CertificateService;
import br.pucrio.inf1416.cofre.db.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationUiTest {
    @TempDir static Path materialDirectory;
    @TempDir Path temp;
    static RegistrationTestSupport.Material admin,user;
    @BeforeAll static void material() throws Exception {admin=RegistrationTestSupport.synthetic(materialDirectory,"uiadmin");user=RegistrationTestSupport.synthetic(materialDirectory,"uiuser");}
    @AfterAll static void clear(){admin.close();user.close();}
    private static final class Dialogs implements RegistrationDialogs {
        final boolean certificateAccepted,enrollmentAccepted;
        int enrollmentCalls,errors;
        Dialogs(boolean certificateAccepted,boolean enrollmentAccepted){this.certificateAccepted=certificateAccepted;this.enrollmentAccepted=enrollmentAccepted;}
        public boolean certificate(X509Certificate c,CertificateService.Identity identity){
            assertTrue(SwingUtilities.isEventDispatchThread());String details=RegistrationDialogs.certificateDetails(c,identity);
            for(String label:new String[]{"Versão:","Série:","Validade:","Tipo de assinatura:","Emissor:","Sujeito:","Nome:","E-mail:"}){assertTrue(details.contains(label));}
            return certificateAccepted;
        }
        public boolean enrollment(String secret,String email){assertTrue(SwingUtilities.isEventDispatchThread());assertEquals(32,secret.length());enrollmentCalls++;return enrollmentAccepted;}
        public void error(String message){assertTrue(SwingUtilities.isEventDispatchThread());errors++;}
    }
    private record Harness(RegistrationPanel panel,RegistrationController controller,AtomicInteger completed,AtomicInteger fatal,Database db) { }
    private Harness harness(Dialogs dialogs) throws Exception {
        Database db=new Database(temp.resolve("cofre.db"));RegistrationService service=new RegistrationService(db);
        var info=service.showForm(null,true);AtomicReference<Harness> result=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            RegistrationPanel panel=new RegistrationPanel(info);AtomicInteger completed=new AtomicInteger(),fatal=new AtomicInteger();
            var controller=new RegistrationController(panel,service,null,true,dialogs,value->{assertFalse(SwingUtilities.isEventDispatchThread());},completed::incrementAndGet,()->{},e->fatal.incrementAndGet());
            panel.certificate.setText(admin.certificate.toString());panel.key.setText(admin.key.toString());
            char[] phrase=admin.phrase(),password=RegistrationTestSupport.password();
            try{panel.phrase.setText(new String(phrase));panel.password.setText(new String(password));panel.confirmation.setText(new String(password));}
            finally{Arrays.fill(phrase,'\0');Arrays.fill(password,'\0');}
            result.set(new Harness(panel,controller,completed,fatal,db));
        });return result.get();
    }
    private void submitAndWait(Harness h) throws Exception {
        SwingUtilities.invokeAndWait(()->h.panel.submit.doClick());long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        AtomicBoolean busy=new AtomicBoolean(true);
        while(busy.get()&&System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->busy.set(h.controller.isBusy()));if(busy.get()){Thread.sleep(20);}}
        assertFalse(busy.get(),"UI worker did not finish");
    }
    private long users(Harness h) throws Exception {try(var c=h.db.open()){return new UserRepository().count(c);}}
    @Test void acceptedEnrollmentCommitsClearsFormAndRunsCryptoOffEdt() throws Exception {
        Dialogs dialogs=new Dialogs(true,true);Harness h=harness(dialogs);submitAndWait(h);
        assertEquals(1,users(h));assertEquals(1,h.completed.get());assertEquals(0,h.fatal.get());assertEquals(1,dialogs.enrollmentCalls);
        SwingUtilities.invokeAndWait(()->{assertTrue(h.panel.certificate.getText().isEmpty());assertEquals(0,h.panel.phrase.getPassword().length);assertEquals(0,h.panel.password.getPassword().length);});
    }
    @Test void rejectedCertificateLeavesNoAccountAndPreservesForm() throws Exception {
        Dialogs dialogs=new Dialogs(false,true);Harness h=harness(dialogs);submitAndWait(h);
        assertEquals(0,users(h));assertEquals(0,dialogs.enrollmentCalls);
        SwingUtilities.invokeAndWait(()->assertFalse(h.panel.certificate.getText().isEmpty()));
        try(var c=h.db.open();var s=c.prepareStatement("SELECT count(*) FROM Registros WHERE MID=6009");var r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
    }
    @Test void rejectedEnrollmentLeavesNoAccountAndPreservesForm() throws Exception {
        Dialogs dialogs=new Dialogs(true,false);Harness h=harness(dialogs);submitAndWait(h);
        assertEquals(0,users(h));assertEquals(1,dialogs.enrollmentCalls);assertEquals(0,h.completed.get());
        SwingUtilities.invokeAndWait(()->assertFalse(h.panel.key.getText().isEmpty()));
    }
    @Test void invalidPasswordShowsErrorAndKeepsInputForCorrection() throws Exception {
        Dialogs dialogs=new Dialogs(true,true);Harness h=harness(dialogs);
        SwingUtilities.invokeAndWait(()->h.panel.confirmation.setText(""));submitAndWait(h);
        assertEquals(0,users(h));assertEquals(1,dialogs.errors);assertEquals(0,h.fatal.get());
        SwingUtilities.invokeAndWait(()->assertFalse(h.panel.certificate.getText().isEmpty()));
    }
    @Test void auditFailurePreventsCommitAndCallsFatalHandler() throws Exception {
        Dialogs dialogs=new Dialogs(true,true);Harness h=harness(dialogs);
        try(var c=h.db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_confirm BEFORE INSERT ON Registros WHEN NEW.MID=6008 BEGIN SELECT RAISE(ABORT,'injected'); END");}
        submitAndWait(h);assertEquals(0,users(h));assertEquals(1,h.fatal.get());assertEquals(1,dialogs.errors);
    }
    @Test void limitsGroupChoiceAndHeadlessPanelRender() throws Exception {
        Harness h=harness(new Dialogs(false,false));
        AtomicReference<BufferedImage> image=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            assertFalse(h.panel.group.isEnabled());assertEquals(Group.ADMINISTRATOR,h.panel.group.getSelectedItem());assertNotEquals(0,h.panel.phrase.getEchoChar());
            h.panel.certificate.setText("");h.panel.certificate.setText("x".repeat(255));assertEquals(255,h.panel.certificate.getText().length());
            h.panel.certificate.replaceSelection("x");assertEquals(255,h.panel.certificate.getText().length());
            h.panel.clear();h.panel.setSize(820,440);layout(h.panel);
            BufferedImage value=new BufferedImage(820,440,BufferedImage.TYPE_INT_RGB);var graphics=value.createGraphics();h.panel.printAll(graphics);graphics.dispose();image.set(value);
            var unlock=new StartupPassphrasePanel();unlock.phrase.setText("x".repeat(255));assertEquals(255,unlock.phrase.getPassword().length);assertNotEquals(0,unlock.phrase.getEchoChar());
        });
        Path target=Path.of(System.getProperty("project.root"),"target/phase2-registration-panel.png");Files.createDirectories(target.getParent());ImageIO.write(image.get(),"png",target.toFile());
    }
    private static void layout(java.awt.Container container){container.doLayout();for(var child:container.getComponents()){if(child instanceof java.awt.Container nested){layout(nested);}}}
    @Test void authorizedRegistrationComponentUpdatesCountAndSupportsAuditedBack() throws Exception {
        Database db=new Database(temp.resolve("cofre.db"));var service=new RegistrationService(db);
        long uid;try(var result=RegistrationTestSupport.register(service,null,true,admin,Group.ADMINISTRATOR)){uid=result.user().uid();}
        try(var authorization=RegistrationTestSupport.authority(db,uid)){
            var info=service.showForm(authorization,false);AtomicReference<Harness> value=new AtomicReference<>();
            var returned=new java.util.concurrent.CountDownLatch(1);Dialogs dialogs=new Dialogs(true,true);
            SwingUtilities.invokeAndWait(()->{
                var panel=new RegistrationPanel(info);var done=new AtomicInteger();var fatal=new AtomicInteger();
                var controller=new RegistrationController(panel,service,authorization,false,dialogs,result->{assertFalse(SwingUtilities.isEventDispatchThread());},done::incrementAndGet,returned::countDown,e->fatal.incrementAndGet());
                assertTrue(panel.group.isEnabled());assertEquals(Group.USER,panel.group.getSelectedItem());
                panel.certificate.setText(user.certificate.toString());panel.key.setText(user.key.toString());char[] phrase=user.phrase(),p=RegistrationTestSupport.password();
                try{panel.phrase.setText(new String(phrase));panel.password.setText(new String(p));panel.confirmation.setText(new String(p));}finally{Arrays.fill(phrase,'\0');Arrays.fill(p,'\0');}
                value.set(new Harness(panel,controller,done,fatal,db));
            });
            Harness h=value.get();submitAndWait(h);assertEquals(2,users(h));assertEquals(1,h.completed.get());assertEquals(0,h.fatal.get());
            SwingUtilities.invokeAndWait(()->h.panel.back.doClick());assertTrue(returned.await(10,TimeUnit.SECONDS));
            try(var c=db.open();var s=c.prepareStatement("SELECT count(*) FROM Registros WHERE MID=6010");var r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
        }
    }
}
