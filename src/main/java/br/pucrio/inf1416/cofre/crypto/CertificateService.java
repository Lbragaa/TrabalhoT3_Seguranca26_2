package br.pucrio.inf1416.cofre.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

public final class CertificateService {
    public record Identity(String name, String email) { }

    public X509Certificate read(Path file) throws IOException, GeneralSecurityException {
        return parse(Files.readAllBytes(file));
    }

    public X509Certificate parse(byte[] input) throws GeneralSecurityException {
        byte[] der = PemSupport.decode(input, "CERTIFICATE");
        var factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
    }

    /** Uses subject CN and subject email OID, not a guessed login or SAN fallback. */
    public Identity identity(X509Certificate certificate) throws GeneralSecurityException {
        String name = null;
        String email = null;
        try {
            for (Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
                var attributes = rdn.toAttributes().getAll();
                while (attributes.hasMore()) {
                    var attribute = attributes.next();
                    String type = attribute.getID();
                    if (type.equalsIgnoreCase("CN")) { name = attribute.get().toString(); }
                    if (type.equalsIgnoreCase("EMAILADDRESS") || type.equals("1.2.840.113549.1.9.1")) {
                        Object value = attribute.get();
                        email = value instanceof byte[] bytes ? decodeSubjectEmail(bytes) : value.toString();
                    }
                }
            }
        } catch (NamingException e) {
            throw new GeneralSecurityException("Invalid certificate subject.");
        }
        if (name == null || name.isBlank() || email == null
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new GeneralSecurityException("Certificate subject requires a common name and valid email.");
        }
        return new Identity(name, email);
    }

    private String decodeSubjectEmail(byte[] encoded) throws GeneralSecurityException {
        // X500Principal exposes unknown OIDs as DER; emailAddress is an IA5String.
        try {
            var value = org.bouncycastle.asn1.ASN1IA5String.getInstance(encoded);
            return value.getString();
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid subject email encoding.");
        }
    }

    public String toPem(X509Certificate certificate) throws GeneralSecurityException {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }
}
