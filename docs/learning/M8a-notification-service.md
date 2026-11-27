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

## Corrected answers

**1.** Two notifications. The redelivery with the same event id gives no second notification: the inbox records each event id in the same transaction as the `notification_log` row, so the second delivery finds the id already there and is skipped. The unique `event_id` column on `notification_log` is a second guard if two deliveries race. The same fact under a new event id is a different event as far as the consumer can tell, so it passes the inbox and produces a second notification. notification-service cannot catch that case, and the test states it. "Once per fact" is the producer's job: it writes exactly one outbox row in the business transaction that creates the fact.

**2.** The new consumer group has no saved offset, and `auto-offset-reset=earliest` makes it read every topic from the beginning, so it meets every `PremiumPaid` published before M8a. Those events have no `holderUserId`, so there is no one to notify. The service records each one in the inbox as applied, writes no notification, and moves on; the offset is committed as normal. Sending them to the dead-letter topic would be wrong because the dead-letter topic is for messages that can succeed once a cause is fixed. These cannot: published events never change, so they will never gain a holder, and replaying them would only fail them again. Each one would also go through the retries first, which slows the first start, and the topic would fill with noise that hides real failures.

**3.** Adding a field is an additive change. Every payload class ignores JSON fields it does not know, so a reader built before the change still reads the new event correctly and simply drops `holderUserId`. Nothing it relied on was removed or renamed, so the contract is not broken and the `version` stays the same. ADR-020 sets the rule: a new field keeps the version; a rename or removal needs a new version. The other direction still needs care: a new reader can meet an event without the field and see `null`. That happens for events published before M8a (still in the topic and read from `earliest`), and during a rollout when the consumer is upgraded before the producer. So every consumer that reads `holderUserId` must handle `null`. notification-service does this by recording the event as applied and sending nothing.

**4.** The replayer sends the message back to its original topic (taken from the `DLT_ORIGINAL_TOPIC` header, or the dead-letter topic name without `.DLT`). The consumer reads it, fails again for the same reason, retries, and puts it back in the dead-letter topic. Nothing is lost and nothing is applied; the only cost is the extra retries. The message is unchanged, so its key and event id are the same. Running the replay twice is safe for two reasons. The replayer's own consumer group committed after the first run, so a second run reads only what has arrived in the dead-letter topic since then: the messages that failed again. And if a message is sent twice anyway (for example after a crash before the commit), the inbox already holds its event id once it has succeeded and skips the repeat, so the customer still gets one notification.

**5.** Reading a message does not move the replayer's bookmark; only a commit does. The replayer sends every message in the batch and commits afterwards. If it crashes after sending some messages but before committing, the next run starts from the last commit and sends the whole batch again. That is at-least-once delivery: nothing is lost, but some messages arrive twice. The inbox makes the repeat harmless: a message whose event id was already applied is skipped, so the customer gets one notification, not two. Committing first would be at-most-once: a crash after the commit but before the sends would move the bookmark past messages that never left the dead-letter topic, and they would never be replayed. A duplicate the inbox can absorb is better than a loss nobody can see.
