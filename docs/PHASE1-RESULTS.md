# Phase 1 results — 17 October 2026

## Outcome and boundary

**Cryptographic engine and all supplied fixture routes passed.** Phases 0 and 1 are complete;
Phase 2 is not authorized and has not started. There is no application database, registration
screen, authentication GUI, keypad, vault interface, production access policy/catalog parser,
or operational log viewer. Existing executable shells and Phase 0 tests are preserved.

## Implemented API

All classes are in `br.pucrio.inf1416.cofre.crypto`.

| Class | Methods / behavior |
| --- | --- |
| CertificateService | read/parse text-prefixed X.509 PEM, identity from subject CN/email OID, toPem round trip |
| LegacyKeyDerivation | fromSeed and fromPassword; fresh seeded SUN SHA1PRNG, SunJCE AES KeyGenerator, 256 bits; UTF-8 phrases |
| AesCipher | encrypt/decrypt using SunJCE AES/ECB/PKCS5Padding; reject invalid ciphertext lengths |
| PrivateKeyService | read/restore binary AES ciphertext -> PEM -> DER PKCS#8 -> RSA; readAndValidate with 10,240-byte challenge |
| SignatureService | SHA1withRSA sign/verify and random validateKeyPair challenge; mismatch/corruption rejection |
| EnvelopeService | recoverSeed/recoverKey for RSA/ECB/PKCS1Padding envelopes, including .N.shr; raw binary seed preserved |
| ProtectedFileService | Path/byte-array decryptAndVerify; returns plaintext only after owner signature succeeds; creates no output files |
| PasswordHasher | random-salted OpenBSDBCrypt generate 2y/cost 8, exact 60-character format; verify rejects other/malformed formats |
| Base32 | Supplied Brian Wellington algorithm transcribed from A pp.11–14, including standard/hex alphabets and configurable padding/case |
| TOTP | Required constructor and helper structure; HMAC-SHA1, six decimal digits, 30-second intervals, +/-1 window; Date public API, numeric-time test seams, close clears key buffer |
| TotpSecretService | generate random 20-byte Base32 secret; encrypt/decrypt canonical Base32 using prescribed password-derived AES-256 |
| PemSupport | Internal byte-oriented PEM parser; decrypted private-key text is not converted into immutable Strings |

Base32 adaptations are limited to explicit ASCII and stricter malformed-padding/noncanonical
input checks; valid supplied-algorithm behavior, case/whitespace support and public API remain.
No third-party TOTP implementation or new runtime dependency was added. TOTP imports only
Mac, SecretKeySpec and Date, with supplied Base32 support as prescribed.

Secret buffers owned by these services are cleared where practical. Callers own returned
plaintext/seed arrays and must clear them after use. No guaranteed JVM heap erasure is claimed;
JCA keys/specifications and Base32/String APIs retain implementation-managed copies.

## Proven interoperability profile

- Private-key phrase -> UTF-8 bytes -> **fresh** `SecureRandom.getInstance("SHA1PRNG", "SUN")`;
  call setSeed before output -> `KeyGenerator.getInstance("AES", "SunJCE")`, init(256, random).
- Envelopes use `RSA/ECB/PKCS1Padding` (SunJCE). Recovered seed bytes go directly into the same
  derivation; no Base64 conversion, trimming, text conversion or seed hashing is inserted.
- Content and private-key encryption use `AES/ECB/PKCS5Padding` (SunJCE), AES-256.
- Protected-file signatures use `SHA1withRSA` (SunRsaSign), verified over exact plaintext bytes.
- Certificates themselves use SHA256withRSA signatures. The exercised certificate check is
  **private-key/public-key correspondence**, not validation of their issuing CA trust chain.
  No issuer certificate/trust infrastructure was supplied or invented.

No compatibility fallback, alternative key size or format substitution was needed.

## Actual professor-fixture results

| Fixture | Executed result |
| --- | --- |
| admin-pkcs8-aes.key + admin-x509.crt | Restored RSA key; 10,240-byte challenge matches certificate |
| user01-pkcs8-aes.key + user01-x509.crt | Restored RSA key; 10,240-byte challenge matches certificate |
| user02-pkcs8-aes.key + user02-x509.crt | Restored RSA key; 10,240-byte challenge matches certificate |
| index.enc/.env/.asd | Decrypted and SHA1withRSA-verified with administrator keys before metadata interpretation |
| share.enc/.env/.asd | Decrypted and SHA1withRSA-verified with administrator keys before metadata interpretation |
| XXYYZZ00.enc/.env/.asd | Decrypted with verified owner admin; owner signature valid |
| XXYYZZ11.enc/.env/.asd | Decrypted with verified owner user01; owner signature valid |
| XXYYZZ22.enc/.env/.asd | Decrypted with verified owner user02; owner signature valid |
| XXYYZZ22.1.shr | Recipient user01 recovers content identical to owner route; user02 signature valid |
| Assignment BCrypt example | Public A p.3 example verifies with required 2y/08 format |

Verified catalog relationships (identities have suffix `@inf1416.puc-rio.br`):

| Code | Owner | Group in signed index | Explicit sharing |
| --- | --- | --- | --- |
| XXYYZZ00 | admin | administrador | None |
| XXYYZZ11 | user01 | usuario | None |
| XXYYZZ22 | user02 | usuario | Sharing ID 1 to user01 |

Relationships come from authenticated catalogs, not filename assumptions. Secret filenames,
private-key contents, recovered seeds, token secrets and decrypted payload contents are omitted.
The fixture-test oracle parses only enough metadata to establish this evidence; it does not
implement the future production catalog parser or access-control layer.

## Build and test evidence

Executed with Oracle JDK 24.0.2 and Maven 3.9.9 through the existing project script:

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','verify'
```

Both completed **BUILD SUCCESS**. The full fixture-enabled clean verification ran **87 tests,
0 failures, 0 errors, 0 skipped**. The default credential-free suite ran **67 tests,
0 failures, 0 errors, 0 skipped**; fixture-tagged tests are intentionally not selected there.

| Test class | Passed |
| --- | ---: |
| AuditEventTest (unchanged Phase 0) | 2 |
| FixtureIntegrityTest (unchanged Phase 0) | 1 |
| FoundationTest (unchanged Phase 0) | 4 |
| Base32Test | 23 |
| CryptoPrimitivesTest | 12 |
| PasswordAndTokenSecretTest | 4 |
| TOTPTest | 21 |
| CryptoCompatibilityTest (explicit fixture profile) | 20 |
| **Full total** | **87** |

Both packaged JARs passed their existing `--check` entry-point smoke checks. An additional
temporary Java 17-targeted smoke program, compiled under ignored `.tools/`, executed BCrypt,
AES-encrypted token-secret round trips, TOTP and certificate subject parsing using **each
shaded JAR as its runtime classpath**. Both passed; these smoke checks are additional to
the 87 JUnit tests. Neither JAR embeds local secret configuration or professor fixtures.

Coverage includes public RFC Base32 standard/hex vectors; public RFC HOTP/TOTP SHA-1 vectors
with six-digit reduction and leading zeros; 30-second boundaries, +/-1 acceptance and +/-2
rejection; invalid tokens/secrets; closed-secret rejection; AES-256 ECB NIST vector; fresh-state
derivation and Unicode encoding; PKCS#8 restoration; mismatched RSA keys; raw binary envelope
seeds; corrupted signatures/envelopes/ciphertext; wrong fixture passphrases; corrupted private-key
copies; required BCrypt format/fresh salts and token-secret encryption round trips.

Reference vector sources: [RFC 4226 Appendix D](https://www.rfc-editor.org/rfc/rfc4226),
[RFC 6238 Appendix B](https://www.rfc-editor.org/rfc/rfc6238), and
[RFC 4648 section 10](https://www.rfc-editor.org/rfc/rfc4648).

`-Pfixtures` reads ignored UTF-8 `.local/fixture-secrets.properties` (keys admin/user01/user02).
The public PDF BCrypt example is in ignored `.local/assignment-bcrypt-example.properties`
(keys password/hash). Missing credentials cause an explicit fixture-gate error, not a skip.
No credential values were printed or placed in Java/source-controlled configuration/docs.

All 22 original fixture checksums are checked before and after the fixture class, as well as
by the original integrity test. Corruption tests only change JUnit temporary copies. Verified
payloads stay in memory and are cleared after comparison. Only code/owner/group/recipient
metadata is written to ignored `target/fixture-relationships.tsv`; no plaintext payload is saved.

## Remaining limitations and next gate

- **No unresolved real-fixture cryptographic failure.**
- Group-only decryption is still undefined by the specification. For example, user02 shares
  group usuario with user01, but XXYYZZ11 has no recipient envelope for user02. Group visibility
  does not establish a usable cryptographic route. Await professor clarification in Phase 4.
- Actual catalog labels `administrador`/`usuario` are established; mapping to future database
  and UI groups is a later implementation concern.
- Java 17 compatibility is enforced at compile time with release 17. Actual execution on a
  Java 17 runtime remains unverified because this machine has JDK 24 only.
- No phone enrollment, desktop authentication, application session, storage, output publishing,
  virtual keypad, log viewer authentication or end-to-end application behavior was tested here.
- Existing non-fatal JDK 24 native-access/Unsafe and Maven Shade manifest warnings remain as
  documented in Phase 0; they did not prevent build/test/package success.

**Stop after Phase 1. Await user review before implementing Phase 2. No commit or push made.**
