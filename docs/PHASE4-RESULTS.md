# Phase 4 results — 17 October 2026

## Scope and outcome

The production vault subsystem and Portuguese consultation flow are implemented: current-user
key validation, authenticated catalogs, deduplicated owner/group/share listing, permission
rechecks, owner/shared extraction, safe publication, counters, Back navigation and audit context.
Phase 5 and operational logView have not started. No commit or push was requested or made.

The original PDFs were consulted for A pp.4–6,8–9 and D pp.3–9, including the consultation
screen and envelope/signature diagrams. Existing Phase 0–3 behavior and algorithms are preserved.

## Implementation and trust boundaries

| Component | Implemented behavior |
| --- | --- |
| Catalog / CatalogParser | Immutable index/share models; ASCII/CRLF records; punctuation in identities/extensions; code/name uniqueness; positive per-file share IDs; valid references/groups; portable safe basenames |
| CatalogService | Both catalogs decrypt and verify before parsing; exact plaintext-byte signatures through the existing ProtectedFileService; owned plaintext buffers cleared on success/failure |
| AccessPolicy | Owner/group/share union without duplicate rows; complete reasons separate from existence of an owner/shared cryptographic route |
| VaultService | Same-database live MFA session; stored encrypted user key/certificate; 10,240-byte possession challenge; scoped key lifetime; verified list/counter commit; fresh catalogs and policy check on selection |
| StartupService / AdministratorContext | Startup opens session-authorized consultation; bootstrap key remains inside its context behind a package-scoped fixed catalog operation, without a key getter |
| SafePaths / publication | Windows-safe basename checks; canonical input/output boundaries; reject unsafe input links, traversal, existing destinations, Pacote-T3 and selected-safe-folder outputs |
| ConsultationPanel / Controller | 255-character folder/phrase controls, masked phrase, folder chooser, Listar, sortable filename/owner/group table, extraction/output chooser, consultation counter and Back |
| SessionContext / ApplicationController | Ordinary-user consultation without registration privilege; session-bound resource cleanup; Back releases key/list; logout closes consultations; bootstrap context survives logout |
| AuditService / UserRepository | File context in Registros, no rendered message copies; consultation count increases once per verified List, atomically with list-presentation events |

All SQL, file work and cryptography run outside the Swing EDT. Buttons disable duplicate actions;
controller generations reject callbacks to disposed views. Disposal also marks the consultation
invalid before asynchronous cleanup. Session monitor locking orders active operations against logout.
The UI clears the phrase immediately after submitting and clears its mutable worker copy afterward.
The service retains only the restored user key until Back/closure, with no personal password.
JCA/JVM-managed copies cannot be guaranteed erased.

Both catalogs must succeed before any rows are exposed. Production metadata is never hardcoded.
The parser rejects duplicate codes (case-insensitive for Windows paths), colliding secret names,
duplicate `(code, share ID)` or `(code, recipient)` pairs, owner self-sharing, unknown groups,
unsafe names, non-ASCII records and absent index references. Catalog owners and recipients must
exist in the database; an unknown owner prevents listing rather than allowing unverifiable output.
Register all three professor identities before using their combined catalog.

## Extraction and publication

Owners use `.env`; explicit recipients use the verified sharing ID's `.N.shr`. The existing
ProtectedFileService recovers RSA/PKCS1 seed bytes, derives the proven AES-256 key, decrypts
AES/ECB/PKCS5Padding, and checks SHA1withRSA with the **owner's stored certificate**.
A recipient's certificate is never substituted as the signing identity.

No temporary plaintext file is created until signature verification succeeds. Verified bytes are
written to a temporary file in the chosen destination directory. Required success audit records
commit before final publication. `Files.createLink(finalName, temporaryName)` creates the final
name atomically and refuses an existing name, including a competing creator; the temporary name
is then removed. Plaintext arrays are cleared in `finally`. Audit rollback creates no final file.
Missing/unsafe input files, RSA/AES failure, invalid signatures, permission denial, group-only
routes, invalid destinations and existing outputs have distinct service categories/messages.

This publication strategy requires a filesystem supporting hard links; NTFS passed here.
Unsupported destinations fail visibly without a fallback that could overwrite a file. Temporary
cleanup errors are surfaced. A failed cleanup after publication can leave two names for an already
verified file; it does not create an unverified file. There is no guaranteed crash/heap erasure.

## Audit and counter semantics

| Trigger | Exact existing event IDs |
| --- | --- |
| Open consultation / Back / List / invalid source folder | 7001 / 7002 / 7003 / 7004 |
| Index decrypted / verified / decrypt failed / verification failed | 7005 / 7006 / 7007 / 7008 |
| Verified index listing presented | 7009 |
| Share decrypted / verified / decrypt failed / verification failed | 7010 / 7011 / 7012 / 7013 |
| Verified sharing listing presented | 7014 |
| File selected / route allowed / route denied | 7015 / 7016 / 7017 |
| File decrypted / verified / decrypt failed / verification failed | 7018 / 7019 / 7020 / 7021 |

Successful List sequence is 7003,7005,7006,7010,7011,7009,7014, following 7001 on opening.
The two presentation events and consultation increment commit together. Invalid/malformed
catalogs expose no rows and increment no counter. Selection revalidates catalogs without another
consultation increment or list-presentation event. Existing 6006/6007 templates describe current-user
key restoration/matching failures; no new codes were invented. Publication I/O failure is reported
with 7020 because the catalog has no distinct output-write event; file verification success events
describe decryption/authenticity, not a guarantee that final publication subsequently succeeded.

File-operation records contain UID, login_context and the selected secret filename in
file_context. Message text stays exclusively in Mensagens. No phrase/password, seed, token,
private key or payload bytes enter audit records. SQL/audit failure propagates, closes consultation
and prevents unrecorded protected publication; the UI invokes its existing fatal shutdown path.

## Verification

Commands (JDK 24.0.2, Maven 3.9.9, Java 17 bytecode target):

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','clean','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
```

| Suite | Actual result |
| --- | --- |
| Default clean verification | 179 tests; zero failures/errors/skips; BUILD SUCCESS (1:24 min) |
| Fixture-enabled clean verification | 202 tests; zero failures/errors/skips; BUILD SUCCESS (52.167s) |

New focused test cases:

| Class | Cases |
| --- | ---: |
| CatalogParserTest | 26 |
| CatalogServiceTest | 2 |
| AccessPolicyTest | 1 |
| VaultServiceTest | 16 |
| ConsultationUiTest | 4 |
| VaultFixtureTest (explicit fixture profile) | 1 |
| New total | 50 |

The previous 130 default / 152 fixture-enabled cases remain unchanged. New service tests cover
all six damaged catalog metafiles; wrong user phrase and stored certificate mismatch; missing
owner envelope; corrupt payload/envelope/signature and recipient envelope; wrong signing identity;
unknown owners; signed metadata changes/share revocation; group-only refusal; no overwrite;
excluded output folders; transactional counter rollback; injected success-audit failure with no
output; same-database/session checks; logout cleanup and bootstrap-context survival.

Controller tests drive real List/extract/Back buttons with generated temporary identities, check
the counter/header, masked 255-character fields, duplicate prevention, EDT responsiveness,
non-administrator privilege rejection and late-result cancellation after disposal. A synthetic
offscreen consultation render is inspected separately from native desktop interaction.
After that inspection, the header alignment and explicit table-column-header attachment were
adjusted; all four ConsultationUiTest cases passed again, and the final JARs were repackaged.

Both executable JARs pass `--check`. ZIP inspection finds no credentials, original fixture
package, runtime databases or test helpers in either JAR. The final 22 SHA-256 checksums match;
`git diff --check` reports no whitespace errors (existing Windows line-ending notices remain).

The first extraction-positive service run inside the Windows sandbox failed with access denied
at hard-link publication (other 17 focused cases passed). Repeating that case outside the sandbox
passed. The complete verification commands use the normal approved escalation for this reason;
the safety operation was preserved. Existing JDK native-access/Unsafe and shade-manifest warnings
remain non-fatal. No additional runtime dependency was introduced.

## Professor-fixture permission matrix

Identities below use `@inf1416.puc-rio.br`. Entries and routes are derived by the production
parser from the actual verified administrator catalogs, then checked against expected identities.
Secret filenames and payload contents are deliberately omitted from this report.

| Current user | XXYYZZ00 | XXYYZZ11 | XXYYZZ22 |
| --- | --- | --- | --- |
| admin | Owner + group; owner `.env` | Denied | Denied |
| user01 | Denied | Owner + group; owner `.env` | Group + explicit share; `.1.shr`, signature by user02 |
| user02 | Denied | Group listing; extraction unavailable | Owner + group; owner `.env` |

The fixture gate extracts all three owner routes and user01's explicit recipient route into
temporary output directories, comparing bytes with independently owner-decrypted/verified memory.
Recipient and owner content must match. Corrupted actual signature/ciphertext copies must leave
their output directories empty. Original checksums are checked before/after this test and through
the unchanged fixture integrity tests. Non-secret matrix evidence is written to ignored
`target/phase4-permissions.tsv`; no runtime database is seeded and credentials are never printed.

## Manual desktop walkthrough

1. Build from the repository root, then launch:

   ```powershell
   & 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar
   ```

2. Complete bootstrap/startup and MFA. As administrator, register both professor users through
   the existing certificate/enrollment flow. Each identity has its own chosen numeric password
   and Authenticator account. Startup validation alone grants no registration authority.
3. Complete MFA as the desired identity. Open Consultar Pasta de Arquivos Secretos. Check the
   login/group/name header and current consultation count. Select `Pacote-T3/Files`; enter that
   current user's **private-key passphrase**, then Listar. Check the rows against the matrix.
4. Select an eligible row and Extrair arquivo selecionado. Choose an existing output directory
   outside Pacote-T3 and outside the safe folder, on NTFS. Inspect the saved verified document
   locally. Repeat with an empty different directory; an existing name must be refused.
5. Demonstrate admin/XXYYZZ00, user01/XXYYZZ11, user02/XXYYZZ22 and user01/XXYYZZ22 sharing.
   Demonstrate that user02 can list XXYYZZ11 through the group but receives the missing-envelope
   explanation on extraction. No administrator override or borrowed key is available.
6. Voltar returns to the main menu, releasing the consultation key. Reopen and check the saved
   consultation count. Logout/reauthenticate and restart; counts persist and role restrictions stay.
   Use only temporary copies for corruption demonstrations; never alter the original package.

## Remaining limitations and stopping point

- Group-based listing is implemented. **Group-only extraction remains unresolved** pending a
  professor-defined envelope/key mechanism; valid owner and explicit-sharing routes are independent.
- Hard-link-capable destination filesystem required; the Codex sandbox itself blocks publication.
- Native desktop/physical-phone walkthrough and execution on an actual Java 17 runtime remain
  unperformed. Tests use headless Swing controllers and `--release 17` on the installed JDK 24.
- Phase 5 independent authenticated log viewer and final audit/submission integration remain future work.

**Stop after Phase 4 and wait for user review. No Phase 5 implementation, commit or push.**
