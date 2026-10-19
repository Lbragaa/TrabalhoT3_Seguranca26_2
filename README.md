# Cofre Digital — INF1416 / Trabalho T3

Integrantes: **Luan Braga (2411776)** e **Diogo Benchimol (2312917)**.

Final compliance review: [FINAL-AUDIT-RESULTS](docs/FINAL-AUDIT-RESULTS.md).
Latest release and hashes: `output/delivery-20261019/` (local generated output).
Use [FINAL-DELIVERY-CHECKLIST](FINAL-DELIVERY-CHECKLIST.md) for the existing-account manual smoke test.

Java SE application for the PUC-Rio digital-vault assignment.
**Phases 0–5 are implemented.** The application supports SQLite persistence, registration,
administrator startup validation, virtual-keypad password verification, TOTP MFA, role menus,
registration authorization, logout, persistent account lockouts, verified vault consultation
owner/shared extraction and the independent administrator-only audit viewer. Group-only
extraction awaits professor clarification. See FINAL-DELIVERY-CHECKLIST.md before submission.

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
The compiler uses `--release 17`. Phase 5 also executes tests and packaged checks with a real,
checksum-verified Temurin Java 17 JRE under ignored `.tools/java17`; it does not change system PATH.
Do not use the Oracle `javapath` shim if it hangs; select the real JDK directory.

## Executables

```powershell
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar --check
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/logView.jar --help
& 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar
# Real terminal, hidden input; uses existing runtime/cofre.db:
./scripts/logview.ps1 'Pacote-T3/Keys/admin-pkcs8-aes.key'
```

The last command opens the first-run registration or administrator startup-validation screen.
The PowerShell launcher sets session-only UTF-8 decoding as well as output encoding. `chcp`
alone can leave Windows PowerShell decoding Java output with its previous encoding.
Use `-JavaPath 'C:\path\to\java.exe'` or `-Database 'runtime/cofre.db'` when needed.
It never accepts or reads a passphrase itself; the independent Java viewer still uses Console.readPassword.

`--check`/`--help` do not create files or read secret material. logView requires a registered
administrator key and System.console().readPassword; redirected/no-console input is refused.
Use optional `--database <existing-path>` after the key argument to select another database.
The two executable JARs bundle dependencies and have independent main classes. The vault JAR
excludes viewer classes; the viewer JAR excludes vault/authentication/UI application classes.

## Independent audit viewer

logView restores the supplied encrypted RSA key and proves possession using a fresh signed
2,048-byte challenge against registered administrator certificates. Ordinary keys, wrong phrases,
invalid paths/certificates and missing secure input fail before audit records are displayed.
It opens SQLite read-only, reads templates from Mensagens and records from Registros, then
orders by timestamp and RID. Output includes timezone-aware timestamps, codes and stored context.
Missing login/file context leaves its placeholder intact; malformed records are labelled safely.
There is no audit-viewing option in the vault application.

Local Java 17 verification can be reproduced with the ignored JRE prepared on this machine:

```powershell
$java17 = (Resolve-Path '.tools/java17/jdk-17.0.20.1+1-jre/bin/java.exe').Path
& $java17 -jar target/cofre-digital.jar --check
& $java17 -jar target/logView.jar --check
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures',"-Djvm=$java17",'clean','verify'
```

This JRE is not included in delivery; use your installed Java 17+ runtime on another machine.

On a Phase 4 database, viewer reading does not migrate or seed data. Normal vault startup adds
nullable publication_context transactionally. File verification event 7019 can show pending,
published or failed publication independently of successful cryptographic verification. Output-I/O
failure no longer falsely emits the decryption-failure event. Audit storage still contains message
references/context only. See docs/AUDIT-COVERAGE.md and docs/PHASE5-RESULTS.md.

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
   cannot. Consultation opens the verified vault workflow described below. Sair do Sistema offers
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

## Manually check vault consultation

1. Register the three professor identities through the normal administrator flow, enrolling
   each separate Authenticator secret. Owners/recipients referenced by catalogs must be registered.
2. Complete MFA as the desired identity, then choose Consultar Pasta de Arquivos Secretos.
   Select `Pacote-T3/Files` as the safe folder and supply **that user's private-key passphrase**.
3. Press Listar. Both administrator catalogs must decrypt and verify before rows appear.
   The header's consultation count increases once per successful List. The phrase field clears.
4. Select a row, press Extrair arquivo selecionado, and choose an output directory such as
   `output/consulta` (create it first). Keep it outside `Pacote-T3` and the selected safe folder.
   Existing files are refused; choose a different empty directory to repeat extraction.
5. Owners extract through `.env`. User01 can also extract user02's shared XXYYZZ22 through
   `.1.shr`, verified using user02's certificate. User02 sees XXYYZZ11 through group membership,
   but extraction reports that no recipient envelope is specified. No other private key is borrowed.
6. Voltar clears the consultation's key/list and returns to the main menu. Logout revokes the
   session and all its consultations; startup administrator material stays until system termination.

Publication uses an atomic hard-link create after verification/audit, followed by temporary-name
cleanup. Use a filesystem supporting hard links (NTFS works on this host). Unsupported destinations
fail explicitly. The Codex Windows sandbox blocks this operation: extraction tests require normal
escalated execution; they pass outside the sandbox. Native desktop/phone walkthrough remains manual.

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
- `docs/PHASE4-RESULTS.md`: vault permission matrix, extraction, audit/UI evidence and limitations.
- `docs/PHASE5-RESULTS.md`: viewer, final integration, runtime/terminal and packaging results.
- `docs/AUDIT-COVERAGE.md`: all 64 event producers and supporting scenarios.
- `FINAL-DELIVERY-CHECKLIST.md`: exact commands, demonstration, submission and remaining checks.

After verification, `./scripts/package-delivery.ps1` produces an allowlisted source/fixture/JAR
archive and SHA-256 hashes under ignored output/delivery. It refuses a nonempty destination;
choose another -OutputDirectory after further changes. Local configuration/runtime data and
tool distributions are excluded. Each group member must submit the Java-source project via EAD,
as described in the final checklist; no upload is performed by the script.

Runtime artifacts belong in ignored `runtime/` and `output/`; private configuration
belongs in ignored `.local/`. Never write outputs into `Pacote-T3/` or commit secrets.
