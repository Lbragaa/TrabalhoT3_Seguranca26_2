# Cofre Digital — INF1416 / Trabalho T3

Java SE application foundation for the PUC-Rio digital-vault assignment.
**Only Phase 0 is implemented.** There is no authentication, cryptographic engine,
application database, vault access, or operational audit viewer yet.

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

- `PLAN.md`: agreed architecture, phase gates, decisions, and future work.
- `REQUIREMENTS.md`: page-referenced requirements and verification matrix.
- `AGENTS.md`: instructions for future Codex sessions.
- `docs/PHASE0-RESULTS.md`: actual validation results and remaining checks.

Runtime artifacts belong in ignored `runtime/` and `output/`; private configuration
belongs in ignored `.local/`. Never write outputs into `Pacote-T3/` or commit secrets.
