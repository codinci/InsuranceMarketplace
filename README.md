# Mini Insurance Platform

Spring Boot microservices for a multi-insurer medical insurance marketplace. The full plan is in [PLAN.md](PLAN.md). Working rules for coding agents are in [CLAUDE.md](CLAUDE.md).

## Requirements

- Java 21 (Temurin recommended)
- Docker (for Hadolint and, from M0b, the compose stack)
- Git Bash on Windows, or any POSIX shell

## One-time setup

```bash
bash scripts/setup-hooks.sh   # enables the pre-commit gate
cp .env.example .env          # local secrets for the compose stack; replace every value
```

## Run the infrastructure

```bash
docker compose --profile infra up -d          # Postgres, Kafka, SeaweedFS
docker compose --profile full up -d --build   # the API gateway on http://localhost:${GATEWAY_HOST_PORT:-8080} (set it in .env)
```

See [docs/runbook.md](docs/runbook.md) for ports, checks and troubleshooting.

## Common commands

```bash
./mvnw -B verify                      # build and all checks for every module
bash scripts/verify.sh                # the full gate, as CI runs it
./mvnw -pl api-gateway -am verify   # one module and the modules it needs
./mvnw spotless:apply                 # format Java
```

## Using the running stack

Clients reach auth-service through the API gateway on `http://localhost:${GATEWAY_HOST_PORT:-8080}` (set `GATEWAY_HOST_PORT` in `.env`). auth-service, policy-service, payment-service and claim-service are not published. The catalogue is public: `GET /api/v1/insurers` lists the seeded fictional insurers. Swagger UI, aggregated through the gateway: `http://localhost:8080/swagger-ui.html`. auth-service needs `AUTH_JWT_PRIVATE_KEY_BASE64` in `.env`. Generate it as described in [docs/runbook.md](docs/runbook.md), section "Auth-service (M1)". policy-service also needs `POLICY_FIELD_KEY_BASE64` and both policy-service and auth-service need `SERVICE_SECRET_POLICY_SERVICE` (docs/runbook.md). For a quick throwaway setup, `bash scripts/make-env.sh > .env` generates every value. Then run `scripts/smoke.sh` against the running stack: it registers a customer, buys a policy, pays for it and waits until the policy is `ACTIVE`.

## Status

Done: M0a (build, quality gates, CI), M0b (local infrastructure, event envelope, correlation IDs), M1 (auth-service), M2 (API gateway), M3a (catalogue model and rating engine), M3b (catalogue API, seeds) and M4a (profiles, quotes, buying a policy). M4b (payment-service with a mock checkout, Kafka events through an outbox and inbox, service tokens, and policy activation). M5a (the claim-service policy read model, ADR-015, and the shared `common-messaging` module). M5b (claims: drafts, documents, the automatic checks and submission, ADR-016). M6 (adjudication: the review queue, limits and co-pay, approval rules and the three claim decision events, ADR-017). M7 (the payout saga: approved claims are paid through the mock provider, failed payouts are retried by an admin, and the claim reaches PAID, ADR-018). M8a (notification-service turns events into a notification log, `UserRegistered` through an auth-service outbox, and a dead-letter replay tool, ADR-019, ADR-020). M8b (the end of the policy lifecycle: customer cancellation with the plan's refund rule, mock refunds with an admin retry, policy expiry, and ShedLock for the scheduled jobs, ADR-021, ADR-022). Next: M9, hardening. The owner's self-check for M8b is still pending. Event payloads are in [docs/events.md](docs/events.md). Plan documents, their validation rules and the import API are described in [docs/catalogue-schema.md](docs/catalogue-schema.md).
