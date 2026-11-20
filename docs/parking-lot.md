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
