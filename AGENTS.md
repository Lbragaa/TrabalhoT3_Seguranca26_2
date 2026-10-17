# Project instructions

## Current authorization and status

Phases 0 and 1 are complete. The user explicitly authorized **Phase 2 only** on 17 October 2026.
Phase 1 baseline: 87 fixture-enabled tests pass (67 in the default suite).
Phase 2 is complete: 120 fixture-enabled tests pass (99 default), with no failures or skips.
Do not implement Phase 3 or later without explicit subsequent user approval.
Read `PLAN.md`, `REQUIREMENTS.md`, and `docs/PHASE2-RESULTS.md`; earlier phase reports preserve history.
These documents preserve decisions; the PDFs remain authoritative if a conflict arises.

## Working rules

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
normal tool approval process on first download. The vault now runs Phase 2 startup; logView remains a shell.
The default suite does not read private fixture credentials. `-Pfixtures` runs the full suite
and fails explicitly if ignored `.local/fixture-secrets.properties` lacks admin/user01/user02.
It also checks the public assignment BCrypt example from ignored
`.local/assignment-bcrypt-example.properties` (`password` and `hash`, copied from A p.3).
Never print either file. Group mapping is implemented (1=administrador, 2=usuario).
Group-only decryption remains unresolved. The registration fixture test uses temporary copies
and proves all three identities survive database reopening; it never seeds runtime/cofre.db.

## Phase 2 / future Phase 3 integration boundary

- Startup READY is not MFA and grants no authenticated session or registration capability.
- RegistrationAuthorization has a package-private constructor. Only test sources mint it now.
  Future AuthenticationService in auth may issue it after full MFA and revoke it on logout.
  Do not add a public issuer or UI shortcut to demonstrate registration before that gate.
- RegistrationService: showForm -> prepare -> confirmCertificate -> enrollment acknowledgement
  -> commit. PendingRegistration and RegistrationRequest are closeable and must be closed.
- Bootstrap commit returns a closeable Result. StartupService.acceptBootstrap consumes its
  administrator context; close the result in all cases. Ordinary results never contain that context.
- Database work uses IMMEDIATE transactions, per-connection foreign keys, and prepared statements.
  Audit confirmation 6008 must stay inside the registration transaction. Never suppress audit errors.
- Crypto classes/interoperability and the original 87 fixture-enabled tests are unchanged.

## Source map

- `docs/Trabalho3.pdf` (A): startup p.1; authentication pp.1–3; registration pp.2–4;
  database p.4; vault pp.4–6; viewer pp.6–7; audit pp.8–9; TOTP/Base32 pp.10–14.
- `docs/Trabalho3-Detalhamento.pdf` (D): vault pp.3–9; key restoration pp.10–11;
  login p.12; keypad pp.13–15; token p.16.
- Exact audit templates live in `audit/AuditEvent.java`; this is a catalog only.
- `src/test/resources/fixtures.sha256` records the original 22-file package.
