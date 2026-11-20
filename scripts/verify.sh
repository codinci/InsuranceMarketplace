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
# Enforced inside the Maven build: auth-service's OpenApiSnapshotIT compares the generated spec with
# docs/openapi/auth-service.json. Nothing more to run here until another service publishes a spec.
echo "covered by OpenApiSnapshotIT in the Maven step above (auth-service)"

step "Hadolint on every Dockerfile"
while IFS= read -r dockerfile; do
  echo "linting ${dockerfile}"
  docker run --rm -i "${HADOLINT_IMAGE}" < "${dockerfile}"
done < <(find . -name Dockerfile -not -path '*/target/*' -not -path './.git/*' | sort)

step "End-to-end smoke script"
# Added at M4, when the compose stack first runs the whole journey.
echo "not yet applicable: smoke script starts at M4"

printf '\nverify.sh: all applicable gates passed\n'
