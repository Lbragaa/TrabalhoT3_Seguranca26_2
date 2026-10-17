package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.auth.*;
import br.pucrio.inf1416.cofre.db.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationUiTest {
    @TempDir static Path materials;
    @TempDir Path temp;
    static RegistrationTestSupport.Material material;
    Database db; AuthenticationService auth; AuthenticationTestSupport.Identity identity;
    AuthenticationTestSupport.MutableClock clock;
    AuthenticationPanel panel; AuthenticationController controller;
    AtomicReference<SessionContext> session = new AtomicReference<>();
    AtomicReference<Exception> fatal = new AtomicReference<>();
    AtomicInteger notices = new AtomicInteger();
    @BeforeAll static void material() throws Exception { material=RegistrationTestSupport.synthetic(materials,"ui-mfa"); }
    @AfterAll static void clearMaterial(){material.close();}
    @BeforeEach void setup() throws Exception {
        db=new Database(temp.resolve("cofre.db"));clock=new AuthenticationTestSupport.MutableClock();
        identity=AuthenticationTestSupport.register(new RegistrationService(db),null,true,material,Group.ADMINISTRATOR,8);
        auth=new AuthenticationService(db,clock);
        SwingUtilities.invokeAndWait(()->{
            panel=new AuthenticationPanel();controller=new AuthenticationController(panel,auth,value->{assertTrue(SwingUtilities.isEventDispatchThread());session.set(value);},message->notices.incrementAndGet(),fatal::set);controller.begin();
        });awaitIdle();
    }
    @AfterEach void cleanup() throws Exception {auth.close();identity.close();}
    private void awaitIdle() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);AtomicBoolean busy=new AtomicBoolean(true);
        while(busy.get()&&System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->busy.set(controller.isBusy()));if(busy.get()){Thread.sleep(10);}}
        assertFalse(busy.get(),"Authentication UI worker timed out");assertNull(fatal.get(),"Authentication UI fatal error");
    }
    private void identify() throws Exception {SwingUtilities.invokeAndWait(()->{panel.login.setText(identity.user.email());panel.identify.doClick(0);});awaitIdle();}
    private void enterPairs(boolean correct) throws Exception {
        char[] password=identity.passwordCopy();
        try{SwingUtilities.invokeAndWait(()->{
            for(char digit:password){int index=0;while(panel.keypad.layout().get(index).contains(digit)!=correct){index++;}panel.pairs[index].doClick(0);}
            assertTrue(panel.verifyPassword.isEnabled());panel.verifyPassword.doClick(0);assertFalse(panel.verifyPassword.isEnabled());
        });}finally{Arrays.fill(password,'\0');}
    }
    @Test void fullLoginIsResponsiveAndOnlyThenExposesAdministratorAuthority() throws Exception {
        identify();assertEquals(AuthenticationService.Stage.PASSWORD,auth.stage());assertNull(session.get());enterPairs(true);
        var pulse=new java.util.concurrent.CountDownLatch(1);SwingUtilities.invokeLater(pulse::countDown);
        assertTrue(pulse.await(2,TimeUnit.SECONDS),"EDT blocked during BCrypt");awaitIdle();
        assertEquals(AuthenticationService.Stage.TOKEN,auth.stage());assertNull(session.get());
        String code=identity.code(clock.millis());SwingUtilities.invokeAndWait(()->{panel.token.setText(code);panel.verifyToken.doClick(0);});awaitIdle();
        assertNotNull(session.get());assertTrue(session.get().active());assertNotNull(auth.registrationAuthorization(session.get()));
        SwingUtilities.invokeAndWait(()->assertTrue(panel.token.getText().isEmpty()));
    }
    @Test void unknownLoginAndClearDoNotCreateSession() throws Exception {
        SwingUtilities.invokeAndWait(()->{panel.login.setText("missing@example.test");panel.identify.doClick(0);});awaitIdle();
        assertEquals(1,notices.get());assertNull(session.get());assertEquals(AuthenticationService.Stage.IDENTIFY,auth.stage());
        SwingUtilities.invokeAndWait(()->{panel.clearLogin.doClick(0);assertTrue(panel.login.getText().isEmpty());});
    }
    @Test void threeIncorrectPasswordSubmissionsReturnToIdentification() throws Exception {
        identify();for(int i=0;i<3;i++){enterPairs(false);awaitIdle();}
        assertEquals(AuthenticationService.Stage.IDENTIFY,auth.stage());assertNull(session.get());
        try(var c=db.open()){assertEquals(3,new UserRepository().find(c,identity.user.uid()).orElseThrow().passwordFailures());}
    }
    @Test void clearingAndRestartingCannotResetPersistentFailureCount() throws Exception {
        identify();enterPairs(false);awaitIdle();
        SwingUtilities.invokeAndWait(()->{panel.clearPassword.doClick(0);assertEquals(0,panel.keypad.length());panel.restart.doClick(0);});awaitIdle();
        assertEquals(AuthenticationService.Stage.IDENTIFY,auth.stage());
        try(var c=db.open()){assertEquals(1,new UserRepository().find(c,identity.user.uid()).orElseThrow().passwordFailures());}
    }
    @Test void roleMenusAndOffscreenScreensDoNotExposePlaintextPasswords() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            MainMenuPanel administrator=new MainMenuPanel(identity.user);assertNotNull(administrator.register.getParent());
            var u=identity.user;var ordinary=new UserRepository.User(u.uid(),u.email(),u.name(),Group.USER,u.kid(),false,0,0,0,1,0);
            MainMenuPanel userMenu=new MainMenuPanel(ordinary);assertNull(userMenu.register.getParent());assertFalse(userMenu.register.isEnabled());
            assertFalse(panel.mask.isEditable());assertNotEquals(0,panel.mask.getEchoChar());
            try{
                render(panel,"phase3-identification.png");panel.display(AuthenticationService.Stage.PASSWORD);render(panel,"phase3-keypad.png");
                render(administrator,"phase3-administrator-menu.png");render(userMenu,"phase3-user-menu.png");render(new SessionExitPanel(u),"phase3-exit.png");
            }catch(Exception e){throw new IllegalStateException(e);}
        });
    }
    private static void render(JComponent component,String name) throws Exception {
        component.setSize(780,400);layout(component);var image=new BufferedImage(780,400,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();
        Path output=Path.of(System.getProperty("project.root"),"target",name);Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents()){if(child instanceof Container nested){layout(nested);}}}
}
