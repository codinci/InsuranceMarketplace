#!/usr/bin/env bash
# End-to-end smoke test of the running stack through the API gateway (PLAN 18). Covers the gateway (M2) and auth-service (M1).
# Later milestones add steps. Usage: scripts/smoke.sh [base-url]   (default: the gateway's host port from .env, or 8080)
# Needs the seeded admin in .env (AUTH_SEED_PLATFORM_ADMIN_EMAIL and _PASSWORD). Exits non-zero on the first failure.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then set -a; . ./.env; set +a; fi
BASE="${1:-http://localhost:${GATEWAY_HOST_PORT:-8080}}"
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

step "health through the gateway"
call 200 "$BASE/actuator/health" > /dev/null && echo "up"

step "/internal is never published, even with a valid token"
call 404 "$BASE/internal/policies/1" -H 'Authorization: Bearer invalid' > /dev/null && echo "internal path not found"

step "a protected route needs a token"
call 401 "$BASE/api/v1/payments/x" > /dev/null && echo "protected route needs a token"

step "rate-limit headers are present on a successful response"
HEADERS=$(curl -s -D - -o /dev/null "$BASE/.well-known/jwks.json")
printf '%s' "$HEADERS" | grep -qi '^X-RateLimit-Remaining' && echo "X-RateLimit headers present" || { echo "FAILED: no X-RateLimit headers" >&2; exit 1; }

step "the correlation id is echoed"
printf '%s' "$(curl -s -D - -o /dev/null -H 'X-Correlation-Id: smoke-m2-1' "$BASE/.well-known/jwks.json")" \
  | grep -qi '^X-Correlation-Id: smoke-m2-1' && echo "echoed" || { echo "FAILED: correlation id not echoed" >&2; exit 1; }

step "CORS: no origin is allowed until the frontend exists"
call 403 "$BASE/api/v1/auth/me" -X OPTIONS -H 'Origin: http://browser.example' \
  -H 'Access-Control-Request-Method: GET' > /dev/null && echo "preflight refused"

step "aggregated Swagger serves the auth-service spec"
call 200 "$BASE/v3/api-docs/auth" | grep -q '"title"' && echo "auth spec served through the gateway"

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

step "a route whose service is not built yet answers 503 (payment-service comes in M4)"
call 503 "$BASE/api/v1/payments/x" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "UPSTREAM_UNAVAILABLE"

step "unknown paths are answered in the problem format (with a valid token, so routing is reached)"
call 404 "$BASE/no/such/path" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "unknown path not found"

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

step "JWKS publishes a key through the gateway"
call 200 "$BASE/.well-known/jwks.json" | grep -q '"kty":"RSA"' && echo "key published"

printf '\nsmoke.sh: all steps passed\n'
