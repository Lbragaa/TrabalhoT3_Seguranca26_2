# Final delivery checklist — Cofre Digital

Latest review: `docs/FINAL-AUDIT-RESULTS.md`. The final audited release is in
`output/delivery-final-audit/`; older `output/delivery/` files are historical.

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

## Manual smoke test starting with an existing administrator

Run from the folder containing your intended `runtime/cofre.db`. Do not delete, replace or
reset that database. These actions intentionally add normal audit records/counters; registration
adds an account only after confirmation. Use your already enrolled phone account and personal
password. If its enrollment is unavailable, stop: this application has no recovery bypass.

1. **Startup:** run `& $java -jar target/cofre-digital.jar`. Enter the bootstrap administrator's
   private-key phrase. Expect the email login screen, not a menu or a new-registration form.
   A wrong phrase must notify and terminate. An unexpected bootstrap form usually means the
   working directory/database is wrong; stop without registering a replacement administrator.
2. **Administrator MFA:** enter the registered email, use the button containing each personal
   password digit (labels reshuffle after every click), then enter the six-digit code from the
   actual Google Authenticator app. Expect a responsive UI and the administrator menu only
   after the token succeeds. A menu before the token, frozen controls, or rejection of repeated
   fresh correct phone codes is a failure to investigate (also check both device clocks).
3. **Menu/registration:** check login/group/name/access count. Open Cadastrar, check the user
   count and certificate fields. If user01/user02 are not registered, register their original
   pairs, enroll each displayed secret on the phone and confirm. Success must clear the form
   and update the count. If already registered, cancel certificate confirmation or exercise
   duplicate rejection; expect retained input and no additional account. Back must return to
   the menu. Never recreate an existing account merely to repeat this check.
4. **Consultation:** use Sair -> Encerrar Sessão, complete user01 MFA and check that Cadastrar
   is absent. List `Pacote-T3/Files` with user01's key phrase after all three identities exist.
   Expect its owned file and user02's explicit share; select each row and press Extrair into
   separate empty NTFS folders outside the safe/fixture folder. Expect verified output and a
   success notice. Repeat to the same destination: refusal, with original output unchanged.
   As user02, the user01 group-visible file must report the missing-envelope limitation, with
   no output. That refusal is an acknowledged compliance gap, not proof of group decryption.
5. **Back/logout/persistence:** Back must release consultation, returning to the menu. Use
   Sair -> Voltar, then Sair -> Encerrar Sessão; expect login stage 1. Exit/relaunch and log in
   again. Startup validation must recur, counts must persist, and access count must increase
   only after full MFA. Missing counts, lingering privileged screens, or extraction after
   logout are failures. Deliberate three-error lockouts are optional here; automated tests
   already check restart persistence and the exact 120-second boundary.
6. **Independent viewer:** close the vault; in interactive PowerShell run `chcp 65001`, then
   `& $java -jar target/logView.jar 'Pacote-T3/Keys/admin-pkcs8-aes.key' --database 'runtime/cofre.db'`.
   Expect a non-echoed phrase prompt and chronological time/code/resolved-message rows with
   readable accents. Wrong phrases and an ordinary user's matching key must produce no rows.
   Displayed password characters, records before authentication, or a writable/mutated database
   are failures. Missing bootstrap contexts may correctly remain literal placeholders.

These six checks are **not marked passed** by automated service/headless Swing tests.

## Reproduce the fixture demonstration from an intentionally fresh database

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
- [ ] Review `docs/FINAL-AUDIT-RESULTS.md` for final executed results and outstanding conditions.
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

The final audited files are `output/delivery-final-audit/cofre-digital-submission.zip`, both executable JARs
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
