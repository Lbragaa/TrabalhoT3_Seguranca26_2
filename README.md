# Cofre Digital — INF1416 / Trabalho T3

Java SE application foundation for the PUC-Rio digital-vault assignment.
**Phases 0 and 1 are implemented.** The standalone cryptographic engine is tested against
the professor fixtures. There is no application authentication, database, vault UI, keypad,
or operational audit viewer yet; the executable launchers remain the original foundation shells.

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

The last command opens a Portuguese Swing foundation shell on a graphical desktop.
`--check`/`--help` do not create files or read secret material. `logView` without those
flags exits with code 2: audit viewing is deliberately unavailable until implemented.
The two executable JARs bundle runtime dependencies and have independent main classes.

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

Runtime artifacts belong in ignored `runtime/` and `output/`; private configuration
belongs in ignored `.local/`. Never write outputs into `Pacote-T3/` or commit secrets.
