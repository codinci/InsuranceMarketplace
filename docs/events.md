# Events

Every event uses the shared envelope (ADR-005, `common-events` module, class `EventEnvelope`). The Kafka message key is the `aggregateId`. Consumers are idempotent through an inbox table keyed by `eventId`.

## Envelope

```json
{
  "eventId": "5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f",
  "type": "PolicyActivated",
  "version": 1,
  "occurredAt": "2026-11-19T04:00:00Z",
  "aggregateId": "policy-42",
  "insurerId": "insurer-a",
  "correlationId": "corr-abc123",
  "payload": {}
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | Unique per event. Inbox key. |
| `type` | string | Event name. Selects the payload class. |
| `version` | integer | Payload schema version for this `type`. |
| `occurredAt` | ISO-8601 UTC | When the business fact happened. |
| `aggregateId` | string | Kafka key. |
| `insurerId` | string or null | Null for platform-level events. |
| `correlationId` | string | Carried from the originating HTTP request. |
| `payload` | object | Event-specific, defined per `type` below. |

Rules: additive changes only. Consumers ignore unknown fields.

## Topics

Topics are created by the compose stack at start (M0b). Each topic has a `.DLT` dead-letter topic.

| Topic | Events (planned; built so far in bold) | Producer | Consumers |
|---|---|---|---|
| `user.events` | **UserRegistered** (M8a) | auth-service | notification-service (M8a) |
| `policy.events` | **PolicyActivated**, **PolicyCancelled**, **PolicyExpired** (M8b) | policy-service | payment-service (M4b; refunds from M8b), claim-service, notification-service (M8a) |
| `payment.events` | **PremiumPaid**, **PremiumFailed**, **PayoutSucceeded**, **PayoutFailed**, **RefundSucceeded**, **RefundFailed** (M8b) | payment-service | policy-service (premium events, M4b), claim-service (payout events, M7), notification-service (M8a) |
| `claim.events` | **ClaimSubmitted**, **ClaimInfoRequested**, **ClaimApproved**, **ClaimRejected** | claim-service | payment-service (`ClaimApproved`, M7), policy-service (`ClaimSubmitted`, M8b), notification-service (M8a) |

Payload schemas for each event are added in the milestone that introduces the producer (see PLAN 10). The payload records live in `common-events`; the `type` names are constants in `EventTypes`.

## Delivery (ADR-013)

- **Producers** write the event to an `outbox_events` table in the same transaction as the business change. A relay sends unsent rows to Kafka every second and marks a row published only after the broker acknowledges it, so a crash causes a resend, never a loss. The relay stops at the first failure, so later events about the same aggregate are not sent out of order.
- **Consumers** record each applied `eventId` in a `processed_events` table (the inbox) in the same transaction as their change. A redelivered event is skipped.
- **Failures:** a consumer retries a failing message three times, one second apart, then sends it to `<topic>.DLT`. An unknown `type` goes to the DLT at once.
- **Keys:** the aggregate id: the policy id for policy, premium and refund events, the claim id for claim and payout events, the user id for `UserRegistered`. All events about one aggregate land on one partition, in order.
- **Replay (ADR-019):** after the cause is fixed, `DeadLetterReplayer` (common-messaging) sends dead letters back to their original topic with the same key. See the runbook. The inbox makes a replay of an already applied event harmless.
- **Additive changes only (ADR-020):** a new field may be added to a payload without a new version, because consumers ignore fields they do not know. An event published before the field existed reads with the field `null`; a consumer must cope with that. Renaming, removing or changing the meaning of a field needs a new `version`.
- **Headers:** every message carries the event `type` as the Kafka header `eventType`, so a consumer or a tool can route or filter without parsing the JSON.

## Recipients (M8a)

Every event a customer should hear about names that customer: `holderUserId` on the policy, payment and claim events, `userId` on `UserRegistered`. `holderUserId` was added in M8a to `PremiumPaid`, `PremiumFailed`, `PolicyCancelled` and `ClaimSubmitted` (ADR-020). Events published before M8a lack it; notification-service skips them and records them as applied.

## Payloads (M4b)

### UserRegistered v1 (`user.events`, auth-service, M8a)

Sent when a customer registers. The aggregate id is the user id; `insurerId` is null. It carries what a welcome message needs and never a password hash, national ID or role.

```json
{
  "userId": "7b3d9f10-5a2e-4c61-8d4f-0e1a2b3c4d5e",
  "email": "amina@example.com",
  "firstName": "Amina",
  "lastName": "Otieno"
}
```

The event is written to auth-service's outbox in the registration transaction, so a registered customer is always announced and a refused registration announces nothing. Contract test: `EventPayloadContractTest.userRegisteredRoundTripsAndCarriesNoSecrets`.

### PremiumPaid v1 (`payment.events`, payment-service)

Sent when a premium payment succeeds. policy-service activates the policy, but only if it is still `PENDING_PAYMENT` and `paymentId` is the policy's own payment.

```json
{
  "paymentId": "0d6f2a4e-3b1c-4c8e-9a7d-2f5e6b7c8d9e",
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "holderUserId": "7b3d9f10-5a2e-4c61-8d4f-0e1a2b3c4d5e",
  "amount": 12500.00,
  "currency": "KES",
  "providerRef": "mock-6a1f0c2d-9b8e-4f7a-8c3d-2e1f0a9b8c7d"
}
```

### PremiumFailed v1 (`payment.events`, payment-service)

Sent when the mock provider declines a payment. The policy stays `PENDING_PAYMENT` and the customer may pay again with a new `Idempotency-Key`.

```json
{
  "paymentId": "0d6f2a4e-3b1c-4c8e-9a7d-2f5e6b7c8d9e",
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "holderUserId": "7b3d9f10-5a2e-4c61-8d4f-0e1a2b3c4d5e",
  "reason": "PROVIDER_DECLINED"
}
```

### PolicyActivated v1 (`policy.events`, policy-service)

Sent when a policy becomes `ACTIVE`. It carries everything claim-service needs to build its read model without calling policy-service (M5): the members and the terms snapshot below.

```json
{
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "policyVersion": 2,
  "holderUserId": "8c2e4f6a-1b3d-4e5f-8a9b-0c1d2e3f4a5b",
  "coverStart": "2026-12-01",
  "coverEnd": "2027-11-30",
  "members": [
    { "memberNumber": "ACA-000002-01", "relationship": "PRINCIPAL", "sex": "FEMALE", "firstName": "Smoke", "lastName": "Buyer", "dateOfBirth": "1990-06-15", "student": false }
  ],
  "terms": { "snapshotVersion": 1, "...": "see Policy terms snapshot v1" }
}
```

### PolicyCancelled v1 (`policy.events`, policy-service)

Two producers, both in policy-service:

- **The unpaid-policy job (M4b):** a policy still `PENDING_PAYMENT` 24 hours after purchase is cancelled with `reason` `UNPAID` and no `refundAmount`. payment-service then marks its payment `EXPIRED`, and a late pay attempt answers 409 `PAYMENT_EXPIRED`.
- **The holder (M8b, D7, ADR-021):** `POST /api/v1/policies/{id}/cancel` sends `reason` `CUSTOMER`. For an active policy `effectiveDate` is today plus the plan's `noticeDays` (the first day without cover) and `refundAmount` is what the plan's refund rule gives, possibly `0.00`. A policy awaiting payment is cancelled with `effectiveDate` today and `refundAmount` `0.00`.

```json
{
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "holderUserId": "7b3d9f10-5a2e-4c61-8d4f-0e1a2b3c4d5e",
  "policyVersion": 3,
  "reason": "CUSTOMER",
  "effectiveDate": "2027-01-31",
  "refundAmount": 33500.00
}
```

| Field | Type | Meaning |
|---|---|---|
| `reason` | string | `UNPAID` or `CUSTOMER` |
| `effectiveDate` | date | The first day without cover. Incidents before it stay covered |
| `refundAmount` | decimal or null | Added in M8b (additive, same version, ADR-020). The refund policy-service computed from the policy's terms snapshot. `null` for `UNPAID` and for events sent before M8b |

**Consumer: payment-service (M4b, M8b).** A premium still payable is marked `EXPIRED`. A paid premium with a `refundAmount` above zero gets one refund payment (reference type `REFUND`), never more than the premium received; see `RefundSucceeded` below.

`policyVersion` starts at 1 at purchase and increases with every change. A consumer that has seen a higher version for a policy ignores a lower one.

**Consumer: claim-service (M5a, ADR-015).** Both events feed claim-service's policy read model. A change applies only when its `policyVersion` is higher than the stored one, so a redelivery or a late older event changes nothing. A `PolicyCancelled` that arrives before its `PolicyActivated` leaves a row with no holder; the later activation is then not newer and is ignored. Unknown `type` values go to `policy.events.DLT`.

### PolicyExpired v1 (`policy.events`, policy-service, M8b)

Sent once by the expiry job, the day after an active policy's last day of cover. Key: the policy id.

```json
{
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "holderUserId": "7b3d9f10-5a2e-4c61-8d4f-0e1a2b3c4d5e",
  "policyVersion": 3,
  "expiredOn": "2027-11-30"
}
```

`expiredOn` is the last day of cover. claim-service stores the policy as `EXPIRED` and still accepts claims for incidents up to that day; payment-service skips the event.

Contract tests: `EventPayloadContractTest` (`aCustomerCancellationCarriesAnExactRefund`, `aPolicyCancelledFromBeforeM8bHasNoRefundAndStillReads`, `policyExpiredRoundTrips`).

### ClaimSubmitted v1 (`claim.events`, claim-service, M5b)

Sent when a claim passes the hard checks and is accepted for review. A claim the checks reject is not announced: its rejection is visible on the claim and in its history (ADR-016). The aggregate id is the claim id; the envelope's `insurerId` is the claim's insurer.

| Field | Type | Meaning |
|---|---|---|
| `claimId` | UUID | The claim |
| `claimNumber` | string | `CLM-<insurerCode>-<year>-<sequence>` |
| `policyId` | UUID | The policy the claim is against |
| `holderUserId` | UUID | The customer who made the claim (added in M8a, so a consumer can notify them) |
| `memberId` | string | The member's number on the policy |
| `moduleCode`, `benefitCode` | string | The benefit claimed |
| `cause` | `ACCIDENT` or `ILLNESS` | Why the member needs the benefit |
| `incidentDate` | date | The day of the incident |
| `amountClaimed` | decimal | Amount claimed; `NUMERIC(19,2)`, never a float |
| `currency` | string | ISO code, from the policy's terms |
| `submittedAt` | instant | When the claim was submitted |

Contract test: `EventPayloadContractTest.claimSubmittedRoundTripsAndKeepsMoneyExact`.

### ClaimApproved v1 (`claim.events`, claim-service, M6)

Sent in the same transaction as the approval (ADR-017), after the approved amount has been reserved against the policy's limits. The payout saga (M7) pays `amountApproved` and uses the envelope's `eventId` as its idempotency key.

| Field | Type | Meaning |
|---|---|---|
| `claimId`, `claimNumber` | UUID, string | The claim |
| `policyId` | UUID | The policy |
| `holderUserId` | UUID | The customer who is paid |
| `memberId` | string | The member's number on the policy |
| `benefitCode` | string | The benefit claimed |
| `amountApproved` | decimal | What will be paid; never more than the evaluated payable amount. `NUMERIC(19,2)` |
| `currency` | string | ISO code |
| `approvedAt` | instant | When the claim was approved |

### ClaimRejected v1 (`claim.events`, claim-service, M6)

Sent when a reviewer rejects a claim during review. A claim rejected by the automatic checks at submission is not announced (ADR-016). Fields: `claimId`, `claimNumber`, `policyId`, `holderUserId`, `reason` (the reviewer's words, shown to the customer), `rejectedAt`.

### ClaimInfoRequested v1 (`claim.events`, claim-service, M6)

Sent when a reviewer asks the customer for more information. The customer answers with `POST /api/v1/claims/{id}/respond`. Fields: `claimId`, `claimNumber`, `policyId`, `holderUserId`, `message` (what the reviewer needs, shown to the customer), `requestedAt`.

Contract tests: `EventPayloadContractTest` (`claimApprovedRoundTripsAndKeepsMoneyExact`, `claimRejectedRoundTripsWithItsReason`, `claimInfoRequestedRoundTripsWithItsMessage`).

### PayoutSucceeded v1 (`payment.events`, payment-service, M7)

Sent in the same transaction as the successful payout attempt, for the first attempt and for a successful retry. The envelope's aggregate id (and Kafka key) is the **claim id**, so every event of one claim stays in order. claim-service moves the claim to PAID.

| Field | Type | Meaning |
|---|---|---|
| `paymentId` | UUID | The payout payment |
| `claimId`, `claimNumber` | UUID, string | The claim that was paid |
| `holderUserId` | UUID | The customer who was paid |
| `amount` | decimal | What was paid. `NUMERIC(19,2)` |
| `currency` | string | ISO code |
| `providerRef` | string | The mock provider's reference |
| `paidAt` | instant | When the provider confirmed the payout |

### PayoutFailed v1 (`payment.events`, payment-service, M7)

Sent for a failed first attempt and again for every failed retry. Fields as for `PayoutSucceeded`, with `reason` (a stable code such as `PROVIDER_DECLINED`, never free text) and `failedAt` in place of `providerRef` and `paidAt`. claim-service moves a PAYOUT_PENDING claim to PAYOUT_FAILED and ignores the event for a claim that is already PAYOUT_FAILED or PAID.

Contract tests: `EventPayloadContractTest` (`payoutSucceededRoundTripsAndKeepsMoneyExact`, `payoutFailedRoundTripsWithAStableReason`, `thePayoutEventTypesAreTheNamesOnTheWire`).

### RefundSucceeded v1 (`payment.events`, payment-service, M8b)

Sent in the same transaction as a successful refund attempt, for the first attempt and for a successful retry. Key: the **policy id**, like the premium events of that policy.

| Field | Type | Meaning |
|---|---|---|
| `paymentId` | UUID | The refund payment |
| `policyId`, `policyNumber` | UUID, string | The cancelled policy |
| `holderUserId` | UUID | The customer who was refunded |
| `amount` | decimal | What was refunded. `NUMERIC(19,2)` |
| `currency` | string | ISO code |
| `providerRef` | string | The mock provider's reference |
| `refundedAt` | instant | When the provider confirmed the refund |

### RefundFailed v1 (`payment.events`, payment-service, M8b)

Sent for a failed first attempt and for every failed retry. Fields as for `RefundSucceeded`, with `reason` (a stable code such as `PROVIDER_DECLINED`) and `failedAt` in place of `providerRef` and `refundedAt`. An admin retries with `POST /api/v1/payments/{id}/retry-refund` and an `Idempotency-Key`.

Contract tests: `EventPayloadContractTest` (`refundSucceededRoundTripsAndKeepsMoneyExact`, `refundFailedRoundTripsWithAStableReason`, `theLifecycleEventTypesAreTheNamesOnTheWire`).

### The refund flow (M8b, ADR-021)

1. The holder cancels. In one transaction policy-service computes the refund from the terms snapshot (and its own claim count, below), marks the policy CANCELLED and writes `PolicyCancelled` with `refundAmount`.
2. payment-service reads it and, for a paid premium and a refund above zero, creates one refund payment. The event's `eventId` is its intent key and the idempotency key of its first attempt; the unique `(reference_type, reference_id)` allows one refund per policy, so a new event id for the same cancellation makes none.
3. The mock provider pays or declines; `RefundSucceeded` or `RefundFailed` goes out through the outbox. policy-service and claim-service skip both.

**The claim counter.** policy-service reads `ClaimSubmitted` from `claim.events` into its own `policy_claims` table (one row per claim id, with the inbox), so the `noRefundIfClaims` rule needs no call to claim-service. The other claim events are skipped. The count can lag by the time an event takes to arrive.

### The payout saga (M7, ADR-018)

1. claim-service approves a claim: in one transaction the claim goes APPROVED then PAYOUT_PENDING and `ClaimApproved` is written to the outbox (key: claim id).
2. payment-service reads `ClaimApproved` and creates one payout payment for the claim. The event's `eventId` is the payout's intent key and the idempotency key of its first attempt. It asks the mock provider to pay and writes `PayoutSucceeded` or `PayoutFailed` through its outbox.
3. claim-service reads the result: PAYOUT_PENDING to PAID, or PAYOUT_FAILED. After a failure an admin retries with `POST /api/v1/payments/{id}/retry-payout`; a successful retry sends `PayoutSucceeded` again and the claim passes PAYOUT_FAILED, PAYOUT_PENDING, PAID.

Redelivery is harmless at every step: each consumer checks its inbox, and a claim can only have one payout row.

Skipped by design: payment-service ignores `ClaimSubmitted`, `ClaimRejected`, `ClaimInfoRequested`, `PolicyActivated` and `PolicyExpired`; policy-service ignores the payout and refund events and every claim event except `ClaimSubmitted`; claim-service ignores the premium and refund events. Only unknown types go to the dead-letter topic.

## Policy terms snapshot v1

`TermsSnapshot` (`common-events`, package `com.example.insurance.events.policy`, ADR-011) is the terms a policy was sold with. policy-service stores it on every policy from M4a and returns it from `GET /api/v1/policies/{id}`. From M4b, `PolicyActivated` carries it to claim-service, which interprets it with the semantics in docs/catalogue-schema.md.

```json
{
  "snapshotVersion": 1,
  "insurerCode": "ACACIA",
  "planId": "5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f",
  "planCode": "STANDARD",
  "planVersion": 1,
  "currency": "KES",
  "ageBasis": "NEXT_BIRTHDAY",
  "modules": [
    {
      "module": "OUTPATIENT",
      "option": "OP-40K",
      "benefits": {
        "code": "OUTPATIENT",
        "kind": "REIMBURSEMENT",
        "limit": { "amount": 40000, "period": "PER_YEAR", "scope": "PER_MEMBER", "maxPerVisit": null, "maxCount": null },
        "copay": { "type": "PERCENT", "value": 0.1 },
        "requiredDocuments": [],
        "preAuthRequired": false,
        "children": [
          {
            "code": "CONSULTATION", "kind": "REIMBURSEMENT",
            "limit": { "amount": 40000, "period": "PER_YEAR", "scope": "PER_MEMBER", "maxPerVisit": 2500, "maxCount": null },
            "copay": null, "requiredDocuments": ["RECEIPT"], "preAuthRequired": false, "children": []
          }
        ]
      }
    }
  ],
  "waitingRules": [
    { "code": "GENERAL_ILLNESS", "days": 30, "precedence": 0, "cause": "ILLNESS", "surgical": null, "conditionTags": [], "benefits": [] }
  ],
  "exclusions": ["Cosmetic treatment"],
  "filingDeadlineDays": 90,
  "cancellation": { "noticeDays": 30, "refundModel": "PRO_RATA", "noRefundAfterMonths": 6, "noRefundIfClaims": true }
}
```

| Field | Notes |
|---|---|
| `snapshotVersion` | Shape version. A non-additive change needs a new version; consumers handle every version still held by live policies |
| `modules` | Only the options the customer selected, each with its full benefit tree |
| `limit`, `copay` | Null when absent. Amounts are decimals in the plan currency; a PERCENT co-pay is a fraction |
| `waitingRules` | All of the plan's rules; claim-service picks one with the D31 semantics |
| New fields | Nullable only, so older stored snapshots still read (ADR-011) |

## Consumer: notification-service (M8a, ADR-020)

notification-service reads all four topics and writes one `notification_log` row per event it can address (the channel is the log row itself). It has no public API.

| Event | Subject | Recipient |
|---|---|---|
| `UserRegistered` | Welcome | `userId` |
| `PolicyActivated` | Your policy is active | `holderUserId` |
| `PolicyCancelled` | Your policy was cancelled | `holderUserId` |
| `PremiumPaid` | Payment received | `holderUserId` |
| `PremiumFailed` | Payment failed | `holderUserId` |
| `ClaimSubmitted` | Claim received | `holderUserId` |
| `ClaimInfoRequested` | More information needed | `holderUserId` |
| `ClaimApproved` | Claim approved | `holderUserId` |
| `ClaimRejected` | Claim rejected | `holderUserId` |
| `PayoutSucceeded` | Claim paid | `holderUserId` |
| `PayoutFailed` | Payout delayed | `holderUserId` |
| `PolicyExpired` | Your policy has ended | `holderUserId` |
| `RefundSucceeded` | Refund paid | `holderUserId` |
| `RefundFailed` | Refund delayed | `holderUserId` |

- **Idempotent twice:** the inbox skips a redelivered `eventId`, and `notification_log.event_id` is unique. A different `eventId` for the same business fact is a different event to this service and sends a second notification; producers own "once per fact".
- **No personal data in the text:** bodies hold numbers and amounts, never a name or an address.
- **Unknown `type`:** goes to the dead-letter topic of the topic it arrived on.
