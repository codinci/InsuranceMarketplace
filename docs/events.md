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

| Topic | Events (planned) | Producer | Consumers |
|---|---|---|---|
| `user.events` | UserRegistered | auth-service | notification-service |
| `policy.events` | PolicyActivated, PolicyCancelled, PolicyExpired | policy-service | claim-service, notification-service |
| `payment.events` | PremiumPaid, PremiumFailed, PayoutSucceeded, PayoutFailed | payment-service | policy-service, claim-service, notification-service |
| `claim.events` | ClaimSubmitted, ClaimInfoRequested, ClaimApproved, ClaimRejected | claim-service | payment-service, notification-service |

Payload schemas for each event are added in the milestone that introduces the producer (see PLAN 10). The first producer arrives in M4b.

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
