# Runbook

Operational steps for running the platform locally. Production runbook sections (replay DLT, retry payouts, key rotation, backups) are added in later milestones (PLAN 14).

## Start and stop (local)

```bash
cp .env.example .env              # then replace every value; .env is never committed
docker compose --profile infra up -d        # Postgres, Kafka, SeaweedFS and init jobs
docker compose --profile full up -d --build # plus auth-service and the API gateway (builds the images)
docker compose --profile tools up -d        # optional: Kafka UI on http://localhost:8090
docker compose ps                           # every service should show "healthy"
docker compose --profile full down          # stop; data volumes are kept
docker compose --profile full down -v       # stop and DELETE all local data
```

Host ports (all bound to 127.0.0.1): Postgres 15432, Kafka 9094 (host listener), SeaweedFS S3 8333, the API gateway on GATEWAY_HOST_PORT (default 8080), Kafka UI 8090. Redis and auth-service are not published. Inside the compose network, Postgres is still `postgres:5432`.

## Before starting: port conflicts

The compose stack needs ports 15432, 8333 and 9094 free. Port 5432 is not used by compose because a locally installed PostgreSQL often already owns it.

A container left over from an earlier run can hold 8333 too. Find it with `docker ps`, and stop or remove it only after checking that its data is not needed. Old exited containers from the same project show as "orphans" in compose output; leave them unless you want to clean them up, and do not use `--remove-orphans` casually.

## Check the infrastructure

On Git Bash, prefix `docker compose exec` commands that use absolute paths with `MSYS_NO_PATHCONV=1`. Without it, Git Bash rewrites `/opt/kafka/...` into a Windows path and the command fails.

```bash
MSYS_NO_PATHCONV=1 docker compose exec -T postgres psql -U insurance -d postgres -At -c 'select datname from pg_database order by 1'   # five service databases
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list               # eight topics
curl -s http://localhost:8333/                                                # SeaweedFS S3 answers
```

## Memory

Measured in M0b with the `infra` profile and the M0b hello-service running (retired at M2). The estimate in PLAN 15 was 4-4.5 GB for the whole stack.

Reading taken at M0b on a single development machine (`docker stats --no-stream`, the `infra` profile plus the M0b hello-service, all idle after startup):

| Container | Memory used | Limit in compose |
|---|---|---|
| kafka (KRaft, 8 topics) | 397 MiB | 768 MiB |
| hello-service (retired at M2; Spring Boot 4, `-Xmx256m`) | 147 MiB | 384 MiB |
| seaweedfs | 62 MiB | 512 MiB |
| postgres (5 databases) | 37 MiB | 256 MiB |
| **Total** | **about 644 MiB** | |

Extrapolation (rough): six Spring services at about 150 MiB each, plus Kafka, Postgres and SeaweedFS, comes to about 1.5 GiB at idle. Real services with JPA and Kafka clients will use more, so budget 2-3 GiB under load. That is well below the 4-4.5 GB estimate in PLAN 15. Recalibrate with the auth-service reading at M1, when the first real service exists.

## Auth-service (M1)

**Signing key (ADR-007).** The service will not start without `AUTH_JWT_PRIVATE_KEY_BASE64` in `.env`. Generate it once and keep it in the password manager as well, because losing it signs every user out:

```bash
TMPK=$(mktemp)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMPK"
openssl pkcs8 -topk8 -nocrypt -in "$TMPK" -outform DER | base64 -w0   # paste the one-line result after the = sign
rm -f "$TMPK"
```

**First platform admin.** Set `AUTH_SEED_PLATFORM_ADMIN_EMAIL` and `AUTH_SEED_PLATFORM_ADMIN_PASSWORD` (at least 12 characters) before the first start. The account is created only if that email does not exist yet, so restarts never change its password. To change the password, sign in as that admin and use a future password-change endpoint, or stop the service and update the account in the database.

**Check the service:**

```bash
docker compose --profile full up -d --build api-gateway   # also starts redis and auth-service (depends_on)
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/.well-known/jwks.json      # the public key, kid "auth-key-1"
scripts/smoke.sh                                         # register, login, refresh, reuse, staff, JWKS
```

Swagger UI, aggregated through the gateway (lists the auth-service API): `http://localhost:8080/swagger-ui.html`.

**Rotate the signing key (overlap, ADR-007):**
1. Generate a new key (above) and give it a new `AUTH_JWT_KEY_ID`, for example `auth-key-2`.
2. Publish the old public key for verification only. Its base64 X.509 public key is `openssl pkey -in old.pem -pubout -outform DER | base64 -w0`. Set `AUTH_JWT_ADDITIONAL_PUBLIC_KEYS_0_KEY_ID=auth-key-1` and `AUTH_JWT_ADDITIONAL_PUBLIC_KEYS_0_PUBLIC_KEY_BASE64=<that value>`.
3. Set the new `AUTH_JWT_PRIVATE_KEY_BASE64` and `AUTH_JWT_KEY_ID`, then restart auth-service.
4. Wait at least 15 minutes (the access-token lifetime), then remove the overlap variables and restart again.

**Run the auth tests and the contract check:** `./mvnw -pl auth-service -am verify`. The OpenAPI snapshot test compares the generated spec with `docs/openapi/auth-service.json`. When an API change is intended, regenerate it with `OPENAPI_UPDATE=1 ./mvnw -pl auth-service -am verify`, review the diff, and commit the file.

## Catalogue and onboarding an insurer (M3b)

policy-service seeds two fictional insurers (ACACIA, BAOBAB) with two plans each on start. Browse them without a token:

```bash
curl -s http://localhost:8080/api/v1/insurers
curl -s http://localhost:8080/api/v1/insurers/ACACIA/plans
```

**Onboard an insurer with a plan document** (docs/catalogue-schema.md, "Importing and publishing"). `$TOKEN` is a platform admin's access token from `/api/v1/auth/login`:

1. Write the plan document, with fictional names and figures only. Never copy brochure text into the repository.
2. Create the insurer: `curl -s -X POST http://localhost:8080/api/v1/admin/insurers -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"code":"NEWCO","name":"New Co"}'`. Note the returned `id`.
3. Dry run until it passes: `curl -s -X POST "http://localhost:8080/api/v1/admin/insurers/$ID/plans/import?dryRun=true" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' --data-binary @plan.json`. A 422 lists every problem in `errors`.
4. Import without `dryRun`, then publish: `curl -s -X POST http://localhost:8080/api/v1/admin/plans/$PLAN_ID/publish -H "Authorization: Bearer $TOKEN"`.
5. Create the insurer's first admin in auth-service (`POST /api/v1/admin/users` with `"role":"ADMIN"` and `"insurerId": "$ID"`). That admin manages the insurer's plans from then on.

To make a plan part of the local seed instead, add it under `policy-service/src/main/resources/catalogue-seed/<insurer>/` together with an expected-premium case in `seed-premiums.json`; the build fails otherwise.

**Change a published plan:** import the same document with the next `version` number and publish it. The old version is retired automatically; its terms never change.

## Troubleshooting

- **A service shows "unhealthy" or "starting" for a long time:** `docker compose logs <service>`. Kafka and SeaweedFS can take 30-60 seconds on first start.
- **`port is already allocated`:** another container holds the port. `docker ps --format '{{.Names}} {{.Ports}}'` shows which one.
- **Kafka topics missing:** the `kafka-topics` job runs once. Check its output with `docker compose logs kafka-topics`.
- **Disk full:** Docker keeps images and build cache. `docker system df` shows usage. Prune only after checking which images other projects use.
