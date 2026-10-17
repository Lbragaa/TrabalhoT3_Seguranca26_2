# Phase 0 results — 17 October 2026

## Implemented

- Maven project targeting Java 17 (`--release 17`), with pinned build plugins and dependencies.
- Project-local PowerShell Maven 3.9.9 bootstrap, pinned SHA-512 validation, local cache,
  explicit real JDK selection and process-scoped JAVA_HOME; no system PATH/registry changes.
- Package scaffold, Portuguese Swing shell, independent vault and logView executable entry
  points with non-mutating `--help`/`--check`. Viewer denies operational launch (exit 2).
- Exact 64-code audit definition catalog extracted from A pp.8–9. No audit persistence yet.
- SHA-256 baseline for all 22 professor fixtures, read-only inventory/integrity test.
- JUnit foundation infrastructure, ignore rules, README, PLAN.md, REQUIREMENTS.md, AGENTS.md.

## Actual verification

Environment: Windows 11 amd64, Oracle JDK 24.0.2 at `C:\Program Files\Java\jdk-24`,
Maven 3.9.9. JARs target Java 17; javap reports application class major version **61**.

| Check | Result |
| --- | --- |
| Maven distribution SHA-512 | Matches official distribution checksum, pinned in bootstrap script |
| `./scripts/maven.ps1 -MavenArgs '-U','clean','verify'` | BUILD SUCCESS (36.468s initial download/build) |
| `./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','verify'` | BUILD SUCCESS (9.639s; proves cached offline build) |
| AuditEventTest | 2 tests passed; exact expected code ranges, 64 unique definitions, template contexts/lookup |
| FixtureIntegrityTest | 1 test passed; all 22 originals match SHA-256 and inventory |
| FoundationTest | 4 tests passed; entry points, EDT Swing panel construction, in-memory SQLite, BC API presence |
| Combined JUnit result | **7 tests, 0 failures, 0 errors, 0 skipped** |
| Packaged cofre-digital.jar `--check` | Exits 0, correctly identifies Phase 0 shell |
| Packaged logView.jar `--check` | Exits 0, explicitly reports authentication/viewing unavailable |
| Packaged logView.jar without flags | Exits 2; does not read records |
| Independent PDF/catalog comparison | All 64 template strings exactly match extracted A pp.8–9 text |
| Packaged artifact inspection | Correct independent Main-Class manifests; professor fixtures not embedded |
| Ignore rules | Tool/cache, target artifacts, local secrets, runtime DB, output examples are ignored |

The build creates `target/cofre-digital.jar` and `target/logView.jar`, each containing its
manifest entry point and bundled runtime dependencies. Test reports are under
`target/surefire-reports/`. The original PDFs and fixture package were not edited.

## Limitations and blockers

- No blocker remains for compilation, unit tests or packaging on the installed JDK.
- Network/DNS restrictions initially blocked downloads. Approved network access completed
  bootstrap and dependency resolution; the cached build subsequently succeeded offline.
- A real Java 17 runtime is not installed here. Actual Java 17 execution remains unverified;
  compilation with release 17 and bytecode version verification are completed.
- Swing panel construction was tested headlessly on the EDT. The desktop window has not
  been visually inspected; run the vault without flags to perform that manual check.
- JDK 24 emits upstream native-access/Unsafe warnings from Maven/SQLite; the build succeeds.
  Maven Shade reports overlapping dependency manifests; explicit manifest transformers set
  the independent executable main classes. Both packaged launcher checks succeed.
- The restricted shell also prints a non-fatal `Access is denied` during Maven command
  initialization; Maven itself returns success. The approved online build did not show it.
- No fixture passphrase was read, no cryptographic compatibility asserted, no application
  database/schema created, and no registration, MFA, extraction or audit viewer implemented.
- Future group-only extraction still requires professor clarification; private fixture
  credentials will be required locally for the Phase 1 interoperability gate.

**Stopped at Phase 0. Await explicit user approval before Phase 1.**
