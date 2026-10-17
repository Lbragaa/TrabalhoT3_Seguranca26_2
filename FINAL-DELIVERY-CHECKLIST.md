# Final delivery checklist — Cofre Digital

## Build and required files

- Use Windows with a desktop-capable Java 17+ runtime. Building needs JDK 17+ and PowerShell.
- Keep `pom.xml`, `src/`, `scripts/`, the original `Pacote-T3/` fixtures and project documentation.
  The default tests verify the original 22-file inventory even without private credentials.
- SQLite JDBC, Bouncy Castle and SLF4J runtime dependencies are bundled in each executable JAR;
  no separate SQLite server or dependency installation is needed to run them.
- Runtime/database directories must be writable. Extraction destinations must support hard links
  (NTFS verified), be outside Pacote-T3 and the selected safe folder, and contain no conflicting file.

Run from the project root:

```powershell
./scripts/maven.ps1 -JdkHome 'C:\Program Files\Java\jdk-24' -MavenArgs clean,verify
# Offline after dependency setup:
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','clean','verify'
# Full professor-fixture gate, with private local configuration already prepared:
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
```

`scripts/maven.ps1` resolves Maven 3.9.9 locally, verifies its archive and targets Java 17 bytecode.
It does not change PATH or the registry. Restricted agents need normal escalation for extraction
checks because this Windows sandbox blocks hard links. Ordinary local terminals do not use that sandbox.

## Executable commands and initialization

The following exact commands use the installed JDK; another Java 17+ path is acceptable:

```powershell
$java = 'C:\Program Files\Java\jdk-24\bin\java.exe'
& $java -jar target/cofre-digital.jar --check
& $java -jar target/logView.jar --check
& $java -jar target/cofre-digital.jar
# In a real interactive terminal; do not pipe/redirect a password:
chcp 65001
& $java -jar target/logView.jar 'Pacote-T3/Keys/admin-pkcs8-aes.key'
# Select another existing database when necessary:
& $java -jar target/logView.jar 'Pacote-T3/Keys/admin-pkcs8-aes.key' --database 'runtime/cofre.db'
```

The vault automatically creates the five-table database at `runtime/cofre.db`. A fresh database
requires initial administrator registration and Authenticator enrollment; there are no demo accounts,
seed passwords or authentication bypasses. Later startup requires the bootstrap administrator's
private-key passphrase, followed by ordinary three-stage MFA. Do not replace an existing database
to perform a fresh demo without preserving it intentionally first.

logView opens only an existing database, read-only, and never initializes it. The supplied private
key must match a registered administrator certificate. It prompts using Console.readPassword,
rejects unavailable secure input, and never accepts a passphrase via argument/environment/file.
An ordinary user's matching key is insufficient. The vault has no audit-viewing menu, and its
JAR excludes viewer classes; logView.jar excludes the vault/authentication/UI application packages.

Phase 5 adds nullable `Registros.publication_context`; normal vault initialization migrates older
databases transactionally without deleting users/logs. logView can read a Phase 4 database without
performing that migration. `pending` is unconfirmed publication, `published` confirms it, and
`failed` reports output failure after valid cryptographic verification. Older null context is unknown.

## Reproduce the fixture demonstration

1. Start the vault with a fresh intended runtime database. Register the professor administrator
   certificate/key, choosing a disposable valid 8–10-digit personal password with no adjacent repeat.
   Confirm certificate details, enroll the displayed Base32 secret in Google Authenticator, acknowledge.
2. Complete administrator email -> reshuffling virtual keypad -> six-digit phone TOTP login.
   Register user01 and user02 using their supplied pairs and separate chosen passwords/token secrets.
   Ordinary users must have no registration button or registration service capability.
3. Select consultation, choose `Pacote-T3/Files`, and enter the **current user's private-key phrase**.
   Press Listar; verify filename/owner/group rows and the consultation count.
4. Extract admin/XXYYZZ00, user01/XXYYZZ11 and user02/XXYYZZ22 through owner routes. Also extract
   XXYYZZ22 as user01 through `.1.shr`; it must verify against user02's signing certificate.
5. Verify user02 sees XXYYZZ11 through group membership but receives the explicit missing-envelope
   limitation when extracting. Admin has no automatic access to ordinary-user files.
6. Use empty, separate NTFS output folders for repeated extraction. Existing names must be refused.
   Use temporary fixture copies for wrong/corrupt signatures/ciphertexts; never alter the originals.
7. Voltar, logout and restart. Verify counters persist and the administrator startup phrase is requested.
   Three wrong submitted passwords or tokens block only that account for 120 seconds, across restart.
8. Launch logView in a real terminal with the administrator key path. Confirm hidden input, successful
   audit inspection, date/time/code/context resolution and chronological order. Ordinary keys/wrong
   phrases must fail before any records appear. No-console launches must fail without echoed fallback.

For the automated equivalent, use the full fixture command above. FinalFixtureIntegrationTest
executes the complete service workflow in temporary directories with real professor pairs and
catalogs, disposable personal passwords, controlled clocks and independent viewer authentication.
Fixture phrases are read only from ignored `.local/fixture-secrets.properties`; the public assignment
BCrypt sample is in ignored `.local/assignment-bcrypt-example.properties`. Neither file is for submission.

## Checks before submission

- [ ] Perform real Google Authenticator enrollment and MFA on the demonstration computer/phone.
- [ ] Complete the desktop owner/shared, Back/logout/restart and role-menu walkthrough above.
- [ ] Run logView in the actual presentation terminal and check displayed accents/timezone/context.
- [ ] Reproduce keypad benchmark on a materially slower demonstration machine if necessary.
- [ ] Resolve or explicitly disclose group-only extraction and provisional adjacent-repeat policy.
- [ ] Review `docs/PHASE5-RESULTS.md` for executed results and outstanding manual conditions.
- [ ] Keep local credentials, databases, extracts, demo identities and tool distributions out of submission.
- [ ] Verify ZIP contents and artifact hashes after any final changes. Do not submit an old build.

## What the assignment actually requests

Assignment A p.7 requires submission of the **project with all Java source files** through PUC-Rio
EAD by **18 October 2026, 23:59**; the maximum late time is **19 October 2026, 11:59**.
**Each group member must submit individually.** A p.1 lists presentations on 19–23 October.
The source does not prescribe an archive name or make JARs a substitute for source.

After verification, prepare the allowlisted source/JAR delivery and SHA-256 hashes:

```powershell
./scripts/package-delivery.ps1
# To regenerate after changes, choose a new empty output directory:
./scripts/package-delivery.ps1 -OutputDirectory 'output/delivery-revised'
```

The prepared files are `output/delivery/cofre-digital-submission.zip`, both executable JARs
and `SHA256SUMS.txt`. The ZIP contains all Java sources, build/run docs, tests, original encrypted
professor fixtures and the two JARs under target/. It contains no local credential configuration,
registered runtime database, extracted content, generated demo material or tool/runtime distributions.
The script refuses to replace a nonempty delivery directory.

Recommended delivery is a source/build/documentation archive plus both executable JARs. Include
Maven configuration, tests and run instructions so the project can be rebuilt. Preserve supplied
fixtures if included; these are the original encrypted professor data, not a registered runtime DB.
Exclude `.local/`, `.tools/`, `runtime/`, extracted `output/`, `.git/` and all locally generated keys.
No upload/submission, commit or push is performed by this phase.

## Readiness limits

The implemented owner/shared workflows and audit viewer have automated coverage. Overall assignment
compliance must still disclose the missing group-envelope mechanism and unperformed physical-phone/
native desktop checks. The hard-link filesystem restriction remains intentional; no unsafe fallback
or invented group cryptography is provided. See the Phase 5 report for Java 17 execution evidence.
