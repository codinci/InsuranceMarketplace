# M0b: Local infrastructure, the event envelope and correlation IDs

## What was new

- **Docker Compose profiles.** `docker-compose.yml` defines every container. A `profiles` entry decides when a container starts: `infra` (Postgres, Kafka, SeaweedFS and their one-off init jobs) for daily work, `full` (plus `hello-service`), and `tools` (Kafka UI). Think of it as `docker-compose.yml` in Laravel Sail with the services split into groups.
- **Healthchecks and `depends_on: condition: service_healthy`.** A container is only "started" when Docker can see it running. A healthcheck is a command Docker runs repeatedly to ask whether the service really works. `depends_on` with `service_healthy` waits for that answer, so `hello-service` does not start before Postgres accepts connections.
- **Init jobs.** Some setup is one-off: creating the Kafka topics and the S3 bucket. These run as short containers that exit (`restart: "no"`). They are the Docker equivalent of a seeder that runs once.
- **Kafka in KRaft mode.** Kafka used to need ZooKeeper to track cluster state. KRaft keeps that inside Kafka itself, so the compose file has one Kafka container with no extra coordinator. The `KAFKA_*` environment variables configure the broker.
- **Two Kafka listeners.** Containers on the compose network reach Kafka at `kafka:9092`. Tools on the host machine reach it at `localhost:9094`. Kafka tells each client which address to reconnect to, so both must be advertised.
- **Host ports and container ports.** `"127.0.0.1:15432:5432"` means the host's port 15432 forwards to port 5432 inside the container. Containers talk to each other by service name and their own port (`postgres:5432`), so only the host side changes when another program already uses a port. That is why Postgres is on 15432 here: a locally installed PostgreSQL already owned 5432.
- **Git Bash path rewriting.** Git Bash converts anything that looks like a Unix path into a Windows path. `/opt/kafka/bin/...` became a path under the Git install directory, and the command failed inside the container. `MSYS_NO_PATHCONV=1` in front of the command turns the conversion off. This matters because the owner runs the scripts from Git Bash (PLAN D25).
- **Pinned image versions.** `postgres:17.12-alpine`, `apache/kafka:4.2.2`, `chrislusf/seaweedfs:4.54`. The plan forbids `latest`: an image that changes under you makes a failure impossible to reproduce.
- **The event envelope (`EventEnvelope<T>`).** A Java `record` is a class whose only job is to hold named fields. It is the closest equivalent to a readonly DTO in PHP 8.2. Every event has the same outer fields and a typed payload. `@JsonIgnoreProperties(ignoreUnknown = true)` tells the JSON reader to skip fields it does not know, so a newer producer does not break an older consumer.
- **Jackson 3.** The JSON library. Spring Boot 4 uses version 3, which moved the ObjectMapper to the `tools.jackson` package. The annotations stayed in `com.fasterxml.jackson.annotation`. If an example online uses `com.fasterxml.jackson.databind`, it is for version 2.
- **Generics on the envelope.** `EventEnvelope<SamplePayload>` tells the compiler which payload type to expect. Java erases generics at runtime, so reading JSON back needs a `TypeReference` to remember the type. It is similar to Laravel's typed collections, but enforced by the compiler.
- **Spring Boot auto-configuration in a library.** `common-starter` is a library, not an application. Its `CommonWebAutoConfiguration` is listed in `META-INF/spring/...AutoConfiguration.imports`, so any service that depends on it gets the correlation filter without scanning packages. This is how Spring starters work, and the same idea as a Laravel service provider that is auto-discovered.
- **Servlet filters.** A filter runs around every HTTP request, like Laravel middleware. The correlation filter reads or creates an id, stores it, and always removes it in a `finally` block. The `finally` matters: a thread can be reused for the next request, and a leftover id would show up in the wrong logs.
- **MDC (Mapped Diagnostic Context).** A per-thread map that the logger adds to every line. Put `correlationId` in it once, and every log line written during that request carries it. Laravel's `Log::withContext()` does the same job.
- **Structured (JSON) logs.** One JSON object per log line instead of free text. `logging.structured.format.console=logstash` turns it on, with no extra library. Log aggregators can then filter on `correlationId` instead of searching text.
- **Failsafe and `*IT` tests.** Surefire runs `*Test` classes during `test`. Failsafe runs `*IT` classes during `integration-test` and `verify`, so slow tests that start containers stay out of the pre-commit hook.
- **Testcontainers.** A library that starts real containers (Postgres, Kafka, SeaweedFS) from inside a test and removes them afterwards. The tests use the same image versions as compose, so they check the same thing the app will run against.
- **Mounting the real init script.** `PostgresSmokeIT` copies `config/postgres/init-databases.sql` into the test container. If the script breaks, the test fails, and the file is not duplicated in test code.

## Why it was done this way

- **Compose instead of a hand-started container per service.** One file describes the whole environment, so a teammate or CI can reproduce it with one command.
- **Testcontainers instead of "run compose, then test by hand".** A test that starts its own containers runs the same way every time, on any machine with Docker, including CI.
- **Envelope fields as strings for `aggregateId` and `insurerId`.** Each service may use a different id format later. A string means the contract does not depend on that choice. The trade-off is that nothing checks the format at compile time.
- **Correlation IDs validated on arrival.** A client can send any header value. Without validation, a value with a newline could write a fake line into the logs. The filter accepts only short, plain tokens and replaces anything else.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `docker-compose.yml` profiles | Sail services, grouped |
| Healthcheck + `service_healthy` | `depends_on` with a wait-for script |
| `EventEnvelope` record | Readonly DTO class |
| Jackson `ObjectMapper` | `json_encode` / `json_decode` with a typed hydrator |
| `CorrelationIdFilter` | Middleware |
| MDC | `Log::withContext()` |
| Testcontainers | A test database that Pest/PHPUnit starts itself (for example `RefreshDatabase` on a Docker service) |

## Self-check questions (answer before starting M1)

1. The compose file pins `postgres:17.12-alpine` instead of `postgres:latest`. Give one concrete failure that the pin prevents, and say how you would find out which version a running container uses.
2. Kafka advertises `kafka:9092` to containers and `localhost:9094` to the host machine. What would happen to `hello-service` if you advertised only `localhost:9094`, and why does the client not just use the address it connected to?
3. `CorrelationIdFilter` removes the id from the MDC in a `finally` block. Describe what would leak into another request's logs if it did not, and why this happens with thread reuse.

## Official documentation

- Docker Compose profiles and healthchecks: https://docs.docker.com/compose/
- Apache Kafka KRaft and listeners: https://kafka.apache.org/documentation/#kraft
- Testcontainers for Java: https://java.testcontainers.org/
- Spring Boot structured logging: https://docs.spring.io/spring-boot/reference/features/logging.html
- SeaweedFS S3 API: https://github.com/seaweedfs/seaweedfs/wiki/Amazon-S3-API
- AWS SDK v2 for Java: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
