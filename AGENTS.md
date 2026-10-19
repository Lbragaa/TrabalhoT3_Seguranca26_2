# Project instructions

## Current authorization and status

19 October follow-up: user-authorized native testing completed registration/enrollment/MFA
for all 3 real identities, owner and explicit-share extraction (byte-identical outputs),
overwrite refusal, navigation/logout, full restart and saved counters. Group-only extraction
still fails closed. The user ran real production logView and confirmed passphrase input was invisible.
Windows PowerShell output garbling was reproduced and fixed with scripts/logview.ps1 using
session-only Console.OutputEncoding and OutputEncoding UTF-8; smoke checks pass on Java17/24.
12 focused Java17 viewer/launcher tests pass; no Java service/crypto change. pom target restored
from an observed local 25 edit to the approved 17. Latest release: output/delivery-20261019/.
See the dated addendum in docs/FINAL-AUDIT-RESULTS.md; earlier manual-pending statements are historical.

Final pre-submission audit was authorized and completed on 17 October 2026 after Phase 5.
Read `docs/FINAL-AUDIT-RESULTS.md` first for the latest compliance assessment and final release.
Current full clean verification: 190 default tests on JDK24 and 214 fixture tests on Java17,
zero failures/errors/skips, including the UTF-8 fix. Production code is unchanged by the audit.
Final audited archive: `output/delivery-final-audit/`; its hashes and verification receipt are
beside the ZIP. Recommendation remains CONDITIONAL GO; no native desktop/phone success or
group-only extraction is claimed. Stop after this audit; no commit, push or submission is authorized.

Phases 0–4 are implemented. The user explicitly authorized **Phase 5** on 17 October 2026:
independent logView, audit review, integration/security checks and delivery preparation.
Phase 1 baseline: 87 fixture-enabled tests pass (67 in the default suite).
Phase 2 is complete: 120 fixture-enabled tests pass (99 default), with no failures or skips.
Phase 3 is complete: 152 fixture-enabled tests pass (130 default), with no failures or skips.
Phase 4 is complete within the approved scope: 202 fixture-enabled tests pass (179 default),
with no failures/errors/skips. Group-only extraction remains explicitly unresolved.
Phase 5 automated verification is complete: 190 default tests pass on JDK 24, and 214
fixture-enabled tests pass with a real Java 17 test JVM. The final UTF-8 output fix passed
12 focused viewer/launcher tests on Java 17. Read docs/PHASE5-RESULTS.md and
FINAL-DELIVERY-CHECKLIST.md; do not claim phone/native desktop checks or group-only decryption passed.
The benchmark gate passed. Implement vault consultation, owner/shared extraction and group listing.
Group-only extraction must fail closed without an envelope. Stop after Phase 5; no commit or push.
Benchmark full BCrypt keypad enumeration before building authentication UI. If compliant
verification is genuinely unusable, stop and report the measurements rather than implementing
an interface around it. Never add an auxiliary hash, password storage or authentication bypass.
Read `PLAN.md`, `REQUIREMENTS.md`, and `docs/PHASE5-RESULTS.md`; earlier phase reports preserve history.
These documents preserve decisions; the PDFs remain authoritative if a conflict arises.

## Working rules

- Every Java source, including tests and package-info.java, must begin with a comment naming
  Luan Braga (2411776) and Diogo Benchimol (2312917), as required by the user's submission notice.

- Keep `Pacote-T3/` and the two original PDFs unchanged. Never regenerate fixtures or update
  the checksum baseline merely to make a failing integrity check pass.
- Never commit passwords, passphrases, token secrets, databases, extracted files, or local
  tool distributions. Use ignored `.local/`, `runtime/`, `output/`, and `.tools/` locations.
- Work incrementally. Complete and report the current phase's acceptance checks before
  starting a dependent phase. Real-fixture compatibility is established by the Phase 1 fixture
  profile and its results report; preserve that gate when changing cryptographic code.
- Do not invent a group decryption mechanism. Group-only extraction requires professor
  clarification; owner and explicit-share routes are separate requirements.
- Use the professor's prescribed legacy algorithms and formats. Do not silently modernize them.
- Password policy provisionally rejects adjacent equal digits; keep it centralized when implemented.
- Use plain Java SE/Swing/JDBC, Java 17 target, Maven, SQLite, BC BCrypt, JUnit 5.
- Package root: `br.pucrio.inf1416.cofre`. Avoid unnecessary frameworks and abstractions.
- No third-party TOTP generator. Phase 1 must implement the supplied structure using only
  Mac, SecretKeySpec, Date, and the supplied Base32 support.
- Run tests appropriate to the change. Report actual results and unperformed manual checks.
- Update the persistent roadmap, requirement evidence, and phase results after each approved phase.
- Do not make commits or push unless requested. No subagent delegation is requested here.

## Commands (PowerShell)

```powershell
./scripts/maven.ps1 -MavenArgs clean,verify
./scripts/maven.ps1 -MavenArgs '-o','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
& "$env:JAVA_HOME/bin/java.exe" -jar target/cofre-digital.jar --check
& "$env:JAVA_HOME/bin/java.exe" -jar target/logView.jar --check
```

The script downloads SHA-512-verified Maven 3.9.9 if missing, sets JAVA_HOME only for
the build, and uses `.tools/m2` as the dependency cache. Pass `-JdkHome` if necessary.
It does not alter system PATH or the registry. Network restrictions may require the
normal tool approval process on first download. Vault and independent logView are implemented.
The default suite does not read private fixture credentials. `-Pfixtures` runs the full suite
and fails explicitly if ignored `.local/fixture-secrets.properties` lacks admin/user01/user02.
It also checks the public assignment BCrypt example from ignored
`.local/assignment-bcrypt-example.properties` (`password` and `hash`, copied from A p.3).
Never print either file. Group mapping is implemented (1=administrador, 2=usuario).
Group-only decryption remains unresolved. The registration fixture test uses temporary copies
and proves all three identities survive database reopening; it never seeds runtime/cofre.db.

## Authentication and vault integration boundary

- Startup READY is not MFA and grants no authenticated session or registration capability.
- AuthenticationService issues SessionContext only after TOTP verification and atomic audit/access
  update. SessionContext internally issues administrator-only RegistrationAuthorization; logout,
  close and fatal persistence errors revoke it. Do not add a public issuer or startup shortcut.
- KeypadPasswordVerifier performs every eligible BCrypt 2y/08 candidate check with at most four
  workers; there is no match-position early exit or auxiliary hash. The three-run worst-case
  medians were 0.9345/1.8463/3.6666 seconds for 8/9/10 digits on this host. Raw non-secret evidence:
  docs/phase3-keypad-benchmark.csv. Reproduce with ./scripts/benchmark-keypad.ps1 -Repetitions 3.
- Authentication restart and duplicate submission are rejected while a password scan is active.
  Closing a flow invalidates its generation; late results cannot grant a session. Never move
  BCrypt/SQL work onto the Swing EDT. Keep the UI completion-generation checks.
- Password/token failure counts and 120-second blocks are transactional in Usuarios, rechecked
  before/after password work. Clock injection is outside TOTP; TotpValidation calls the unchanged
  allowed-class TOTP implementation through its existing numeric-time seam.
- RegistrationService: showForm -> prepare -> confirmCertificate -> enrollment acknowledgement
  -> commit. PendingRegistration and RegistrationRequest are closeable and must be closed.
- Bootstrap commit returns a closeable Result. StartupService.acceptBootstrap consumes its
  administrator context; close the result in all cases. Ordinary results never contain that context.
- Database work uses IMMEDIATE transactions, per-connection foreign keys, and prepared statements.
  Audit confirmation 6008 must stay inside the registration transaction. Never suppress audit errors.
- Crypto classes/interoperability and the original 87 fixture-enabled tests are unchanged.
- StartupService.openVault requires a live MFA SessionContext bound to the same Database.
  AdministratorContext.catalog stays package-scoped; never add a raw-key getter or bootstrap bypass.
- VaultService keeps the user key only for consultation. Back/session close clears it. Session
  monitor orders vault operations against logout. Disposed views invalidate work and reject callbacks.
- Verify BOTH catalogs before parsing. Reverify them and recheck metadata/permissions on selection.
  Catalog references require registered owners and recipients. Only OWNER/SHARE yield envelope routes.
  GROUP alone yields listing and a clear extraction limitation; do not invent missing group crypto.
- Successful verified List commits 7009/7014 and consultation_count together. File events persist
  file_context only; never duplicate message templates or log plaintext/key/seed/phrase values.
- Extraction verifies the owner's signature before temporary output; success audit commits before
  final no-clobber hard-link publication. Refuse unsafe/existing destinations and outputs inside
  Pacote-T3 or the safe folder. NTFS hard links work outside the sandbox; this sandbox denies them.
  Run required extraction/build checks using normal escalation rather than weakening publication.
- Phase 5 adds nullable Registros.publication_context on 7019: pending/published/failed. Never
  misreport output-publication failure as a 7020 crypto failure. Preserve additive migration and
  read-only compatibility with legacy logs. Outcome persistence failure must surface.
- logView must use only System.console().readPassword in production, with no fallback/argument/file/
  environment secret input. Database.existingReadOnly never initializes/migrates. Only privately
  issued viewer Access may read logs, after fresh 2,048-byte challenge and administrator role/cert
  checks. Recheck the role/cert before each read; preserve UTF-8 output and safe placeholder handling.
- Shaded vault JAR excludes logview classes; logView JAR excludes app/auth/ui/vault application
  classes. Check both manifests and that no local credentials, runtime files or test helpers enter JARs.
- Java 17 JRE is ignored under .tools/java17/jdk-17.0.20.1+1-jre; Maven builds with installed JDK 24.
  Pass -Djvm=<absolute-java17-bin/java.exe> to MavenArgs to reproduce the Java 17 test run.
- Stop after completed Phase 5; changes or submission beyond that require user direction.
  No commit/push without a user request. Regenerate delivery archive after any source/docs changes.

## Source map

- `docs/Trabalho3.pdf` (A): startup p.1; authentication pp.1–3; registration pp.2–4;
  database p.4; vault pp.4–6; viewer pp.6–7; audit pp.8–9; TOTP/Base32 pp.10–14.
- `docs/Trabalho3-Detalhamento.pdf` (D): vault pp.3–9; key restoration pp.10–11;
  login p.12; keypad pp.13–15; token p.16.
- Exact audit templates live in `audit/AuditEvent.java`; this is a catalog only.
- `src/test/resources/fixtures.sha256` records the original 22-file package.
