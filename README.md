# Cofre Digital — INF1416 / Trabalho T3

Java SE application foundation for the PUC-Rio digital-vault assignment.
**Phases 0–3 are implemented.** The application supports SQLite persistence, registration,
administrator startup validation, virtual-keypad password verification, TOTP MFA, role menus,
registration authorization, logout and persistent account lockouts. Vault consultation and
the operational audit viewer await Phases 4 and 5.

## Build

Requires a JDK 17 or newer and PowerShell. All dependency/plugin versions are pinned
in `pom.xml`. The project-local script bootstraps Maven 3.9.9 with SHA-512 verification:

```powershell
./scripts/maven.ps1 -MavenArgs clean,verify
# Explicit JDK, if JAVA_HOME is absent or points to a broken installation:
./scripts/maven.ps1 -JdkHome 'C:\Program Files\Java\jdk-24' -MavenArgs clean,verify
# After the first successful build, validate with the local dependency cache:
./scripts/maven.ps1 -MavenArgs '-o','verify'
```

For a preinstalled Maven, set JAVA_HOME to a JDK and run `mvn clean verify`.
The compiler uses `--release 17`; this does not substitute for a final Java 17 runtime test.
Do not use the Oracle `javapath` shim if it hangs; select the real JDK directory.

## Executables

```powershell
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar --check
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/logView.jar --help
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar
```

The last command opens the first-run registration or administrator startup-validation screen.
`--check`/`--help` do not create files or read secret material. `logView` without those
flags exits with code 2: audit viewing is deliberately unavailable until implemented.
The two executable JARs bundle runtime dependencies and have independent main classes.

## Manually check startup and MFA

Run commands from the project root. Normal startup automatically creates `runtime/cofre.db`;
do not prepopulate it with test credentials.

1. On the first run, select the administrator certificate and encrypted private key, enter
   its private passphrase, and choose an 8–10 ASCII-digit personal password with no equal
   adjacent digits. Confirm that password. The bootstrap group is fixed to Administrador.
2. Click Cadastrar. Review version/serial/validity/signature/issuer/subject/name/email in
   the certificate dialog. Canceling either dialog creates no account and retains the form.
3. Accept the certificate, then enroll the displayed Base32 key as a time-based token in
   Google Authenticator. Acknowledge enrollment to commit the account. No QR code is required.
4. The form clears and login begins. Enter the certificate email, click the button containing
   each digit of your personal password (the pairs reshuffle after **every click**), then OK.
   Enter the current six-digit Google Authenticator code, retaining any leading zeros.
5. The main menu appears only after full MFA. Administrators can register users; ordinary users
   cannot. Consultation displays an explicit Phase 4 unavailable notice. Sair do Sistema offers
   Encerrar Sessão, Encerrar Sistema and Back. Logout returns to login and revokes registration authority.
6. Close using Encerrar Sistema, then run the same command again. Supply the administrator's
   private-key passphrase; the key is now loaded from the database, not the original file path.
   An incorrect phrase/key validation ends the process after an error notification.

Three submitted password failures or three token failures block only that account for 120
seconds; restarting the application does not erase the block. Limpar clears input, and Reiniciar
identificação returns to login after the current verification finishes; neither resets failures.
Keep the computer/phone clocks synchronized. The prescribed current +/-30-second TOTP window is used.

Administrator registration navigation is connected to the post-MFA session capability.
Startup key validation deliberately cannot grant registration privileges. No demo bypass exists.
The default registration service/UI tests use generated certificates and disposable temporary
databases. The fixture profile additionally registers all three professor identities in a
temporary database and verifies their encrypted records after reopening it. It does not seed
the normal runtime database or print credentials.

## Keypad performance

Full candidate enumeration remains mandatory. On this 16-logical-processor JVM, the production
four-worker verifier's three-run worst-case medians were **0.935s / 1.846s / 3.667s** for
8/9/10 digits (256/512/1,024 checks). Serial worst-case ten-digit verification took 14.194s.
The UI stays responsive with duplicate actions disabled while work runs. Slower machines may
take longer; the worker limit falls back with available CPU count. Reproduce on the demo machine:

```powershell
./scripts/benchmark-keypad.ps1 -Repetitions 3
```

This explicit benchmark uses generated disposable passwords, never private fixture credentials,
and writes timings/counts to target/keypad-benchmark.csv. It is not part of normal unit tests.
Recorded evidence is docs/phase3-keypad-benchmark.csv and docs/PHASE3-RESULTS.md.

## Tests and project records

JUnit checks launchers, headless Swing construction, SQLite JDBC availability using an
in-memory connection, the BC API, the 64-code audit catalog, and SHA-256/inventory
integrity for all 22 professor-provided files. No passwords are needed for Phase 0.
No cryptographic compatibility is established by these tests.

Phase 1 adds focused cryptographic tests and a separately invoked real-fixture gate:

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
```

Without `-Pfixtures`, tests use generated disposable inputs and public RFC/NIST vectors;
the public certificates and fixture checksums need no passphrases. The fixture profile reads
ignored UTF-8 `.local/fixture-secrets.properties` with keys `admin`, `user01`, `user02`.
Supply values privately; never put them on the command line or commit them. Java properties
escaping applies (for example, a literal backslash must be escaped). The public BCrypt
example is copied from assignment p.3 into ignored `.local/assignment-bcrypt-example.properties`
using keys `password` and `hash`; it is already prepared locally. Missing configuration fails
the fixture gate rather than silently skipping it.

The fixture profile checks SHA-256 before and after crypto tests, verifies both catalogs before
using their relationships, and writes only public identity/group relationships (no secret
filenames or file contents) to ignored `target/fixture-relationships.tsv`. Negative tests use
temporary copies. The cryptographic API returns plaintext only after signature verification
and does not create extracted files. See `docs/PHASE1-RESULTS.md` for exact evidence and limits.

- `PLAN.md`: agreed architecture, phase gates, decisions, and future work.
- `REQUIREMENTS.md`: page-referenced requirements and verification matrix.
- `AGENTS.md`: instructions for future Codex sessions.
- `docs/PHASE0-RESULTS.md`: actual validation results and remaining checks.
- `docs/PHASE1-RESULTS.md`: cryptographic APIs, real-fixture compatibility and test results.
- `docs/PHASE2-RESULTS.md`: schema, registration/startup behavior, audit semantics, tests and limitations.
- `docs/PHASE3-RESULTS.md`: performance gate, MFA/session implementation and validation results.

Runtime artifacts belong in ignored `runtime/` and `output/`; private configuration
belongs in ignored `.local/`. Never write outputs into `Pacote-T3/` or commit secrets.
