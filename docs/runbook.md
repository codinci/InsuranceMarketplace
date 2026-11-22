# Runbook

Operational steps for running the platform locally. Production runbook sections (replay DLT, retry payouts, key rotation, backups) are added in later milestones (PLAN 14).

## Start and stop (local)

```bash
cp .env.example .env              # then replace every value; .env is never committed
docker compose --profile infra up -d        # Postgres, Kafka, SeaweedFS and init jobs
docker compose --profile full up -d --build # plus auth-, policy- and payment-service and the API gateway (builds the images)
docker compose --profile tools up -d        # optional: Kafka UI on http://localhost:8090
docker compose ps                           # every service should show "healthy"
docker compose --profile full down          # stop; data volumes are kept
docker compose --profile full down -v       # stop and DELETE all local data
```

Host ports (all bound to 127.0.0.1): Postgres 15432, Kafka 9094 (host listener), SeaweedFS S3 8333, the API gateway on GATEWAY_HOST_PORT (default 8080), Kafka UI 8090. Redis and the services behind the gateway are not published. Inside the compose network, Postgres is still `postgres:5432`.

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

Reading taken at M4b with the `full` profile, idle after a smoke run (`docker stats --no-stream`): api-gateway 246 MiB, auth-service 331 MiB, policy-service 382 MiB, payment-service 304 MiB, kafka 414 MiB, seaweedfs 122 MiB, postgres 89 MiB, redis 15 MiB, about 1.9 GiB in all. policy-service was close to its 384 MiB limit, so its limit is now 512 MiB (heap unchanged at 256m). No container was killed for memory.

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

## Policies and the field encryption key (M4a)

policy-service encrypts the national ID with a key from `.env` (D37, ADR-012) and refuses to start without it. Generate one per environment and never commit it:

```bash
echo "POLICY_FIELD_KEY_BASE64=$(openssl rand -base64 32)" >> .env
echo "POLICY_FIELD_KEY_ID=policy-key-1" >> .env
```

Keep the key with the data: losing it makes the stored national IDs unreadable. Changing it needs a rotation (ADR-012), not a simple replacement.

Buying a policy locally (`$TOKEN` is a customer's access token): `PUT /api/v1/me/profile`, then `POST /api/v1/quotes` with a plan id from `GET /api/v1/insurers/ACACIA/plans`, then `POST /api/v1/policies` with the quote id. `scripts/smoke.sh` runs this flow. The policy stays `PENDING_PAYMENT` until it is paid (next section).

## Payments, events and service tokens (M4b)

**The flow.** `POST /api/v1/policies` creates the policy and, in the same request, its premium in payment-service (`paymentId` in the response). The customer pays with `POST /api/v1/payments/{paymentId}/pay` and an `Idempotency-Key` header; the mock provider succeeds unless the body is `{"simulate":"FAIL"}`. payment-service publishes `PremiumPaid` on `payment.events`, policy-service activates the policy and publishes `PolicyActivated` on `policy.events`. Activation is asynchronous: poll `GET /api/v1/policies/{id}`, it normally takes one to two seconds. A policy unpaid after 24 hours is cancelled (`PolicyCancelled`, reason `UNPAID`) and its payment expires.

**Service secret (ADR-014).** policy-service gets a short-lived `SERVICE` token from auth-service with a shared secret. Both read `SERVICE_SECRET_POLICY_SERVICE` from `.env`; set it once to a long random value:

```bash
echo "SERVICE_SECRET_POLICY_SERVICE=$(openssl rand -hex 24)" >> .env
```

Without it, auth-service refuses the token request and every purchase answers 503 `PAYMENT_UNAVAILABLE`. To change it, update `.env` and restart both services; tokens already issued stay valid for at most five minutes.

**A throwaway `.env`.** `bash scripts/make-env.sh > .env` writes one with fresh random values (CI uses it). Never use it for anything you keep: a new signing key signs every user out, and a new field key makes stored national IDs unreadable.

**Look at the events.** Kafka UI (`docker compose --profile tools up -d`, http://localhost:8090), or from the shell:

```bash
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic policy.events --from-beginning --timeout-ms 8000
```

Use `payment.events` for the payment side and `<topic>.DLT` for messages a consumer gave up on. Unsent events wait in each service's `outbox_events` table (`published_at` is null); applied ones are in `processed_events`.

**Replaying a dead letter** is a manual procedure until M8: fix the cause, then publish the message value again to the original topic with the same key. The inbox makes a replay of an already applied event harmless.

**The full gate starts the stack.** `scripts/verify.sh` builds the images, starts the `full` profile, waits until the gateway is healthy and runs `scripts/smoke.sh`. If the stack was not running before, it is stopped again at the end (data volumes are kept).

## Claims: the policy read model (M5a)

**The secret.** claim-service gets its own SERVICE token from auth-service, with `SERVICE_SECRET_CLAIM_SERVICE` from `.env`. Set it once, the same way as the policy secret:

```bash
echo "SERVICE_SECRET_CLAIM_SERVICE=$(openssl rand -hex 24)" >> .env
```

Without it, the fallback answers 503 `POLICY_UNAVAILABLE` whenever the read model does not have the policy. The read model itself does not need it.

**Where a policy is answered from.** `GET /api/v1/claims/policies` lists only what the read model holds (activated or cancelled policies). `GET /api/v1/claims/policies/{id}` answers from the read model, or asks policy-service when the event has not arrived yet.

**If a bought policy is missing from the claim list.** Check, in order: that `policy.events` has the `PolicyActivated` message for it (Kafka UI or the console consumer above); that claim-service has not moved it to `policy.events.DLT` (an unknown type); that the message is not older than the stored version (the log says "not newer than version"); and the consumer group `claim-service` lag. A policy that is still `PENDING_PAYMENT` is not expected in the list.

**A 503 with Retry-After: 30.** policy-service did not answer (down, timing out, or refusing the token). The circuit breaker opens after repeated failures and stays open for 30 seconds, so customers get the fast 503 until it closes again. The log line "policy-service lookup failed" shows the cause.

## Claims and documents (M5b)

**Storage.** Claim documents live in the private `insurance-documents` bucket on SeaweedFS, under random keys (`claims/<uuid>`). claim-service uses `S3_ACCESS_KEY` and `S3_SECRET_KEY` from `.env`, the same pair the bucket is created with, and refuses to start without them. Uploads go to `http://seaweedfs:8333` on the compose network. Download links are presigned against `CLAIM_STORAGE_PUBLIC_ENDPOINT` (the host's `localhost:8333` by default) and expire after five minutes.

**Limits.** A document is 10 MB at most and must be a PDF, JPEG or PNG whose first bytes match its declared type. A claim holds at most 10 documents, and only a draft takes new ones.

**A claim the checks reject.** `POST /api/v1/claims/{id}/submit` runs the hard checks at once. A failing claim is `REJECTED` with `rejectionReason` set to the first failing rule (for example `POLICY_IN_FORCE`, `WAITING_PERIOD_ELAPSED` or `REQUIRED_DOCUMENTS`). The response lists every check. The history shows the rejection with the actor `SYSTEM`. Nothing is sent to `claim.events` for a rejected claim.

**Looking at a submission.** The accepted claims appear on `claim.events` as `ClaimSubmitted`, after the outbox relay has sent them. Unsent rows wait in `outbox_events` as before.

**Orphaned documents.** If the database write fails after an upload, the object stays in the bucket with no row pointing at it. Nothing reads it; the lifecycle job (M8) will remove such objects.

## Reviewing claims (M6)

**Who can do what.** An adjuster or an insurer admin works on the claims of the insurer in their token. A platform admin (an ADMIN with no insurer) works on every insurer's. The queue is `GET /api/v1/claims/queue`, flagged claims first. A claim of another insurer answers 404, like one that does not exist. An adjuster token with no `insurerId` is refused with `STAFF_SCOPE_MISSING`; create staff through the admin endpoint with an insurer. Nobody reviews a claim they filed themselves.

**The thresholds are configuration** in claim-service, set by environment variables or properties: `claim.review.approval-threshold` (KES 50,000: above it only an admin approves), `claim.review.high-amount-flag` (KES 100,000), `claim.review.early-cover-days` (30), `claim.review.frequent-claims-count` (3) and `claim.review.frequent-claims-days` (30). Changing one needs a restart.

**What approval does.** `POST /api/v1/claims/{id}/approve` evaluates the policy's limits again, refuses more than the payable amount, and reserves the approved amount in the same transaction as the status change. The body is optional: leave the amount out to approve the full payable amount. A smaller amount needs a reason.

**Refusals you may see.** `COUNT_LIMIT_REACHED` and `LIMIT_EXHAUSTED` (422): nothing can be paid under the limits, so reject the claim with a reason. `ADMIN_APPROVAL_REQUIRED` (403): the amount is above the threshold. `CONFLICT` (409): another approval changed the same limit at the same moment; retry.

**The audit log.** Every staff action writes one structured line on the logger named `audit`, with `action`, `outcome` (`SUCCESS`, `REFUSED` or `ERROR`), `code`, `claimId`, `actorId`, `role` and `insurerId`. It never holds names, the reviewer's typed text or request bodies. Find a refused action with `docker compose logs claim-service | grep '"logger_name":"audit"'`.

**Limits on the totals.** Running totals are in `benefit_utilisation` and what each claim reserved is in `claim_reservations` (claim_db). A reservation stays in place while a claim is PAYOUT_FAILED, because the money is still owed (ADR-018). `UtilisationService.release` can give a reservation back, but nothing calls it yet; it is parked.

## Payouts (M7)

**The flow.** Approving a claim leaves it PAYOUT_PENDING. payment-service pays it through the mock provider and claim-service moves it to PAID, normally within one or two seconds. If the claim stays PAYOUT_PENDING, check that payment-service is running and look in `claim.events.DLT` and `payment.events.DLT`.

**Find a payout.** As an admin (an insurer admin sees their own insurer's, a platform admin sees all): `GET /api/v1/payments/payouts?claimId=<claim id>`. To find claims whose payout failed: `GET /api/v1/claims/queue?status=PAYOUT_FAILED`.

**Retry a failed payout.** `POST /api/v1/payments/{paymentId}/retry-payout` with an `Idempotency-Key` header (a new value for each attempt; repeating a value returns the first result). Only a payout in FAILED can be retried (`PAYOUT_NOT_RETRYABLE`, 409). A successful retry moves the claim to PAID. A payout of another insurer answers 404. Adjusters cannot retry.

**The mock provider and failures.** payment-service property `payments.mock.payout-decline-payees` (environment variable `PAYMENTS_MOCK_PAYOUT_DECLINE_PAYEES`, comma-separated holder ids) makes the mock decline payouts to those customers. It is empty in normal use. The smoke script sets it for one customer to prove the failure and retry path, then clears it.

**Replaying a dead letter.** As for premiums (see Payments above): fix the cause, then publish the message value again to the original topic with the same key. The inbox and the unique payout row make a replay harmless.

## Troubleshooting

- **A service shows "unhealthy" or "starting" for a long time:** `docker compose logs <service>`. Kafka and SeaweedFS can take 30-60 seconds on first start.
- **`port is already allocated`:** another container holds the port. `docker ps --format '{{.Names}} {{.Ports}}'` shows which one.
- **Kafka topics missing:** the `kafka-topics` job runs once. Check its output with `docker compose logs kafka-topics`. policy-service and payment-service start only after it has finished.
- **Purchase answers 503 `PAYMENT_UNAVAILABLE`:** payment-service is down, or auth-service refused the service token (check `SERVICE_SECRET_POLICY_SERVICE`). After several failures the circuit breaker stays open for 30 seconds and purchases fail at once; they recover on their own once payment-service is back. Nothing is saved and the quote stays open.
- **A paid policy stays `PENDING_PAYMENT`:** check `docker compose logs policy-service` for a warning about the `PremiumPaid` event, then the `payment.events.DLT` topic and the payment-service `outbox_events` table.
- **Disk full:** Docker keeps images and build cache. `docker system df` shows usage. Prune only after checking which images other projects use.
