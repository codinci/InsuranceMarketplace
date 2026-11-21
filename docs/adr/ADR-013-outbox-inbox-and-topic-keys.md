# ADR-013: Transactional outbox, inbox and topic keys

- **Status:** Accepted (2026-11-21). Implemented in M4b (payment-service and policy-service).
- **Context:** A service changes its database and must tell other services. Writing to the database and then calling Kafka is a dual write: if the process dies between the two, the database and the events disagree (PLAN 11). Consumers also see duplicates, because delivery is at least once. PLAN 21.6 names topic and partition design as expensive to change, and the M0b parking lot asked for this ADR before the first event is published.

## Decision

1. **Transactional outbox, per service.** A service writes its business change and an `outbox_events` row in one database transaction. A scheduled relay in the same service reads unsent rows, publishes each to Kafka, and marks it sent.
   - Columns: `event_id` (primary key, the envelope's `eventId`), `topic`, `message_key` (the aggregate id), `event_type`, `payload` (text, the whole envelope exactly as it is sent), `created_at`, `published_at` (null until sent), `attempts`. Amended during M4b implementation: the event id is the key, so no separate `id` is needed, and the payload is stored as text because the relay sends it unchanged and nothing queries inside it.
   - The relay selects unsent rows `ORDER BY created_at, event_id LIMIT n FOR UPDATE SKIP LOCKED`, sends each with `KafkaTemplate` and waits for the broker's acknowledgement before it marks the row. A crash after sending and before marking means the event goes out again. That is accepted, because consumers are idempotent.
   - The relay runs on one instance in v1 (PLAN 11: ShedLock is deferred to M8). `SKIP LOCKED` already makes a second instance safe, so this is not a correctness dependency.
2. **Inbox, per consumer.** Each consuming service has `processed_events (event_id PK, consumer, processed_at)`. The consumer inserts the row and applies its business change in **one transaction**. A duplicate `event_id` violates the primary key, the transaction is rolled back, and the message is acknowledged without a second effect.
3. **Topics and keys.** The topics are the four in PLAN 10, with 3 partitions each (compose). The Kafka **message key is the `aggregateId`**, which for `payment.events` and `policy.events` is the policy id. Kafka keeps order within a partition, so all events for one policy are seen in the order they were written. Across policies there is no order, and no consumer needs any.
4. **Envelope and headers.** The JSON envelope (ADR-005) is the message value. The event `type` is also sent as the Kafka header `eventType`, so a consumer can route or reject before parsing.
5. **Failures.** A consumer retries with backoff. After the retries it sends the message to `<topic>.DLT` and logs. Replay is a manual procedure (PLAN 11, M8 test).
6. **Libraries.** Spring Kafka (`KafkaTemplate`, `@KafkaListener`, `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`). The relay and tables are plain Spring and JPA code, so the owner can read every step.

## Alternatives considered

- **Publish after commit, without an outbox.** Simpler, but a crash between commit and publish loses the event. Rejected: the purchase-and-pay path cannot lose `PremiumPaid`.
- **Kafka transactions across the database and Kafka.** Kafka cannot enlist a PostgreSQL transaction, so this gives no guarantee across both. Rejected.
- **Debezium (CDC from the outbox table).** Removes the relay code, but adds a Kafka Connect cluster to compose. Kept as a stretch item (PLAN 17).
- **One topic per event type.** More topics without a gain: consumers still need ordering per aggregate, which the key gives. Rejected.

## Consequences

- Every event is at least once. The consumer's inbox row is the only guarantee of "once effect", so every consumer must write its inbox row in the same transaction as its effect.
- Events are published a little after the commit (the relay interval, about one second). Tests use a short interval and wait for the message.
- Old `published_at` rows grow the table. A cleanup belongs with the lifecycle jobs (M8).
- The outbox and inbox code was copied into each service in M4b and extracted in M5a, when claim-service became the third user (see the amendment below).

## Amendment (M5a, 2026-11-21): the shared `common-messaging` module

The plumbing now lives in one module, `common-messaging` (package `com.example.insurance.messaging`), used by payment-, policy- and claim-service:

- **In the module:** the `OutboxEvent` and `ProcessedEvent` entities and their repositories, `OutboxWriter`, the scheduled `OutboxRelay` (with the `eventType` header), `Inbox` (`alreadyApplied`, `markApplied`), `EventJson` (one mapper, amounts as `BigDecimal`), `UnknownEventTypeException` and the dead-letter error handler.
- **Wiring:** `MessagingAutoConfiguration`, loaded by Spring Boot from `META-INF/spring/...AutoConfiguration.imports`. It adds its package to the packages Spring Boot scans for JPA entities and repositories (`@AutoConfigurationPackage`) instead of using `@EntityScan`, which would replace each service's own scanning.
- **Settings:** `messaging.outbox.relay-delay-ms` (default 1000) and `messaging.consumer-name` (default `spring.application.name`, recorded with each inbox row).
- **Stays in each service:** its tables (`outbox_events`, `processed_events`, created by the service's own Flyway migration in its own database), its `Topics` constants, its listeners and its business code.

Nothing on the wire or in the tables changed.
