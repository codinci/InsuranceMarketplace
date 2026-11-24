# M7: The payout saga

## What M7 delivers

- **Payouts in payment-service.** `ClaimApproved` creates one payout payment, the mock provider pays or declines, and `PayoutSucceeded` or `PayoutFailed` goes out through the outbox.
- **The claim side.** Approval leaves the claim PAYOUT_PENDING. The payout events move it to PAID or PAYOUT_FAILED, with a history row for every step.
- **Retry.** `POST /api/v1/payments/{id}/retry-payout` for admins, scoped to their insurer, with an `Idempotency-Key`. `GET /api/v1/payments/payouts?claimId=` finds the payout, and `GET /api/v1/claims/queue?status=PAYOUT_FAILED` finds the claims.
- **Two new event contracts** in `common-events`, with contract tests.
- **A smoke script that reaches PAID**, then proves the failure path: a declined payout, PAYOUT_FAILED, a retry, PAID.

## New concepts

**A saga.** One business action (approve, then pay) spans two services with two databases, so one database transaction cannot cover it. A saga is a chain of local transactions joined by events: each step commits on its own and announces the result, and the next service reacts. If a step fails, the saga does not roll back; it records the failure and a person or a compensating step deals with it. In Laravel terms, think of a queued job chain where each job writes to its own database and dispatches the next, not of a single `DB::transaction`.

**Choreography.** No service is in charge. claim-service does not tell payment-service what to do; it announces `ClaimApproved`, and payment-service decides to pay. The alternative, orchestration, has one coordinator that calls each step. Choreography needs no coordinator, but the flow is spread over the services, so the docs (`docs/events.md`) have to show it in one place.

**Idempotency, two layers.** A message can arrive twice. The first layer is the inbox: the consumer writes the event id in the same transaction as its effect, and a second delivery finds it and stops. The second layer is a unique key on the data: a claim can only have one payout row, so even a new event id for the same claim creates nothing. Layers matter because each one fails differently: the inbox cannot help when the same business fact arrives under a new id.

**A detached entity.** `payments.save(entity)` can return a different object than the one you passed in. When an entity has a primitive `@Version` field, Spring Data cannot tell a new entity from an existing one, so `save` calls `merge`, which copies the state into a managed object and returns that. Changes made later to your original are not written. We hit this: the payout stayed PENDING in the database although the code had set SUCCEEDED. The fix is to keep the object `save` returns. It is the kind of bug a test that reads the database back finds, and a mock cannot.

**`@MockitoSpyBean`.** A spy is the real object with the ability to override single methods. The payout test makes the real mock provider decline one holder, then lets it behave normally again, so one test can show fail, fail again, then succeed on retry.

## Decisions to remember

- **The claim waits at once.** Approval moves the claim to PAYOUT_PENDING in the same transaction that writes `ClaimApproved`, so the claim never says APPROVED while a payout is already on its way (ADR-018).
- **The reservation is kept on failure.** The money is still owed. Giving the limit back needs a way to abandon a payout, which is parked.
- **Retry is admin-only.** An adjuster decides a claim; moving money needs the higher role.
- **Failure is configuration.** A premium can be asked to fail by the customer's request. A payout has no request, so a property lists the holders the mock declines.

## Self-check (answer before M8 starts)

1. Why does approval move the claim to PAYOUT_PENDING in the same transaction as writing `ClaimApproved`, and not when payment-service answers?
2. Two things stop a claim being paid twice when `ClaimApproved` is delivered twice. Name both, and say which one still works when the second delivery has a new event id.
3. The payout test passed on the first run for a premium but every payout stayed PENDING in the database. What was the cause, and what is the one-line fix?
4. A payout fails and an admin retries it successfully. List the claim's statuses from PAYOUT_FAILED to the end, and say why the claim passes through PAYOUT_PENDING.
5. Retry is called on a failed payout of insurer B by an adjuster of insurer B, by an admin of insurer A, and by an admin of insurer B. What does each get, and why are the first two answers different?

## Owner's answers

Answered so far: questions 1 to 4.

1. "Since the ClaimApproved event is itself a payment request it should never say approved when a payout exists." Verdict: right on the core idea, and incomplete. The approval event is the payout request, so once it is committed a payout is on its way and the claim must already say PAYOUT_PENDING, not APPROVED. What the answer leaves out is why it is the same transaction: the claim status and the outbox row commit together or not at all, so there is no moment where the event can be relayed while the claim still reads APPROVED, and no crash can leave one without the other. Waiting for payment-service to answer would leave exactly that gap, and would also need an extra acknowledgement event and consumer for behaviour the claim does not need (ADR-018).

2. "The inbox records the event id in the same transaction as the effect and the unique payout row per claim constraint ensures that a claim can only have only one payout row and this ensures that even when the second delivery has a new event id it sees the new id as a new event." Verdict: both layers named correctly, and the ending is muddled. The inbox is the first layer: a redelivery with the same event id finds its row and stops. The unique payout row per claim is the second layer. The last clause needs one correction: with a new event id the inbox does treat it as a new event and lets it through, so the inbox does not help there. It is the unique key on the claim that still works, because the second insert for the same claim is refused, so no second payout is created.

3. "The test passed because the status was checked in memory or in the response, the code kept changing the original object and succeeded was never written. Keeping the object save fixes this." Verdict: correct on the symptom and the fix, and it leaves out the cause of the detached copy. The test only looked at the object in memory or at the response, which showed SUCCEEDED, while the database row kept the old state. The cause is that the entity has a primitive `@Version` field, so Spring Data cannot tell a new entity from an existing one and `save` calls `merge`. `merge` copies the state into a managed object and returns that one, so later changes to the original are never written. The one-line fix is to keep what `save` returns: `payment = payments.save(payment);`. Only a test that reads the database back catches this; a mock cannot.

4. "The state machine's goes from payout_failed->payout_pending->paid and the history reads payout retried and then payout succeeded written by the system actor with the role of service." Verdict: correct on the statuses and on the history, which is more detail than the question asked for, and the second half of the question (why the claim passes through PAYOUT_PENDING) is not answered. Checked against `PayoutOutcomeService`: both rows are written with the system actor and role SERVICE, with the reasons "payout retried" and "payout succeeded". One precision: the claim stays PAYOUT_FAILED while the retry runs. Both moves happen together, in one transaction, when the PayoutSucceeded event arrives. Why through PAYOUT_PENDING: the state machine has no direct PAYOUT_FAILED to PAID edge, so the only way is the existing edges PAYOUT_FAILED to PAYOUT_PENDING and PAYOUT_PENDING to PAID. The retry then shows in the history as its own row instead of the claim jumping from failed to paid, and no new edge or state is needed.
