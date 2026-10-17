# Phase 3 results — 17 October 2026

## Outcome and scope

**Multifactor authentication, persistent attempt limits, authenticated sessions, role menus and
post-MFA registration authorization are implemented.** The keypad performance gate passed
before the authentication GUI was built. Phases 4 and 5 have not started: consultation only
shows an explicit unavailable notice, and logView remains its independent foundation shell.

Existing startup administrator-key validation remains separate from ordinary user login.
It cannot issue a session or registration capability. The Phase 0–2 tests and professor fixtures
are preserved; no algorithm/storage-format change or new dependency was introduced.

## Critical performance gate

The initial serial experiment established that a full ten-digit scan took approximately
14.2 seconds. The compliant optimization is **bounded candidate parallelism**, keeping every
eligible check at BCrypt **2y/cost 08**, without a fast hash, stored plaintext, early match exit
or reduced search space beyond the agreed password policy.

The final experiment calls the actual production KeypadPasswordVerifier, with three measured
runs per row after 32 BC wrapper warm-up checks. Worst-case layouts have disjoint adjacent
pairs, so **every** one of 256/512/1,024 combinations satisfies the no-adjacent-repeat policy.
The valid password is the final enumeration candidate; the verifier checks all candidates
regardless of where a match occurs. Unit tests also cover early matches and incorrect passwords.

Environment: Windows, Oracle JDK 24.0.2, Bouncy Castle 1.78.1; JVM reports 16 available processors.
Durations below are seconds of wall-clock verification latency; password hashing/setup is outside
the timed scan, while task dispatch, complete BCrypt checks, result collection and cleanup are inside.

| Digits | Candidate checks | Serial median | Four-worker median | Four-worker min–max |
| --- | ---: | ---: | ---: | ---: |
| 8 | 256 | 3.5470 | 0.9345 | 0.9221–0.9399 |
| 9 | 512 | 7.0906 | 1.8463 | 1.8346–1.8601 |
| 10 | 1,024 | 14.1940 | 3.6666 | 3.6656–3.7606 |

One generated representative pair dataset per length, with overlapping pairs and policy pruning,
gave four-worker medians 0.3568s (70 checks), 1.1361s (256 checks), and 2.0647s (576 checks).
These are measured examples, not a population-level latency estimate or a guarantee for other hosts.
Raw non-secret observations are preserved in `docs/phase3-keypad-benchmark.csv`.

Decision: proceed with at most four workers. The measured worst case is usable for this local
assignment when run in the background with visible busy feedback and duplicate actions disabled.
This consumes several CPU cores; on fewer/slower processors latency increases. The production
worker count is min(4, max(1, availableProcessors − 1)). Rebenchmark on the presentation machine:

```powershell
./scripts/benchmark-keypad.ps1 -Repetitions 3
```

The explicit benchmark is not a timing-sensitive unit test. It uses generated disposable inputs
and outputs only counts/timings, never professor credentials, hashes or recovered passwords.

## Implemented components

| Component | Behavior |
| --- | --- |
| DigitPair / VirtualKeypad | Five two-digit buttons, 0–9 exactly once, capture displayed pair before each fresh shuffle, 8–10-click submission, clear and masked feedback |
| KeypadPasswordVerifier | Full policy-valid enumeration, up to four workers, no match short circuit, drained worker results and closeable/wiped password buffers |
| AuthenticationService | Explicit identify/password/token/session states, bound flow generation, duplicate/restart guard during password work, transactional persistent failure limits, fail-closed audit handling |
| UserRepository extensions | Read protected credentials, clear expired lockouts, update separate failure counters, reset only the successful stage counter, increment access count only on complete MFA |
| TotpValidation | Controlled-time adapter to the unchanged Phase 1 TOTP implementation and its existing numeric-time seam; no new supporting imports inside TOTP |
| SessionContext | Issued only after valid TOTP and committed audit/counter update; contains identity and optional administrator capability, no password or token secret |
| RegistrationAuthorization | Still package-private issuance; actual administrator MFA now supplies it, while logout/close/fatal errors revoke it |
| AuthenticationPanel / Controller | Portuguese three-stage Swing flow, actual click capture, background verification/SQL, disabled duplicate actions, completion-generation checks, clear/restart and failure messages |
| MainMenuPanel / SessionExitPanel | Required identity/group/name/access count, administrator-only registration, Phase 4 consultation notice, logout/end system/Back navigation |
| ApplicationController | Startup -> real MFA -> role menu, real registration capability wiring, session closure before process shutdown |

The bootstrap phrase/key context remains alive across ordinary user logout as the assignment
requires; user sessions do not contain that material. Matched personal passwords survive only
long enough to decrypt the stored Base32 token secret, then mutable buffers are cleared.
The token validator survives only until stage 3 succeeds, is restarted, blocked or closed.
As in earlier phases, guaranteed erasure of all JVM-managed String/JCA copies is not claimed.

## Lockout, concurrency and audit semantics

- A valid known/unblocked login enters stage 2, without issuing a session. Unknown identities
  and blocked accounts remain at stage 1. Unknown valid email context is stored with a null UID;
  malformed input is not copied into the audit login field.
- Each completed incorrect password submission increments the account counter once, not once
  per candidate. The third failure blocks the account until current epoch time +120,000ms and
  returns to identification. Token failures follow the equivalent independent counter path.
- Positive stage 2 resets password failures; positive stage 3 resets token failures. Restarting
  input does not reset either counter. Expired lockouts clear the completed block/counter cycle.
- SQLite IMMEDIATE transactions serialize counter updates across service instances. A block
  is checked before expensive work and again before accepting its result. BCrypt does not hold
  a database write transaction while scanning; other accounts remain usable.
- Duplicate submission/restart is rejected while a scan is active. Closing a flow invalidates
  its generation; late work cannot create a token stage/session or increment access count.
- Session creation, token reset, access increment and 1003 audit commit together. Audit failure
  rolls back database changes and prevents session/capability issuance. Logout revokes the
  capability even when its audit transaction fails.

Exact successful sequence: 2001,2003,2002,3001,3003,3002,4001,4003,4002,1003; presenting the
main menu records 5001. Failed password/token submissions use 3004–3006 / 4004–4006, followed
by 3007/4007 on the third failure, stage end and 2001. Session termination records 1004.
Menu choices use 5002–5004; the minimum exit navigation uses the existing 8001–8004 definitions.
No event codes were invented, no 7000-series consultation events are emitted, and token values,
passwords, keys and token secrets are never passed to the audit API.

## Executed tests

```powershell
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','clean','verify'
./scripts/maven.ps1 -MavenArgs '-o','-B','-ntp','-Pfixtures','clean','verify'
```

| Final clean verification | Tests | Failures / errors / skipped | Result |
| --- | ---: | --- | --- |
| Default | 130 | 0 / 0 / 0 | BUILD SUCCESS (36.155s) |
| Fixture-enabled | 152 | 0 / 0 / 0 | BUILD SUCCESS (39.315s) |

The original 120 fixture-enabled tests remain, with 32 new Phase 3 cases:

| New test class | Cases |
| --- | ---: |
| VirtualKeypadTest | 3 |
| KeypadPasswordVerifierTest | 5 |
| AuthenticationServiceTest | 18 |
| AuthenticationUiTest | 5 |
| AuthenticationFixtureTest | 1 |
| **New total** | **32** |

Service tests cover known/unknown/blocked logins, exact 120-second expiry, both lockouts across
reopening, account isolation, stage-success resets, current/neighbor/distant TOTP windows and
leading zeros, exclusive post-TOTP session issuance, access counts, logout/reauthentication,
ordinary/foreign session rejection, capability revocation, exact audit sequences and injected
audit rollback. Five concurrent flows cannot exceed the account's failure limit; duplicate and
restart attempts during a scan are rejected, and a closed flow discards late results.

Verifier tests run full 8/9/10-digit worst-case scans and verify all expected checks, including
no match-position short circuit. Keypad tests inspect 1,000 layouts/captures. Swing tests drive
actual password buttons and token submission against a temporary registered identity, confirm
an EDT pulse during verification, exercise clear/restart and lockout navigation, and check role menus.

### Actual professor identity flow

AuthenticationFixtureTest registers the administrator using a temporary copy of its professor
pair and generated personal password. It validates the stored startup key, completes administrator
MFA, and uses the **real issued capability** to register user01 and user02. Both users then complete
MFA with 9/10-digit personal passwords. Ordinary users cannot obtain registration authorization;
the administrator's captured capability is rejected after logout. Reopening the database confirms
all three identities and exactly one completed-authentication access count for each.

No normal runtime database is seeded by tests. Local fixture phrases stay in ignored configuration.
The original fixture integrity and cryptographic interoperability tests still run; no professor
file was changed. Tests use controlled clocks rather than waiting two minutes.

Final packaged checks: both executable JARs return successfully for `--check`. All 22 original
SHA-256 fixture checksums match. ZIP inspection finds no local credentials, runtime databases,
professor package or test helpers in either artifact. `git diff --check` reports no whitespace
errors (Git emits the existing Windows line-ending conversion notices). No commit or push was made.

### UI inspection and unperformed checks

Offscreen identification, keypad, administrator/user menu and exit-panel renders were inspected.
They use synthetic test identities and contain no password or token secret. The actual desktop
first-run/login/logout walkthrough and enrollment with a physical phone remain manual checks.
Compilation targets Java 17; actual Java 17 runtime execution remains unverified on this JDK 24 host.
Existing non-fatal native-access/Unsafe and shaded-manifest warnings remain unchanged.

## Manual Google Authenticator flow

1. Build and launch from the repository root:

   ```powershell
   & 'C:\Program Files\Java\jdk-24\bin\java.exe' -jar target/cofre-digital.jar
   ```

2. On a fresh database, register the bootstrap administrator with its certificate/encrypted key,
   private passphrase and a chosen valid numeric personal password. Accept certificate details,
   enroll the displayed Base32 key in Google Authenticator as a time-based account, and acknowledge.
   On an existing database, first validate the stored administrator private key using its phrase.
3. Identify using the registered certificate email. For every personal-password digit, click the
   currently displayed pair containing that digit; read the new layout after **each click**. Submit
   after 8–10 clicks. Wait for background verification, then enter the current six-digit phone token.
4. Only after TOTP succeeds does the role menu appear. The administrator can register users using
   the connected Phase 2 form. Enroll each new user's separate secret; ordinary user menus omit registration.
5. Sair do Sistema -> Encerrar Sessão returns to login and revokes registration authority. Repeated
   complete MFA increments access count once per login. Three wrong submitted passwords or tokens
   block only that account for 120 seconds, including across application restart.
6. Consultation shows a Phase 4 unavailable notice. There is no extraction or log-viewing route.

**Stop after Phase 3. No Phase 4/5 implementation, commit or push. Await user review.**
