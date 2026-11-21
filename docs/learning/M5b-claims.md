# M5b: Claims, the automatic checks and documents

## What M5b delivers

- **Claim intake** for the customer: start a draft against one of your policies, change it while it is a draft, submit it, withdraw it, and read it with its checks and history. Every call answers 404 for another customer's claim.
- **A status machine** (`statemachine.ClaimStateMachine`). Every change goes through it and is written to the history. A test checks every pair of statuses against a table written from the PLAN.
- **Automatic checks at submission** (`rules.HardChecks`): the policy was in force on the incident date, the incident has happened, the member is on the policy, the benefit is covered, the waiting period has elapsed, the claim is filed in time, the required documents are attached, and the amount is above zero. A failing claim is rejected at once with the first failing rule as the reason.
- **Waiting periods** (`rules.WaitingRuleResolver`), resolved from the policy's stored terms by the D31 semantics, not by hardcoded insurer rules.
- **Documents**: PDF, JPEG and PNG up to 10 MB, checked by their first bytes, stored under random keys in SeaweedFS through the AWS SDK v2, and downloaded through five-minute presigned links.
- **`ClaimSubmitted`** on `claim.events`, sent through the outbox that M5a built, with a contract test.
- Smoke steps: a draft, a document, a presigned link, a rejected submission and the history.

## New concepts

**A state machine as data.** The allowed changes are a table: from each status, the list of statuses it may move to. The code checks the table and nothing else decides a move. The test does not read the table back from the code; it writes out the expected pairs by hand, so a mistake in either side shows up. Keeping the rule as data also makes the diagram in the PLAN and the code easy to compare.

**Optimistic locking with `@Version`.** Hibernate adds `WHERE version = ?` to every update and increments the column. If two requests change the same claim, the second one finds the version has moved and fails, instead of silently overwriting the first. Laravel's equivalent is a manual `updated_at` check or a pessimistic `lockForUpdate()`; the version column needs no lock held while the request runs.

**Pure rules, tested as tables.** `HardChecks` and `WaitingRuleResolver` take plain values and return plain values. No database, no clock: the submission day is an argument. That lets the tests state each rule as one row of facts and one expected answer. The service only gathers facts from the database and records what the rules return.

**A sequence in a native query.** Claim numbers come from a Postgres sequence (`nextval`), read through a native `@Query`. A sequence never hands the same value out twice, even under concurrent requests, which a "read the max, add one" approach cannot promise.

**Multipart uploads.** `@RequestParam MultipartFile` gives the file as a stream of bytes. Spring enforces the servlet size limit (`spring.servlet.multipart.max-file-size`) before the controller runs and throws `MaxUploadSizeExceededException`, which the handler maps to 413. The declared content type is only what the client says, so the service also checks the first bytes of the file (the magic bytes): `%PDF-` for a PDF, `FF D8 FF` for a JPEG, and the eight-byte signature for a PNG.

**Presigned URLs and two endpoints.** A presigned URL carries a signature over the host name it was made for. Uploads go to the store's internal address on the compose network; the presigner uses the public address, because the customer's browser downloads from it. Signing with the wrong host makes every link fail, so the two are separate settings.

**SpotBugs and justified suppressions.** The build runs SpotBugs at medium confidence, and it flagged the storage port and the AWS clients as `EI_EXPOSE_REP2` (storing a reference a caller could mutate). Those objects are thread-safe and owned by Spring, so the warning is wrong for them. The fix is a `@SuppressFBWarnings` with a justification and an ADR entry, not a silent `@SuppressWarnings` (which SpotBugs does not read). The record that exposed its list was fixed by copying the list in the constructor instead.

**`jsonb` and Hibernate.** A column of type `jsonb` needs `@JdbcTypeCode(SqlTypes.JSON)` on a `String` field. Without it, Hibernate sends the value as `varchar` and Postgres refuses it (the failure we hit in the tests).

## Why this approach

- **A draft that is not checked until submission.** A customer can fill in a claim in several sessions. Checking on every save would reject partial claims for missing documents; the checks run once, when the customer says the claim is complete.
- **Rejection at submission is a status, not an error.** The customer's request succeeded and the claim has a definite answer. A `422` would hide the claim, and the checks would have no record.
- **The checks are stored.** Each result is a row, so a rejection can be explained later, and M6 can add review results beside them.
- **Random storage keys.** The file name the customer gives is shown back to them, but it never names the object, so nothing can guess another customer's document key.

## Self-check (answer before M6 starts)

1. A claim is REJECTED at submission. Why does `ClaimSubmitted` not go out for it, and where does a consumer learn the outcome instead?
2. Two requests submit the same draft at the same moment. What stops both from succeeding, and which status change is lost?
3. The incident is ten days before cover starts. Which check fails, which others still run, and why is the reason `POLICY_IN_FORCE` rather than `WAITING_PERIOD_ELAPSED`?
4. Why is the claim number taken from a sequence instead of counting the claims for the year?
5. A customer uploads a file named `receipt.pdf` whose bytes start with `%PNG`. Which check refuses it, and why does the declared type not decide the outcome?

## Corrected answers

**1.** `ClaimSubmitted` is published only for a claim that passes the hard checks and enters review, so a consumer can treat it as "this claim is now in the workflow". A claim rejected at submission never enters that workflow, so nothing is published for it. In M5b a consumer learns that outcome only by reading the claim (`GET /api/v1/claims/{id}`, with its checks and `rejectionReason`) and its history. The intended way to push an outcome is a separate event through the outbox, such as `ClaimRejected`, but that event does not exist yet: it is parked for M6 (ADR-016, `docs/parking-lot.md`).

**2.** Two things stop both requests from succeeding; neither is an idempotency key (submit has none in M5b). If both requests load the draft before either commits, `Claim`'s `@Version` column makes the second update fail, and the service answers 409 `CONFLICT`. If the second request starts after the first has committed, it sees SUBMITTED (or REJECTED), `canMove` is false, and it answers 409 `INVALID_TRANSITION`. The losing request loses its whole transaction, not only its DRAFT to SUBMITTED change: its history row, its assessments and its `ClaimSubmitted` outbox row roll back too, so only one event is ever published. There is no concurrency test for double submission yet.

**3.** `POLICY_IN_FORCE` fails, because the incident date is before the cover start date. Every other check still runs, because each is an independent function of the facts: `INCIDENT_NOT_FUTURE`, `MEMBER_ON_POLICY`, `BENEFIT_COVERED`, `REQUIRED_DOCUMENTS`, `FILING_DEADLINE` and `AMOUNT_POSITIVE` can each fail or pass on their own. (Limits are not among them; they belong to adjudication in M6.) `WAITING_PERIOD_ELAPSED` is counted from the cover start date, so for an illness claim with a 30-day rule it also fails, and for an accident, which has no rule, it passes. The reason stored on the claim is the first failing check in evaluation order, and `POLICY_IN_FORCE` is evaluated first. Nothing makes the waiting-period check depend on the policy check: the stored checks list every failure, and only the reason picks one.

**4.** Counting has three weaknesses a sequence does not. Under concurrency, two requests that count at the same moment both read N and both issue N+1, so two claims get one number; `nextval` is atomic and hands each caller a different value. A count can also go back down, for example if claims were ever removed, and then numbers are issued again; a sequence value is never issued twice. And counting scans the year's rows, which grows with the data, while `nextval` reads one value in constant time. The price is gaps: a sequence does not roll back with the transaction, so a failed request skips a number. No data is lost, and a gap is acceptable where a duplicate is not. The unique constraint on `claim_number` stays as a backstop. The sequence is a single global one (`claim_number_seq`), so numbers are unique but not consecutive within an insurer and year.

**5.** The magic-byte check refuses it with 422 `DOCUMENT_CONTENT_MISMATCH`: `DocumentChecks` has already accepted the declared `application/pdf` against the allowlist, then finds the bytes start with the PNG signature, not `%PDF-`. The declared type cannot decide the outcome because the client sets it: anyone can send an executable with a PDF content type. The file name does not decide anything either; the service ignores the extension and only cleans the name for display. The bytes are the only part the client cannot choose freely, so they decide. This proves the type, not that the file is harmless: a file that really starts with `%PDF-` can still carry a malicious payload, and v1 has no malware scan. The private bucket, random object keys, five-minute presigned links and the stored content type limit the exposure.
