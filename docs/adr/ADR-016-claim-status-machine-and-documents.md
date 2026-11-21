# ADR-016: Claim status machine, automatic rejection and document storage

- **Status:** Accepted (2026-11-21). Implemented in M5b.
- **Context:** PLAN 20.6 names the claim status machine as expensive to change, so its shape is decided in writing. PLAN 9 says a claim that fails a hard check is "rejected at once with reasons", but the PLAN 7 diagram has no path from SUBMITTED to REJECTED: REJECTED is reached only from UNDER_REVIEW, which is adjudication (M6). Documents are stored in SeaweedFS through the S3 API (PLAN 12), and the SDK is not managed by Spring Boot.

## Decision

1. **The machine is the PLAN 7 diagram plus one edge.** Every allowed change is listed in `statemachine.ClaimStateMachine`, and a test checks all pairs of statuses against an explicit table. The one addition is **SUBMITTED to REJECTED**, used only by the automatic hard checks at submission. It is recorded in the history with the actor `SYSTEM` and the first failing rule as the reason. A later review (M6) can still reject from UNDER_REVIEW. PLAN 7 should gain this edge when M6 is planned.
2. **A claim that passes is SUBMITTED and announced; a claim that fails is REJECTED and not announced.** `ClaimSubmitted` is published only for a claim that reaches review, so downstream services never see a claim that is already decided. The rejection is visible through the claim, its checks and its history. A `ClaimRejected` event, if the notification service needs one, belongs with M6.
3. **Every check is stored.** Each submission writes one `ClaimAssessment` per rule, PASS or FAIL (FLAG arrives with the soft checks in M6). The checks are append-only and are returned with the claim.
4. **Waiting rules and hard checks are pure functions** of the facts and the policy's stored terms (`rules` package). They take the day of submission as an argument, so the tables do not depend on the clock. The day is the business calendar's day (Africa/Nairobi, D38).
5. **Tie-break for waiting rules.** When precedence and length are equal, the lower `code` wins. This is not in the catalogue schema; this ADR adds it so the outcome never depends on storage order.
6. **Documents go to a port.** `DocumentStorage` has two implementations: S3 for real use and an in-memory one for tests. Uploads are checked for size, allowlisted type and magic bytes; objects get random keys; downloads are presigned and expire after five minutes. Presigned URLs are signed with the public endpoint, because the customer's browser downloads them.
7. **Two documented SpotBugs suppressions** (`EI_EXPOSE_REP2`): the `DocumentStorage` port in `DocumentService`, and the AWS clients in `S3DocumentStorage`. Both are thread-safe objects owned by Spring, so sharing the reference is intended. No other suppression is added.

## Alternatives considered

- **Keep REJECTED reachable only from UNDER_REVIEW and hold failed claims in SUBMITTED.** That would show a failed claim as awaiting review, which the PLAN 9 wording rules out, and it would make the customer wait for a review that cannot change the outcome.
- **Announce every submission, including rejections.** Simpler for consumers, but it publishes a decision that has already been made, with no event type to describe it. Rejected until M6 has a `ClaimRejected` event.
- **Compute the waiting rule with a database query.** Possible, but the rule would then depend on SQL ordering and could not be tested as a table. Rejected.
- **Store documents in the database.** Simpler, but PLAN 12 names object storage and a presigned download, and the database would carry every file. Rejected.

## Consequences

- Once a claim is REJECTED at submission, the customer can file a new one; the rejected one stays as a record.
- The status machine is a shared contract with M6 (adjudication) and with the notification service (M8). Changing an edge later means a new ADR and a data check against existing claims.
- An upload can leave an unreferenced object if the database write fails afterwards. The lifecycle job (M8) will remove such objects; it is parked in `docs/parking-lot.md`.
- Document storage is exercised against SeaweedFS by the smoke script, and against an in-memory fake in the integration tests.
