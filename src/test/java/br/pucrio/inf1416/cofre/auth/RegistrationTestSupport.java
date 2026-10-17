package br.pucrio.inf1416.cofre.auth;

import br.pucrio.inf1416.cofre.crypto.*;
import br.pucrio.inf1416.cofre.db.Database;
import br.pucrio.inf1416.cofre.db.Group;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;

/** Test-only setup. No authorization minting entry point is packaged in the application. */
public final class RegistrationTestSupport {
    private RegistrationTestSupport() { }
    public static RegistrationAuthorization authority(Database database, long uid) { return new RegistrationAuthorization(database, uid); }
    public static char[] password() {
        SecureRandom random = new SecureRandom(); char[] result = new char[10];
        for (int i = 0; i < result.length; i++) {
            do { result[i] = (char) ('0' + random.nextInt(10)); } while (i > 0 && result[i] == result[i-1]);
        }
        return result;
    }
    public static final class Material implements AutoCloseable {
        public final Path certificate;
        public final Path key;
        private final char[] phrase;
        public Material(Path certificate, Path key, char[] phrase) {
            this.certificate = certificate; this.key = key; this.phrase = phrase.clone();
        }
        public char[] phrase() { return phrase.clone(); }
        public RegistrationRequest request(Group group, char[] password) {
            return new RegistrationRequest(certificate.toString(), key.toString(), group, phrase, password, password);
        }
        @Override public void close() { Arrays.fill(phrase, '\0'); }
    }
    public static Material synthetic(Path directory, String id) throws Exception {
        Files.createDirectories(directory);
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var algorithm = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);
        var name = new X500Name("C=BR,O=Test,CN=" + id + ",E=" + id + "@example.test");
        var tbs = new V3TBSCertificateGenerator();
        tbs.setSerialNumber(new ASN1Integer(new java.math.BigInteger(64, new SecureRandom()).add(java.math.BigInteger.ONE)));
        tbs.setSignature(algorithm); tbs.setIssuer(name); tbs.setSubject(name);
        tbs.setStartDate(new Time(new Date(0))); tbs.setEndDate(new Time(new Date(4_102_444_800_000L)));
        tbs.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded()));
        var body = tbs.generateTBSCertificate();
        var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update(body.getEncoded());
        byte[] der = new DERSequence(new ASN1Encodable[]{body, algorithm, new DERBitString(signature.sign())}).getEncoded();
        Path certificate = directory.resolve(id + ".crt"); Path key = directory.resolve(id + ".key");
        Files.writeString(certificate, "Certificate dump prefix\n-----BEGIN CERTIFICATE-----\n" + Base64.getEncoder().encodeToString(der)
                + "\n-----END CERTIFICATE-----\n", StandardCharsets.US_ASCII);
        char[] phrase = java.util.UUID.randomUUID().toString().toCharArray();
        byte[] pem = ("-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n").getBytes(StandardCharsets.US_ASCII);
        try {
            Files.write(key, new AesCipher().encrypt(pem, new LegacyKeyDerivation().fromPassword(phrase)));
            return new Material(certificate, key, phrase);
        } finally { Arrays.fill(pem, (byte) 0); Arrays.fill(phrase, '\0'); }
    }
    public static RegistrationService.Result register(RegistrationService service, RegistrationAuthorization authorization,
                                                     boolean bootstrap, Material material, Group group) throws Exception {
        char[] password = password();
        try (var request = material.request(group, password); var pending = service.prepare(authorization, bootstrap, request)) {
            service.confirmCertificate(pending); return service.commit(pending);
        } finally { Arrays.fill(password, '\0'); }
    }
}
