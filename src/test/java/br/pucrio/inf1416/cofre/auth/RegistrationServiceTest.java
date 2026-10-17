package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.app.StartupService;
import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationServiceTest {
    @TempDir static Path fixtureDirectory;
    @TempDir Path temp;
    static RegistrationTestSupport.Material admin, user, other;
    Database db;
    RegistrationService service;
    @BeforeAll static void fixtures() throws Exception {
        admin = RegistrationTestSupport.synthetic(fixtureDirectory, "admin");
        user = RegistrationTestSupport.synthetic(fixtureDirectory, "user");
        other = RegistrationTestSupport.synthetic(fixtureDirectory, "other");
    }
    @AfterAll static void clear() { admin.close(); user.close(); other.close(); }
    @BeforeEach void database() throws Exception { db = new Database(temp.resolve("cofre.db")); service = new RegistrationService(db); }
    private long count(String table) throws Exception {
        try (var c = db.open(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM " + table)) { r.next(); return r.getLong(1); }
    }
    private RegistrationAuthorization bootstrap() throws Exception {
        try (var result = RegistrationTestSupport.register(service, null, true, admin, Group.ADMINISTRATOR)) { return new RegistrationAuthorization(db, result.user().uid()); }
    }
    @Test void bootstrapRegistrationPersistsProtectedDataAndZeroCounters() throws Exception {
        char[] password = RegistrationTestSupport.password();
        try (var request = admin.request(Group.ADMINISTRATOR, password); var pending = service.prepare(null, true, request)) {
            assertEquals(0, count("Usuarios")); assertEquals(0, count("Chaveiro"));
            assertThrows(IllegalStateException.class, pending::enrollmentSecret);
            service.confirmCertificate(pending);
            String secret = pending.enrollmentSecret();
            try (var result = service.commit(pending); var c = db.open(); var s = c.prepareStatement("SELECT * FROM Usuarios WHERE UID=?")) {
                assertTrue(result.user().bootstrap()); assertEquals(Group.ADMINISTRATOR, result.user().group());
                s.setLong(1, result.user().uid());
                try (var r = s.executeQuery()) {
                    assertTrue(r.next()); assertTrue(r.getString("password_hash").startsWith("$2y$08$"));
                    assertTrue(new PasswordHasher().verify(r.getString("password_hash"), password));
                    assertTrue(secret.equals(new TotpSecretService().decrypt(r.getBytes("token_encrypted"), password)), "Stored token round trip failed");
                    for (String field : new String[]{"password_failures","token_failures","blocked_until","access_count","consultation_count"}) { assertEquals(0, r.getLong(field)); }
                }
                var key = new KeyRepository().find(c, result.user().kid());
                assertTrue(Arrays.equals(Files.readAllBytes(admin.key), key.encrypted()));
                assertTrue(key.certificatePem().startsWith("-----BEGIN CERTIFICATE-----"));
                assertEquals(result.user().uid(), key.uid());
            }
            assertThrows(RegistrationException.class, () -> service.commit(pending));
        } finally { Arrays.fill(password, '\0'); }
        var reopened = new Database(db.path());
        try (var c = reopened.open()) { assertEquals(1, new UserRepository().count(c)); }
    }
    @Test void authorizedUserRegistrationAndDuplicateRejection() throws Exception {
        try (var authorization = bootstrap(); var result = RegistrationTestSupport.register(service, authorization, false, user, Group.USER)) {
            assertFalse(result.user().bootstrap()); assertEquals(Group.USER, result.user().group());
            var failure = assertThrows(RegistrationException.class, () -> RegistrationTestSupport.register(service, authorization, false, user, Group.USER));
            assertEquals(RegistrationException.Reason.DUPLICATE, failure.reason());
            assertEquals(2, count("Usuarios")); assertEquals(2, count("Chaveiro"));
        }
    }
    @Test void unauthorizedMissingRevokedOrdinaryAndForeignDatabaseCapabilitiesFail() throws Exception {
        try (var authorization = bootstrap(); var result = RegistrationTestSupport.register(service, authorization, false, user, Group.USER)) {
            assertThrows(RegistrationException.class, () -> service.showForm(null, false));
            try (var ordinary = new RegistrationAuthorization(db, result.user().uid())) { assertThrows(RegistrationException.class, () -> service.showForm(ordinary, false)); }
            var foreign = new Database(temp.resolve("other.db"));
            assertThrows(RegistrationException.class, () -> new RegistrationService(foreign).showForm(authorization, false));
            authorization.close(); assertThrows(RegistrationException.class, () -> service.showForm(authorization, false));
            assertThrows(RegistrationException.class, () -> service.showForm(null, true));
        }
    }
    @Test void privilegesAreRecheckedAtCommitAndCancellationDoesNotInsert() throws Exception {
        char[] password = RegistrationTestSupport.password();
        try (var authorization = bootstrap(); var request = user.request(Group.USER, password); var pending = service.prepare(authorization, false, request)) {
            service.confirmCertificate(pending); authorization.close();
            assertThrows(RegistrationException.class, () -> service.commit(pending));
            assertEquals(1, count("Usuarios"));
        } finally { Arrays.fill(password, '\0'); }
        char[] fresh = RegistrationTestSupport.password();
        try (var authority = new RegistrationAuthorization(db, 1); var request = user.request(Group.USER, fresh); var pending = service.prepare(authority, false, request)) {
            service.cancel(pending); assertThrows(RegistrationException.class, () -> service.commit(pending));
            assertEquals(1, count("Usuarios"));
        } finally { Arrays.fill(fresh, '\0'); }
    }
    @Test void auditCommitFailureRollsBackUserKeyAndConfirmationTogether() throws Exception {
        try (var c = db.open(); var s = c.createStatement()) { s.execute("CREATE TRIGGER fail_confirmation BEFORE INSERT ON Registros WHEN NEW.MID=6008 BEGIN SELECT RAISE(ABORT,'injected'); END"); }
        assertThrows(SQLException.class, () -> RegistrationTestSupport.register(service, null, true, admin, Group.ADMINISTRATOR));
        assertEquals(0, count("Usuarios")); assertEquals(0, count("Chaveiro"));
        try (var c = db.open(); var s = c.prepareStatement("SELECT count(*) FROM Registros WHERE MID=6008"); var r = s.executeQuery()) { r.next(); assertEquals(0, r.getInt(1)); }
    }
    @Test void lateDuplicateAndSecondBootstrapCannotCreatePartialRecords() throws Exception {
        char[] password = RegistrationTestSupport.password();
        try (var request = admin.request(Group.ADMINISTRATOR, password); var first = service.prepare(null,true,request); var second = service.prepare(null,true,request)) {
            service.confirmCertificate(first); service.confirmCertificate(second);
            try (var result = service.commit(first)) { assertThrows(RegistrationException.class, () -> service.commit(second)); }
        } finally { Arrays.fill(password,'\0'); }
        try (var authority = new RegistrationAuthorization(db,1)) {
            char[] p = RegistrationTestSupport.password();
            try (var request = user.request(Group.USER,p); var first = service.prepare(authority,false,request); var second=service.prepare(authority,false,request)) {
                service.confirmCertificate(first); service.confirmCertificate(second);
                try (var result=service.commit(first)) { assertEquals(RegistrationException.Reason.DUPLICATE, assertThrows(RegistrationException.class,()->service.commit(second)).reason()); }
            } finally { Arrays.fill(p,'\0'); }
        }
        assertEquals(2,count("Usuarios")); assertEquals(2,count("Chaveiro"));
    }
    @Test void registrationRejectsWrongPhraseMismatchedKeyAndMissingPathsWithCorrectEvents() throws Exception {
        char[] p=RegistrationTestSupport.password(); char[] phrase=admin.phrase(); char[] wrong=java.util.UUID.randomUUID().toString().toCharArray();
        try {
            try(var request=new RegistrationRequest(admin.certificate.toString(),admin.key.toString(),Group.ADMINISTRATOR,wrong,p,p)) {
                assertEquals(RegistrationException.Reason.KEY_RESTORE,assertThrows(RegistrationException.class,()->service.prepare(null,true,request)).reason());
            }
            try(var request=new RegistrationRequest(user.certificate.toString(),admin.key.toString(),Group.ADMINISTRATOR,phrase,p,p)) {
                assertEquals(RegistrationException.Reason.KEY_MISMATCH,assertThrows(RegistrationException.class,()->service.prepare(null,true,request)).reason());
            }
            try(var request=new RegistrationRequest("missing",admin.key.toString(),Group.ADMINISTRATOR,phrase,p,p)) { assertThrows(RegistrationException.class,()->service.prepare(null,true,request)); }
            try(var request=new RegistrationRequest(admin.certificate.toString(),"missing",Group.ADMINISTRATOR,phrase,p,p)) { assertThrows(RegistrationException.class,()->service.prepare(null,true,request)); }
        } finally { Arrays.fill(p,'\0'); Arrays.fill(phrase,'\0'); Arrays.fill(wrong,'\0'); }
        assertEquals(0,count("Usuarios"));
        try(var c=db.open();var s=c.prepareStatement("SELECT MID FROM Registros ORDER BY RID");var r=s.executeQuery()) {
            var events=new ArrayList<Integer>();while(r.next()){events.add(r.getInt(1));}
            assertEquals(java.util.List.of(6002,6006,6002,6007,6002,6004,6002,6005),events);
        }
    }
    @ParameterizedTest @ValueSource(strings={"", "1234567", "12345678901", "112345678", "１２３４５６７８", "12345x78"})
    void invalidPersonalPasswordsDoNotCreateAccounts(String invalid) throws Exception {
        char[] p=invalid.toCharArray();
        try(var request=admin.request(Group.ADMINISTRATOR,p)) { assertEquals(RegistrationException.Reason.PASSWORD,assertThrows(RegistrationException.class,()->service.prepare(null,true,request)).reason()); }
        finally { Arrays.fill(p,'\0'); }
        assertEquals(0,count("Usuarios")); assertEquals(0,count("Chaveiro"));
    }
    @Test void confirmationMismatchAndBootstrapGroupRejected() throws Exception {
        char[] p=RegistrationTestSupport.password();char[] confirm=p.clone();confirm[0]=(char)('0'+(confirm[0]-'0'+1)%10);char[] phrase=admin.phrase();
        try(var request=new RegistrationRequest(admin.certificate.toString(),admin.key.toString(),Group.ADMINISTRATOR,phrase,p,confirm)) {
            assertThrows(RegistrationException.class,()->service.prepare(null,true,request));
        } finally { Arrays.fill(phrase,'\0');Arrays.fill(confirm,'\0'); }
        try(var request=admin.request(Group.USER,p)) {assertThrows(RegistrationException.class,()->service.prepare(null,true,request));}
        finally {Arrays.fill(p,'\0');}
    }
    @Test void startupFirstRunRestartCorrectWrongAndContextCleanup() throws Exception {
        var startup=new StartupService(db);assertEquals(StartupService.State.BOOTSTRAP_REQUIRED,startup.begin());
        try(var result=RegistrationTestSupport.register(service,null,true,admin,Group.ADMINISTRATOR)) {startup.acceptBootstrap(result);}
        assertTrue(startup.hasAdministratorMaterial());startup.shutdown();assertFalse(startup.hasAdministratorMaterial());startup.shutdown();
        var reopened=new Database(db.path());var next=new StartupService(reopened);assertEquals(StartupService.State.UNLOCK_REQUIRED,next.begin());
        char[] phrase=admin.phrase();try{next.unlock(phrase);}finally{Arrays.fill(phrase,'\0');}
        assertEquals(StartupService.State.READY,next.state());next.shutdown();
        var failed=new StartupService(reopened);failed.begin();char[] wrong=java.util.UUID.randomUUID().toString().toCharArray();
        try{assertThrows(GeneralSecurityException.class,()->failed.unlock(wrong));}finally{Arrays.fill(wrong,'\0');}
        assertEquals(StartupService.State.TERMINATED,failed.state());assertFalse(failed.hasAdministratorMaterial());
        assertThrows(IllegalStateException.class,()->failed.unlock(new char[0]));
    }
    @Test void deferredForeignKeysRejectOrphansCrossLinksAndBootstrapDemotion() throws Exception {
        try(var authority=bootstrap();var result=RegistrationTestSupport.register(service,authority,false,user,Group.USER)) {
            assertThrows(SQLException.class,()->db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE Usuarios SET KID=99 WHERE UID=1");}return null;}));
            assertThrows(SQLException.class,()->db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE Usuarios SET GID=2 WHERE bootstrap=1");}return null;}));
            assertThrows(SQLException.class,()->db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("DELETE FROM Chaveiro WHERE UID=1");}return null;}));
            assertEquals(2,count("Usuarios"));assertEquals(2,count("Chaveiro"));
        }
    }
    @Test void noPlaintextCredentialsOrTokenSecretAreStoredInDatabaseFile() throws Exception {
        char[] password=RegistrationTestSupport.password();char[] phrase=admin.phrase();String secret;
        try(var request=admin.request(Group.ADMINISTRATOR,password);var pending=service.prepare(null,true,request)){
            service.confirmCertificate(pending);secret=pending.enrollmentSecret();try(var result=service.commit(pending)){}
            String raw=new String(Files.readAllBytes(db.path()),java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains(new String(password)),"Plaintext password in database");
            assertFalse(raw.contains(new String(phrase)),"Plaintext passphrase in database");
            assertFalse(raw.contains(secret),"Plaintext token secret in database");
            assertFalse(raw.contains("-----BEGIN PRIVATE KEY-----"),"Unencrypted private key in database");
        }finally{Arrays.fill(password,'\0');Arrays.fill(phrase,'\0');}
    }
    @Test void startupCertificateMismatchTerminatesAndShutdownAuditFailureClearsMaterial() throws Exception {
        try(var authority=bootstrap()){}
        var original=new CertificateService().read(admin.certificate);
        String mismatch=new CertificateService().toPem(new CertificateService().read(other.certificate));
        try(var c=db.open();var s=c.prepareStatement("UPDATE Chaveiro SET certificate_pem=? WHERE UID=1")){s.setString(1,mismatch);s.executeUpdate();}
        var startup=new StartupService(db);startup.begin();char[] phrase=admin.phrase();
        try{assertThrows(GeneralSecurityException.class,()->startup.unlock(phrase));}finally{Arrays.fill(phrase,'\0');}
        assertEquals(StartupService.State.TERMINATED,startup.state());
        try(var c=db.open();var s=c.prepareStatement("UPDATE Chaveiro SET certificate_pem=? WHERE UID=1")){s.setString(1,new CertificateService().toPem(original));s.executeUpdate();}
        var next=new StartupService(db);next.begin();char[] valid=admin.phrase();try{next.unlock(valid);}finally{Arrays.fill(valid,'\0');}
        try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_shutdown BEFORE INSERT ON Registros WHEN NEW.MID=1002 BEGIN SELECT RAISE(ABORT,'injected'); END");}
        assertThrows(SQLException.class,next::shutdown);assertFalse(next.hasAdministratorMaterial());assertEquals(StartupService.State.TERMINATED,next.state());
    }
    @Test void startupAuditFailureStopsInitializationAndRegistrationKeyFailureRollsBack() throws Exception {
        try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_start BEFORE INSERT ON Registros WHEN NEW.MID=1001 BEGIN SELECT RAISE(ABORT,'injected'); END");}
        var startup=new StartupService(db);assertThrows(SQLException.class,startup::begin);assertEquals(StartupService.State.TERMINATED,startup.state());
        try(var c=db.open();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_key BEFORE INSERT ON Chaveiro BEGIN SELECT RAISE(ABORT,'injected'); END");}
        assertThrows(SQLException.class,()->RegistrationTestSupport.register(service,null,true,admin,Group.ADMINISTRATOR));
        assertEquals(0,count("Usuarios"));assertEquals(0,count("Chaveiro"));
    }
    @Test void startupAuditSequenceAndNoRegistrationPrivilegeFromStartupReadiness() throws Exception {
        var startup=new StartupService(db);startup.begin();service.showForm(null,true);
        try(var result=RegistrationTestSupport.register(service,null,true,admin,Group.ADMINISTRATOR)){startup.acceptBootstrap(result);}
        assertThrows(RegistrationException.class,()->service.showForm(null,false));startup.shutdown();
        var reopened=new StartupService(new Database(db.path()));reopened.begin();char[] phrase=admin.phrase();try{reopened.unlock(phrase);}finally{Arrays.fill(phrase,'\0');}reopened.shutdown();
        try(var c=db.open();var s=c.prepareStatement("SELECT MID FROM Registros ORDER BY RID");var r=s.executeQuery()){
            var events=new ArrayList<Integer>();while(r.next()){events.add(r.getInt(1));}
            assertEquals(java.util.List.of(1001,1005,6001,6002,6008,1002,1001,1006,1002),events);
        }
    }
}
