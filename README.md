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

## Auth service (M1) and the gateway (M2)

Clients reach auth-service through the API gateway on `http://localhost:${GATEWAY_HOST_PORT:-8080}` (set `GATEWAY_HOST_PORT` in `.env`). auth-service and policy-service are not published. The catalogue is public: `GET /api/v1/insurers` lists the seeded fictional insurers. Swagger UI, aggregated through the gateway: `http://localhost:8080/swagger-ui.html`. auth-service needs `AUTH_JWT_PRIVATE_KEY_BASE64` in `.env`. Generate it as described in [docs/runbook.md](docs/runbook.md), section "Auth-service (M1)". Then run `scripts/smoke.sh` against the running stack.

## Status

Done: M0a (build, quality gates, CI), M0b (local infrastructure, event envelope, correlation IDs), M1 (auth-service), M2 (API gateway), M3a (catalogue model and rating engine) and M3b (catalogue API, seeds). In progress: M4a, profiles, quotes and buying a policy (to PENDING_PAYMENT; payment comes in M4b). policy-service needs `POLICY_FIELD_KEY_BASE64` in `.env` (see docs/runbook.md). Plan documents, their validation rules and the import API are described in [docs/catalogue-schema.md](docs/catalogue-schema.md).
