# Phase 5 results — 17 October 2026

## Outcome and readiness

The independent administrator-only logView application, audit interpretation fix, full integration
workflows and delivery documentation are implemented. Phases 0–4 functionality and real-fixture
cryptography remain covered. No commit, push or EAD submission was performed.

Automated verification passes. Submission readiness is **conditional**, not a claim of complete
assignment compliance: group-only extraction has no specified envelope mechanism, the adjacent-repeat
password policy remains provisional, and real phone/native desktop demonstration checks remain.
FINAL-DELIVERY-CHECKLIST.md describes those checks and the assignment's actual submission requirement.

## Independent viewer

`LogViewMain` accepts `<encrypted-key-path> [--database <existing-db>]`, defaulting to
`runtime/cofre.db`. `--help`/`--check` are non-mutating. Production password input is exclusively
System.console().readPassword; null console, EOF and console I/O failure fail closed. There is no
argument, environment, file or echoed-input fallback for the viewer's passphrase.

`LogViewService` uses Database.existingReadOnly, which never creates/seeds/migrates a database.
Only registered administrator certificate metadata is queried before authentication. The supplied
key restores through the existing PrivateKeyService. Each candidate administrator certificate is
subject-identity checked and challenged using SignatureService.validateKeyPair with **2,048 fresh
random bytes**. Ordinary users, mismatches, damaged keys and wrong phrases are refused.

A successful match creates a closeable Access bound privately to that exact viewer instance.
It holds no private key or phrase. Null/foreign/closed access is rejected. Role, email and registered
certificate are rechecked in the same read transaction before querying Registros. Existing database
files are still subject to OS filesystem access; this is application authorization, not encrypted
or tamper-proof audit storage. No new CA/revocation infrastructure was invented.

The viewer LEFT JOINs Registros to Mensagens and orders by timestamp then RID. It prints local
date/time with milliseconds and UTC offset, event code and resolved message. Exact templates stay
in Mensagens. Context substitution is single-pass; missing context leaves its placeholder literal,
without guessing the current administrator's identity or looking up invented file names. Control/
format characters are escaped. Bad timestamps, unknown codes, altered/missing templates and invalid
context/outcome records receive a safe invalid-record label. Empty logs have an explicit message.
UTF-8 output avoids the demonstrated Java 17 legacy-codepage accent corruption.

The CLI exits 0 on authenticated output/help, 1 on key/path/authentication refusal, 2 on argument/
secure-console errors, and 3 on database/input-output failures. No logs are displayed on failed
authentication. The vault has no viewer menu; packaging now physically excludes logview classes
from cofre-digital.jar and vault/authentication/UI application classes from logView.jar.

## Audit review and demonstrated fix

All 64 IDs/templates remain unchanged. docs/AUDIT-COVERAGE.md maps every MID to its producing
branch and supporting test scenarios, distinguishing exact-trace assertions from source review.
Registration/startup, both authentication failure cycles, session lifetime, menus, consultation,
catalog/file verification, Back and shutdown were reviewed against A pp.6–9 and existing tests.

The demonstrated Phase 4 defect was a **false 7020 decryption-failure event** when final output
publication failed after successful cryptographic verification. That event is removed from the
output-I/O branch. Genuine decryption/missing-input and signature failure branches are unchanged.

Registros gains nullable `publication_context`, restricted to `pending`, `published`, or `failed`
on MID 7019. This is enum context, not message text or a new event code. Normal vault initialization
adds the column transactionally to an old database without deleting records; the viewer reads old
databases without altering them. 7018/7019 still report only cryptographic success. Verification audit
commits with publication pending **before** a final file can appear. After publication it updates to
published; an I/O failure updates to failed. Failure to persist that outcome surfaces and closes
consultation. An after-publication update failure leaves the outcome pending/unconfirmed; the
already-created file remains verified. No atomic transaction across SQLite and the filesystem is claimed.

Tests inject publication failure and audit-outcome failure, assert no false 7020/7021, inspect failed
outcome through the authenticated viewer and confirm cleanup/no final output on those failures.
Existing success-audit rollback tests still ensure no publication when initial verification audit fails.
No passwords, phrases, keys, seeds, token secrets or file contents are written into records.

## Executed verification

| Verification | Actual result |
| --- | --- |
| Default Maven clean verify, JDK 24.0.2 | **190 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (1:35 min)** |
| Full fixture-enabled Maven clean verify, test JVM Java 17.0.20.1 | **214 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (1:54 min)** |
| New LogViewTest | 8 cases: positive/ordinary/wrong phrase/key/cert/path, revoked/closed/foreign authority, read-only/no-init, missing/unsafe/malformed context, order, empty logs, no console, console failure, legacy migration |
| New PublicationAuditTest | 2 injected-failure cases; no false crypto-failure event and audit errors surfaced |
| New final integration | 1 synthetic + 1 real-fixture workflow, both pass; also included in the full Java 17 run |
| Final UTF-8 output fix and packaging | Focused LogViewTest + FoundationTest: **12 tests pass on Java 17**, repackaged after the terminal-discovered display defect |
| Existing cryptographic gate | All 20 CryptoCompatibilityTest cases pass in the full Java 17 profile |
| Professor fixtures | All 22 original SHA-256 values match; before/after checks also run in fixture workflows |

The original Phase 4 suite had 179 default / 202 fixture-enabled cases; Phase 5 adds 11 default
and one additional fixture-only case. DatabaseTest's column expectation is updated for the additive
context migration; its five cases remain. Existing algorithms, fixtures and preceding workflow tests
are preserved. Test helpers gained an optional genuine bootstrap flow for final integration.

Exact full-suite commands used:

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','clean','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','-Djvm=C:\Users\luanc\OneDrive\Documentos\GitHub\TrabalhoT3_Seguranca26_2\.tools\java17\jdk-17.0.20.1+1-jre\bin\java.exe','clean','verify'
```

Extraction suites require normal tool escalation outside this Windows sandbox because the sandbox
denies hard-link publication. The full suites were each run once after implementation; only focused
checks were rerun for the later UTF-8 display fix. Existing Maven JDK 24 native/Unsafe and shade
manifest warnings are non-fatal. No new dependency was added.

## End-to-end evidence

FinalIntegrationTest and FinalFixtureIntegrationTest exercise:

1. Empty database, genuine initial administrator registration, certificate/enrollment acceptance,
   consumed bootstrap result/context, shutdown, and later stored-key startup validation.
2. Complete administrator email/keypad/TOTP login; real issued registration capability registers
   user01 and user02; role-menu selections are audited and logout revokes the capability.
3. All three identities complete MFA; verified catalogs are listed, each owner file is extracted,
   and user01 extracts user02's explicit share. Bytes compare against independent owner-verified
   in-memory data. No payload contents or secret filenames appear in this report.
4. Wrong user phrase/source directory, permission denial and group-only listing/refusal; ordinary
   user direct registration authority rejection; Back and current-user key cleanup.
5. Malformed TOTP three-failure lockout, restart with reopened database, blocked account isolation,
   separate three-failure personal-password lockout, controlled exact 120-second expiry.
6. Reauthentication/logout, preserved access/consultation counters, startup-context shutdown,
   independent administrator viewer authentication and deterministic timestamp/RID inspection.
7. All four verified extraction outcomes appear as published; ordinary-key viewer authentication
   is rejected. Real professor originals are integrity-checked before and after.

The real fixture matrix remains:

| Identity | XXYYZZ00 | XXYYZZ11 | XXYYZZ22 |
| --- | --- | --- | --- |
| admin | Owner | Denied | Denied |
| user01 | Denied | Owner | Explicit share + group |
| user02 | Denied | Group listing only | Owner |

Group-only extraction remains intentionally unavailable; no group key or borrowed private key exists.

## Java 17, terminal and packaged checks

A local JRE was feasible. No installed Java 17 runtime was found in the inspected common locations.
The [official Adoptium API/release](https://adoptium.net/temurin/releases/?version=17) supplied
Temurin **17.0.20.1+1 Windows x64 JRE**, extracted only into ignored `.tools/java17` after SHA-256
verification (`bc21a93923103cdaac93ee337b0ae4365e739fde36df823dd456bc67c8a9d352`). No PATH, registry
or system-wide installation change occurred. Full fixture Surefire XML reports java.version=17.0.20.1.

Both independent JAR main entry points pass --check on that JRE. A disposable packaged-classpath
bootstrap/MFA/registration setup also runs using cofre-digital.jar on Java 17. A real ConPTY-backed
terminal reports System.console() available; readPassword accepts input without echo. The final
logView.jar authenticates a generated administrator in that terminal and prints correct UTF-8 accents,
timestamps, codes and chronological context, exiting 0. A wrong non-secret test input exits 1 with
no audit rows; plain piped/non-console launch exits 2 with no input fallback.

For the positive terminal test, an ignored helper submits only a generated disposable credential
after checking the console ECHO_INPUT flag is off. Professor credentials are never injected into
terminal tool calls or printed. Viewer ordinary-user rejection with the correct ordinary key/phrase
is tested in JUnit and both final integration paths. No human keyboard/physical-phone interaction
is claimed by the automated terminal test.

JAR inspection verifies manifest main classes, bundled dependencies, intended application-package
separation and absence of local configuration, fixtures, databases, test helpers and demo keys.
The Java 17 native desktop Swing walkthrough remains manual; headless Swing controller tests pass
on that runtime. Run the delivered commands in FINAL-DELIVERY-CHECKLIST.md on the demonstration PC.

## Filesystem decision and delivery preparation

Hard-link publication remains. A generic Java atomic move does not provide an unconditional
portable no-overwrite contract for existing targets; choosing that fallback would weaken the
current safety property. See [Java 17 Files API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
NTFS owner/shared extraction passes under Java 17. Unsupported output filesystems fail explicitly;
no FAT/exFAT or other-OS portability is claimed. Temporary/output names stay outside original fixtures.

The prepared project includes source, Maven configuration, scripts, tests, persistent roadmap/
requirements/instructions, audit traceability and the final checklist. A delivery archive under
ignored output/delivery contains allowlisted project files, original encrypted professor fixtures
and both executable JARs. It excludes all local phrase files, runtime databases, extracted content,
generated demo material, tool/JRE distributions and Git metadata. Archive/artifact hashes are in
output/delivery/SHA256SUMS.txt. Inspect the archive and regenerate it after any further changes.

The assignment explicitly asks for the project with **all Java sources**, submitted individually
by every group member via EAD by 18 October 2026 23:59 (maximum late time 19 October 11:59).
JARs and a ZIP are delivery conveniences, not substitutes for sources or a claim that a particular
archive name is required. The local build/documentation are ready for user review; actual submission,
physical-phone/desktop checks and resolution/disclosure of the group ambiguity remain with the user.

**Stop after Phase 5 automated verification and present for review. No commit or push.**
