# M8a: notification-service, UserRegistered and dead-letter replay

## What M8a delivers

- **A new service, `notification-service`.** It reads all four event topics and writes one row per event in its own `notification_log` table. The channel is a stub: the row is the "message sent". It has no public API and is not routed by the gateway.
- **`UserRegistered`.** auth-service now has an outbox. Registering a customer writes the event in the same transaction as the user row, so a registered customer is always announced.
- **Recipients on events.** `holderUserId` was added to `PremiumPaid`, `PremiumFailed`, `PolicyCancelled` and `ClaimSubmitted`, so a consumer knows who to notify (ADR-020).
- **A dead-letter replay tool** in `common-messaging` (ADR-019): after a fix, a service can be started once with a property and it sends the dead letters back to where they came from.
- **Smoke script** reads `notification_log` and checks the buyer got a welcome, a payment, an approval and both payout outcomes.

## New concepts

**A consumer group.** Every Kafka consumer belongs to a group, and Kafka remembers how far each group has read each topic (its offset). A new group with `auto-offset-reset=earliest` reads a topic from the very beginning, which is why notification-service meets old events on its first start. Think of it as a queue worker that keeps its own bookmark per queue; in Laravel, a queue worker takes jobs off a queue and the job is gone, but here the messages stay and each group has its own bookmark.

**Additive schema change.** Adding a field to an event does not break readers, because every payload class ignores JSON fields it does not know. Removing or renaming a field does. That is why ADR-020 says "new field: same version; rename or remove: new version". The catch is the other direction: an old event has no such field, so the new reader sees `null`. Laravel parallel: adding a nullable column is safe, adding a NOT NULL column without a default is not.

**A pure function as a template.** `NotificationTemplates.render(event)` takes a payload and returns a subject and body, and touches nothing else (no database, no Kafka). That is why it has a plain unit test with no containers. The service around it does the I/O. Keeping the decision separate from the effect is the same idea as a Laravel `Mailable` that only builds the message while the queue sends it.

**A pattern-matching `switch`.** `switch (event.payload()) { case PremiumPaid p -> ...; case ClaimApproved p -> ... }` picks the branch by the object's type and gives it a typed name in one step. It replaces a chain of `instanceof` checks and casts. The `default` branch handles a payload the switch does not know.

**A functional interface.** `DeadLetterReplayer.ConsumerSource` has one method, so a lambda can stand in for it. We introduced it because SpotBugs flagged storing the factory directly (`EI_EXPOSE_REP2`: the class keeps a reference to an object someone else can mutate). The project rule is to fix a finding rather than suppress it, and a one-method interface of our own was the smallest fix.

**`ApplicationRunner` and `@ConditionalOnProperty`.** A Spring bean that implements `ApplicationRunner` runs once, after the application has started. `@ConditionalOnProperty` creates the bean only when a property is set. Together they make "replay the dead letters when I start the service with this setting" and "do nothing otherwise" with no code path in normal runs. It is like an Artisan command that only exists when an environment variable is present.

**Kafka commit, and why replay commits last.** Reading a message does not move the group's bookmark; committing does. The replayer sends every message of a batch first and commits afterwards. A crash between the two repeats the batch, and the inbox makes a repeat harmless. Committing first would lose messages on a crash, which is worse.

**`@MockitoSpyBean` again, as a switch.** In the replay test the real `NotificationService` is wrapped so that it throws until the test flips a flag, standing in for "the cause is fixed". The message fails, retries, lands in the dead-letter topic, then the flag flips, the replay runs and exactly one notification appears.

**A new Maven module touches the others.** Each service's Dockerfile copies every module, because the Maven reactor lists them all. So adding `notification-service` meant adding a `COPY` line to five other Dockerfiles. Forgetting one fails that service's image build.

## Decisions to remember

- **The event names its recipient.** A lookup or a policy-to-holder table in the consumer would add state and a race (ADR-020).
- **Idempotent twice, and honest about the limit.** The inbox stops a repeated event id; the unique `event_id` column backs it up. A new event id for the same fact sends a second notification, and the test says so. Producers own "once per fact".
- **No personal data in what it writes.** Bodies hold numbers and amounts. The recipient is an id.
- **Old events are skipped, not failed.** Events from before M8a have no holder, so the service records them as applied and moves on.
- **Replay is a property, not an endpoint.** No new HTTP surface to secure (ADR-019).
- **Unknown types still go to the DLT.** `PolicyExpired` will be added when M8b publishes it.

## Self-check (answer before M8b starts)

1. `PremiumPaid` is redelivered with the same event id, and later the same business fact is announced again under a new event id. How many notifications does the customer get, and which mechanism decides each answer?
2. notification-service starts for the first time on a platform that has been running since before M8a. What does it do with the old `PremiumPaid` events, why does it not send them to the dead-letter topic, and what would go wrong if it did?
3. Why can adding `holderUserId` to `PremiumPaid` be done without a new `version`, and what must a consumer still be ready for?
4. A dead letter is replayed while its cause is not fixed. What happens, and why is it safe to run the replay twice?
5. Why does the replayer commit its offsets only after the batch was sent, and what does the inbox do for the crash case?
