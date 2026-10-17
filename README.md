# Cofre Digital — INF1416 / Trabalho T3

Java SE application foundation for the PUC-Rio digital-vault assignment.
**Phases 0–2 are implemented.** The tested cryptographic engine now supports SQLite
persistence, transactional registration, administrator bootstrap, startup validation and
Portuguese Swing forms. MFA login, the virtual keypad, user main menu, vault and operational
audit viewer await later phases.

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

## Manually check Phase 2

Run commands from the project root. Normal startup automatically creates `runtime/cofre.db`;
do not prepopulate it with test credentials.

1. On the first run, select the administrator certificate and encrypted private key, enter
   its private passphrase, and choose an 8–10 ASCII-digit personal password with no equal
   adjacent digits. Confirm that password. The bootstrap group is fixed to Administrador.
2. Click Cadastrar. Review version/serial/validity/signature/issuer/subject/name/email in
   the certificate dialog. Canceling either dialog creates no account and retains the form.
3. Accept the certificate, then enroll the displayed Base32 key as a time-based token in
   Google Authenticator. Acknowledge enrollment to commit the account. No QR code is required.
4. The form clears and startup reaches an explicit **Phase 3 unavailable** screen. This
   screen is not a logged-in administrator session and offers no user-registration shortcut.
5. Close using Encerrar sistema, then run the same command again. Supply the administrator's
   private-key passphrase; the key is now loaded from the database, not the original file path.
   An incorrect phrase/key validation ends the process after an error notification.

The administrator-authorized registration component is implemented and exercised with a
test-only authorization setup. Production navigation to it must wait for Phase 3 MFA;
startup key validation deliberately cannot grant registration privileges. Do not add a demo bypass.
The default registration service/UI tests use generated certificates and disposable temporary
databases. The fixture profile additionally registers all three professor identities in a
temporary database and verifies their encrypted records after reopening it. It does not seed
the normal runtime database or print credentials.

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

Runtime artifacts belong in ignored `runtime/` and `output/`; private configuration
belongs in ignored `.local/`. Never write outputs into `Pacote-T3/` or commit secrets.
