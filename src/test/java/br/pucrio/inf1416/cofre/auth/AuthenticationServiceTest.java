package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.db.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {
    @TempDir static Path materials;
    @TempDir Path temp;
    static RegistrationTestSupport.Material administrator, ordinary;
    Database db; RegistrationService registrations; AuthenticationTestSupport.Identity admin,user;
    AuthenticationTestSupport.MutableClock clock;
    @BeforeAll static void material() throws Exception { administrator=RegistrationTestSupport.synthetic(materials,"authadmin");ordinary=RegistrationTestSupport.synthetic(materials,"authuser"); }
    @AfterAll static void clear(){administrator.close();ordinary.close();}
    @BeforeEach void setup() throws Exception {
        db=new Database(temp.resolve("cofre.db"));registrations=new RegistrationService(db);clock=new AuthenticationTestSupport.MutableClock();
        admin=AuthenticationTestSupport.register(registrations,null,true,administrator,Group.ADMINISTRATOR,8);
        try(var authority=RegistrationTestSupport.authority(db,admin.user.uid())){user=AuthenticationTestSupport.register(registrations,authority,false,ordinary,Group.USER,8);}
    }
    @AfterEach void clearPasswords(){admin.close();user.close();}
    private UserRepository.User stored(long uid) throws Exception {try(var c=db.open()){return new UserRepository().find(c,uid).orElseThrow();}}
    private void passwordStage(AuthenticationService auth,AuthenticationTestSupport.Identity identity) throws Exception {if(auth.stage()==AuthenticationService.Stage.NEW){auth.start();}assertEquals(AuthenticationService.Identification.KNOWN,auth.identify(identity.user.email()));}
    private void tokenStage(AuthenticationService auth,AuthenticationTestSupport.Identity identity) throws Exception {passwordStage(auth,identity);assertEquals(AuthenticationService.PasswordResult.ACCEPTED,auth.password(identity.pairs()));}
    private List<Integer> events() throws Exception {
        try(var c=db.open();var s=c.prepareStatement("SELECT MID FROM Registros WHERE MID NOT BETWEEN 6001 AND 6010 ORDER BY RID");var r=s.executeQuery()) {var result=new ArrayList<Integer>();while(r.next()){result.add(r.getInt(1));}return result;}
    }
    @Test void unknownMalformedAndKnownLoginsNeverIssueSession() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            auth.start();assertEquals(AuthenticationService.Identification.UNKNOWN,auth.identify("missing@example.test"));
            assertEquals(AuthenticationService.Identification.UNKNOWN,auth.identify("bad login"));assertEquals(AuthenticationService.Stage.IDENTIFY,auth.stage());
            assertThrows(AuthenticationException.class,auth::session);passwordStage(auth,admin);assertThrows(AuthenticationException.class,auth::session);
        }
    }
    @Test void passwordAndTokenGateSessionAndCounterIncrementsOnce() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            tokenStage(auth,admin);assertEquals(0,stored(admin.user.uid()).accessCount());assertThrows(AuthenticationException.class,auth::session);
            assertEquals(AuthenticationService.TokenResult.ACCEPTED,auth.token(admin.code(clock.millis())));
            SessionContext session=auth.session();assertEquals(1,session.user().accessCount());auth.mainMenu(session);auth.mainMenu(session);
            assertThrows(AuthenticationException.class,()->auth.token(admin.code(clock.millis())));assertEquals(1,stored(admin.user.uid()).accessCount());
            auth.logout(session);assertFalse(session.active());assertEquals(AuthenticationService.Stage.IDENTIFY,auth.stage());
            AuthenticationTestSupport.login(auth,admin,clock);assertEquals(2,stored(admin.user.uid()).accessCount());
        }
    }
    @Test void passwordThreeFailuresBlockForExactly120SecondsAndSurviveReopening() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            passwordStage(auth,admin);
            for(int i=1;i<=3;i++){assertEquals(i<3?AuthenticationService.PasswordResult.REJECTED:AuthenticationService.PasswordResult.BLOCKED,auth.password(admin.wrongPairs()));assertEquals(i,stored(admin.user.uid()).passwordFailures());}
            long blockedUntil=stored(admin.user.uid()).blockedUntil();assertEquals(clock.millis()+120_000,blockedUntil);
            var trace=events();assertEquals(List.of(3004,3005,3006,3007,3002,2001),trace.subList(trace.indexOf(3004),trace.indexOf(3004)+6));
            assertEquals(AuthenticationService.Identification.BLOCKED,auth.identify(admin.user.email()));
            try(var reopened=new AuthenticationService(new Database(db.path()),clock)){
                reopened.start();clock.set(blockedUntil-1);assertEquals(AuthenticationService.Identification.BLOCKED,reopened.identify(admin.user.email()));
                clock.set(blockedUntil);assertEquals(AuthenticationService.Identification.KNOWN,reopened.identify(admin.user.email()));
                assertEquals(0,stored(admin.user.uid()).passwordFailures());assertEquals(0,stored(admin.user.uid()).blockedUntil());
            }
        }
    }
    @Test void tokenThreeFailuresBlockAndOtherAccountStillAuthenticates() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            tokenStage(auth,admin);
            for(int i=1;i<=3;i++){assertEquals(i<3?AuthenticationService.TokenResult.REJECTED:AuthenticationService.TokenResult.BLOCKED,auth.token("invalid"));assertEquals(i,stored(admin.user.uid()).tokenFailures());}
            var trace=events();assertEquals(List.of(4004,4005,4006,4007,4002,2001),trace.subList(trace.indexOf(4004),trace.indexOf(4004)+6));
            assertEquals(AuthenticationService.Identification.BLOCKED,auth.identify(admin.user.email()));
            AuthenticationTestSupport.login(auth,user,clock);assertEquals(1,stored(user.user.uid()).accessCount());assertEquals(0,stored(admin.user.uid()).accessCount());
        }
    }
    @Test void stageSuccessResetsItsOwnFailureCountAndRestartDoesNotEraseFailures() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            passwordStage(auth,admin);auth.password(admin.wrongPairs());auth.restart();passwordStage(auth,admin);
            assertEquals(1,stored(admin.user.uid()).passwordFailures());auth.password(admin.pairs());assertEquals(0,stored(admin.user.uid()).passwordFailures());
            auth.token("invalid");auth.restart();tokenStage(auth,admin);assertEquals(1,stored(admin.user.uid()).tokenFailures());
            auth.token(admin.code(clock.millis()));assertEquals(0,stored(admin.user.uid()).tokenFailures());
        }
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void currentAndNeighborTokenIntervalsAccepted(int delta) throws Exception {
        try(var auth=new AuthenticationService(db,clock)){tokenStage(auth,admin);assertEquals(AuthenticationService.TokenResult.ACCEPTED,auth.token(admin.code(clock.millis()+delta*30_000L)));}
    }
    @Test void distantMalformedAndLeadingZeroTokens() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            tokenStage(auth,admin);assertEquals(AuthenticationService.TokenResult.REJECTED,auth.token(admin.code(clock.millis()-60_000)));
            assertEquals(AuthenticationService.TokenResult.REJECTED,auth.token(admin.code(clock.millis()+60_000)));
            long time=clock.millis();while(!admin.code(time).startsWith("0")){time+=30_000;}
            clock.set(time);assertEquals(AuthenticationService.TokenResult.ACCEPTED,auth.token(admin.code(time)));
        }
    }
    @Test void adminRegistrationAuthorizationOnlyAfterMfaAndRevokedOnLogout() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            auth.start();assertThrows(AuthenticationException.class,()->auth.registrationAuthorization(null));
            var session=AuthenticationTestSupport.login(auth,admin,clock);var authority=auth.registrationAuthorization(session);
            assertEquals(admin.user.uid(),registrations.showForm(authority,false).actor().uid());
            auth.logout(session);assertThrows(RegistrationException.class,()->registrations.showForm(authority,false));assertThrows(AuthenticationException.class,()->auth.registrationAuthorization(session));
        }
    }
    @Test void ordinaryAndForeignSessionsCannotRegister() throws Exception {
        try(var auth=new AuthenticationService(db,clock);var other=new AuthenticationService(db,clock)){
            var session=AuthenticationTestSupport.login(auth,user,clock);
            assertThrows(AuthenticationException.class,()->auth.registrationAuthorization(session));assertThrows(AuthenticationException.class,()->auth.menu(session,AuthenticationService.Menu.REGISTER));
            assertThrows(AuthenticationException.class,()->other.mainMenu(session));assertThrows(RegistrationException.class,()->registrations.showForm(null,false));
        }
    }
    @Test void exactSuccessfulAuthenticationAndLogoutAuditSequence() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            var session=AuthenticationTestSupport.login(auth,admin,clock);auth.mainMenu(session);auth.logout(session);
            assertEquals(List.of(2001,2003,2002,3001,3003,3002,4001,4003,4002,1003,5001,8002,1004,2001),events());
        }
    }
    @Test void concurrentFlowsCannotExceedFailureLimitOrBypassBlock() throws Exception {
        List<AuthenticationService> flows=new ArrayList<>();var pool=Executors.newFixedThreadPool(5);
        try{
            for(int i=0;i<5;i++){var auth=new AuthenticationService(new Database(db.path()),clock);passwordStage(auth,admin);flows.add(auth);}
            List<Callable<AuthenticationService.PasswordResult>> tasks=new ArrayList<>();for(var auth:flows){tasks.add(()->auth.password(admin.wrongPairs()));}
            for(var future:pool.invokeAll(tasks)){assertNotEquals(AuthenticationService.PasswordResult.ACCEPTED,future.get());}
            assertEquals(3,stored(admin.user.uid()).passwordFailures());assertTrue(stored(admin.user.uid()).blockedUntil()>clock.millis());
            for(var auth:flows){assertThrows(AuthenticationException.class,auth::session);}
        }finally{pool.shutdownNow();for(var auth:flows){auth.close();}}
    }
    @Test void failedAuditRollsBackTokenSuccessAndRevokesAllSessionState() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            tokenStage(auth,admin);try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_session BEFORE INSERT ON Registros WHEN NEW.MID=1003 BEGIN SELECT RAISE(ABORT,'injected'); END");}
            assertThrows(SQLException.class,()->auth.token(admin.code(clock.millis())));assertEquals(0,stored(admin.user.uid()).accessCount());assertEquals(AuthenticationService.Stage.CLOSED,auth.stage());assertThrows(AuthenticationException.class,auth::session);
        }
    }
    @Test void duplicateSubmissionAndRestartAreRejectedAndClosedFlowDiscardsRunningResult() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            passwordStage(auth,admin);var pool=Executors.newSingleThreadExecutor();
            try{
                List<DigitPair> full=new ArrayList<>();for(int i=0;i<8;i++){char first=(char)('0'+2*(i%5));full.add(new DigitPair(first,(char)(first+1)));}
                var running=pool.submit(()->auth.password(full));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!auth.passwordVerificationInProgress()&&System.nanoTime()<deadline){Thread.sleep(5);}
                assertTrue(auth.passwordVerificationInProgress());
                assertEquals(AuthenticationException.Reason.BUSY,assertThrows(AuthenticationException.class,()->auth.password(admin.pairs())).reason());
                assertEquals(AuthenticationException.Reason.BUSY,assertThrows(AuthenticationException.class,auth::restart).reason());
                auth.close();assertEquals(AuthenticationService.PasswordResult.STALE,running.get(10,TimeUnit.SECONDS));
                assertEquals(AuthenticationService.Stage.CLOSED,auth.stage());assertEquals(0,stored(admin.user.uid()).passwordFailures());assertEquals(0,stored(admin.user.uid()).accessCount());
            }finally{pool.shutdownNow();}
        }
    }
    @Test void passwordFailureAuditErrorRollsBackCounterAndLogoutErrorRevokesCapability() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            passwordStage(auth,admin);try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_password BEFORE INSERT ON Registros WHEN NEW.MID=3004 BEGIN SELECT RAISE(ABORT,'injected'); END");}
            assertThrows(SQLException.class,()->auth.password(admin.wrongPairs()));assertEquals(0,stored(admin.user.uid()).passwordFailures());assertEquals(AuthenticationService.Stage.CLOSED,auth.stage());
        }
        try(var auth=new AuthenticationService(db,clock)){
            var session=AuthenticationTestSupport.login(auth,admin,clock);var capability=auth.registrationAuthorization(session);
            try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_logout BEFORE INSERT ON Registros WHEN NEW.MID=1004 BEGIN SELECT RAISE(ABORT,'injected'); END");}
            assertThrows(SQLException.class,()->auth.logout(session));assertFalse(session.active());assertThrows(RegistrationException.class,()->registrations.showForm(capability,false));
        }
    }
    @Test void exitNavigationUsesRequiredEventsAndDoesNotIncrementAccessCounter() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){
            var session=AuthenticationTestSupport.login(auth,admin,clock);auth.mainMenu(session);auth.menu(session,AuthenticationService.Menu.CONSULT);
            auth.menu(session,AuthenticationService.Menu.EXIT);auth.exitScreen(session);auth.backFromExit(session);auth.mainMenu(session);
            auth.exitSystem(session);assertFalse(session.active());assertEquals(AuthenticationService.Stage.CLOSED,auth.stage());assertEquals(1,stored(admin.user.uid()).accessCount());
            assertEquals(List.of(2001,2003,2002,3001,3003,3002,4001,4003,4002,1003,5001,5003,5004,8001,8004,5001,8003,1004),events());
        }
    }
    @Test void tokenLockoutPersistsAcrossReopeningAndExpiresAtExactBoundary() throws Exception {
        try(var auth=new AuthenticationService(db,clock)){tokenStage(auth,admin);for(int i=0;i<3;i++){auth.token("invalid");}}
        long until=stored(admin.user.uid()).blockedUntil();
        try(var auth=new AuthenticationService(new Database(db.path()),clock)){
            auth.start();clock.set(until-1);assertEquals(AuthenticationService.Identification.BLOCKED,auth.identify(admin.user.email()));
            clock.set(until);assertEquals(AuthenticationService.Identification.KNOWN,auth.identify(admin.user.email()));
            assertEquals(0,stored(admin.user.uid()).tokenFailures());assertEquals(0,stored(admin.user.uid()).passwordFailures());
        }
    }
}
