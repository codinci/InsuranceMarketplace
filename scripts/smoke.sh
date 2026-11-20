#!/usr/bin/env bash
# End-to-end smoke test of the running stack (PLAN 18). M1 covers auth-service only; later milestones add steps.
# Usage: scripts/smoke.sh [base-url]   (default http://localhost:8081, the auth-service host port)
# Needs the seeded admin in .env (AUTH_SEED_PLATFORM_ADMIN_EMAIL and _PASSWORD). Exits non-zero on the first failure.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE="${1:-http://localhost:8081}"
if [ -f .env ]; then set -a; . ./.env; set +a; fi
: "${AUTH_SEED_PLATFORM_ADMIN_EMAIL:?set in .env}"
: "${AUTH_SEED_PLATFORM_ADMIN_PASSWORD:?set in .env}"

JSON='Content-Type: application/json'
step() { printf '\n== %s\n' "$1"; }

# call EXPECTED_STATUS URL [curl options...]: prints the body, fails unless the HTTP status matches.
call() {
  local expected="$1" url="$2"; shift 2
  local raw status body
  raw=$(curl -s -w '\n%{http_code}' "$@" "$url")
  status=$(printf '%s' "$raw" | tail -n1)
  body=$(printf '%s' "$raw" | sed '$d')
  if [ "$status" != "$expected" ]; then
    echo "FAILED: expected HTTP $expected, got $status: $body" >&2
    exit 1
  fi
  printf '%s\n' "$body"
}

# field BODY NAME: the first string value of a JSON field.
field() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -n1 | cut -d'"' -f4; }

step "health"
call 200 "$BASE/actuator/health" > /dev/null && echo "up"

step "register a policyholder"
EMAIL="smoke.$(date +%s)@example.com"
REG=$(call 201 "$BASE/api/v1/auth/register" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Test\"}")
echo "$REG"
printf '%s' "$REG" | grep -q '"roles":\["CUSTOMER"\]' || { echo "FAILED: new account is not a CUSTOMER" >&2; exit 1; }

step "log in as the customer"
LOGIN=$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"a-long-enough-password\"}")
ACCESS=$(field "$LOGIN" accessToken)
REFRESH=$(field "$LOGIN" refreshToken)
[ -n "$ACCESS" ] && [ -n "$REFRESH" ] || { echo "FAILED: tokens missing" >&2; exit 1; }
echo "tokens issued"

step "me with the access token"
call 200 "$BASE/api/v1/auth/me" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "current user returned"

step "refresh rotates the token"
ROTATED=$(call 200 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}")
NEW_REFRESH=$(field "$ROTATED" refreshToken)
[ "$NEW_REFRESH" != "$REFRESH" ] || { echo "FAILED: refresh token was not rotated" >&2; exit 1; }
echo "rotated"

step "reusing the old refresh token revokes the login"
call 401 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}" > /dev/null && echo "reuse rejected"
call 401 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$NEW_REFRESH\"}" > /dev/null && echo "replacement also rejected"

step "staff creation is refused to a customer and to anonymous callers"
call 403 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ACCESS" \
  -d '{"email":"nope@example.com","password":"a-long-enough-password","firstName":"A","lastName":"B","role":"ADJUSTER","insurerId":"6f1d2c3e-1111-4a2b-9c3d-000000000001"}' > /dev/null && echo "customer refused"
call 401 "$BASE/api/v1/admin/users" -H "$JSON" -d '{}' > /dev/null && echo "anonymous refused"

step "the platform admin can create an insurer admin"
ADMIN=$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$AUTH_SEED_PLATFORM_ADMIN_EMAIL\",\"password\":\"$AUTH_SEED_PLATFORM_ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(field "$ADMIN" accessToken)
call 201 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"email\":\"smoke.admin.$(date +%s)@example.com\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Admin\",\"role\":\"ADMIN\",\"insurerId\":\"6f1d2c3e-1111-4a2b-9c3d-000000000001\"}" > /dev/null && echo "created"

step "JWKS publishes a key"
call 200 "$BASE/.well-known/jwks.json" | grep -q '"kty":"RSA"' && echo "key published"

printf '\nsmoke.sh: all steps passed\n'
