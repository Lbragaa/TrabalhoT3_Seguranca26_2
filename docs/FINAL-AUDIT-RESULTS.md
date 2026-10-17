# Final compliance audit and release verification — 17 October 2026

## Recommendation: CONDITIONAL GO

The implemented owner/explicit-share workflows, MFA, persistence and independent viewer pass the
final complete automated checks. This is **not an unconditional claim of assignment compliance**.
Group-only extraction is absent, the password repetition wording needs confirmation, and the
actual Windows desktop/Google Authenticator walkthrough remains unperformed. If the professor
requires group-only extraction as written without accepting the documented ambiguity, that is
a compliance blocker; a passing owner/share test cannot waive it.

Review baseline: commit `29b21ba` (Phase5 - Commit 1), initially clean working tree. This audit
changes documentation only; no production algorithm, service, test assertion or dependency was
changed. No newly demonstrated defect justified a speculative implementation change. The existing
Phase 5 output-failure fix was inspected and its regression tests rerun. No commit, push or EAD
submission was made. The existing runtime database was neither opened by the application nor reset.

## Method and evidence limits

Both originals were reread in full: A = `Trabalho3.pdf`, all 14 pages including the audit catalog,
TOTP skeleton and supplied Base32; D = `Trabalho3-Detalhamento.pdf`, all 16 pages. Rendered
registration/consultation/exit screens and the envelope, key-restoration, keypad and TOTP diagrams
were inspected as well as extracted text. Physical PDF page numbers are used below.

Production code was inspected directly in app, auth, crypto, db, audit, ui, vault and logview;
the prior phase reports were historical context, not proof. Class names below are relative to
`src/main/java/br/pucrio/inf1416/cofre/`; test names are relative to the equivalent test package.
VERIFIED means supported by the inspected implementation and executed automated evidence within
the stated scope, not a proof against arbitrary local filesystem/database tampering.

## Requirement-by-requirement assessment

| Requirement and source | Classification | Actual implementation / evidence |
| --- | --- | --- |
| Java SE relational application, A1 | VERIFIED | pom.xml release 17; Swing; SQLite JDBC; packaged Java 17 checks. No server required. |
| First-run administrator registration, A1 | VERIFIED | app/StartupService.begin checks zero users; auth/RegistrationService.authorize restricts bootstrap to empty DB/admin group. RegistrationServiceTest and both final integration tests. |
| Subsequent administrator phrase validation and termination on failure, A1 | VERIFIED | StartupService.unlock restores stored key and 10,240-byte challenge; fail records shutdown. RegistrationServiceTest startupFirstRunRestartCorrectWrongAndContextCleanup and startupCertificateMismatchTerminatesAndShutdownAuditFailureClearsMaterial. |
| Administrator material lifetime, A1 | PARTIALLY VERIFIED | AdministratorContext clones phrase and clears its array/key reference on shutdown; startup is not a session. Cleanup tests pass. JVM/provider immutable/internal copies cannot be guaranteed erased. |
| Email stage 1, unknown/blocked stay there, A1/D12 | VERIFIED | AuthenticationService.identify validates syntax, registered email and persisted block; stage stays IDENTIFY on refusal. AuthenticationServiceTest and AuthenticationUiTest. |
| Five two-digit buttons, 0–9 once, reshuffle each click, A1/D13–15 | VERIFIED | VirtualKeypad.press captures pair before Fisher–Yates shuffle; AuthenticationPanel masks count. VirtualKeypadTest, KeypadPasswordVerifierTest, AuthenticationUiTest. |
| Ambiguous 8/9/10-position BCrypt verification, A1/D15 | VERIFIED | KeypadPasswordVerifier enumerates all eligible candidates with <=4 workers and no early match exit, auxiliary hash or stored password. Five verifier tests pass; historical benchmark retained, not rerun. |
| Numeric 8–10 digits and matching confirmation, A3 | VERIFIED | PersonalPasswordPolicy + RegistrationService.prepare enforce ASCII numeric length and equality; invalid-password and confirmation tests. |
| Repeated-number sequences, A3 | SPECIFICATION AMBIGUITY | Adjacent-equal digits rejected centrally. Repeated blocks are not rejected. Exact wording and D15 counterexample discussed below; no silent policy change. |
| Three consecutive failures / two-minute account blocks, A1–2 | VERIFIED | AuthenticationService.password/token and UserRepository refreshLockout persist counters and 120,000ms deadline transactionally. Positive stage resets its own counter; expiry resets cycle. Restart, isolation, concurrency and boundary tests plus final integration pass. |
| Local TOTP, HMAC-SHA1, 30s, 6 digits, +/-one interval, A1–3/10,D16 | VERIFIED | crypto/TOTP preserves prescribed constructor/helper structure and Mac/SecretKeySpec/Date/Base32 support; no third-party generator. TOTPTest: 21 cases including vectors, boundaries and leading zero. |
| Session only after full MFA, A2 | VERIFIED | AuthenticationService.token commits acceptance/access count/audit before issuing package-private SessionContext. AuthenticationServiceTest rollback, close/stale-result and UI success tests. |
| Admin-only registration, A2/6 | VERIFIED | Session-issued RegistrationAuthorization is DB-bound/revocable; RegistrationService checks current stored role at prepare and commit. Ordinary, missing, revoked and foreign capabilities rejected in RegistrationServiceTest. |
| Registration field limits/groups, A2 | VERIFIED | RegistrationPanel/FieldLimits: paths and phrase 255, personal fields 10; Group values Admin/User. Service independently checks paths/phrase/password. RegistrationUiTest. |
| Certificate details confirmation and subject identity, A3 | VERIFIED | RegistrationDialogs.certificateDetails includes version, serial, dates, signature, issuer, subject/CN/email. CertificateService extracts subject CN/email OID. UI dialog-seam and crypto tests. |
| Unique login / no partial failed or cancelled account, A3 | VERIFIED | Schema UNIQUE email plus IMMEDIATE transactional user/key insert; duplicate race and cancelled/pending tests in RegistrationServiceTest. |
| 10,240 random-byte key possession challenge, A3 | VERIFIED | SignatureService.validateKeyPair uses fresh SecureRandom bytes; called by registration/startup/consultation. Actual three keys match their certificates in CryptoCompatibilityTest. |
| Encrypted original key and PEM certificate linked through UID/KID, A3–4 | VERIFIED | RegistrationService.commit, KeyRepository, Schema reciprocal deferred composite FKs; RegistrationFixtureTest reopens all three identities and DatabaseTest rejects inconsistent data. |
| BCrypt 2y/08, salt/alphabet, 60 characters, A3 | VERIFIED | PasswordHasher invokes OpenBSDBCrypt.generate("2y",...,8), random 16-byte salt, validates format; fixture profile checks the PDF example. |
| Random 20-byte Base32 token enrollment/encrypted storage, A3–4 | VERIFIED | TotpSecretService generates SecureRandom bytes, supplied Base32, AES256 ECB/PKCS5 with password-derived SHA1PRNG key. PasswordAndTokenSecretTest and registration persistence tests. |
| Actual Google Authenticator enrollment/use, A3–4 | MANUAL VERIFICATION REQUIRED | Enrollment dialog exposes Base32 and requires acknowledgement; automated tests do not operate a phone. QR is explicitly optional and not implemented. |
| Success clears registration; failure retains input; Back, A4 | VERIFIED | RegistrationController completion clears panel, failed/cancel keeps values; back audits then clears. Seven RegistrationUiTest cases pass using real EDT/workers and injected dialogs. |
| Key restoration, PEM/X.509/PKCS8 and algorithms, A4/D10–11 | VERIFIED | PrivateKeyService, CertificateService, PemSupport, LegacyKeyDerivation: SUN SHA1PRNG fresh seeded instance, UTF-8 phrase/raw seed, SunJCE AES256 ECB/PKCS5, RSA PKCS8. Real fixture compatibility passes. |
| Five tables, identities/relationships, A4 | VERIFIED | Schema creates Grupos/Usuarios/Chaveiro/Mensagens/Registros; unique IDs/FKs enforced each connection; transactional migration preserves legacy logs. DatabaseTest and LogViewTest. |
| Header/counters, role menus and exit controls, A2/4–6 | PARTIALLY VERIFIED | MainMenuPanel, RegistrationPanel, ConsultationPanel, SessionExitPanel contain required functional fields/actions; ordinary register control absent. Headless controller/render tests pass; actual native layout/dialog/navigation needs manual check. Consultation follows A5 mockup's consultation counter, despite contradictory prose on A4. |
| Current-user phrase validation before listing, A5 | VERIFIED | VaultService.list/restore validates stored key/cert challenge, clears old listing first. VaultServiceTest wrongPhraseMismatchAndBadPathClearPriorListing. |
| Both admin catalogs decrypt and verify before listing, A5/D8–9 | VERIFIED | StartupService.openVault/AdministratorContext.catalog; CatalogService.load verifies both exact-byte signatures before CatalogParser. Damaged catalog parameterized tests publish no list/counter. |
| Catalog records and actual fixture labels, A5/D4–5 | VERIFIED | CatalogParser parses ASCII index/share, emails/extensions and positive IDs; rejects unsafe/duplicate/reference errors. 26 parser cases; real fixture matrix verified. D examples override overly narrow “alphanumeric” description. |
| Owner/group/explicit-share listing union, A5 | VERIFIED | AccessPolicy reasons/set and listing filter; actual admin/user01/user02 matrix in VaultFixtureTest and FinalFixtureIntegrationTest. |
| Group-only file decryption, A5–6/D4 | NOT IMPLEMENTED | Visible same-group file without owner/share route receives GROUP_ROUTE and no output. Specification mechanism is unresolved; see detailed finding below. |
| Owner .env / recipient .N.shr and owner signature, A6/D6–9 | VERIFIED | AccessPolicy.envelope, EnvelopeService RSA/ECB/PKCS1Padding, ProtectedFileService AES and SHA1withRSA; VaultService.decrypt fetches owner's certificate. All 3 owner routes and user01 recipient route pass, identical verified content. |
| Permission recheck on selection, A6 | VERIFIED | VaultService.extract reloads both catalogs, validates references, compares shown/current metadata, checks current DB identity and active same-DB session. Changed metadata/revoked share/unknown owners tests pass. |
| Verify before secret-name output, A6 | VERIFIED | ProtectedFileService returns bytes only after verification; temporary file created afterwards; final no-clobber hard link. Tampered signatures, ciphertexts and audit failures leave no output. |
| Path traversal / overwrites / unsafe destinations | VERIFIED | SafePaths rejects unsafe Windows names, links, paths outside expected inputs, existing output, safe-folder/fixture-tree output. Final createLink protects competing target creation. VaultServiceTest; NTFS extraction tests. Host directory ACLs remain a trusted boundary. |
| Filesystem portability, D3 | PARTIALLY VERIFIED | Safe input folder may be on traditional filesystem; output needs hard links (NTFS verified). FAT32/exFAT output unsupported. No unsafe overwrite-capable fallback added. |
| Back/logout/restart/system shutdown, A4/6 | PARTIALLY VERIFIED | Controllers route Back, logout and exit; service tests verify audit/resource/counter behavior. Busy actions prevent competing navigation; disposed auth/consultation callbacks guarded. Native ApplicationController/JFrame exit sequence remains manual. |
| Exact 64 messages, A8–9 | VERIFIED | Fresh programmatic extraction comparison of all 64 PDF IDs/texts to AuditEvent: exact match after PDF trailing whitespace removal. AuditEventTest + database seed checks pass. |
| Event timestamp/MID/UID/file context, messages only in Mensagens, A6 | VERIFIED | AuditService prepared inserts store references/context; Schema separates templates. No secret/password/payload producers found. Viewer resolves stored context only. |
| All event trigger timing / failure paths, A6/8–9 | PARTIALLY VERIFIED | Every producer reviewed; exact traces, injected failures and integration tests pass. Not all 64 triggers have an individually asserted native UI trace. Presentation events are recorded before actual paint; see audit analysis below. |
| Output errors not falsely reported as crypto failures | VERIFIED | VaultService.publication + AuditService.verifiedPendingPublication record pending/published/failed on 7019; PublicationAuditTest (2 cases) verifies failures do not emit 7020/7021. |
| Independent executable / no vault audit menu, A6–7 | VERIFIED | LogViewMain separate manifest; shade filters physically separate application packages; ApplicationController menu contains no viewer. JAR inventory checked. |
| Viewer key-path CLI / non-echo input / fail closed, A7 | VERIFIED | LogViewMain only System.console().readPassword, no fallback; null console exit2, authentication refusal exit1. LogViewTest and packaged no-console check; real terminal evidence separately identified below. |
| Viewer admin identity and fresh 2,048-byte challenge, A7 | VERIFIED | LogViewService.authenticate queries registered administrators' certificates, validates subject email/key match; private Access bound to service, role/cert rechecked before log query. Positive/ordinary/wrong/mismatched/revoked tests pass. |
| Chronological date/time/code/resolved messages, A7 | VERIFIED | LogViewService.read LEFT JOIN Mensagens ORDER BY timestamp,RID, single-pass placeholders, UTF-8 output; missing contexts remain literal, malformed data labelled safely. LogViewTest and final integration. |
| Viewer DB read-only | VERIFIED | Database.existingReadOnly never initializes/migrates; SQLite read-only connection. Write rejection, unchanged bytes and legacy schema tests pass. |
| All Java sources and EAD submission, A7 | VERIFIED | Release contains all 94 Java files (61 production, 33 test), Maven config, scripts/docs, both JARs and original package. This verifies deliverable contents, not actual EAD submission. |

## Findings and compliance consequences

### Group-only access: unresolved, potentially blocking

A5 explicitly grants listing by ownership, group and explicit sharing. A6 then says selecting a
listed filename should decrypt and save it, or report no permission. D4 describes GRUPO_ARQUIVO
as the group authorized to access the file. Together these support an expectation of group access,
not a confident conclusion that group-only listing fully satisfies the assignment.

However, A6 defines `.env` for the owner and `.N.shr` for an explicitly shared recipient. D6–9
shows only those routes; no group private key, per-group envelope, group seed distribution or
administrator decryption delegation is specified. A group label supplies authorization metadata,
not the RSA private key needed to unwrap another owner's envelope. In actual fixtures user02 can
list XXYYZZ11 by group but lacks a usable envelope for it. This is **NOT IMPLEMENTED**, caused by
a **SPECIFICATION AMBIGUITY**, not a test passed by denying access.

Ask the professor whether every same-group reader must also have a named `.N.shr` entry, or what
additional envelope mechanism is intended. No key borrowing, crypto invention or override was added.

### Password sequence rule: unchanged provisional interpretation

A3 says: “Não podem ser aceitas sequências de números repetidos.” It does not define adjacent
runs versus repeated blocks. D15's candidate examples include `81527230`, which repeats 2 in
nonadjacent positions; this weighs against banning every repeated digit. The approved adjacent
rule is plausible, but examples do not conclusively authorize repeated blocks such as `12121212`.
PersonalPasswordPolicy still rejects adjacent equal digits and permits nonadjacent recurrence.
Clarify before claiming strict compliance; changing it now would invalidate existing enrolled
passwords and candidate pruning without a justified migration decision.

### Audit event producers, ordering and interpretation

All 64 exact templates match. The producer review supplements `AUDIT-COVERAGE.md`:

| Codes | Precise producing transitions reviewed |
| --- | --- |
| 1001/1005/1006 | StartupService.begin: start plus bootstrap/normal branch. |
| 1002 | StartupService.fail/shutdown; failed startup sets TERMINATED to prevent duplicate shutdown. |
| 1003/1004 | AuthenticationService.token successful transaction; logout/close termination. |
| 2001–2005 | start, identify unknown/blocked/known, endToIdentification/restart/close; known emits 2003,2002,3001. |
| 3001–3007 | identify starts; password success emits 3003,3002,4001; submitted failures choose 3004–3006; third adds 3007 then 3002,2001. |
| 4001–4007 | password starts; token success emits 4003,4002,1003; failures choose 4004–4006; third adds 4007 then 4002,2001. |
| 5001–5004 | mainMenu presents; menu maps REGISTER/CONSULT/EXIT; unauthorized registration rejected before menu event. |
| 6001–6010 | showForm; prepare submit/password/cert/path/key failures; commit accepted confirmation; cancel rejected confirmation; back. Startup/consultation reuse relevant 6006/6007 key failure templates. |
| 7001–7004 | VaultService constructor/back/list/source-folder failure. |
| 7005–7014 | CatalogService.read index/share decryption and verification; VaultService.list commits 7009/7014 with count only after both verified catalogs parse and references validate. |
| 7015–7021 | extract selection; current policy route allow/deny; decrypt success and bad signature; extraction verification audit before publication; real decryption/input failure. |
| 8001–8004 | exitScreen, logout button, exitSystem button, backFromExit respectively. |

Selection re-verifies catalogs, so repeated catalog crypto events are intentional; list-presentation
events/counter do not repeat on extraction. Group-only refusal uses 7017 for unavailable extraction,
despite permitted visibility; this does not prove fulfilled group decryption. Bad signature emits
successful decryption then failed verification. Output-I/O failure after verification updates 7019
context to failed, never emits a false 7020. Preflight invalid/existing output stops after selection/
permission events because no decryption occurred and no prescribed output-error MID exists.

6008 represents accepted complete registration confirmation and commits with the account after
enrollment acknowledgement; accepting the certificate then cancelling enrollment records 6009,
without an account. Bootstrap records before an identity exists have null context; viewer must
not invent a login. No extra viewer-authentication MID is specified. Shutdown, registration,
session issuance and listing audit failures surface and prevent silent protected transitions.

Evidence limits: producer presence is not exhaustive event-sequence proof. Screen/presentation
events are recorded as the service returns data, before Swing physically paints it; database/EDT
painting cannot be atomic. A crash may leave an unconfirmed publication or verified temporary file.
An outcome-update failure after publication may leave a verified output with pending audit status.
No tamper-proof audit or cross-filesystem/database transaction is claimed.

### GUI/security review and minor limitations

Authentication and consultation use workers, duplicate-submit guards and completion generations;
session checks prevent stale results from granting new privilege. Registration blocks Back/window
close while work/dialogs are pending; request/pending/result buffers are closed, success clears the
form and failed input is retained as specified. Key references are released on consultation Back,
logout and shutdown. The application waits for busy work before normal exit rather than cancelling
partway through a protected operation. No SQL/BCrypt work was found on the EDT in reviewed paths.

Native window sizing, modal dialog interactions and the top-level JFrame exit flow remain manual.
Extraction uses a separate explicit button after selecting a row and an output-directory chooser;
the underlying required operation is implemented, but this is not pixel-identical to PDF sketches.
The `--check` vault banner still says Phase 4 and an unused FoundationPanel retains its old shell
text; neither is reachable as a normal workflow defect. They were not cosmetically refactored.

Path checks/no-clobber publication protect normal application operations. Concurrent hostile OS
directory replacement, local DB editing, crash-proof cleanup and guaranteed heap erasure are not
provided. All decrypted payloads are read into memory, so arbitrary huge files are not a tested
capacity claim. These limits do not justify weakening verified output safety or inventing crypto.

## Final executed verification

Both complete builds below ran against the current production tree, including the prior UTF-8
fix. Only documentation changed afterwards. Tests/coverage were not removed or weakened.

| Check | Actual result |
| --- | --- |
| Default clean verify, JDK 24.0.2 | 190 tests; 0 failures/errors/skips; BUILD SUCCESS; 1:03; completed 14:46:01 -03:00. |
| Full fixture clean verify, test JVM Temurin 17.0.20.1 | 214 tests; 0 failures/errors/skips; BUILD SUCCESS; 1:13; completed 14:47:44 -03:00. |
| Java runtime proof | All fixture Surefire XML properties report java.version=17.0.20.1; Maven compiler remains installed JDK24 with release17. |
| CryptoCompatibilityTest | All 20 actual professor-fixture cases pass, including 3 keys, 2 catalogs, owner/shared bytes and negative cases. |
| Complete workflow | FinalIntegrationTest + FinalFixtureIntegrationTest both pass: bootstrap, restart, MFA, registration, catalog/extraction, denial/group refusal, both lockouts, counters, logout and authenticated viewer. |
| Viewer and publication regression | 8 LogViewTest + 2 PublicationAuditTest pass as part of each applicable full suite. |
| Automated Swing | 7 registration + 5 authentication + 4 consultation cases pass on Java17, including real button/worker flows, responsiveness and late-callback rejection. Headless, not native desktop/phone. |
| Originals | 22 fixture SHA-256 checks pass; both PDFs and fixture files also checked against a fresh audit-start hash snapshot. |

Default excludes 24 fixture-tagged cases by profile configuration; these are not reported as JUnit
skips. Full fixture profile includes them all. Build warnings about JDK24 native/Unsafe use and
overlapping dependency manifests remain nonfatal. Hard-link tests used normal approved execution
outside the restricted sandbox; no safety code was disabled.

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','clean','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','-Djvm=C:\Users\luanc\OneDrive\Documentos\GitHub\TrabalhoT3_Seguranca26_2\.tools\java17\jdk-17.0.20.1+1-jre\bin\java.exe','clean','verify'
```

The preceding Phase 5 real ConPTY terminal positive/wrong-input/no-console tests remain historical
evidence, not a newly performed human interaction. This audit reruns viewer positive/negative
authentication in both full suites and in a separate harness loading the extracted logView JAR.
Production still accepts its phrase only through Console.readPassword; a test harness using the
service API is not a new CLI secret-input path. Packaged no-console launch is also checked.

## Final archive and extracted-artifact verification

Release directory: `output/delivery-final-audit/`. ZIP: `cofre-digital-submission.zip`.
The script `scripts/package-delivery.ps1` was reviewed: it copies explicit source/documentation
roots and the two JARs, rejects nonempty delivery directories and forbidden local directories/
links in staging. Directory allowlisting is not a universal secret scanner; the actual release
inventory/content was separately checked. Future changes require a new inspection.

The prior `output/delivery` hashes and all 94 source bytes were checked first. The new ZIP includes
61 production and 33 test Java sources, pom.xml, scripts, docs including this audit, checklist,
roadmap/instructions, both original PDFs, all 22 unchanged professor package files and both JARs
under target/. It excludes `.local`, `.tools`, runtime databases, extracted output, generated
private keys/identities, token enrollment files, Git metadata and local tool/JRE distributions.
Original encrypted professor key files are intentional fixtures, not generated private material.

After creation the ZIP is extracted to a new ignored `.tools/final-audit` directory. Verification
compares every archived file with its current source/build bytes and original fixture checksums,
checks the two manifests, bundled JDBC/BC dependencies and separate application packages, and
runs both Java17 `--check` launchers from the extracted root. Additional external smoke harnesses
load only the extracted production JARs for SQLite bootstrap/reopen/audit, BCrypt/AES/TOTP/certificate
operations, and admin-positive/ordinary-negative independent viewer reads. Temporary databases
are separate from the user's runtime database. No default application launch initializes a
database accidentally in the user's working folder.

| Artifact | SHA-256 |
| --- | --- |
| cofre-digital.jar | `422994c6b5ef8a500ed6ff03be0cf3b483ba5cf4ae94634d5746f5c492dc6663` |
| logView.jar | `de3599d39aa616abc54e1b16b43cdb09ad0a4cd2a3eed1f655bb7a610b5eebd7` |

The **ZIP SHA-256** is recorded in the release's `SHA256SUMS.txt` beside these same JAR hashes.
It cannot be embedded literally in this report inside that ZIP without changing the ZIP hash.
`RELEASE-VERIFICATION.txt` beside the ZIP records final extraction/check outcomes and the manifest;
these external receipts bind the final immutable ZIP, avoiding a stale archive/self-reference.

Reproduce from the extracted root with a Java17 executable of your choice:

```powershell
$java17 = 'C:\path\to\java17\bin\java.exe'
& $java17 -jar target/cofre-digital.jar --check
& $java17 -jar target/logView.jar --check
# Native manual operation, only in the intended database working folder:
& $java17 -jar target/cofre-digital.jar
chcp 65001
& $java17 -jar target/logView.jar 'Pacote-T3/Keys/admin-pkcs8-aes.key' --database 'runtime/cofre.db'
```

Java17 is actually tested, not just bytecode compatibility. The ignored local JRE is not submitted.
NTFS publication passes; unsupported hard-link output filesystems still fail closed.

## Remaining manual checks and submission requirement

Follow the six numbered existing-account smoke checks in `../FINAL-DELIVERY-CHECKLIST.md`:
startup validation; real phone MFA; menu/registration; owner/share extraction and group limitation;
Back/logout/restart/counters; independent non-echo PowerShell viewer. Each step gives expected
results and failure indicators. None is marked passed on the strength of headless tests.
Do not reset `runtime/cofre.db` or re-enroll an existing account to manufacture a passing demo.

A7 requires **the project with all Java sources**, submitted through PUC-Rio EAD by **18 October
2026 at 23:59**, maximum late time **19 October at 11:59**; **each group member submits individually**.
A1 lists presentations 19–23 October. ZIP naming is not prescribed and JARs do not replace sources.
The final release is ready for inspection and conditional submission after the manual checks and
honest disclosure/clarification of the group and password ambiguities. No full-compliance GO is
claimed, and no submission action has been performed.
