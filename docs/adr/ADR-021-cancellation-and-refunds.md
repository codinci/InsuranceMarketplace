# ADR-021: customer cancellation, the refund rule and refunds

- **Status:** Accepted (2026-11-26). Implemented in M8b.
- **Context:** D7 makes the cancellation and refund rule data in each plan (`cancellation.noticeDays`, `refund.model`, `noRefundAfterMonths`, `noRefundIfClaims`), and the terms snapshot has carried it since M4a, but nothing evaluated it. A paid policy could not be cancelled, and payment-service only logged a warning when a cancelled policy had a paid premium. M8b closes the policy lifecycle. The `PolicyCancelled` shape is an expensive-to-change decision (PLAN 20.6), so its change was reviewed with the owner in plan mode.

## Decision

1. **The holder cancels; the status changes at once.** `POST /api/v1/policies/{id}/cancel` (CUSTOMER, owner only, 404 otherwise) moves an ACTIVE policy to CANCELLED with `cancelEffectiveDate = today + noticeDays`, the first day without cover. claim-service already accepts incidents before that date, so no pending state or second job is needed. A policy awaiting payment is cancelled with today's date and no refund. Anything else answers 409 `POLICY_NOT_CANCELLABLE`; a notice period past the end of cover answers 422 `NOTICE_PAST_END`.
2. **policy-service computes the refund from the snapshot.** `policy.rules.RefundRule` is a pure function of the cancellation terms, premium, dates, claim count and whether the premium was paid. `NONE` gives zero; `PRO_RATA` gives the unused days' share, unless the request came `noRefundAfterMonths` or more after the start or `noRefundIfClaims` is set and a claim was submitted. It is under the 90% line and 85% branch gate.
3. **The claim count comes from events, not a call.** policy-service consumes `ClaimSubmitted` from `claim.events` into its own `policy_claims` table, keyed by claim id, with the inbox. Any submitted claim counts, including one rejected later, so a customer cannot collect a refund and then a payout for the same period.
4. **The refund travels in `PolicyCancelled`.** The event gains `refundAmount` (additive, same version, ADR-020) and the reason `CUSTOMER`. The status, the refund amount and the outbox row commit together.
5. **payment-service pays the refund asynchronously.** On `PolicyCancelled` with a paid premium and `refundAmount > 0`, it creates one payment with reference type `REFUND` and reference the policy, and attempts it through the mock provider. The event id is the intent key and the first attempt's idempotency key; the unique `(reference_type, reference_id)` allows one refund per policy. It never refunds more than the premium received. Outcomes go out as `RefundSucceeded` or `RefundFailed`, keyed by the policy id.
6. **A failed refund is retried by an admin.** `POST /api/v1/payments/{id}/retry-refund` with an `Idempotency-Key`, insurer-scoped (another insurer's refund answers 404), like the payout retry (ADR-018). `GET /api/v1/payments/refunds?policyId=` finds it.
7. **Customers cannot pay outgoing money.** A refund, like a payout, answers 409 `PAYMENT_NOT_PAYABLE` to the pay endpoint.

## Consequences

- Both seed insurers' rules work through the same code; a new rule shape (a different model) is a code change with tests and a schema update, as CLAUDE.md requires.
- The claim count can lag: a claim submitted seconds before a cancellation may not be counted yet. Accepted for now and parked; closing it needs a synchronous check or a hold on refunds.
- A policy can now carry three payments over its life in payment-service: the premium, possibly an expired attempt, and one refund. The customer's payment list for a policy shows the premium and the refund.
- policy-service and claim-service skip the two refund events; notification-service tells the customer.

## Alternatives rejected

- **A synchronous `POST /internal/refunds` from policy-service.** It would give the customer an immediate refund status, but a payment-service outage would fail the cancellation with 503. The event path works while payment-service is down and matches the payout saga.
- **Stay ACTIVE until the effective date, then a job cancels.** Closer to the real world, but adds a pending state, a second job and its tests, for no difference in what the customer is owed.
- **Count only approved claims.** Kinder to the customer, but a claim submitted before cancelling and approved after it would give both a refund and a payout.
- **payment-service computes the refund.** It would need the plan's rule and the claim count, which belong to policy-service. The server that owns the terms computes the amount; payment-service only moves money and caps it at the premium.
