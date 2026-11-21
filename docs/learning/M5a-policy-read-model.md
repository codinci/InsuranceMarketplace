# M5a: The policy read model in claim-service

## What M5a delivers

- **The shared messaging module.** The outbox, inbox, relay and dead-letter policy moved out of payment-service and policy-service into `common-messaging` (the extraction ADR-013 named). Each service keeps its own tables and topics. Nothing on the wire changed.
- **claim-service**, a new module with its own database (`claim_db`), Dockerfile and compose entry. It consumes `policy.events` and keeps a copy of each policy it has heard about: `policy_snapshots`, built only from `PolicyActivated` and `PolicyCancelled` (ADR-015).
- **Ordering by version.** Every change carries `policyVersion`. A change applies only when it is higher than the stored version, so a redelivered or late event does nothing.
- **The fallback.** When a customer asks for a policy the read model does not have, claim-service calls `GET /internal/policies/{id}` on policy-service with its own SERVICE token. The call has a timeout, retry and a circuit breaker. When policy-service is unavailable the customer gets 503 `POLICY_UNAVAILABLE` with `Retry-After: 30`.
- **The internal endpoint in policy-service.** It admits only the `claim-service` subject. A SERVICE token from any other service gets 403, a customer token gets 403, and no token gets 401.
- `GET /api/v1/claims/policies` (the holder's policies) and `GET /api/v1/claims/policies/{id}` (one policy, 404 to anyone else).
- The gateway routes `/v3/api-docs/claim` into the aggregated Swagger, and the smoke script checks the read model after a purchase is paid.

## New concepts

**Kafka consumers and groups.** A consumer group is a set of instances that share the partitions of a topic. Each partition is read by one instance of the group at a time, in order. `claim-service` is one group, so its copy of each policy is built in the order policy-service wrote the events for that policy, because they share a key (the policy id). Two different policies may be processed in either order, and the design does not depend on that.

**`@KafkaListener` as a method on a `@Component`.** The annotation names the topic. Spring Kafka calls the method with each message's value (a JSON string here). The listener reads the `type` first, then parses the envelope into the right payload class. A `type` it does not know throws `UnknownEventTypeException`, and the shared error handler sends the message to `policy.events.DLT`. That is the same pattern payment-service uses (ADR-005).

**Ordering by a version, not a clock.** The tempting rule is "the newest `occurredAt` wins". It fails when two services' clocks disagree, or when two events have the same timestamp. A version written by the owner of the aggregate has one job: it only goes up. Comparing two integers is exact, and a redelivery carries the same number. This is the rule in `PolicySnapshot.applyActivated` and `applyCancelled`, and the four cases in `PolicySnapshotTest` (in order, duplicate, cancel before activate, stale activate after cancel) are the ones the milestone plan named.

**A domain class with no Spring in it.** `PolicySnapshot` holds the ordering rule as two methods that return a boolean. The unit test builds it with the factory method `unseen` and calls the apply methods directly, so the four cases run in milliseconds with no database. The service around it does the lookup, the inbox check and the save. Keeping the rule out of the service makes it testable without a database, and that is why the rule lives in the domain package.

**JPA entities with an assigned id and jsonb.** The key is the policy id, which the event carries, so `save` decides between insert and update by the key. The `activation` column is `jsonb` and holds the whole `PolicyActivated` JSON, so the terms are kept as sold. The Java side reads it back with the shared mapper (`EventJson.read`), so claim-service never needs catalogue types.

**Resilience4j: a breaker and a retry, on purpose.** Retry rides out a blip: three attempts, 50 ms apart. The breaker watches the failures: once half of the last ten calls fail, it opens and fails fast for 30 seconds. Both decorate the same function, and the order matters. The retry is the outer layer, so each retry attempt is counted by the breaker. Laravel's HTTP client has retries, but it has no breaker; the nearest Laravel equivalent is a retry middleware plus a circuit-breaker package. A 404 is not a failure for either: it is an answer ("this policy does not exist"), so `isTransient` returns false for it.

**`RestClient` with a 404 that means "empty".** `retrieve()` turns a 404 into `HttpClientErrorException.NotFound`. The client catches exactly that and returns `Optional.empty()`. Every other error stays an exception, so the caller sees either a policy, nothing, or a failure. Returning `Optional` for "not found" keeps the 404 out of the error path.

**Primitive fields in records and missing JSON.** `PolicyLookup.policyVersion` is a `long`. If policy-service omits it, Jackson 3 refuses the null. Our tests hit this twice: a stub that left the field out failed with "Cannot map null into type long". policy-service always sends the field, so the contract holds, but a partial fixture is a trap. Test fixtures should be complete answers.

**Spring Security expressions on a path.** `SecurityConfig` checks `hasRole('SERVICE') and authentication.name == 'claim-service'` for the internal path. `authentication.name` is the JWT subject, which auth-service sets to the calling service's name (ADR-014). A path rule can express "which service" because the subject is in the token, and no code in the controller is needed.

**Testcontainers and a stand-in server.** The integration tests start a real Postgres and a real Kafka broker in containers (the same images as compose). The policy-service side is a JDK `HttpServer` on a random port, started once and shared, which answers the token call and the lookup. A test can switch it "down" to simulate an outage, without a second container. The JDK server needs no dependency.

**Where the test annotations live.** `@SpringBootTest` and `@AutoConfigureMockMvc` sit on each concrete test class, so a reader sees the context configuration in the file that uses it. The abstract base class holds only the containers, the stub and the helpers. (payment-service does the same.)

**A helper named `get` hides a static import.** `AbstractClaimIT.get(token, path)` shadowed `MockMvcRequestBuilders.get`, so a call inside the same class resolved to the helper and failed to compile. Helpers that share a name with a framework call are renamed (`getWith`).

## Why this approach

- **A read model, not a call per claim** (ADR-015). Claims are filed often and must not wait on another service. The events give a local copy; the fallback covers the short gap before the event arrives.
- **The fallback result is not stored.** Storing it would let a late, slow lookup write outside the event order. The events stay the only writer.
- **policy-service decides the version, claim-service only compares.** The owner of a policy knows exactly when a change happened, so the number is written there.
- **A new module for the shared messaging code, not a copy.** claim-service is the third user, the point ADR-013 set. The shared module contains no domain code (CLAUDE.md layout), so each service still owns its tables.

## Self-check (answer before M5b starts)

1. A `PolicyCancelled` with version 3 is stored. A `PolicyActivated` with version 2 arrives later. What does claim-service do, and why is that correct even though the activation was the "first" event in time?
2. Why does the fallback answer 404 (not 403) when the caller is not the holder of a policy that exists?
3. The circuit breaker is open. A customer asks for a policy that is in the read model. Do they get the policy or a 503, and why?
4. Why is the retry placed outside the breaker in `PolicyClient.fetch`, and what would change if it were inside?
5. Why is the `activation` payload stored whole, instead of only the members and terms columns?
