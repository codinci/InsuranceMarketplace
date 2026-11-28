# Parking lot

Ideas that are outside the current milestone (CLAUDE.md workflow rule 8, PLAN 21.4). Nothing here enters scope without a plan edit.

| Found in | Idea or issue | Notes |
|---|---|---|
| M0b | Kafka warns that topics with `.` and `_` can collide in metric names | Our topics use dots only (PLAN 10), so the warning is harmless. Keep it in mind if an underscore topic is ever proposed. |
| M0b | `.gitignore` lists `PLAN.md`, but `PLAN.md` is tracked in Git | Ignoring has no effect on a tracked file. Decide whether to remove the line. |
| M0b | Leftover containers and volumes from earlier local runs can hold ports the project needs | Owner decision on cleanup. Do not remove them as part of the project. |
| M0b | ADR-001 (catalogue in policy-service), ADR-002 (document-first catalogue), ADR-003 (rating strategies) | Written in M3a. ADR-002 also records D31 and D32. |
| M0b | ADRs for the section 3 design decisions (monorepo, roles and JWT, outbox and inbox, money, IDs, time, API style) | Each is written in the milestone that first implements it (for example the JWT ADR at M1, the outbox ADR at M4). Not written in advance. |
| M0b | ADR for topic and partition design (PLAN 21.6) | Topics exist since M0b, but the first producer arrives at M4. Write the ADR at M4, before the first event is published, and reopen it then if the M0b topic layout needs to change. |
| M0b | Host ports can be taken by other software already installed on a development machine | Compose uses 15432 for Postgres and `GATEWAY_HOST_PORT` for the gateway. The gateway port decision was made at M2. |
| M1 | Rate-limit buckets live in memory and the map is never pruned | Memory grows with the number of distinct IPs and emails. Add eviction (or move to a shared store) before public exposure. The trusted-proxy part was done in M2: the gateway sets X-Forwarded-For and auth-service reads it (ADR-009). The in-memory map in auth-service is still open. |
| M1 | The service connects to Postgres as the `insurance` superuser | One login per service with rights only on its own database. Add with the first multi-service compose change (M2 or M4). |
| M1 | Expired and revoked refresh-token rows are never deleted | A cleanup job for expired and revoked refresh tokens belongs to M8 (lifecycle jobs). |
| M1 | Immediate revocation of a role or a deactivated user | Access tokens stay valid for up to 15 minutes. A deny list keyed on `jti` would close the gap. Decide whether it is needed before M9 hardening. |
| M1 | Password change, password reset and account deactivation endpoints | Not in the M1 test list. Needed before a real launch; the reset flow needs email, which is stubbed until M8. |
| M1 | Request bodies with unknown fields are silently ignored, not rejected | Security holds, because the body can never set a role or insurer. Strict rejection would make the API stricter for clients; decide when the gateway's OpenAPI is published. |
| M1 | The JWKS path is `/.well-known/jwks.json`, outside `/api/v1` | This is the standard location that libraries look for. Keep it; the gateway must route it (M2). |
| M1 | Argon2 instead of BCrypt | Accepted by PLAN 12. Needs a library outside the Spring Boot BOM. Revisit with the security checklist (M9). |
| M2 | The gateway fails open when Redis is down (ADR-009) | During an outage there is no gateway rate limit. Add a Redis health alert before public launch (M9). |
| M2 | Rate limits are per client IP | Clients behind one NAT share a bucket. Revisit with the security checklist (M9), for example a per-user limit after login. |
| M2 | Swagger UI is open at the gateway | PLAN 12 says it is disabled or protected in production. Add a profile switch before any deployment. |
| M3a | Eligibility ages are read in the plan's age basis | Under `NEXT_BIRTHDAY`, a principal range of 18 to 70 means a rated age of 18 to 70 (docs/catalogue-schema.md). Confirm when M4 checks eligibility at quote time, where a customer thinks in completed years. |
| M3a | An empty waiting-rule `when` matches every claim | Follows from D31 (AND of no fields). Decide at M5 whether to keep it as a general waiting period or reject it in the validator. |
| M3a | The D31 and D32 rules are implemented in two services | Validated in policy-service, applied in claim-service (ADR-002). If the two drift, reconsider a shared library and an ADR. |
| M3a | The schema `$id` uses an `example.com` placeholder | Replace it if the schema is ever published at a real URL. |
| M3b | auth-service cannot check that a staff `insurerId` exists in policy-service | A platform admin can create an insurer admin for an id no insurer has; that admin then sees nothing. Close the gap with an insurer event or an internal lookup once inter-service calls exist (M4+). |
| M3b | Suspending an insurer | `insurers.status` supports `SUSPENDED` and browse hides it, but no endpoint sets it. Add it with the admin hardening (M9) if needed. |
| M3b | A plan's off-sale but still published state | A published plan whose sale period has ended stays `PUBLISHED` and its terms stay readable at `GET /plans/{id}`. Decide at M4 whether quotes must also check the sale period (they should) and whether such plans should be retired by a job (M8). |
| M4a | The policy holder has no email | Access tokens carry no email claim and auth-service owns the address. Notifications (M8) can take it from `UserRegistered` or an auth lookup. Add an `email` claim only through an ADR-006 change. |
| M4a | Searching by national ID | It is encrypted with a random nonce, so SQL cannot match it. Add an HMAC column if duplicate detection is needed (ADR-012). |
| M4a | Key rotation for field encryption | Only one key is configured. Rotation needs a key map and a re-encryption job (ADR-012). |
| M4a | Old OPEN quotes are never cleaned up | Expiry is computed on read. A cleanup job belongs with the lifecycle jobs (M8). |
| M4a | A customer can buy several policies for the same members | Not refused today. Decide whether overlapping cover with the same insurer should be blocked (M9 business rules). |
| M4b | A payment can exist without its policy | Purchase calls payment-service just before its own commit. If that commit then fails, payment-service keeps a PENDING payment for a policy that does not exist; if the customer pays it, `PremiumPaid` is logged and ignored. Rare, but money would be stuck. The reconciliation job (M8) should expire such payments and refund any that were paid. |
| M4b | A premium paid after the unpaid deadline | The policy is already CANCELLED, so `PremiumPaid` is logged and ignored and the money is not refunded. Same reconciliation job (M8/M9). |
| M4b | The purchase holds a database connection during the payment-service call | Up to about seven seconds in the worst case (three attempts with short timeouts). Fine at this scale; revisit if the connection pool runs short under load (M9). |
| M4b | Outbox and inbox tables grow forever | Published outbox rows and inbox rows are never deleted. Add a cleanup job with the other lifecycle jobs (M8), keeping inbox rows longer than any redelivery window. |
| M4b | Outbox and inbox code is copied in two services | Resolved in M5a: `common-messaging` (ADR-013). |
| M5a | The SERVICE token client is copied in policy-service and claim-service | `ServiceTokens` and the breaker/retry config are near-identical. Extract them into a shared module with the first further caller, or with the client-credentials change (ADR-014). |
| M5b | Orphaned document objects | A failed database write after an upload leaves an object nothing points at. The lifecycle job (M8) removes them (ADR-016). |
| M5b | Terms exclusions are not checked at submission | The snapshot carries exclusions as strings, but no hard check reads them yet. Decide the mapping with adjudication (M6). |
| M5b | `preAuthRequired` is stored only | As PLAN 2 says; no check or flag uses it. |
| M5b | Double-submit has no concurrency test | Two parallel submits of one draft are stopped by the `@Version` column and the state check, but no test runs them in parallel. Agreed 2026-11-22 for the hardening milestone (M9): exactly one submit succeeds, the other gets 409, and one `ClaimSubmitted` outbox row is written. |
| M5b | Raise the module line-coverage floor from 70% to 85% | Agreed 2026-11-22 for the hardening milestone (M9), by ADR. It revises earlier modules' tests: common-messaging is lowest at 86.8% and needs margin; every other module is above 93%. |
| M5b | Per-package coverage gates are not in the POM | CLAUDE asks 90% line and 85% branch on `rules` and `statemachine`. Only the 70% bundle rule is enforced today; add the package rules once the numbers are measured in CI. |
| M5b | No `ClaimRejected` event | Rejections at submission are visible on the claim only (ADR-016). Add the event with M6 review, if the notification service needs it. |
| M6 | No persistent audit table | Staff actions are audited as structured log lines on the `audit` logger (ADR-017). A table would allow queries and retention rules; decide with the M9 security work. |
| M6 | Reservation release has no caller yet | `UtilisationService.release` is built and tested; M7 calls it when a payout fails for good. |
| M6 | Terms exclusions are still not applied | The M5b item stands: neither the hard checks nor the evaluator read `exclusions`. Decide the mapping with the owner before M7. |
| M6 | The approval retry gives up after three attempts | Under heavy load on one policy a reviewer may see 409 and retry by hand. Revisit if load tests (M9) show it. |
| M6 | Claims knowledge base and FAQ (stretch) | Agreed 2026-11-22: the information requests adjusters send (`ClaimInfoRequested`) can be collected into a knowledge base and an FAQ for customers. Stretch objective, later in the plan. |
| M6 | Vector search in the search (stretch) | Agreed 2026-11-22: when the knowledge base exists, search gains vector (semantic) search next to the keyword search. Stretch objective; it needs an embedding model choice and a vector store, which belong in their own ADR. |
| M5b | Soft flags and review states | `FLAG`, `UNDER_REVIEW`, `INFO_REQUESTED` and the payout states are in the machine but not reached yet (M6, M7). |
| M5a | The fallback has no cache or bulkhead | Every read-model miss calls policy-service, bounded only by the breaker. Fine at this scale; revisit if a customer can trigger many misses (M9). |
| M4b | Internal calls use a plain `RestClient` | Spring Boot 4 auto-configures `RestClient.Builder` only with its separate restclient starter, so the payment and token clients are built directly and miss Boot's metrics and tracing hooks. Revisit with observability (M8/M9). |
| M4b | The mock checkout accepts `simulate=FAIL` from any customer | Fine for a mock provider. The field must not exist once a real provider is added. |
| M7 | Releasing a reservation when a payout is abandoned | `UtilisationService.release` is built and tested but has no caller. M7 keeps the reservation while a claim is PAYOUT_FAILED (money still owed, retry allowed). Giving it back needs a decision on abandoning a payout: a new PAYOUT_FAILED to REJECTED (or similar) edge, which changes the state machine. See ADR-018. |
| M7 | A real payout destination | The mock pays to the holder's user id. A real provider needs a payout destination (bank or mobile-money reference) on the policyholder, carried to payment-service. `payoutDestinationRef` in PLAN 7 is not built. |
| M7 | Automatic retry of failed payouts | A failed payout waits for an admin. A scheduled retry with a limit and back-off belongs with the lifecycle jobs (M8) or later. |
| M7 | Payout failure switch is configuration | `payments.mock.payout-decline-payees` exists for tests and the smoke run. Remove it when a real provider replaces the mock. |
| M8b | Staff-initiated cancellation | Only the holder can cancel. An insurer admin cancelling (fraud, non-disclosure) needs its own reason codes, an audit entry and a decision on refunds. |
| M8b | Premium paid just before an unpaid cancellation | When a payment succeeds as the unpaid job cancels the policy, payment-service logs a warning and keeps the money (PLAN 11 reconciliation). A reconciliation job, or an automatic refund for this case, is still to design. |
| M8b | Refund and payout attempt code is duplicated | `RefundService` and `PayoutService` each hold their own attempt-and-retry code with different events. Extract a shared helper if a third kind of outgoing payment appears. |
| M8b | The claim count can lag | A claim submitted seconds before a cancellation may not be counted yet, so the refund can ignore it (ADR-021). Closing the gap needs a synchronous check or a short hold on refunds; decide if it matters in practice. |
| M8b | Integration tests need a running Docker | Testcontainers tests fail with "Could not find a valid Docker environment" when Docker is not running, and the build stops at the first one. `DeadLetterReplayerIT` also threw a second error from its teardown in that case (fixed in M8b: teardown skips what setup never created). A pre-flight Docker check at the start of `scripts/verify.sh` would give a clearer message sooner. |
| M8b | Refund switch is configuration | `payments.mock.refund-decline-holders` exists for tests and the smoke run, like the payout switch. Remove both with the mock. |
