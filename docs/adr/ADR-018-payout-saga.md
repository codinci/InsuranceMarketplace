# ADR-018: The claim payout saga

- **Status:** Accepted (2026-11-22). Implemented in M7.
- **Context:** An approved claim has to be paid, and the money moves in another service. A payout must never be made twice, and a claim must never say PAID when nothing was paid. Three things here are expensive to change: the shape of the saga and its events, how a duplicate is made harmless, and what happens to the limit reservation when a payout fails.

## Decision

1. **The saga is choreographed with events.** `ClaimApproved` (claim.events, key claim id) makes payment-service create and attempt a payout. The result comes back as `PayoutSucceeded` or `PayoutFailed` (payment.events, key claim id). claim-service reads it and moves the claim. No service calls another synchronously, so either side can be down and the saga resumes when it returns.
2. **The claim waits at once.** In the same transaction as the approval, the claim goes APPROVED then PAYOUT_PENDING. The approval event is the payout request, so waiting for the payout is the truth the moment it is committed. No acknowledgement event is needed. Both steps are in the history.
3. **A payout is a payment whose reference is a claim.** `payments.reference_type` allows `CLAIM` as well as `POLICY`, and the existing unique `(reference_type, reference_id)` means a claim can only ever have one payout row. A retry reuses the row and adds a ledger row, so the ledger shows every attempt.
4. **Duplicates are harmless twice over.** The consumer's inbox remembers the event id, and the payout row is unique per claim, so even a second `ClaimApproved` with a different event id changes nothing. The event id is the payout's intent key and the idempotency key of its first attempt. A retry carries its own `Idempotency-Key`; repeating a key returns the first result, and parallel use of one key leaves one ledger row.
5. **Retry is an admin action.** `POST /api/v1/payments/{id}/retry-payout` is for an ADMIN only (an adjuster cannot move money), scoped by the token's `insurerId` (a platform admin sees all; another insurer's payout answers 404). Only a payout in FAILED can be retried. A payout that fails again stays FAILED and sends `PayoutFailed` again; claim-service ignores it because the claim is already PAYOUT_FAILED. A retry that succeeds sends `PayoutSucceeded`, and the claim passes PAYOUT_FAILED, PAYOUT_PENDING, PAID through edges already on the state machine.
6. **The limit reservation is kept while a payout is failed.** The money is still owed and a retry is allowed, so giving the limit back would let a second claim use it up and leave the first unpayable. This changes ADR-017 point 4: `UtilisationService.release` stays built and tested but is not called, because calling it needs a way to abandon a payout (a new state-machine edge), which is parked. The state machine is unchanged by M7.
7. **Changes by events are made by the system.** History rows written by the saga use a fixed system actor id (the nil UUID) and the role `SERVICE`, so a reader can tell a person's change from an event's.
8. **The mock pays the holder's user id.** There is no payout destination in the data yet (parked). A customer cannot pay a payout through the premium endpoint (`PAYMENT_NOT_PAYABLE`).
9. **Failure is switched on by configuration.** A premium has a request that can ask the mock to decline; a payout has none. `payments.mock.payout-decline-payees` lists holder ids the mock declines. It is empty by default and exists for tests and the smoke run.
10. **Events not for a service are skipped, not dead-lettered.** payment-service skips claim events other than `ClaimApproved`; policy-service skips the payout events; claim-service skips the premium events. Only unknown types go to the dead-letter topic.

## Consequences

- A claim can sit in PAYOUT_PENDING if payment-service is down; it resolves when the service returns and the event is read. A claim in PAYOUT_FAILED needs a person.
- Nothing pays out automatically twice, and a late or repeated event cannot move a PAID claim.
- A claim whose payout can never succeed keeps its limit reserved until the abandon path is built.

## Alternatives rejected

- **Release the reservation on every failure and reserve again on retry.** Two writes on money for one claim, and a window in which another claim can take the limit. Rejected.
- **Move the claim to PAYOUT_PENDING when payment-service acknowledges.** More accurate, but adds an event contract and a consumer path for no behaviour the claim needs. Rejected.
- **A synchronous call from claim-service to payment-service.** Ties approval to payment-service being up and makes a half-done approval possible. Rejected.
- **A `type` column on payments.** `reference_type` already tells a premium from a payout. Rejected as redundant.
