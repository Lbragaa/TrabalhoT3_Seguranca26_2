# Requirements and verification matrix

## Sources and status

A = `docs/Trabalho3.pdf` (14 pages); D = `docs/Trabalho3-Detalhamento.pdf` (16 pages).
PDF page numbers below are physical PDF pages. Both were reviewed in full during planning.
The PDFs override this extraction. Phase 0 does not establish crypto/security functionality.

| ID | Requirement / acceptance | Source | Phase | Verification / status |
| --- | --- | --- | --- | --- |
| F01 | Java SE 17 target, Maven pinned plugins/dependencies, Swing, SQLite JDBC, BC, JUnit 5 | A p.1; approved plan | 0 | pom.xml; FoundationTest dependency checks; build report |
| F02 | Independent executable vault and logView shells | A pp.6–7; plan | 0 | Packaged --check launches; FoundationTest |
| F03 | Preserve originals and inventory | User/plan | 0+ | FixtureIntegrityTest: 22 files, SHA-256 |
| F04 | Persistent plan, instructions and requirement evidence | User | 0 | PLAN.md, AGENTS.md, this file |
| S01 | Empty DB starts administrator registration; no ordinary bootstrap | A p.1 | 2 | Future fresh-DB registration test/manual screen |
| S02 | Later startup validates bootstrap private key; failure terminates | A p.1 | 2 | Future restart/correct-wrong-passphrase tests |
| S03 | Administrator phrase lives only in memory until shutdown | A p.1 | 2–4 | Future lifecycle/storage/log review |
| R01 | Admin-only registration; 255-character path/phrase fields; groups Admin/User | A p.2 | 2 | Future authorization and field boundary tests/manual |
| R02 | Password 8–10 ASCII digits, confirmation; provisional no adjacent repeats | A p.3; user clarification | 2 | Future policy/boundary/mismatch tests |
| R03 | Certificate confirmation: version, serial, validity, signature type, issuer, subject, email | A p.3 | 2 | Future parsing tests/manual confirmation |
| R04 | Name/email from subject; email unique; cancellation/failure inserts nothing | A p.3 | 2 | Future duplicate/rollback/cancel tests |
| R05 | Sign/verify 10,240 random bytes to validate key pair | A p.3 | 1–2 | Future real-key and mismatch tests |
| R06 | Store original encrypted key binary and certificate PEM in Chaveiro linked UID/KID | A pp.3–4 | 2 | Future transactional DB/storage inspection |
| R07 | BCrypt 2y, cost 08, own Base64, 60 chars, supplied sample verifies | A p.3 | 1–2 | Future BCrypt example/format tests |
| R08 | Generate random 20-byte TOTP key, Base32 enrollment display; encrypt Base32 with password-derived AES-256 | A pp.3–4 | 1–2 | Future enrollment round trip/storage tests/manual phone |
| R09 | Successful registration clears form; errors retain data; return to menu | A p.4 | 2 | Future UI manual checks |
| C01 | Binary key AES/ECB/PKCS5Padding; SHA1PRNG/KeyGenerator AES-256; PKCS8 PEM/Base64 recovery | A p.4; D p.10 | 1 | Future actual three-key compatibility gate |
| C02 | Extract X.509 PEM with text prefix; RSA public key | A p.4; D p.11; observed fixtures | 1 | Future three-certificate identity tests |
| C03 | RSA/ECB/PKCS1Padding envelope -> raw seed -> SHA1PRNG AES key | D pp.7–9 | 1 | Future catalogs/owner/recipient fixture proof |
| C04 | AES/ECB/PKCS5Padding payload; SHA1withRSA signature over plaintext bytes | A pp.5–6; D pp.7–9 | 1 | Future valid/corrupt signature/ciphertext tests |
| D01 | Five tables Usuarios, Chaveiro, Grupos, Mensagens, Registros with unique IDs/associations | A pp.3–4,6 | 2 | Future schema/FK/rollback tests |
| A01 | Login valid email, registered and not blocked; otherwise remain stage 1 | A p.1; D p.12 | 3 | Future unknown/blocked/valid login tests |
| A02 | Five two-digit buttons, 0–9 once, reshuffle after each click; capture displayed pair | A p.1; D pp.13–15 | 3 | Future permutation/capture/manual tests |
| A03 | Enumerate captured pair candidates and verify BCrypt without fast-hash shortcut | D p.15; plan | 3 | Future 8/9/10 digit verification/latency tests |
| A04 | Three consecutive password failures -> stage 1, account blocked 120s | A p.1 | 3 | Future deterministic timing/restart/account isolation |
| A05 | Handwritten TOTP using only Mac, SecretKeySpec, Date, supplied Base32 support | A pp.3,10–14 | 1 | Future source review and RFC vector tests |
| A06 | HMAC-SHA1; 30s time counter; six digits including zeros; current +/- one interval | A pp.1–2; D p.16 | 1–3 | Future vectors/boundaries/malformed token tests |
| A07 | Three consecutive token failures -> stage 1 and 120s block; success grants session | A p.2 | 3 | Future failure reset/session/lockout tests |
| U01 | Login/group/name header; access count; role-dependent menu; no user registration option | A pp.2,6 | 3–4 | Future role tests/manual screens |
| U02 | Consultation field limits, path/phrase, List, names/owner/group table, count, Back | A pp.4–5 | 4 | Future manual UI and service tests |
| U03 | Exit confirmation; end session/end system/back; logout restarts login | A p.6 | 4 | Future lifecycle/navigation/manual checks |
| V01 | Validate current user's passphrase before listing | A p.5 | 4 | Future correct/wrong/mismatched key tests |
| V02 | Bootstrap admin decrypts and verifies index/share before any list | A p.5; D pp.4–9 | 1,4 | Future fixture and tampered-catalog tests |
| V03 | Parse index: code, secret filename, owner, group; share: code, positive ID, recipient | A p.5; D pp.4–5 | 4 | Future parsing/verified fixture metadata matrix |
| V04 | Deduplicated listing union: owner, same group, explicit recipient | A p.5 | 4 | Future per-user/per-file policy tests |
| V05 | Owner .env, recipient .N.shr; verify owner signature, then save secret filename | A p.6; D pp.6–9 | 1,4 | Future owner/share equal-content proof and extraction tests |
| V06 | Recheck permissions; unknown owner/missing envelope/corruption cannot publish output | A pp.5–6; plan | 4 | Future negative tests on temporary copies |
| V07 | Safe output outside original fixture tree; no traversal/overwrite; verify before publish | Approved plan | 4 | Future output safety tests |
| V08 | Group-only decryption mechanism | A pp.5–6 ambiguity | 4 | BLOCKED pending professor clarification; listing still required |
| L01 | Exact 64 MID codes/templates from assignment | A pp.8–9 | 0,2 | AuditEvent catalog tests now; DB seeding later |
| L02 | Timestamp/MID/user/file contexts; message text only in Mensagens | A p.6 | 2–5 | Future schema/audit sequence/storage tests |
| L03 | Record all specified UI/auth/crypto/lifecycle transitions; fail visibly on audit error | A pp.6,8–9; plan | 2–5 | Future event scenario coverage and failure injection |
| L04 | Separate logView, registered admin only, key CLI path, no-echo passphrase | A pp.6–7 | 5 | Future real console/manual and role tests |
| L05 | Viewer key/cert 2,048-byte challenge; failure exits; chronological date/time/code/messages | A p.7 | 5 | Future matching/mismatched keys and ordered rendering |

## Audit scenario map (definitions exist; emission is NOT implemented)

| Codes | Future trigger coverage |
| --- | --- |
| 1001–1006 | System start/end, session start/end, initial/normal startup |
| 2001–2005 | Stage 1 entry/exit, identified allowed/blocked, unknown login |
| 3001–3007 | Stage 2 entry/exit, valid password, first/second/third failure, block |
| 4001–4007 | Stage 3 entry/exit, valid token, first/second/third failure, block |
| 5001–5004 | Main screen and each numbered menu choice |
| 6001–6010 | Registration screen/submit/password/path/key failures, confirmation accept/reject, Back |
| 7001–7004 | Consultation screen/Back/List/invalid directory |
| 7005–7009 | Index decrypt/verify success or failure and index list presentation |
| 7010–7014 | Share decrypt/verify success or failure and share list presentation |
| 7015–7021 | File selection, access allowed/denied, decrypt/verify success or failure |
| 8001–8004 | Exit screen, end session, end system, Back |

## Fixture baseline and limits of current evidence

22 files: three certificate/encrypted-key pairs; index/share encrypted+envelope+signature
triplets; XXYYZZ00/11/22 triplets; XXYYZZ22.1.shr. Public identities are admin, user01,
user02 at inf1416.puc-rio.br. RSA keys are 2048 bits; envelopes/signatures are 256 bytes;
certificate signatures are SHA256withRSA. These observed facts are not decryption proof.

Phase 1 must use locally supplied credentials to authenticate both catalogs, establish real
owners/groups/recipient, verify all payloads, and prove owner/share paths produce identical
content. Never infer recipient from the .shr filename or guess fixture group mappings.
No passphrase or extracted plaintext belongs in this document or Git.
