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

Payload schemas for each event are added in the milestone that introduces the producer (see PLAN 10). No event payload is defined yet in M0b.
