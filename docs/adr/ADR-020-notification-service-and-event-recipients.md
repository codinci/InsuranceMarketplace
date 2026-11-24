# ADR-020: notification-service, and who an event is for

- **Status:** Accepted (2026-11-24). Implemented in M8a.
- **Context:** notification-service has to tell a customer about what happened to them. Four events (`PolicyCancelled`, `PremiumPaid`, `PremiumFailed`, `ClaimSubmitted`) named a policy or a claim but not the customer, so a consumer could not tell who to notify. The event shapes are one of the expensive-to-change decisions (PLAN 20.6), so this was reviewed before it was changed.

## Decision

1. **Events carry their recipient.** `holderUserId` is added to the four events, in the same position as on `ClaimApproved`. Each producer already holds the id on its own entity, so no new data is stored. The alternative, notification-service keeping a policy-to-holder table, would add state and a race when events arrive out of order.
2. **Adding a field is not a new version.** Consumers ignore fields they do not know (`@JsonIgnoreProperties(ignoreUnknown = true)` on every payload), so an additive field is safe for every reader. A new `version` is for renaming, removing or changing the meaning of a field. A consumer must cope with the field being `null`, because events written before it existed are still in Kafka.
3. **`UserRegistered` is published by auth-service through its own outbox.** The event is written in the registration transaction. It carries the user id, email and names, and never a password hash, national ID or role. This closes the M1 deferral.
4. **notification-service is a plain consumer.** It reads all four topics, renders one message per known event type and writes a `notification_log` row. It has its own database, no public API and no security surface; the channel is the log row itself (a real channel, such as email or SMS, replaces it later without changing the events).
5. **Idempotent twice.** The inbox skips a redelivered `eventId`; `notification_log.event_id` is unique as the second layer. A different `eventId` for the same business fact is a different event to the consumer and sends a second notification. Producers own "once per business fact".
6. **No personal data in what it writes.** Message bodies and log lines hold ids, numbers and amounts, never a name or an address. The recipient is a user id.
7. **An event with no recipient is skipped, not failed.** A new consumer group reads each topic from the beginning, so it meets events from before `holderUserId` existed. It records them as applied and moves on; failing them would fill the dead-letter topic with messages no replay can fix.
8. **Unknown types are dead-lettered.** A type the service does not know goes to `<topic>.DLT` at once (ADR-013), so a new event type is noticed, not dropped. `PolicyExpired` is added when M8b publishes it.

## Consequences

- Every event a customer should hear about now names the customer; the contract tests lock it.
- Four producers and their tests changed in M8a; no consumer needed changes.
- `UserRegistered` puts an email address on `user.events`. The topic is internal to the compose network and the compose stack is not published to the host; a later channel that needs more (a phone number) extends the payload additively.
- notification-service starts reading history on first start. That is wanted for a first run and harmless afterwards.

## Alternatives rejected

- **A lookup from notification-service to auth-service for the recipient.** A synchronous dependency for a stub, and it still would not say which customer a policy or claim belongs to. Rejected.
- **A new version of each of the four events.** Needless: the change is additive and every consumer ignores unknown fields. Rejected.
- **Putting the name and email in every event.** Spreads personal data across topics. Rejected: the id is enough for the log, and a real channel can look the address up when it exists.
