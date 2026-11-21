#!/usr/bin/env bash
# End-to-end smoke test of the running stack through the API gateway (PLAN 18). Covers auth-service (M1), the gateway (M2)
# the catalogue in policy-service (M3b), buying a policy (M4a) and paying for it until it is ACTIVE (M4b).
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
# A check written as `cmd && echo ok` does not stop the script under set -e when cmd fails, so each one ends
# with `|| fail "what was expected"`.
fail() { echo "FAILED: $1" >&2; exit 1; }

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
call 200 "$BASE/actuator/health" > /dev/null && echo "up" || fail "up"

step "a protected route needs a token"
call 401 "$BASE/api/v1/payments/x" > /dev/null && echo "protected route needs a token" || fail "protected route needs a token"

step "rate-limit headers are present on a successful response"
HEADERS=$(curl -s -D - -o /dev/null "$BASE/.well-known/jwks.json")
printf '%s' "$HEADERS" | grep -i '^X-RateLimit-Remaining' >/dev/null && echo "X-RateLimit headers present" || { echo "FAILED: no X-RateLimit headers" >&2; exit 1; }

step "the correlation id is echoed"
printf '%s' "$(curl -s -D - -o /dev/null -H 'X-Correlation-Id: smoke-m2-1' "$BASE/.well-known/jwks.json")" \
  | grep -i '^X-Correlation-Id: smoke-m2-1' >/dev/null && echo "echoed" || { echo "FAILED: correlation id not echoed" >&2; exit 1; }

step "CORS: no origin is allowed until the frontend exists"
call 403 "$BASE/api/v1/auth/me" -X OPTIONS -H 'Origin: http://browser.example' \
  -H 'Access-Control-Request-Method: GET' > /dev/null && echo "preflight refused" || fail "preflight refused"

step "aggregated Swagger serves the auth-service, policy-service and payment-service specs"
call 200 "$BASE/v3/api-docs/auth" | grep '"title"' >/dev/null && echo "auth spec served through the gateway" || fail "auth spec served through the gateway"
call 200 "$BASE/v3/api-docs/policy" | grep '"policy-service"' >/dev/null && echo "policy spec served through the gateway" || fail "policy spec served through the gateway"
call 200 "$BASE/v3/api-docs/payment" | grep '"payment-service"' >/dev/null && echo "payment spec served through the gateway" || fail "payment spec served through the gateway"
call 200 "$BASE/v3/api-docs/claim" | grep '"claim-service"' >/dev/null && echo "claim spec served through the gateway" || fail "claim spec served through the gateway"

step "register a policyholder"
EMAIL="smoke.$(date +%s)@example.com"
REG=$(call 201 "$BASE/api/v1/auth/register" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Test\"}")
echo "$REG"
printf '%s' "$REG" | grep '"roles":\["CUSTOMER"\]' >/dev/null || { echo "FAILED: new account is not a CUSTOMER" >&2; exit 1; }

step "log in as the customer"
LOGIN=$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"a-long-enough-password\"}")
ACCESS=$(field "$LOGIN" accessToken)
REFRESH=$(field "$LOGIN" refreshToken)
[ -n "$ACCESS" ] && [ -n "$REFRESH" ] || { echo "FAILED: tokens missing" >&2; exit 1; }
echo "tokens issued"

step "me with the access token"
call 200 "$BASE/api/v1/auth/me" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "current user returned" || fail "current user returned"

step "/internal is never published, even with a valid token"
call 404 "$BASE/internal/policies/1" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "internal path not found" || fail "internal path not found"
call 404 "$BASE/internal/payments" -X POST -H "Authorization: Bearer $ACCESS" > /dev/null && echo "internal payments not found" || fail "internal payments not found"

step "claims are routed to claim-service: a malformed claim id is answered by it"
# 'x' is not a UUID, so claim-service's own validation answers 400 VALIDATION_FAILED.
call 400 "$BASE/api/v1/claims/x" -H "Authorization: Bearer $ACCESS" | grep '"VALIDATION_FAILED"' >/dev/null \
  && echo "claim-service answered" || fail "claim-service answered"

step "unknown paths are answered in the problem format (with a valid token, so routing is reached)"
call 404 "$BASE/no/such/path" -H "Authorization: Bearer $ACCESS" > /dev/null && echo "unknown path not found" || fail "unknown path not found"

step "refresh rotates the token"
ROTATED=$(call 200 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}")
NEW_REFRESH=$(field "$ROTATED" refreshToken)
[ "$NEW_REFRESH" != "$REFRESH" ] || { echo "FAILED: refresh token was not rotated" >&2; exit 1; }
echo "rotated"

step "reusing the old refresh token revokes the login"
call 401 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$REFRESH\"}" > /dev/null && echo "reuse rejected" || fail "reuse rejected"
call 401 "$BASE/api/v1/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$NEW_REFRESH\"}" > /dev/null && echo "replacement also rejected" || fail "replacement also rejected"

step "staff creation is refused to a customer and to anonymous callers"
call 403 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ACCESS" \
  -d '{"email":"nope@example.com","password":"a-long-enough-password","firstName":"A","lastName":"B","role":"ADJUSTER","insurerId":"6f1d2c3e-1111-4a2b-9c3d-000000000001"}' > /dev/null && echo "customer refused" || fail "customer refused"
call 401 "$BASE/api/v1/admin/users" -H "$JSON" -d '{}' > /dev/null && echo "anonymous refused" || fail "anonymous refused"

step "the platform admin can create an insurer admin"
ADMIN=$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$AUTH_SEED_PLATFORM_ADMIN_EMAIL\",\"password\":\"$AUTH_SEED_PLATFORM_ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(field "$ADMIN" accessToken)
call 201 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"email\":\"smoke.admin.$(date +%s)@example.com\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Admin\",\"role\":\"ADMIN\",\"insurerId\":\"6f1d2c3e-1111-4a2b-9c3d-000000000001\"}" > /dev/null && echo "created" || fail "created"

step "JWKS publishes a key through the gateway"
call 200 "$BASE/.well-known/jwks.json" | grep '"kty":"RSA"' >/dev/null && echo "key published" || fail "key published"

step "catalogue: the seeded fictional insurers are listed without a token"
INSURERS=$(call 200 "$BASE/api/v1/insurers?size=100")
printf '%s' "$INSURERS" | grep '"code":"ACACIA"' >/dev/null && printf '%s' "$INSURERS" | grep '"code":"BAOBAB"' >/dev/null \
  && echo "ACACIA and BAOBAB listed" || { echo "FAILED: seeded insurers missing" >&2; exit 1; }
# id_of BODY CODE: the id of the insurer with that code (records serialise id before code).
id_of() { printf '%s' "$1" | grep -o "\"id\":\"[^\"]*\",\"code\":\"$2\"" | cut -d'"' -f4; }
BAOBAB_ID=$(id_of "$INSURERS" BAOBAB)

step "catalogue: an insurer's plans on sale, and a plan's terms"
PLANS=$(call 200 "$BASE/api/v1/insurers/ACACIA/plans")
printf '%s' "$PLANS" | grep '"totalElements":2' >/dev/null || { echo "FAILED: expected two ACACIA plans: $PLANS" >&2; exit 1; }
SEED_PLAN=$(field "$PLANS" id)
call 200 "$BASE/api/v1/plans/$SEED_PLAN" | grep '"modules"' >/dev/null && echo "terms shown" || fail "terms shown"

step "catalogue: the platform admin onboards an insurer from a JSON document"
CODE="SMOKE-$(date +%s)"
call 201 "$BASE/api/v1/admin/insurers" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"code\":\"$CODE\",\"name\":\"Smoke Insurer (fictional)\"}" > /dev/null
NEW_INSURER=$(id_of "$(call 200 "$BASE/api/v1/insurers?size=100")" "$CODE")
DOC=$(sed "s/\"code\": \"ACACIA\"/\"code\": \"$CODE\"/" policy-service/src/main/resources/catalogue-seed/acacia/standard.v1.json)
IMPORT_URL="$BASE/api/v1/admin/insurers/$NEW_INSURER/plans/import"
call 422 "$IMPORT_URL?dryRun=true" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"schemaVersion":1}' | grep '"PLAN_INVALID"' >/dev/null && echo "invalid document refused with PLAN_INVALID" || fail "invalid document refused with PLAN_INVALID"
call 200 "$IMPORT_URL?dryRun=true" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  --data-binary "$DOC" | grep '"VALIDATED"' >/dev/null && echo "dry run passed" || fail "dry run passed"
NEW_PLAN=$(field "$(call 201 "$IMPORT_URL" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary "$DOC")" id)
call 404 "$BASE/api/v1/plans/$NEW_PLAN" > /dev/null && echo "draft hidden from the public" || fail "draft hidden from the public"
call 200 "$BASE/api/v1/admin/plans/$NEW_PLAN/publish" -X POST -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null && echo "published" || fail "published"
call 200 "$BASE/api/v1/insurers/$CODE/plans" | grep "\"id\":\"$NEW_PLAN\"" >/dev/null && echo "on sale" || fail "on sale"

step "catalogue: an admin of another insurer cannot touch it"
OTHER_EMAIL="smoke.baobab.$(date +%s)@example.com"
call 201 "$BASE/api/v1/admin/users" -H "$JSON" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Baobab\",\"role\":\"ADMIN\",\"insurerId\":\"$BAOBAB_ID\"}" > /dev/null
OTHER_TOKEN=$(field "$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"a-long-enough-password\"}")" accessToken)
call 404 "$BASE/api/v1/admin/plans/$NEW_PLAN/retire" -X POST -H "Authorization: Bearer $OTHER_TOKEN" \
  | grep '"PLAN_NOT_FOUND"' >/dev/null && echo "another insurer's admin gets 404" || fail "another insurer's admin gets 404"

step "policies: a customer creates a profile, gets a quote and buys a policy"
BUYER_EMAIL="smoke.buyer.$(date +%s)@example.com"
call 201 "$BASE/api/v1/auth/register" -H "$JSON" \
  -d "{\"email\":\"$BUYER_EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Smoke\",\"lastName\":\"Buyer\"}" > /dev/null
BUYER=$(field "$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$BUYER_EMAIL\",\"password\":\"a-long-enough-password\"}")" accessToken)
call 200 "$BASE/api/v1/me/profile" -X PUT -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d '{"firstName":"Smoke","lastName":"Buyer","dateOfBirth":"1990-06-15","sex":"FEMALE","nationalId":"SMOKE1234","phone":"+254700000000"}' \
  | grep '"nationalIdMasked":"\*\*\*234"' >/dev/null && echo "profile saved, national ID masked" || fail "profile saved, national ID masked"
STANDARD=$(call 200 "$BASE/api/v1/insurers/ACACIA/plans" \
  | grep -o '"id":"[^"]*","insurerCode":"ACACIA","productCode":"[^"]*","productName":"[^"]*","code":"STANDARD"' | cut -d'"' -f4)
START=$(date -u -d '+10 days' +%F)
QUOTE=$(call 201 "$BASE/api/v1/quotes" -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d "{\"planId\":\"$STANDARD\",\"coverType\":\"INDIVIDUAL\",\"startDate\":\"$START\",\"members\":[{\"relationship\":\"PRINCIPAL\",\"sex\":\"FEMALE\",\"dateOfBirth\":\"1990-06-15\",\"student\":false}],\"selectedModules\":{\"INPATIENT\":\"IP-200K\"}}")
QUOTE_ID=$(field "$QUOTE" id)
printf '%s' "$QUOTE" | grep '"annualPremium"' >/dev/null && echo "quote priced by the server" || fail "quote priced by the server"
POLICY=$(call 201 "$BASE/api/v1/policies" -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d "{\"quoteId\":\"$QUOTE_ID\",\"members\":[{\"relationship\":\"PRINCIPAL\",\"sex\":\"FEMALE\",\"dateOfBirth\":\"1990-06-15\",\"student\":false,\"firstName\":\"Smoke\",\"lastName\":\"Buyer\"}],\"declaration\":{\"preExistingConditions\":false,\"accepted\":true}}")
POLICY_ID=$(field "$POLICY" id)
printf '%s' "$POLICY" | grep '"status":"PENDING_PAYMENT"' >/dev/null && printf '%s' "$POLICY" | grep '"policyNumber":"POL-ACACIA-' >/dev/null \
  && echo "policy $(field "$POLICY" policyNumber) awaits payment" || { echo "FAILED: unexpected policy: $POLICY" >&2; exit 1; }

step "policies: another customer cannot see it, and staff cannot use customer endpoints"
call 403 "$BASE/api/v1/policies/$POLICY_ID" -H "Authorization: Bearer $OTHER_TOKEN" \
  | grep '"FORBIDDEN"' >/dev/null && echo "an insurer admin gets 403" || fail "an insurer admin gets 403"
OTHER_BUYER_EMAIL="smoke.other.$(date +%s)@example.com"
call 201 "$BASE/api/v1/auth/register" -H "$JSON" \
  -d "{\"email\":\"$OTHER_BUYER_EMAIL\",\"password\":\"a-long-enough-password\",\"firstName\":\"Other\",\"lastName\":\"Buyer\"}" > /dev/null
OTHER_BUYER=$(field "$(call 200 "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$OTHER_BUYER_EMAIL\",\"password\":\"a-long-enough-password\"}")" accessToken)
call 404 "$BASE/api/v1/policies/$POLICY_ID" -H "Authorization: Bearer $OTHER_BUYER" \
  | grep '"POLICY_NOT_FOUND"' >/dev/null && echo "another customer gets 404" || fail "another customer gets 404"

# policy_status TOKEN ID: the current status of a policy.
policy_status() { field "$(call 200 "$BASE/api/v1/policies/$2" -H "Authorization: Bearer $1")" status; }
# await_status TOKEN ID STATUS: waits up to 30 seconds for the policy to reach STATUS (events are asynchronous).
await_status() {
  local i current=""
  for i in $(seq 1 60); do
    current=$(policy_status "$1" "$2")
    [ "$current" = "$3" ] && return 0
    sleep 0.5
  done
  echo "FAILED: policy $2 is $current, expected $3" >&2
  exit 1
}

step "payments: the premium was created at purchase, and only its owner can see or pay it"
PAYMENT_ID=$(field "$POLICY" paymentId)
[ -n "$PAYMENT_ID" ] || { echo "FAILED: the policy has no paymentId: $POLICY" >&2; exit 1; }
call 200 "$BASE/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $BUYER" \
  | grep '"status":"PENDING"' >/dev/null && echo "payment $PAYMENT_ID is PENDING" || fail "payment $PAYMENT_ID is PENDING"
call 404 "$BASE/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $OTHER_BUYER" \
  | grep '"PAYMENT_NOT_FOUND"' >/dev/null && echo "another customer gets 404" || fail "another customer gets 404"
call 404 "$BASE/api/v1/payments/$PAYMENT_ID/pay" -X POST -H "Authorization: Bearer $OTHER_BUYER" \
  -H "Idempotency-Key: smoke-other-$(date +%s)" > /dev/null && echo "another customer cannot pay it" || fail "another customer cannot pay it"
call 403 "$BASE/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $OTHER_TOKEN" > /dev/null && echo "staff get 403" || fail "staff get 403"
call 400 "$BASE/api/v1/payments/$PAYMENT_ID/pay" -X POST -H "Authorization: Bearer $BUYER" \
  | grep '"IDEMPOTENCY_KEY_REQUIRED"' >/dev/null && echo "paying needs an Idempotency-Key" || fail "paying needs an Idempotency-Key"

step "payments: pay once, replay the same key, and the policy becomes ACTIVE through Kafka"
PAY_KEY="smoke-pay-$(date +%s)"
PAID=$(call 200 "$BASE/api/v1/payments/$PAYMENT_ID/pay" -X POST -H "Authorization: Bearer $BUYER" -H "Idempotency-Key: $PAY_KEY")
printf '%s' "$PAID" | grep '"status":"SUCCEEDED"' >/dev/null && echo "paid" || { echo "FAILED: payment not paid: $PAID" >&2; exit 1; }
REPLAY=$(call 200 "$BASE/api/v1/payments/$PAYMENT_ID/pay" -X POST -H "Authorization: Bearer $BUYER" -H "Idempotency-Key: $PAY_KEY")
[ "$(field "$REPLAY" providerRef)" = "$(field "$PAID" providerRef)" ] && echo "replay returned the same result" \
  || { echo "FAILED: replay differs: $REPLAY" >&2; exit 1; }
call 409 "$BASE/api/v1/payments/$PAYMENT_ID/pay" -X POST -H "Authorization: Bearer $BUYER" \
  -H "Idempotency-Key: smoke-again-$(date +%s)" | grep '"PAYMENT_ALREADY_PAID"' >/dev/null && echo "a new key cannot charge twice" || fail "a new key cannot charge twice"
await_status "$BUYER" "$POLICY_ID" ACTIVE && echo "policy $POLICY_ID is ACTIVE" || fail "policy $POLICY_ID is ACTIVE"

step "claims: the activated policy reaches claim-service's read model (PolicyActivated), for its holder only"
# claim_list_has TOKEN ID: the claim-service list (read model only, no fallback) contains the policy.
claim_list_has() { call 200 "$BASE/api/v1/claims/policies" -H "Authorization: Bearer $1" | grep "\"policyId\":\"$2\"" >/dev/null; }
for i in $(seq 1 60); do
  claim_list_has "$BUYER" "$POLICY_ID" && break
  sleep 0.5
done
claim_list_has "$BUYER" "$POLICY_ID" && echo "policy is in the claim read model" || fail "policy is in the claim read model"
call 200 "$BASE/api/v1/claims/policies/$POLICY_ID" -H "Authorization: Bearer $BUYER" | grep '"status":"ACTIVE"' >/dev/null \
  && echo "holder reads it with its terms" || fail "holder reads it with its terms"
call 404 "$BASE/api/v1/claims/policies/$POLICY_ID" -H "Authorization: Bearer $OTHER_BUYER" | grep '"POLICY_NOT_FOUND"' >/dev/null \
  && echo "another customer gets 404" || fail "another customer gets 404"
call 200 "$BASE/api/v1/claims/policies" -H "Authorization: Bearer $OTHER_BUYER" | grep "\"policyId\":\"$POLICY_ID\"" >/dev/null \
  && fail "another customer's list must not contain it" || echo "another customer's list does not contain it"
call 403 "$BASE/api/v1/claims/policies" -H "Authorization: Bearer $OTHER_TOKEN" | grep '"FORBIDDEN"' >/dev/null \
  && echo "staff get 403 on the customer endpoint" || fail "staff get 403 on the customer endpoint"

step "payments: a declined payment leaves a second policy awaiting payment"
QUOTE2_ID=$(field "$(call 201 "$BASE/api/v1/quotes" -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d "{\"planId\":\"$STANDARD\",\"coverType\":\"INDIVIDUAL\",\"startDate\":\"$START\",\"members\":[{\"relationship\":\"PRINCIPAL\",\"sex\":\"FEMALE\",\"dateOfBirth\":\"1990-06-15\",\"student\":false}],\"selectedModules\":{\"INPATIENT\":\"IP-200K\"}}")" id)
POLICY2=$(call 201 "$BASE/api/v1/policies" -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d "{\"quoteId\":\"$QUOTE2_ID\",\"members\":[{\"relationship\":\"PRINCIPAL\",\"sex\":\"FEMALE\",\"dateOfBirth\":\"1990-06-15\",\"student\":false,\"firstName\":\"Smoke\",\"lastName\":\"Buyer\"}],\"declaration\":{\"preExistingConditions\":false,\"accepted\":true}}")
call 200 "$BASE/api/v1/payments/$(field "$POLICY2" paymentId)/pay" -X POST -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -H "Idempotency-Key: smoke-fail-$(date +%s)" -d '{"simulate":"FAIL"}' | grep '"status":"FAILED"' >/dev/null && echo "declined" || fail "declined"
sleep 3
[ "$(policy_status "$BUYER" "$(field "$POLICY2" id)")" = "PENDING_PAYMENT" ] && echo "policy still PENDING_PAYMENT" \
  || { echo "FAILED: a declined payment changed the policy" >&2; exit 1; }

step "claims: a draft with a document and a presigned link, then a submission the checks reject"
# The policy above starts in ten days, so an incident today is outside cover: the checks reject it at once,
# and that is the path this step exercises. The approved path is covered by the claim-service integration tests.
MEMBER=$(field "$(call 200 "$BASE/api/v1/claims/policies/$POLICY_ID" -H "Authorization: Bearer $BUYER")" memberNumber)
[ -n "$MEMBER" ] || { echo "FAILED: the policy has no member number" >&2; exit 1; }
TODAY_DATE=$(date -u +%F)
CLAIM=$(call 201 "$BASE/api/v1/claims" -H "$JSON" -H "Authorization: Bearer $BUYER" \
  -d "{\"policyId\":\"$POLICY_ID\",\"claim\":{\"memberId\":\"$MEMBER\",\"moduleCode\":\"INPATIENT\",\"benefitCode\":\"GENERAL_INPATIENT\",\"cause\":\"ACCIDENT\",\"surgical\":false,\"conditionTags\":[],\"incidentDate\":\"$TODAY_DATE\",\"providerName\":\"Smoke Clinic\",\"description\":\"Smoke test admission\",\"amountClaimed\":3000.00}}")
CLAIM_ID=$(field "$CLAIM" id)
printf '%s' "$CLAIM" | grep '"status":"DRAFT"' >/dev/null && printf '%s' "$CLAIM" | grep '"claimNumber":"CLM-ACACIA-' >/dev/null \
  && echo "draft $(field "$CLAIM" claimNumber) created" || { echo "FAILED: unexpected draft: $CLAIM" >&2; exit 1; }

# In the working directory, not /tmp: curl on Windows cannot open a Git Bash /tmp path.
SMOKE_DOC=$(mktemp "./smoke-doc.XXXXXX")
trap 'rm -f "$SMOKE_DOC"' EXIT
printf '%%PDF-1.4\nsmoke receipt\n' > "$SMOKE_DOC"
DOCUMENT=$(call 201 "$BASE/api/v1/claims/$CLAIM_ID/documents" -H "Authorization: Bearer $BUYER" \
  -F "docType=RECEIPT" -F "file=@$SMOKE_DOC;type=application/pdf")
DOC_ID=$(field "$DOCUMENT" id)
echo "document $DOC_ID stored"
call 200 "$BASE/api/v1/claims/$CLAIM_ID/documents/$DOC_ID/download-url" -H "Authorization: Bearer $BUYER" \
  | grep '"url":"' >/dev/null && echo "presigned link issued" || fail "presigned link issued"
call 415 "$BASE/api/v1/claims/$CLAIM_ID/documents" -H "Authorization: Bearer $BUYER" \
  -F "docType=RECEIPT" -F "file=@$SMOKE_DOC;type=text/plain" | grep '"DOCUMENT_TYPE_NOT_ALLOWED"' >/dev/null \
  && echo "a text file is refused" || fail "a text file is refused"

REJECTED=$(call 200 "$BASE/api/v1/claims/$CLAIM_ID/submit" -X POST -H "Authorization: Bearer $BUYER")
printf '%s' "$REJECTED" | grep '"status":"REJECTED"' >/dev/null && [ "$(field "$REJECTED" rejectionReason)" = "POLICY_IN_FORCE" ] \
  && echo "submission rejected at once: POLICY_IN_FORCE" || { echo "FAILED: unexpected submission result: $REJECTED" >&2; exit 1; }
HISTORY=$(call 200 "$BASE/api/v1/claims/$CLAIM_ID/history" -H "Authorization: Bearer $BUYER")
printf '%s' "$HISTORY" | grep '"totalElements":3' >/dev/null && echo "history: created, submitted, rejected" \
  || { echo "FAILED: unexpected history: $HISTORY" >&2; exit 1; }
call 404 "$BASE/api/v1/claims/$CLAIM_ID" -H "Authorization: Bearer $OTHER_BUYER" | grep '"CLAIM_NOT_FOUND"' >/dev/null \
  && echo "another customer gets 404 on the claim" || fail "another customer gets 404 on the claim"

printf '\nsmoke.sh: all steps passed\n'
