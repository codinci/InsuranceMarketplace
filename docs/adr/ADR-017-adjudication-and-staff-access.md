# ADR-017: Adjudication, limit reservation and staff access

- **Status:** Accepted (2026-11-22). Implemented in M6.
- **Context:** M6 decides what is paid and who may decide it. Three things in it are expensive to change: how running totals against limits are kept (they hold money), the staff access rules (they decide who sees which insurer's claims), and the events that tell other services about a decision. PLAN 9.2 gives the algorithm; this ADR records the choices it leaves open.

## Decision

1. **The evaluator is a set of pure functions** (`adjudication` package). It takes the benefit chain from the policy's stored terms, the claimed amount and the running totals, and returns what is eligible after the limits and payable after the co-pay. The co-pay of the **deepest node on the chain that has one** applies, after all limits. Money is `BigDecimal`, two places, HALF_UP. The rules are written down in `docs/catalogue-schema.md`.
2. **Running totals are rows with a version.** `benefit_utilisation` has one row per policy, benefit, policy year and (for `PER_MEMBER` limits) member. The row's `@Version` column makes two approvals that read the same total unable to both save it: the second fails, and approval starts again from the new total, up to three attempts. A first-use race on creating the row ends in a unique-key violation and is retried the same way. If the attempts run out the reviewer gets `409 CONFLICT` and nothing has changed.
3. **What is reserved is the approved amount, after the co-pay.** A limit caps what the insurer pays, so the customer's co-pay does not use it up. The amount is reserved on every `PER_YEAR` node of the chain, and a visit is counted only where a limit sets `maxCount`. Each reservation is recorded per claim (`claim_reservations`) so it can be released exactly.
4. **Release is built now; ADR-018 decides when it is called.** `UtilisationService.release(claimId)` gives back the amounts and visits and is safe to call twice. No endpoint calls it in M6, because the state machine has no APPROVED to REJECTED edge. M7 calls it when a payout fails for good.
5. **A claim that cannot be paid is refused, not auto-rejected.** If a count limit is reached or nothing is left under a limit, approval answers 422 (`COUNT_LIMIT_REACHED`, `LIMIT_EXHAUSTED`) and the claim stays under review, so the reviewer chooses the reason when rejecting.
6. **The reviewer's amount.** Omitted means the full payable amount. Less needs a reason (`PARTIAL_APPROVAL_REASON_REQUIRED`). More is refused (`APPROVAL_EXCEEDS_PAYABLE`). Above `claim.review.approval-threshold` (KES 50,000 by default) only an ADMIN may approve (`ADMIN_APPROVAL_REQUIRED`, 403); an amount exactly at the threshold is still the adjuster's. The payable amount is evaluated again at approval, inside the transaction. The figure shown when review starts is only a guide.
7. **Staff access is scoped by the token, never the request.** An adjuster or insurer admin sees and acts on claims of the `insurerId` in their token; a platform admin (an ADMIN with no `insurerId`) sees every insurer. An adjuster token with no `insurerId` is refused (`STAFF_SCOPE_MISSING`), so a missing claim can never widen an adjuster's reach. A claim of another insurer answers 404, exactly like a claim that does not exist. A staff member can never act on a claim they filed themselves (`SELF_REVIEW_NOT_ALLOWED`). Staff may read a claim, its history and its documents; they cannot file or change a claim as a customer.
8. **Soft flags route, they never reject.** Four flags are raised at submission and stored as `FLAG` checks: possible duplicate, high amount, incident within 30 days of the start of cover, and frequent claims (three in 30 days, counting this one). A flagged claim goes first in the queue. The flags and the payable estimate are hidden from the customer, because flags are signals for reviewers. All thresholds are configuration.
9. **Events.** `ClaimApproved`, `ClaimRejected` and `ClaimInfoRequested` are published through the outbox in the same transaction as the decision. `ClaimApproved` carries the amount and the holder, for the payout saga (M7).
10. **The audit trail is a structured log.** Every staff action, successful, refused or failed, writes one line on the `audit` logger with the action, outcome, stable error code, claim id, actor id, role and insurer id. It never contains a name, the reviewer's typed words, an amount reason or a request body. A persistent audit table is not built in M6 (parked).
11. **The customer answers an information request with `POST /claims/{id}/respond`** (INFO_REQUESTED back to UNDER_REVIEW), and may upload documents while the claim waits.

## Alternatives considered

- **Pessimistic row locks (`SELECT ... FOR UPDATE`) on the totals.** Simpler to reason about, but it holds a lock across the whole evaluation and risks deadlocks when a chain touches several rows in different orders. Optimistic locking with a short retry was chosen, as PLAN 11 states.
- **Reserve the amount before the co-pay.** Would make a co-pay use up the limit and could leave the insurer paying less than the limit allows. Rejected.
- **Auto-reject a claim whose limit is used up.** Takes the reason away from the reviewer and publishes a decision no person made. Rejected.
- **Compute the running total from approved claims on every approval.** No second table, but the sum would have to be locked and re-read for every approval, and a released reservation would have no record. Rejected.
- **A persistent audit table.** Better for queries and retention, but it needs its own lifecycle and access rules. Deferred; the log carries the facts for now.

## Consequences

- The total rows and reservations are money: a change to how they are written needs a migration and a data check against existing totals.
- `policy year` is part of the row key from the start, so renewals can add a year without a schema change.
- The first approval on a policy and a racing second one both try to create the row; the loser retries. Under heavy load on one policy a reviewer may see `409 CONFLICT` and should retry.
- Reviewer-only fields (`flagged`, `amountEligible`, `reviewerId`, the `FLAG` checks) must never be added to a customer response.
- The state machine is unchanged in M6: every edge a reviewer uses was already in ADR-016.
