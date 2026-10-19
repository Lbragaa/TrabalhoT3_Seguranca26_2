# Approved implementation roadmap

19 October follow-up evidence and the Windows PowerShell UTF-8 launcher are recorded in
the dated FINAL-AUDIT-RESULTS addendum. Latest release: output/delivery-20261019/.
Native phone/MFA, extraction, sharing and persistence were actually exercised with the user;
group-only decryption remains unresolved. No additional development phase was added.

Latest checkpoint: the user-authorized final pre-submission audit is recorded in
`docs/FINAL-AUDIT-RESULTS.md`. Both complete suites were rerun after the UTF-8 fix
(190 default / 214 on Java17, zero failures/errors/skips). Audited delivery is in
`output/delivery-final-audit/`. Prior phase details below preserve historical decisions.

## Authorization and current boundary

Architecture and all six phases are approved as a roadmap. Current execution authorization
is **Phase 5**, explicitly authorized on 17 October 2026. All six phases have implemented
their authorized scope; final evidence is in `docs/PHASE5-RESULTS.md`.
The benchmark gate passed using bounded parallel full enumeration, before authentication UI work.
Phase 3 is complete: clean verification passes 130 default / 152 fixture-enabled tests,
with zero failures, errors or skips. See the phase report for unperformed manual checks.
Stop after Phase 5 and await review. Native desktop/phone checks and group-only extraction
remain unresolved/unperformed, so overall assignment compliance is conditional.
Phase 4 verification: 179 default / 202 fixture-enabled tests, zero failures/errors/skips;
actual owner and explicit-share extraction pass. See its report for remaining manual checks.
Phase 5 verification: 190 default tests on JDK 24 / 214 fixture-enabled tests on actual Java 17,
zero failures/errors/skips; 12 focused viewer/launcher cases also pass after the final encoding fix.
Both integration paths and real non-echo terminal viewer authentication pass. No commit or push.

## Architecture

One Maven project, package root `br.pucrio.inf1416.cofre`, Java 17 target, Swing UI,
SQLite through plain JDBC, Bouncy Castle OpenBSDBCrypt, JUnit 5. No ORM/DI framework.
Two executable artifacts: `cofre-digital.jar` and `logView.jar`; the latter is an
independent process. The vault has no audit-viewing menu or viewer invocation.

| Package | Planned components |
| --- | --- |
| app | VaultMain, StartupService, administrator lifetime context, shutdown |
| crypto | LegacyKeyDerivation, CertificateService, PrivateKeyService, EnvelopeService, SignatureService, PasswordHasher, supplied Base32, assignment TOTP |
| db | Database, schema, JDBC repositories for users, keys, groups, audit |
| auth | RegistrationService, AuthenticationService, VirtualKeypad, KeypadPasswordVerifier, SessionContext |
| vault | CatalogService, CatalogParser, AccessPolicy, VaultService, catalog records |
| audit | AuditEvent, AuditService, exact message catalog |
| ui | Startup, three login stages, menu, registration, consultation, exit; CardLayout |
| logview | LogViewMain, hidden-input console authentication and chronological output |

Phase 0 creates only the entry points, Swing shell, event definitions, package placeholders,
build infrastructure, documentation, and foundation tests. It does not create service stubs
that pretend to implement later-phase behavior.

## Contracts to preserve in later phases

Phase 1 proves the proposed SUN/SunJCE profile against all supplied keys/catalogs/payloads
and the sharing envelope. No alternative seed encoding, key size or algorithm was required.
Crypto services, database, transactional registration, audit persistence and startup/forms are
implemented. Phase 3 adds MFA, keypad, role menus, session authorization/revocation and minimum
exit navigation. Phase 4 adds verified catalogs, access policy, session-bound consultation,
owner/shared extraction, safe publication and consultation counters/audit. Phase 5 implements the
independent read-only audit viewer and final integration/delivery preparation.

Phase 2 registration capabilities have package-private issuance, database identity binding,
revocation and role rechecks at prepare and commit. AuthenticationService now issues them through
SessionContext only after complete administrator MFA; startup READY is still not a session.
Normal startup uses ignored runtime/cofre.db. Group IDs 1/2 map exactly to administrador/usuario
and UI Administrador/Usuário. Reciprocal composite UID/KID foreign keys are deferred until commit.
Registration validates and snapshots source files before dialogs; cancellation inserts no account.
Accepted enrollment commits user/key/token and event 6008 together. There is no invented success
MID or dedicated duplicate-email event in the professor catalog; 6002 records the attempted submit.

Phase 3 implementation decision: keep all eligible BCrypt checks and parallelize them across
at most four workers. Three-run worst-case medians: serial 3.547/7.091/14.194s; four workers
0.935/1.846/3.667s (8/9/10 digits). No auxiliary password hash, plaintext storage, lower cost,
early match return or changed keypad behavior was introduced. Long-running scans stay outside
database write transactions and the EDT; current block state is rechecked transactionally afterward.
Session creation/access increment and audit commit together; logout closes the capability.
Candidate scan buffers and recovered password copies are cleared; only the token validator
survives into stage 3 and is closed on success, restart, block or shutdown.

Phase 4 boundary: StartupService opens a VaultService only for an active MFA session bound to
the same Database instance. AdministratorContext offers only a package-scoped catalog operation;
its key is never returned. Both signed catalogs are verified before strict ASCII parsing.
Selection reloads/verifies catalogs and rechecks policy; group-only listing never borrows keys.
Consultation counters increase once per verified List, atomically with 7009/7014. User keys
are scoped to consultation and cleared on Back/session close. Output uses a verified temporary
file and atomic no-clobber hard-link creation, then removes the temporary name; unsupported
filesystems fail explicitly. This Windows sandbox blocks hard links, so extraction checks
must run with normal tool escalation. No cryptographic shortcut or fixture change is permitted.

Phase 5 boundary: viewer reads only an existing read-only database, proves a registered admin's
private-key possession with 2,048 fresh random bytes, and accepts production phrases only via
Console.readPassword. Its bound closeable authorization gates every log query. It resolves only
stored contexts with no guessed identities, escapes terminal controls and labels malformed records.
Additive publication_context on 7019 distinguishes pending/published/failed output from successful
crypto verification; output failure no longer falsely emits 7020. Existing five tables/templates
are preserved. See docs/AUDIT-COVERAGE.md for all 64 MID producers and supporting scenarios.

- Fresh SUN SHA1PRNG instance for each deterministic derivation, seeded before output;
  AES KeyGenerator at 256 bits. Start with UTF-8 password/passphrase bytes and raw envelope
  seed bytes. Freeze provider/encoding only after actual fixture interoperability proof.
- AES/ECB/PKCS5Padding, RSA/ECB/PKCS1Padding, SHA1withRSA for vault signatures.
  Do not confuse certificate SHA256withRSA signatures with protected-file signatures.
- Private-key files decrypt to PEM PKCS#8; strip PEM armor then Base64-decode DER.
  Extract certificate PEM despite the human-readable prefix in supplied certificates.
- BC OpenBSDBCrypt version 2y, cost 8, random salt, exact 60-character stored value.
- Registration validates a 10,240-byte signature challenge. Viewer uses 2,048 bytes.
- TOTP is handwritten to assignment structure; 20-byte random secret, supplied Base32,
  HMAC-SHA1, six digits, 30-second counter and exact +/- one-step window. Only supporting
  classes Mac, SecretKeySpec, Date, Base32; numeric-time helpers permit deterministic tests.
- Store Base32 token secret under AES-256 derived from the personal password. Display it
  for Google Authenticator enrollment; optional QR is deferred.
- Schema has exactly the required five application tables: Grupos, Usuarios, Chaveiro,
  Mensagens, Registros. Usuarios holds identity, GID/KID, BCrypt, encrypted token secret,
  failure counts, blocked-until, access/consultation counts and unique bootstrap flag.
  Chaveiro holds unique KID/UID, original encrypted key BLOB, certificate PEM. Deferred
  reciprocal UID/KID foreign keys allow atomic registration. Enable foreign keys per connection.
- Mensagens holds MID/event code and exact template. Registros holds RID, timestamp, MID,
  nullable UID, login/file context, never rendered message text. Audit failures must surface
  and prevent unrecorded protected operations. Seed exact catalog, add events with features.
- Login state: startup -> login -> password -> token -> authenticated. Persist separate
  failure counters and lockouts. Three submitted failures block only that account for 120s;
  each successful stage resets its own count. Expiry clears the completed lockout cycle.
- Keypad captures displayed pairs before each reshuffle. Enumerate 2^n candidate passwords
  for 8–10 clicks, prune using shared password policy, check BCrypt without a shortcut hash.
  Initially scan all eligible candidates; benchmark on demo machine. Background workers,
  disabled duplicate submission, EDT-only Swing updates, stale-result protection required.
- Recovered personal password exists only long enough to decrypt the token secret, then clear
  mutable buffers. Clear token secret after stage 3. Never persist a reversible personal password.
- Catalogs index/share require bootstrap administrator decryption and signature verification
  before parsing/listing. Verify exact plaintext bytes. Listing is deduplicated owner/group/share
  union; owner .env and explicit recipient .N.shr decrypt with current user's key.
- Verify content against owner's certificate, including shared files. Unknown owner fails safely.
  Recheck access at extraction; no borrowing other users' keys for group-only access.
- Output preserves safe secret basename in a selected directory outside the fixture tree;
  reject traversal and existing outputs; publish only after valid signature.
- Preserve bootstrap administrator passphrase in memory through process lifetime, clear on
  shutdown. Clear current-user private material on leaving consultation/logout. Minimize
  immutable secret strings; do not claim guaranteed JVM heap erasure.
- Viewer takes key path on command line, identifies a registered administrator through stored
  certificates, reads passphrase using a real no-echo console, rejects unavailable Console,
  authenticates possession, then joins messages and orders by timestamp/RID.

## Phase gates

| Phase | Implementation goals and primary acceptance | Dependencies / blockers |
| --- | --- | --- |
| 0 Foundation | Pinned Maven build and dependencies; Java 17 target; package skeleton; two executable shells; ignore rules; persistent roadmap/matrix/instructions; exact 64-code catalog; fixture hashes; successful test/package and launcher checks. Record visual/runtime checks not run. | No predecessor. Tool download/network and JDK availability. |
| 1 Crypto | Implement crypto classes/TOTP/Base32/BCrypt. Restore all three actual keys, challenge-match certificates, decrypt/verify both catalogs and all owner files, prove actual sharing recipient and equal content via .shr. Record provider/seed profile and verified permissions matrix without secrets/content. Negative tests use copies. Verify assignment BCrypt sample and RFC vectors. | Phase 0 accepted; privately supplied passphrases. Do not pass fixture gate using synthetic-only tests. |
| 2 Persistence/registration | Five-table schema/repositories, atomic registration, exact message seeding/AuditService, certificate confirmation, enrollment, bootstrap and later startup screens. Duplicate/invalid/cancel rollback, encrypted storage, restart and failed-start termination tests. | Proven phase 1 compatibility. UI enrollment/confirmation manual checks. |
| 3 MFA | Persisted state machine/lockouts, keypad model and background verifier, TOTP stage, sessions, access count, role menu and associated events. Test 8/9/10 clicks, 120s boundaries without sleeps, account isolation, restart resistance and timing. | Phase 2. BCrypt latency, clock drift, asynchronous navigation. |
| 4 Vault/UI | Catalog parser/services, deduplicated access listing, owner/share extraction, output safety, counters, exit/logout/navigation and events. Corruption exposes neither list nor output. Test verified fixture permissions, role restrictions, missing owner/envelope, overwrite/traversal. | Phases 1 and 3. Group-only decryption needs clarification; group listing proceeds. |
| 5 Audit/integration | Independent authenticated logView; ordinary-user rejection, wrong key/passphrase/path, no-console handling, chronological context expansion. Exact 64-event trigger coverage, packaged E2E from empty DB through restart and audit, Java 17 runtime and unchanged fixtures. | Phases 2–4. Missing transitions, console behavior, group clarification. |

Each phase needs a results report, automated and appropriate manual checks, a reviewable
checkpoint, and user approval before the next phase under the current incremental workflow.
For source pages and per-requirement scenarios see REQUIREMENTS.md.

## Agreed defaults and unresolved issues

1. User has fixture passphrases privately; do not ask to paste them into chat or commit them.
2. Password policy provisionally rejects adjacent identical digits, allows recurrence elsewhere.
3. Group-visible files have no specified group envelope. Professor clarification is a gate for
   group-only extraction; do not silently implement it as sharing or mark overall compliance done.
4. Additional administrators may register; bootstrap identity owns startup/catalog context.
5. Verified labels are `administrador` and `usuario`. Phase 2 maps them explicitly to GID 1/2
   and UI Administrador/Usuário; Phase 4 consumes that production mapping.
6. Consultation count increments once per successful, verified List operation; access count
   increments only after complete MFA.
7. Catalog tokens allow email punctuation and filename extensions despite 'alphanumeric' wording.
   Share diagram contains copied index labels; follow semantic share role and actual verified data.
8. AES-256 and the specified derivation are now proven against every supplied fixture route.
9. Portuguese functional screens; optional QR, polish, installers and frameworks deferred.
10. No CA/revocation infrastructure or upload/encrypt/share-authoring feature is invented.

## Time and testing strategy

Ordinary deadline: 18 October 2026 23:59 America/Sao_Paulo; maximum late submission:
19 October 2026 11:59 (A p.7). Critical path: build -> actual crypto proof -> registration/DB
-> MFA -> verified extraction -> independent audit review. Reserve approximately 25% of
remaining implementation time for integration/manual checking/packaging and repairs.

Use JUnit temporary databases/directories, deterministic numeric times, and a separately
invoked fixture profile. Missing credentials must be explicit. Check professor fixture
hashes before/after runs; never mutate originals for negative tests. Manual checks cover
enrollment, keypad interaction, prescribed screens, console no-echo, packaged desktop launch.

First useful demonstration: bootstrap/enroll -> MFA -> register fixture user -> user MFA ->
owned and explicitly shared extraction -> lockout/logout -> independent audit. This is an
intermediate gate, not permission to omit group clarification, errors, screens, or audit coverage.
