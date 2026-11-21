# ADR-015: Policy read model in claim-service

- **Status:** Accepted (2026-11-21). Implemented in M5a.
- **Context:** A claim must be checked against the policy as it was sold: its terms, its members and its cover dates (PLAN 7, 9). claim-service must not read policy_db (CLAUDE.md, ADR-001), so it needs its own copy. The copy can come from `policy.events` (PLAN 10), but an event may still be on its way when a customer files a claim. PLAN 11 names the fallback: a call to policy-service with a timeout, retry and circuit breaker, and 503 with Retry-After when that fails. PLAN 20.6 lists the policy terms snapshot and the event envelope as expensive to change, so the shape of this copy is decided here.

## Decision

1. **The read model is built only from `policy.events`.** claim-service consumes `PolicyActivated` and `PolicyCancelled` through the inbox (ADR-013). Each event is applied at most once.
2. **Ordering by `policyVersion`.** policy-service increases `policyVersion` on every change to a policy. A change applies only when its version is higher than the stored one. This is simpler than comparing timestamps, which can tie or skew across services. Equal versions are the same change again and are ignored.
3. **A row can exist without an activation.** A cancellation for a policy that was never activated (unpaid, PLAN 8.2) leaves a row with no holder and no terms. Such a row is never returned to a customer. A later activation with a lower version is ignored.
4. **The activation payload is stored whole.** `policy_snapshots.activation` keeps the `PolicyActivated` JSON as received. The terms stay exactly as sold (ADR-011); claim-service reads them generically (CLAUDE.md) and never maps catalogue types.
5. **PENDING_PAYMENT policies are not in the read model.** They have no cover, so no event is sent for them. The list endpoint shows only what the read model holds.
6. **The fallback is a read, not a copy.** When a customer asks for a policy the read model does not have, claim-service calls `GET /internal/policies/{id}` on policy-service with its SERVICE token (ADR-014). The answer is returned and not stored, so the event stream stays the only writer of the read model. The caller must be the holder, otherwise the answer is 404. policy-service admits only the `claim-service` subject (`SecurityConfig`).
7. **Failure is a 503, not a guess.** A timeout, a 5xx or an open circuit breaker gives `POLICY_UNAVAILABLE` with `Retry-After: 30`. Retry is for transient failures only: a 404 means the policy does not exist and is not retried.

## Alternatives considered

- **Synchronous calls only (no read model).** Simple, but every claim depends on policy-service being up, and claim intake would be coupled to policy's availability. Rejected: PLAN 3 and 11 call for a local copy.
- **Compare `occurredAt` instead of `policyVersion`.** Timestamps from two services can tie or move backwards, and redelivery order is not guaranteed. The version is written by the owner of the aggregate. Rejected.
- **Store every event in an append-only history and derive the current state.** More useful for audit, but it needs a rebuild path and a projection job that M5a does not need. Deferred: the `ClaimStatusHistory` (M5b) covers the audit need for claims.
- **Copy the fallback result into the read model.** Would make the answer faster, but it writes outside the event order and could overwrite a newer event. Rejected.

## Consequences

- A customer can see a policy only after its `PolicyActivated` event arrives, or through the fallback while it is still pending. The fallback is the only path that answers for a policy with no event yet.
- A cancelled policy whose activation never arrived is invisible to the holder. That is correct, because the holder never had cover.
- `policyVersion` is now a contract (docs/events.md). policy-service must keep increasing it on every change.
- Two copies of the SERVICE token client exist (policy-service and claim-service). Extraction is parked (docs/parking-lot.md).
