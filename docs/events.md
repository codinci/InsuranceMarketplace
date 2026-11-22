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
| `user.events` | UserRegistered | auth-service | notification-service |
| `policy.events` | **PolicyActivated**, **PolicyCancelled**, PolicyExpired | policy-service | payment-service (M4b), claim-service, notification-service |
| `payment.events` | **PremiumPaid**, **PremiumFailed**, PayoutSucceeded, PayoutFailed | payment-service | policy-service (M4b), claim-service, notification-service |
| `claim.events` | **ClaimSubmitted**, **ClaimInfoRequested**, **ClaimApproved**, **ClaimRejected** | claim-service | payment-service (M7), notification-service |

Payload schemas for each event are added in the milestone that introduces the producer (see PLAN 10). The payload records live in `common-events`; the `type` names are constants in `EventTypes`.

## Delivery (ADR-013)

- **Producers** write the event to an `outbox_events` table in the same transaction as the business change. A relay sends unsent rows to Kafka every second and marks a row published only after the broker acknowledges it, so a crash causes a resend, never a loss. The relay stops at the first failure, so later events about the same aggregate are not sent out of order.
- **Consumers** record each applied `eventId` in a `processed_events` table (the inbox) in the same transaction as their change. A redelivered event is skipped.
- **Failures:** a consumer retries a failing message three times, one second apart, then sends it to `<topic>.DLT`. An unknown `type` goes to the DLT at once.
- **Keys:** the aggregate id (the policy id for every event below), so all events about one policy land on one partition, in order.
- **Headers:** every message carries the event `type` as the Kafka header `eventType`, so a consumer or a tool can route or filter without parsing the JSON.

## Payloads (M4b)

### PremiumPaid v1 (`payment.events`, payment-service)

Sent when a premium payment succeeds. policy-service activates the policy, but only if it is still `PENDING_PAYMENT` and `paymentId` is the policy's own payment.

```json
{
  "paymentId": "0d6f2a4e-3b1c-4c8e-9a7d-2f5e6b7c8d9e",
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
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

In M4b the only producer is the unpaid-policy job: a policy still `PENDING_PAYMENT` 24 hours after purchase is cancelled with `reason` `UNPAID`. payment-service then marks its payment `EXPIRED`, and a late pay attempt answers 409 `PAYMENT_EXPIRED`. Cancelling a paid policy (D7) arrives in M8.

```json
{
  "policyId": "03834615-4b48-4152-a6fc-834c06b1139a",
  "policyNumber": "POL-ACACIA-2026-000002",
  "policyVersion": 2,
  "reason": "UNPAID",
  "effectiveDate": "2026-11-22"
}
```

`policyVersion` starts at 1 at purchase and increases with every change. A consumer that has seen a higher version for a policy ignores a lower one.

**Consumer: claim-service (M5a, ADR-015).** Both events feed claim-service's policy read model. A change applies only when its `policyVersion` is higher than the stored one, so a redelivery or a late older event changes nothing. A `PolicyCancelled` that arrives before its `PolicyActivated` leaves a row with no holder; the later activation is then not newer and is ignored. Unknown `type` values go to `policy.events.DLT`.

### ClaimSubmitted v1 (`claim.events`, claim-service, M5b)

Sent when a claim passes the hard checks and is accepted for review. A claim the checks reject is not announced: its rejection is visible on the claim and in its history (ADR-016). The aggregate id is the claim id; the envelope's `insurerId` is the claim's insurer.

| Field | Type | Meaning |
|---|---|---|
| `claimId` | UUID | The claim |
| `claimNumber` | string | `CLM-<insurerCode>-<year>-<sequence>` |
| `policyId` | UUID | The policy the claim is against |
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
