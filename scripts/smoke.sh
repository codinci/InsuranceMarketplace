#!/usr/bin/env bash
# End-to-end smoke test of the running stack through the API gateway (PLAN 18). Covers auth-service (M1), the gateway (M2)
# and the catalogue in policy-service (M3b).
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

step "aggregated Swagger serves the auth-service and policy-service specs"
call 200 "$BASE/v3/api-docs/auth" | grep -q '"title"' && echo "auth spec served through the gateway"
call 200 "$BASE/v3/api-docs/policy" | grep -q '"policy-service"' && echo "policy spec served through the gateway"

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

step "/internal is never published, even with a valid token"
call 404 "$BASE/internal/policies/1" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "internal path not found"

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

step "catalogue: the seeded fictional insurers are listed without a token"
INSURERS=$(call 200 "$BASE/api/v1/insurers?size=100")
printf '%s' "$INSURERS" | grep -q '"code":"ACACIA"' && printf '%s' "$INSURERS" | grep -q '"code":"BAOBAB"' \
  && echo "ACACIA and BAOBAB listed" || { echo "FAILED: seeded insurers missing" >&2; exit 1; }
# id_of BODY CODE: the id of the insurer with that code (records serialise id before code).
id_of() { printf '%s' "$1" | grep -o "\"id\":\"[^\"]*\",\"code\":\"$2\"" | cut -d'"' -f4; }
BAOBAB_ID=$(id_of "$INSURERS" BAOBAB)

step "catalogue: an insurer's plans on sale, and a plan's terms"
PLANS=$(call 200 "$BASE/api/v1/insurers/ACACIA/plans")
printf '%s' "$PLANS" | grep -q '"totalElements":2' || { echo "FAILED: expected two ACACIA plans: $PLANS" >&2; exit 1; }
SEED_PLAN=$(field "$PLANS" id)
call 200 "$BASE/api/v1/plans/$SEED_PLAN" | grep -q '"modules"' && echo "terms shown"

step "catalogue: the platform admin onboards an insurer from a JSON document"
CODE="SMOKE-$(date +%s)"
call 201 "$BASE/api/v1/admin/insurers" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"code\":\"$CODE\",\"name\":\"Smoke Insurer (fictional)\"}" > /dev/null
NEW_INSURER=$(id_of "$(call 200 "$BASE/api/v1/insurers?size=100")" "$CODE")
DOC=$(sed "s/\"code\": \"ACACIA\"/\"code\": \"$CODE\"/" policy-service/src/main/resources/catalogue-seed/acacia/standard.v1.json)
IMPORT_URL="$BASE/api/v1/admin/insurers/$NEW_INSURER/plans/import"
call 422 "$IMPORT_URL?dryRun=true" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"schemaVersion":1}' | grep -q '"PLAN_INVALID"' && echo "invalid document refused with PLAN_INVALID"
call 200 "$IMPORT_URL?dryRun=true" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  --data-binary "$DOC" | grep -q '"VALIDATED"' && echo "dry run passed"
NEW_PLAN=$(field "$(call 201 "$IMPORT_URL" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary "$DOC")" id)
call 404 "$BASE/api/v1/plans/$NEW_PLAN" > /dev/null && echo "draft hidden from the public"
call 200 "$BASE/api/v1/admin/plans/$NEW_PLAN/publish" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null && echo "published"
call 200 "$BASE/api/v1/insurers/$CODE/plans" | grep -q "\"id\":\"$NEW_PLAN\"" && echo "on sale"

step "catalogue: an admin of another insurer cannot touch it"
OTHER_EMAIL="smoke.baobab.$(date +%s)@example.com"
call 201 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Baobab\",\"role\":\"ADMIN\",\"insurerId\":\"$BAOBAB_ID\"}" > /dev/null
OTHER_TOKEN=$(field "$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"a-long-enough-password\"}")" accessToken)
call 404 "$BASE/api/v1/admin/plans/$NEW_PLAN/retire" -X POST -H "Authorization: Bearer $OTHER_TOKEN" \
  | grep -q '"PLAN_NOT_FOUND"' && echo "another insurer's admin gets 404"

printf '\nsmoke.sh: all steps passed\n'
