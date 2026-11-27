# ADR-019: Replaying dead letters

- **Status:** Accepted (2026-11-24). Implemented in M8a.
- **Context:** A message that still fails after its retries goes to `<topic>.DLT` (ADR-013). Until M8a, getting it back was a manual procedure: read the message from the DLT and publish it again by hand. A hand procedure on production data is easy to get wrong (a wrong topic, a lost key, the same message sent twice). The replay has to keep the key, run once per message, and need no new way into the service.

## Decision

1. **A `DeadLetterReplayer` in `common-messaging`.** `replay(dltTopic, max)` reads the dead-letter topic and sends each message to its original topic with the same key and the `eventType` header. The original topic comes from the `kafka_dlt-original-topic` header that the dead-letter recoverer sets; if that is missing, from the topic name without `.DLT`. Every service that uses `common-messaging` gets it.
2. **It reads under its own consumer group** (`<service>-dlt-replay`) and commits only after every message of a batch was sent. A message is replayed once, a second replay finds nothing new, and a crash in the middle repeats at most one batch. The inbox (ADR-013) makes that repeat harmless. The group differs from the service's listener group, so the replay never takes part in the listeners' partition assignment.
3. **A property starts it, not an endpoint.** When `messaging.dlt.replay-topics` is set, a `DeadLetterReplayRunner` replays those topics once at start (`messaging.dlt.replay-max` messages per topic, default 100). Without the property nothing runs. The operator fixes the cause, starts the service once with the property set, and removes it. This adds no HTTP surface and no new role to secure.
4. **The cause is fixed first.** A replay of a message that still fails just returns it to the DLT after its retries. The runbook says so.
5. **The consumer type is a small interface.** The replayer takes a `ConsumerSource` (a lambda over the service's consumer factory), not the factory itself. Storing the factory trips SpotBugs `EI_EXPOSE_REP2`, and the project's rule is to fix a finding, not suppress it.

## Consequences

- Replay is tested at two levels: against a real broker in `common-messaging` (key, value and header kept; once only; the limit; the fallback topic name) and end to end in notification-service (fail, dead-letter, fix, replay, exactly one notification).
- The replay group's offsets live in Kafka. To replay the same dead letters again on purpose, delete that group or reset its offsets.
- Replay is per service start. A service with several dead-letter topics lists them all in the property.

## Alternatives rejected

- **An `/internal` replay endpoint per service.** Needs security, a role and an OpenAPI entry in every service for something done a few times a year. Rejected for now; it can be added later on top of the same class.
- **A separate command-line tool.** A second program to build, package and keep compatible with the topics. Rejected.
- **Kafka's console tools in the runbook only.** What M7 had: it loses headers, depends on the operator's care and has no test. Rejected.
