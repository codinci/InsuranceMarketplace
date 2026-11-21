# M4b: Payments, events and activation

## What M4b delivers

- **payment-service**, a new module with its own database, Dockerfile and compose entry. It records premium payments and keeps an append-only ledger of attempts (`payment_transactions`). A mock provider succeeds unless asked to fail.
- `POST /internal/payments` (SERVICE token only, never routed by the gateway): policy-service creates the premium at purchase, with the policy id as the idempotency key.
- `POST /api/v1/payments/{id}/pay` and `GET /api/v1/payments/**` for the customer who owns the payment. Another customer gets 404 and staff get 403. Paying needs an `Idempotency-Key` header, and a replay returns the stored result.
- **Kafka for real.** payment-service publishes `PremiumPaid` and `PremiumFailed`. policy-service consumes them, activates the policy and publishes `PolicyActivated` with the frozen terms. Both services send events through a **transactional outbox** and apply them through an **inbox**.
- **Service tokens** (ADR-014): auth-service issues five-minute `SERVICE` JWTs from a shared secret, and policy-service caches them.
- **Resilience4j** around the payment call: retry for a short blip, a circuit breaker for an outage. If payment-service cannot be reached, the purchase answers 503 `PAYMENT_UNAVAILABLE` and nothing is saved.
- **The unpaid-policy job**: a policy still unpaid 24 hours after purchase is cancelled, `PolicyCancelled(UNPAID)` is published, and payment-service expires the payment.
- `scripts/verify.sh` now starts the whole stack and runs the smoke script, which pays for a policy and waits until it is `ACTIVE`.

## New concepts

**Kafka in one paragraph.** Kafka is a log of messages, split into *topics* (`payment.events`) and each topic into *partitions*. A producer appends a message with a *key*. Messages with the same key always go to the same partition, and one partition is read in order. A *consumer group* (here, one per service) shares the partitions among its instances and remembers how far it has read (the *offset*). Kafka keeps messages after they are read, so a new consumer can start from the beginning. Delivery is *at least once*: after a crash a consumer may see a message again, so every consumer must be safe to repeat.

**`@KafkaListener` and the error handler.** Spring Kafka calls the annotated method for each message on the topic. If it throws, a `DefaultErrorHandler` retries (three times, one second apart). Then a `DeadLetterPublishingRecoverer` copies the message to `<topic>.DLT` and moves on, so one bad message cannot block the partition. An unknown event type is marked "not retryable" and goes to the DLT at once. One surprise: the library's default DLT name changed to `-dlt` in the Spring Kafka version used here, so the name is set explicitly to match the topics compose creates (`.DLT`).

**The transactional outbox.** The naive way, "save the policy, then send to Kafka", fails in two directions: the save commits and the send fails (the event is lost), or the send succeeds and the commit fails (an event about something that never happened). The outbox writes the event as a row in the *same database transaction* as the change, so both exist or neither does. A scheduled relay (`@Scheduled`) sends unsent rows and marks them published once Kafka acknowledges. A crash between "sent" and "marked" means the row is sent again, which is why consumers need an inbox. `FOR UPDATE SKIP LOCKED` lets two relay instances share the table without sending the same row twice.

**The inbox.** Each consumer stores the `eventId` of every event it applied, in the same transaction as the change. Before applying, it checks the table. A redelivered `PremiumPaid` finds its id and does nothing, so the policy activates once and `PolicyActivated` is sent once. The test `theSamePremiumPaidTwiceActivatesOnce` proves it.

**Idempotency keys over HTTP.** The client sends a unique `Idempotency-Key` with `pay`. The server stores the key with the result (a unique column on `payment_transactions`). The same key again returns the stored result without charging, so a customer who double-clicks or retries after a timeout is charged once. A new key on an already paid payment gets 409 `PAYMENT_ALREADY_PAID`. policy-service uses the policy id as its key for creating the payment, so its own retries are safe as well.

**Resilience4j.** It is a library of small decorators around a function call. `Retry` calls again after a short wait when the failure looks temporary: no connection, a timeout, a 5xx, or a rejected token after dropping the cached one. A 4xx is a bug on our side and is not retried. `CircuitBreaker` counts recent failures. When half of the last calls failed, it *opens*, and for 30 seconds every call fails at once (`CallNotPermittedException`) instead of waiting for timeouts. Then it lets a few trial calls through. Only the core modules are used, configured in plain Java (`PaymentClientConfig`), because its Spring Boot starter is built against Boot 4.0 and this project runs 4.1. The core modules have no Spring dependency at all.

**`RestClient`.** Spring's synchronous HTTP client, with a fluent API: `post().uri(...).header(...).body(...).retrieve().body(Type.class)`. A non-2xx answer becomes an exception (`HttpClientErrorException` for 4xx, `HttpServerErrorException` for 5xx), and connection problems become `ResourceAccessException`. In Spring Boot 4 the auto-configured `RestClient.Builder` lives in a separate starter, so the clients are built with `RestClient.builder()` directly.

**`@ConfigurationProperties` records.** `PaymentClientProperties` binds `policy.payment.*` to a record, with `@DefaultValue` for the timeouts. It is the typed alternative to many `@Value` fields, and the record cannot be changed after startup.

**`@Scheduled`.** `@EnableScheduling` turns it on, and `@Scheduled(fixedDelayString = ...)` runs a method again a fixed time after the last run ended. It runs in every instance of the service. That is fine with one instance, and ShedLock (a lock in the database) is planned for M8 when more instances run.

**A JPA trap found in this milestone: `save()` can return a different object.** Spring Data's `save` calls `persist` for a new entity and `merge` otherwise. It decides "new" from the id and the `@Version` field. `Policy` has an id we assign ourselves and a primitive `long` version, so Spring Data cannot tell, assumes "not new", and calls `merge`. `merge` copies our object into a *new managed instance* and returns that. Our original object is left detached, so later changes to it (setting `paymentId`) were silently never saved. The fix is to keep working with the returned instance: `policy = policies.save(policy)`. The integration test caught it: activation ignored every `PremiumPaid` because the stored policy had no payment id.

**A shell trap: `cmd && echo ok` under `set -e`.** Bash does not stop for a failing command that is part of an `&&` list, so a failed smoke check printed nothing and the script went on. Every check now ends with `|| fail "..."`. Also, `grep -q` exits at the first match. Under `set -o pipefail` the command writing into the pipe can then die from SIGPIPE and fail the pipeline at random. `grep ... >/dev/null` reads all of its input instead.

## Why this approach

- **Payment created inside the purchase, 503 on failure** (decided in planning). The customer gets a payable policy in one call, and an outage leaves nothing half-made. The remote call is the last step before commit, after a `flush()`, so a database constraint failure stops the purchase before payment-service is asked. One gap remains, a payment without a policy if the final commit fails. It is in the parking lot for the M8 reconciliation job.
- **Activation through Kafka, not a callback.** payment-service does not need to know policy-service exists. claim-service (M5) and notification-service (M8) read the same events without any change to the producer.
- **The terms travel in `PolicyActivated`.** claim-service can build its read model from events alone and never calls policy-service in the claim path (ADR-011).
- **Outbox and inbox copied in two services.** Two copies are easy to read and keep each service independent. The third user (claim-service) is the point to extract a shared module.
- **Plain shared-secret service auth** (ADR-014). It is simple, rotates by restarting, and gives tokens that the existing JWKS check accepts. The secret never leaves the compose network.
- **The test stub is the JDK's own `HttpServer`**, not WireMock. There is no new dependency, and the real client, headers, retry and breaker are exercised over HTTP.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| Kafka topic and consumer group | A queue connection and a queue worker, except that Kafka keeps messages after they are read and many services can read the same topic |
| `@KafkaListener` method | A queued job's `handle()`, or a listener for a broadcast event |
| `DefaultErrorHandler` retries, then DLT | `$tries` and `$backoff` on a job, then the `failed_jobs` table |
| Transactional outbox | `DB::afterCommit()` or `ShouldDispatchAfterCommit`, but stored in a table so nothing is lost if the process dies right after the commit |
| Inbox `processed_events` | A unique-job lock, or checking a `processed_at` column before acting |
| `Idempotency-Key` header | A middleware that caches the response per key (as Stripe's API does) |
| Resilience4j `Retry` | `Http::retry(3, 200)` |
| Resilience4j `CircuitBreaker` | No built-in equivalent; a cache flag set after repeated failures that short-circuits the call |
| `@Scheduled` job | A command in the scheduler (`$schedule->command(...)->everyMinute()`) |
| `SERVICE` token from auth-service | A Passport client-credentials grant |

## Self-check questions (answer before starting M5)

1. payment-service commits a successful payment and its `PremiumPaid` outbox row, then crashes before the relay sends it. What happens to the event, and what does the customer see in the meantime? Now say the relay sent it but crashed before marking the row published: what happens next, and why is the policy still activated only once?
2. A customer clicks "Pay" twice, very fast, with the same `Idempotency-Key`. A minute later, impatient, they retry with a new key. How many charges happen, and what does each of the three requests get back?
3. payment-service is down for two minutes and customers keep pressing "Buy". Describe what the first purchase experiences, what a purchase twenty seconds later experiences, and what happens once payment-service is back. What state are the quotes in throughout?

## Corrected answers

1. **Answered by the owner:** On crashing before the relay, the event sits unpublished until the relay restarts it. If the relay sent it but crashed before marking the row published, the relay restarts, sees it as unpublished and sends it again. The policy activates only once because the consumer is idempotent.
   **Corrected version:** right on all three points. A complete answer adds the following:
   - **Where the event waits.** The `PremiumPaid` row is in payment-service's `outbox_events` with `published_at` null. It was committed in the same transaction as the payment, so it cannot be lost: both exist or neither does. Nothing "restarts the event". When payment-service starts again, the scheduled relay finds unsent rows on its next run (about a second later) and sends them.
   - **What the customer sees meanwhile.** The pay request already answered 200 `SUCCEEDED`, so the payment shows as paid, but the policy stays `PENDING_PAYMENT` until the event is delivered and applied. The gap lasts as long as the outage. If it lasted more than 24 hours, the unpaid-policy job would cancel the policy first, and the late `PremiumPaid` would be logged and ignored. That is the reconciliation case in the parking lot (M8).
   - **Why the resend is harmless.** The resent message carries the **same `eventId`**, because the outbox row stores the whole envelope and the relay sends it unchanged. policy-service checks its inbox (`processed_events`) for that id and records it in the same transaction as the activation. The second delivery finds the id and does nothing, so there is one activation and one `PolicyActivated`. A second guard backs this up: activation applies only to a policy that is still `PENDING_PAYMENT`.
   - **The general rule.** The outbox gives at-least-once delivery, never exactly-once. The inbox turns "at least once" into "effect once".

2. **Answered by the owner:** The quote is protected against a second payment with the same idempotency key, so the second press gets "payment in progress". The third retry, with a new key, gets a conflict error: quote already purchased.
   **Corrected version:** the outcome is right: **one charge**. What protects it, and what each request gets back, need correcting:
   - **It is the payment that is protected, not the quote.** The quote was used up at purchase, when the policy and its payment were created. `pay` works on the payment (`/api/v1/payments/{id}/pay`), and the protection is the unique `idempotency_key` column on `payment_transactions`, the ledger of attempts.
   - **First press:** the mock provider charges, one ledger row is written with the key, and the answer is **200 `SUCCEEDED`** with a `providerRef`.
   - **Second press, same key:** there is no "in progress" state. If it arrives after the first one finished (the usual case), payment-service finds the ledger row for that key and **replays the stored result: 200 `SUCCEEDED` with the same `providerRef`**, without charging. If it arrives at exactly the same moment, both requests try to insert a ledger row with the same key. The database's unique constraint lets one win, and the loser gets **409 `CONFLICT`** ("changed at the same time, try again"). Retrying with the same key then replays 200 `SUCCEEDED`. Either way there is one ledger row and one charge (`parallelPayRequestsWithOneKeyLeaveExactlyOneLedgerRow`).
   - **Third request, new key:** the key is unknown, so this counts as a new attempt. The payment is already `SUCCEEDED`, though, so it is refused with **409 `PAYMENT_ALREADY_PAID`**. That code is about the payment; the quote's 409 `QUOTE_NOT_OPEN` only comes from buying a quote twice.
   - **The rule to remember:** the same key means "the same request, give me its answer again". A new key means "a new attempt", and a new attempt is allowed only while the payment can still be paid (`PENDING` or `FAILED`).

3. *Not answered yet.*

## Official documentation

- Spring for Apache Kafka, reference: https://docs.spring.io/spring-kafka/reference/
- Spring Kafka error handling and dead-letter publishing: https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html
- Apache Kafka introduction (topics, partitions, consumer groups): https://kafka.apache.org/documentation/#introduction
- Transactional outbox pattern: https://microservices.io/patterns/data/transactional-outbox.html
- Resilience4j CircuitBreaker and Retry: https://resilience4j.readme.io/docs/circuitbreaker and https://resilience4j.readme.io/docs/retry
- Spring Framework `RestClient`: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Data JPA, entity state detection (`save`, `persist` and `merge`): https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
