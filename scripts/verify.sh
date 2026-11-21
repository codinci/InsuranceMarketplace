#!/usr/bin/env bash
# The full gate (G2, PLAN 16.2). Run from anywhere; it changes to the repo root.
# A step that cannot run is a failure, never a silent skip.
set -euo pipefail
cd "$(dirname "$0")/.."

HADOLINT_IMAGE="hadolint/hadolint:v2.12.0"

step() { printf '\n== %s\n' "$1"; }

step "Maven: format, style, static analysis, architecture, tests, coverage"
./mvnw -B clean verify

step "OpenAPI diff"
# Enforced inside the Maven build: each service's OpenApiSnapshotIT compares its generated spec with
# docs/openapi/<service>.json.
echo "covered by OpenApiSnapshotIT in the Maven step above (auth-service, policy-service, payment-service)"

step "Hadolint on every Dockerfile"
while IFS= read -r dockerfile; do
  echo "linting ${dockerfile}"
  docker run --rm -i "${HADOLINT_IMAGE}" < "${dockerfile}"
done < <(find . -name Dockerfile -not -path '*/target/*' -not -path './.git/*' | sort)

step "End-to-end smoke script"
# Builds and starts the full compose stack, runs the journey through the gateway, and stops the stack again if
# this script started it (M4b). Without a .env (CI, a fresh clone) a throwaway one is generated first.
if [ ! -f .env ]; then
  echo "no .env found: writing a throwaway one with random secrets (scripts/make-env.sh)"
  bash scripts/make-env.sh > .env
fi
already_running=$(docker compose --profile full ps -q api-gateway 2>/dev/null || true)
if [ -z "${already_running}" ]; then
  trap 'docker compose --profile full down' EXIT
fi
docker compose --profile full up -d --build
# Not `up --wait`: it treats the one-shot setup containers (topics, bucket) that exit 0 as failures. The gateway
# starts only after every service is healthy, so its health stands for the whole stack.
health=""
for _ in $(seq 1 120); do
  gateway=$(docker compose --profile full ps -q api-gateway)
  health=$(docker inspect -f '{{.State.Health.Status}}' "${gateway}" 2>/dev/null || true)
  [ "${health}" = "healthy" ] && break
  sleep 3
done
if [ "${health}" != "healthy" ]; then
  echo "the stack did not become healthy in 6 minutes" >&2
  docker compose --profile full ps >&2
  exit 1
fi
bash scripts/smoke.sh

printf '\nverify.sh: all applicable gates passed\n'
