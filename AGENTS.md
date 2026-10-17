# Project instructions

## Current authorization and status

The user approved the architecture and six-phase roadmap, but authorized **Phase 0 only**.
Do not implement Phase 1 or later without explicit subsequent user approval.
Read `PLAN.md`, `REQUIREMENTS.md`, and `docs/PHASE0-RESULTS.md` before continuing.
These documents preserve decisions; the PDFs remain authoritative if a conflict arises.

## Working rules

- Keep `Pacote-T3/` and the two original PDFs unchanged. Never regenerate fixtures or update
  the checksum baseline merely to make a failing integrity check pass.
- Never commit passwords, passphrases, token secrets, databases, extracted files, or local
  tool distributions. Use ignored `.local/`, `runtime/`, `output/`, and `.tools/` locations.
- Work incrementally. Complete and report the current phase's acceptance checks before
  starting a dependent phase. Real-fixture compatibility is a Phase 1 gate, not established fact.
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
& "$env:JAVA_HOME/bin/java.exe" -jar target/cofre-digital.jar --check
& "$env:JAVA_HOME/bin/java.exe" -jar target/logView.jar --check
```

The script downloads SHA-512-verified Maven 3.9.9 if missing, sets JAVA_HOME only for
the build, and uses `.tools/m2` as the dependency cache. Pass `-JdkHome` if necessary.
It does not alter system PATH or the registry. Network restrictions may require the
normal tool approval process on first download. Both entry points are Phase 0 shells.

## Source map

- `docs/Trabalho3.pdf` (A): startup p.1; authentication pp.1–3; registration pp.2–4;
  database p.4; vault pp.4–6; viewer pp.6–7; audit pp.8–9; TOTP/Base32 pp.10–14.
- `docs/Trabalho3-Detalhamento.pdf` (D): vault pp.3–9; key restoration pp.10–11;
  login p.12; keypad pp.13–15; token p.16.
- Exact audit templates live in `audit/AuditEvent.java`; this is a catalog only.
- `src/test/resources/fixtures.sha256` records the original 22-file package.
