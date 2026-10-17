# Audit event traceability — final review

The exact 64 templates remain in AuditEvent and Mensagens. This table maps each prescribed
MID to its producing branch and supporting automated scenarios. Existing exact-sequence tests
assert selected traces; this table also records source review of the other branches. It does
not claim that one end-to-end test individually asserts all 64 MIDs. Native screen presentation
still needs the manual walkthrough in FINAL-DELIVERY-CHECKLIST.md.

| MID | Producer | Supporting evidence |
| --- | --- | --- |
| 1001 | StartupService.begin / fail / shutdown | RegistrationServiceTest startup/audit-failure cases; Final[Fixture]IntegrationTest |
| 1002 | StartupService.begin / fail / shutdown | RegistrationServiceTest startup/audit-failure cases; Final[Fixture]IntegrationTest |
| 1003 | AuthenticationService.token / logout / close | AuthenticationServiceTest success/rollback/logout; final integration |
| 1004 | AuthenticationService.token / logout / close | AuthenticationServiceTest success/rollback/logout; final integration |
| 1005 | StartupService.begin / fail / shutdown | RegistrationServiceTest startup/audit-failure cases; Final[Fixture]IntegrationTest |
| 1006 | StartupService.begin / fail / shutdown | RegistrationServiceTest startup/audit-failure cases; Final[Fixture]IntegrationTest |
| 2001 | AuthenticationService.start / identify / restart | AuthenticationServiceTest known/unknown/blocked; final integration |
| 2002 | AuthenticationService.start / identify / restart | AuthenticationServiceTest known/unknown/blocked; final integration |
| 2003 | AuthenticationService.start / identify / restart | AuthenticationServiceTest known/unknown/blocked; final integration |
| 2004 | AuthenticationService.start / identify / restart | AuthenticationServiceTest known/unknown/blocked; final integration |
| 2005 | AuthenticationService.start / identify / restart | AuthenticationServiceTest known/unknown/blocked; final integration |
| 3001 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3002 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3003 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3004 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3005 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3006 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 3007 | AuthenticationService.identify / password / restart / close | AuthenticationServiceTest password attempts, lockout, expiry and audit sequence |
| 4001 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4002 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4003 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4004 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4005 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4006 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 4007 | AuthenticationService.password / token / restart / close | AuthenticationServiceTest token attempts/lockout/windows; final integration |
| 5001 | AuthenticationService.mainMenu / menu | AuthenticationServiceTest exit/menu; final integration (5001/5002/5003) |
| 5002 | AuthenticationService.mainMenu / menu | AuthenticationServiceTest exit/menu; final integration (5001/5002/5003) |
| 5003 | AuthenticationService.mainMenu / menu | AuthenticationServiceTest exit/menu; final integration (5001/5002/5003) |
| 5004 | AuthenticationService.mainMenu / menu | AuthenticationServiceTest exit/menu; final integration (5001/5002/5003) |
| 6001 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6002 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6003 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6004 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6005 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6006 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6007 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6008 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6009 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 6010 | RegistrationService.showForm / prepare / commit / cancel / back | RegistrationServiceTest validation/rollback; RegistrationUiTest accept/reject/Back |
| 7001 | VaultService constructor / back / list | VaultServiceTest, ConsultationUiTest and final integration |
| 7002 | VaultService constructor / back / list | VaultServiceTest, ConsultationUiTest and final integration |
| 7003 | VaultService constructor / back / list | VaultServiceTest, ConsultationUiTest and final integration |
| 7004 | VaultService constructor / back / list | VaultServiceTest, ConsultationUiTest and final integration |
| 7005 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7006 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7007 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7008 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7009 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7010 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7011 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7012 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7013 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7014 | CatalogService.read; VaultService.list commit | CatalogServiceTest, six-case invalid-catalog tests, fixture and final integration |
| 7015 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7016 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7017 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7018 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7019 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7020 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 7021 | VaultService.extract / decrypt | VaultServiceTest routes/tampering; PublicationAuditTest; fixture and final integration |
| 8001 | AuthenticationService.exitScreen / logout / exitSystem / backFromExit | AuthenticationServiceTest exit/Back/system-end and logout traces |
| 8002 | AuthenticationService.exitScreen / logout / exitSystem / backFromExit | AuthenticationServiceTest exit/Back/system-end and logout traces |
| 8003 | AuthenticationService.exitScreen / logout / exitSystem / backFromExit | AuthenticationServiceTest exit/Back/system-end and logout traces |
| 8004 | AuthenticationService.exitScreen / logout / exitSystem / backFromExit | AuthenticationServiceTest exit/Back/system-end and logout traces |

## Interpretation and reviewed failure boundaries

- 6008 is accepted certificate/enrollment confirmation committed with registration, not an invented
  additional registration-success code. 6009 records rejected/cancelled confirmation.
- 6006/6007 also describe current-user/startup key restoration or signature-challenge failure.
  A padding failure alone does not distinguish a wrong phrase from damaged encrypted key data.
- 7009/7014 and consultation_count commit together only after BOTH verified catalogs parse.
- 7018/7019 describe successful file decryption/signature verification. Final publication is
  separately recorded as enum context on 7019 (pending/published/failed), without another MID.
  Phase 5 removes the misleading 7020 event on post-verification output-I/O failure. Genuine
  RSA/AES or missing-input failures still use 7020; bad payload signatures use 7018 then 7021.
- A preflight unsafe/existing output refusal leaves the 7015/7016 selection/authorization trace;
  it does not invent decryption/verification events for work never performed. UI reports the reason.
- If publication occurs but the outcome update fails, the error surfaces and the audit remains
  pending (unconfirmed); the output is already verified. No transaction across SQLite and the
  filesystem is claimed. Crashes can leave pending outcomes or a verified temporary file.
- Required audit failure rolls back protected database transitions and prevents session issuance
  or pre-publication success. Shutdown/cleanup errors are surfaced, never silently discarded.
- logView has no prescribed own MID and performs no writes. It authenticates before reading
  Registros, uses Mensagens templates, orders by timestamp/RID, preserves absent placeholders
  and safely labels malformed records. It does not pretend to provide tamper-proof audit storage.
