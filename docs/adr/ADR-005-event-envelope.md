# ADR-005: Shared event envelope and JSON wire format

- **Status:** Accepted (2026-11-19). Implemented in M0b.
- **Context:** Every service publishes and consumes events through Kafka. PLAN 20.6 names the event envelope as an expensive-to-change decision: every service depends on it, and changing it after data exists is costly. The owner is new to Java and Spring, so the contract has to be simple to read and hard to break by accident.

## Decision

1. **One envelope record for every event** in `common-events`: `EventEnvelope<T>` with `eventId, type, version, occurredAt, aggregateId, insurerId, correlationId, payload` (PLAN 10). `payload` is generic, and each event type has its own payload record (for example `PolicyActivated`, added in M4).
2. **JSON on the wire**, serialized with Jackson. Spring Boot 4 uses Jackson 3, so the ObjectMapper package is `tools.jackson`. The annotations are still `com.fasterxml.jackson.annotation`.
3. **Forward compatibility:** the envelope carries `@JsonIgnoreProperties(ignoreUnknown = true)`, so consumers ignore fields they do not know. Changes are additive only (PLAN 11).
4. **Identifiers:** `eventId` is a UUID and is the inbox key for idempotent consumers. `aggregateId` is the Kafka message key (PLAN 10). `aggregateId` and `insurerId` are strings, so the envelope does not depend on each service's id format. `insurerId` is null for platform-level events.
5. **Timestamps** are `Instant` in UTC, serialized as ISO-8601 strings.

## Alternatives considered

- **Untyped JSON payload** (`JsonNode`): flexible, but consumers would parse fields by name and mistakes would only show at runtime. Rejected.
- **A separate envelope class per topic:** more code for the same shape. Rejected.

## Consequences

- Adding a field is safe. Renaming or removing one is a breaking change and needs a new ADR and a new `version` for the affected event type.
- Consumers must still check `type` and `version` before reading the payload. Unknown types are logged and sent to the DLT (PLAN 11), not silently dropped.
- The envelope tests in `common-events` (`EventEnvelopeTest`) lock down the JSON field names and the unknown-field behaviour. Changing either breaks the build.
