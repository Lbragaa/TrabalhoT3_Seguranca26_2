package br.pucrio.inf1416.cofre.crypto;

import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit -Pfixtures gate. No credential values or decrypted content in assertion messages. */
@Tag("fixtures")
class CryptoCompatibilityTest {
    private static final Path ROOT = Path.of(System.getProperty("project.root"));
    private static final Path KEYS = ROOT.resolve("Pacote-T3/Keys");
    private static final Path FILES = ROOT.resolve("Pacote-T3/Files");
    private static final Map<String, PrivateKey> PRIVATE = new HashMap<>();
    private static final Map<String, X509Certificate> CERTIFICATES = new HashMap<>();
    private static final Map<String, String> EMAIL_TO_ID = new HashMap<>();
    private record Entry(String code, String owner, String group) { }
    private record Share(String code, int id, String recipient) { }

    @BeforeAll static void restoreActualKeys() throws Exception {
        checkOriginals();
        Path secrets = Path.of(System.getProperty("fixture.secrets", ROOT.resolve(".local/fixture-secrets.properties").toString()));
        if (!Files.isRegularFile(secrets)) {
            throw new IllegalStateException("Fixture credentials missing: provide ignored .local/fixture-secrets.properties.");
        }
        Properties properties = new Properties();
        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(secrets, StandardCharsets.UTF_8))) {
            int first = reader.read();
            if (first != -1 && first != 0xfeff) { reader.unread(first); }
            properties.load(reader);
        }
        try {
            for (String id : List.of("admin", "user01", "user02")) {
                String value = properties.getProperty(id);
                if (value == null || value.isEmpty()) { throw new IllegalStateException("Missing local fixture credential for " + id); }
                char[] phrase = value.toCharArray();
                try {
                    X509Certificate cert = new CertificateService().read(KEYS.resolve(id + "-x509.crt"));
                    CERTIFICATES.put(id, cert);
                    EMAIL_TO_ID.put(new CertificateService().identity(cert).email(), id);
                    PRIVATE.put(id, new PrivateKeyService().readAndValidate(KEYS.resolve(id + "-pkcs8-aes.key"), phrase, cert));
                } catch (GeneralSecurityException e) {
                    throw new GeneralSecurityException("Fixture key restoration failed for " + id + "; no secret data included.");
                } finally { Arrays.fill(phrase, '\0'); }
            }
        } finally { properties.clear(); }
    }

    @AfterAll static void cleanupAndCheckOriginals() throws Exception {
        PRIVATE.clear(); CERTIFICATES.clear(); EMAIL_TO_ID.clear();
        checkOriginals();
    }

    private static void checkOriginals() throws Exception {
        for (String line : Files.readAllLines(ROOT.resolve("src/test/resources/fixtures.sha256"))) {
            String[] parts = line.split("  ", 2);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(ROOT.resolve(parts[1]))));
            assertEquals(parts[0], hash, "Original fixture checksum changed: " + parts[1]);
        }
    }

    private static byte[] decrypt(String code, String envelope, String recipient, String signer) throws Exception {
        return new ProtectedFileService().decryptAndVerify(FILES.resolve(code + ".enc"), FILES.resolve(envelope),
                FILES.resolve(code + ".asd"), PRIVATE.get(recipient), CERTIFICATES.get(signer).getPublicKey());
    }

    // Catalog interpretation here is a fixture-test oracle, not the Phase 4 production parser.
    private static List<Entry> index() throws Exception {
        byte[] bytes = decrypt("index", "index.env", "admin", "admin");
        try {
            List<Entry> entries = new ArrayList<>();
            for (String line : new String(bytes, StandardCharsets.US_ASCII).lines().toList()) {
                if (line.isBlank()) { continue; }
                String[] fields = line.trim().split("\\s+");
                assertEquals(4, fields.length, "Verified index record shape");
                assertTrue(EMAIL_TO_ID.containsKey(fields[2]), "Owner certificate must be supplied");
                entries.add(new Entry(fields[0], fields[2], fields[3]));
            }
            return entries;
        } finally { Arrays.fill(bytes, (byte) 0); }
    }

    private static List<Share> shares() throws Exception {
        byte[] bytes = decrypt("share", "share.env", "admin", "admin");
        try {
            List<Share> shares = new ArrayList<>();
            for (String line : new String(bytes, StandardCharsets.US_ASCII).lines().toList()) {
                if (line.isBlank()) { continue; }
                String[] fields = line.trim().split("\\s+");
                assertEquals(3, fields.length, "Verified share record shape");
                int id = Integer.parseInt(fields[1]);
                assertTrue(id > 0);
                assertTrue(EMAIL_TO_ID.containsKey(fields[2]), "Recipient certificate must be supplied");
                shares.add(new Share(fields[0], id, fields[2]));
            }
            return shares;
        } finally { Arrays.fill(bytes, (byte) 0); }
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "user01", "user02"})
    void actualPrivateKeyMatchesCertificate(String id) throws Exception {
        new SignatureService().validateKeyPair(PRIVATE.get(id), CERTIFICATES.get(id).getPublicKey(), 10_240);
        assertEquals(id + "@inf1416.puc-rio.br", new CertificateService().identity(CERTIFICATES.get(id)).email());
    }

    @ParameterizedTest @ValueSource(strings = {"index", "share"})
    void actualCatalogDecryptsAndVerifies(String catalog) throws Exception {
        byte[] plain = decrypt(catalog, catalog + ".env", "admin", "admin");
        try { assertTrue(plain.length > 0); } finally { Arrays.fill(plain, (byte) 0); }
    }

    @ParameterizedTest @ValueSource(strings = {"XXYYZZ00", "XXYYZZ11", "XXYYZZ22"})
    void actualFileDecryptsWithItsVerifiedOwner(String code) throws Exception {
        Entry entry = index().stream().filter(e -> e.code().equals(code)).findFirst().orElseThrow();
        String id = EMAIL_TO_ID.get(entry.owner());
        byte[] plain = decrypt(code, code + ".env", id, id);
        try { assertTrue(plain.length > 0); } finally { Arrays.fill(plain, (byte) 0); }
    }

    @Test void actualSharingEnvelopeMatchesOwnerContentAndRecordsVerifiedRelationships() throws Exception {
        List<Entry> entries = index();
        List<Share> shared = shares();
        assertEquals(3, entries.size());
        assertEquals(1, shared.size());
        Share share = shared.get(0);
        // Filename assertion is made only after obtaining the actual share record.
        assertEquals("XXYYZZ22", share.code());
        assertEquals(1, share.id());
        Entry owner = entries.stream().filter(e -> e.code().equals(share.code())).findFirst().orElseThrow();
        String ownerId = EMAIL_TO_ID.get(owner.owner());
        String recipientId = EMAIL_TO_ID.get(share.recipient());
        byte[] owned = decrypt(owner.code(), owner.code() + ".env", ownerId, ownerId);
        byte[] received = decrypt(owner.code(), owner.code() + "." + share.id() + ".shr", recipientId, ownerId);
        try { assertTrue(java.security.MessageDigest.isEqual(owned, received), "Verified owner/recipient contents differ"); }
        finally { Arrays.fill(owned, (byte) 0); Arrays.fill(received, (byte) 0); }
        StringBuilder report = new StringBuilder("code\towner\tgroup\trecipient\n");
        for (Entry entry : entries) {
            report.append(entry.code()).append('\t').append(entry.owner()).append('\t').append(entry.group()).append('\t');
            if (entry.code().equals(share.code())) { report.append(share.recipient()); }
            report.append('\n');
        }
        Files.writeString(ROOT.resolve("target/fixture-relationships.tsv"), report, StandardCharsets.UTF_8);
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "user01", "user02"})
    void wrongPhraseRejectsActualKey(String id) throws Exception {
        char[] wrong = java.util.UUID.randomUUID().toString().toCharArray();
        try { assertThrows(GeneralSecurityException.class, () -> new PrivateKeyService().read(KEYS.resolve(id + "-pkcs8-aes.key"), wrong)); }
        finally { Arrays.fill(wrong, '\0'); }
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "user01", "user02"})
    void corruptedPrivateKeyCopyIsRejected(String id, @TempDir Path temp) throws Exception {
        // Truncation guarantees structurally invalid AES input without touching the original.
        byte[] bytes = Files.readAllBytes(KEYS.resolve(id + "-pkcs8-aes.key"));
        Path copy = temp.resolve("corrupted.key");
        Files.write(copy, Arrays.copyOf(bytes, bytes.length - 1));
        char[] random = java.util.UUID.randomUUID().toString().toCharArray();
        try { assertThrows(GeneralSecurityException.class, () -> new PrivateKeyService().read(copy, random)); }
        finally { Arrays.fill(random, '\0'); }
    }

    @Test void mismatchedCertificateAndWrongEnvelopeKeyAreRejected() {
        assertThrows(GeneralSecurityException.class, () -> new SignatureService().validateKeyPair(
                PRIVATE.get("admin"), CERTIFICATES.get("user01").getPublicKey(), 10_240));
        assertThrows(GeneralSecurityException.class, () -> decrypt("index", "index.env", "user01", "admin"));
        assertThrows(GeneralSecurityException.class, () -> decrypt("index", "index.env", "admin", "user01"));
    }

    @ParameterizedTest @ValueSource(strings = {"asd", "enc", "env"})
    void corruptedTemporaryCopyNeverReturnsPlaintext(String extension, @TempDir Path temp) throws Exception {
        for (String suffix : List.of("enc", "env", "asd")) {
            Files.copy(FILES.resolve("index." + suffix), temp.resolve("index." + suffix));
        }
        Path changed = temp.resolve("index." + extension);
        byte[] bytes = Files.readAllBytes(changed);
        bytes[0] ^= 1;
        Files.write(changed, bytes);
        assertThrows(GeneralSecurityException.class, () -> new ProtectedFileService().decryptAndVerify(
                temp.resolve("index.enc"), temp.resolve("index.env"), temp.resolve("index.asd"),
                PRIVATE.get("admin"), CERTIFICATES.get("admin").getPublicKey()));
        try (var files = Files.list(temp)) { assertEquals(3, files.count(), "No decrypted output may be created"); }
    }

    @Test void suppliedPublicBcryptExampleVerifiesWhenAvailable() throws Exception {
        Path example = ROOT.resolve(".local/assignment-bcrypt-example.properties");
        assertTrue(Files.isRegularFile(example), "Extract public BCrypt example from assignment into ignored local test data");
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(example, StandardCharsets.UTF_8)) { properties.load(reader); }
        char[] sample = properties.getProperty("password").toCharArray();
        try { assertTrue(new PasswordHasher().verify(properties.getProperty("hash"), sample)); }
        finally { Arrays.fill(sample, '\0'); properties.clear(); }
    }
}
