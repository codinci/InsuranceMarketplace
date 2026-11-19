# Runbook

Operational steps for running the platform locally. Production runbook sections (replay DLT, retry payouts, key rotation, backups) are added in later milestones (PLAN 14).

## Start and stop (local)

```bash
cp .env.example .env              # then replace every value; .env is never committed
docker compose --profile infra up -d        # Postgres, Kafka, SeaweedFS and init jobs
docker compose --profile full up -d --build # plus hello-service (builds the image)
docker compose --profile tools up -d        # optional: Kafka UI on http://localhost:8090
docker compose ps                           # every service should show "healthy"
docker compose --profile full down          # stop; data volumes are kept
docker compose --profile full down -v       # stop and DELETE all local data
```

Host ports (all bound to 127.0.0.1): Postgres 15432, Kafka 9094 (host listener), SeaweedFS S3 8333, hello-service 8080, Kafka UI 8090. Inside the compose network, Postgres is still `postgres:5432`.

## Before starting: port conflicts

The compose stack needs ports 15432, 8333 and 9094 free. Port 5432 is not used by compose because a locally installed PostgreSQL often already owns it.

A container left over from an earlier run can hold 8333 too. Stop or remove it only after checking that its data is not needed. Old exited containers from the same project show as "orphans" in compose output; leave them unless you want to clean them up, and do not use `--remove-orphans` casually.

## Check the infrastructure

On Git Bash, prefix `docker compose exec` commands that use absolute paths with `MSYS_NO_PATHCONV=1`. Without it, Git Bash rewrites `/opt/kafka/...` into a Windows path and the command fails.

```bash
MSYS_NO_PATHCONV=1 docker compose exec -T postgres psql -U insurance -d postgres -At -c 'select datname from pg_database order by 1'   # five service databases
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list               # eight topics
curl -s http://localhost:8333/                                                # SeaweedFS S3 answers
```

## Memory

Measured in M0b with the `infra` profile and `hello-service` running. The estimate in PLAN 15 was 4-4.5 GB for the whole stack.

Reading taken at M0b on a single development machine (`docker stats --no-stream`, the `infra` profile plus `hello-service`, all idle after startup):

| Container | Memory used | Limit in compose |
|---|---|---|
| kafka (KRaft, 8 topics) | 397 MiB | 768 MiB |
| hello-service (Spring Boot 4, `-Xmx256m`) | 147 MiB | 384 MiB |
| seaweedfs | 62 MiB | 512 MiB |
| postgres (5 databases) | 37 MiB | 256 MiB |
| **Total** | **about 644 MiB** | |

Extrapolation (rough): six Spring services at about 150 MiB each, plus Kafka, Postgres and SeaweedFS, comes to about 1.5 GiB at idle. Real services with JPA and Kafka clients will use more, so budget 2-3 GiB under load. That is well below the 4-4.5 GB estimate in PLAN 15. Recalibrate with the auth-service reading at M1, when the first real service exists.

## Troubleshooting

- **A service shows "unhealthy" or "starting" for a long time:** `docker compose logs <service>`. Kafka and SeaweedFS can take 30-60 seconds on first start.
- **`port is already allocated`:** another container holds the port. `docker ps --format '{{.Names}} {{.Ports}}'` shows which one.
- **Kafka topics missing:** the `kafka-topics` job runs once. Check its output with `docker compose logs kafka-topics`.
- **Disk full:** Docker keeps images and build cache. `docker system df` shows usage. Prune only after checking which images other projects use.
