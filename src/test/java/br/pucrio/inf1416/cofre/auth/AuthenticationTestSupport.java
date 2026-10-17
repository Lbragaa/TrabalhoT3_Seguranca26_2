package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.crypto.TotpTestClock;
import br.pucrio.inf1416.cofre.db.Group;
import br.pucrio.inf1416.cofre.db.UserRepository.User;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AuthenticationTestSupport {
    private AuthenticationTestSupport() { }
    public static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(1_700_000_000_000L);
        public void set(long value) { millis.set(value); }
        public void advance(long delta) { millis.addAndGet(delta); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis()); }
        public long millis() { return millis.get(); }
    }
    public static final class Identity implements AutoCloseable {
        public final User user;
        private final String secret;
        private final char[] password;
        Identity(User user, String secret, char[] password) { this.user = user; this.secret = secret; this.password = password.clone(); }
        public List<DigitPair> pairs() { return Collections.nCopies(password.length, new DigitPair(password[0], password[1])); }
        public List<DigitPair> wrongPairs() {
            char first = '0'; while (first == password[0] || first == password[1]) { first++; }
            char second = (char) (first + 1); while (second == password[0] || second == password[1]) { second++; }
            return Collections.nCopies(password.length, new DigitPair(first, second));
        }
        public char[] passwordCopy() { return password.clone(); }
        public String code(long millis) throws Exception { return TotpTestClock.code(secret, millis); }
        @Override public void close() { Arrays.fill(password, '\0'); }
        @Override public String toString() { return "TestIdentity[redacted]"; }
    }
    public static Identity register(RegistrationService registrations, RegistrationAuthorization authority, boolean bootstrap,
            RegistrationTestSupport.Material material, Group group, int length) throws Exception {
        return register(registrations,authority,bootstrap,material,group,length,null);
    }
    public static Identity register(RegistrationService registrations, RegistrationAuthorization authority, boolean bootstrap,
            RegistrationTestSupport.Material material, Group group, int length, br.pucrio.inf1416.cofre.app.StartupService startup) throws Exception {
        SecureRandom random = new SecureRandom(); char first = (char) ('0' + random.nextInt(10)), second;
        do { second = (char) ('0' + random.nextInt(10)); } while (second == first);
        char[] password = new char[length];
        for (int i = 0; i < length; i++) { password[i] = i % 2 == 0 ? first : second; }
        try (var request = material.request(group, password); var pending = registrations.prepare(authority, bootstrap, request)) {
            registrations.confirmCertificate(pending);
            String secret = pending.enrollmentSecret();
            try (var result = registrations.commit(pending)) {
                if(startup!=null){startup.acceptBootstrap(result);}
                return new Identity(result.user(), secret, password);
            }
        } finally { Arrays.fill(password, '\0'); }
    }
    public static SessionContext login(AuthenticationService auth, Identity identity, Clock clock) throws Exception {
        if (auth.stage() == AuthenticationService.Stage.NEW) { auth.start(); }
        if (auth.identify(identity.user.email()) != AuthenticationService.Identification.KNOWN) { throw new IllegalStateException("Test identification failed"); }
        if (auth.password(identity.pairs()) != AuthenticationService.PasswordResult.ACCEPTED) { throw new IllegalStateException("Test password failed"); }
        if (auth.token(identity.code(clock.millis())) != AuthenticationService.TokenResult.ACCEPTED) { throw new IllegalStateException("Test token failed"); }
        return auth.session();
    }
}
