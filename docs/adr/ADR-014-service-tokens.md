# ADR-014: Service tokens for internal calls

- **Status:** Accepted (2026-11-21). Implemented in M4b.
- **Context:** policy-service calls payment-service at `/internal/**` (PLAN 3, 13). The gateway never routes `/internal/**` (PLAN 12), and an internal endpoint must still check who is calling. PLAN 4 describes a short-lived service token with a shared secret for v1, with client credentials as a stretch. ADR-006 rejected HS256 for user tokens because every service would hold the signing secret and could forge tokens.

## Decision

1. **auth-service issues the service token.** `POST /internal/service-token` takes a service name and that service's secret, and returns a short-lived **RS256** access token. It is signed with the same key as user tokens and published in the same JWKS.
2. **Claims:** `sub` = the service name (for example `policy-service`), `roles` = `["SERVICE"]`, `iss` = the usual issuer, `exp` = now plus 5 minutes. There is **no `insurerId`**, because a service acts for no insurer.
3. **Receivers do not change.** Every service keeps its JWKS decoder and checks issuer and expiry (ADR-006). An internal endpoint requires `hasRole("SERVICE")`.
4. **Secrets come from the environment.** auth-service has one secret per calling service (`auth.service-clients.secrets.<name>`, from `AUTH_SERVICE_SECRET_<NAME>`). A service with an empty secret cannot get a token. Comparison is constant-time (`MessageDigest.isEqual`). Secrets are never logged or written to the database.
5. **The gateway does not route it.** `/internal/**` stays unrouted at the gateway (existing `InternalPathBlockFilter`), and `/internal/service-token` lives under the same prefix.
6. **Caching.** The caller caches the token and asks again 30 seconds before it expires.

## Alternatives considered

- **HS256 with a shared secret between services (PLAN 4 as written).** Every receiver would hold the secret, so any service could forge tokens for any other. Rejected, for the same reason as ADR-006.
- **OAuth2 client credentials (stretch).** The standard flow, and the same shape as this endpoint. Deferred because it needs a registered client model and grant handling that M4b does not need. This endpoint can become the client-credentials grant later without changing the receivers, because the token stays RS256 and the claims stay the same.
- **Mutual TLS between services.** Strong, but it needs a certificate authority in compose. Out of scope for v1.

## Consequences

- Compromising one calling service's secret lets an attacker call internal endpoints as that service. Rotation means changing the environment value and restarting auth-service and the caller.
- Internal calls depend on auth-service being up for a token. The caller caches tokens, so a short auth-service outage does not stop calls until the cache expires.
- The receivers accept any `SERVICE` token signed by auth-service, so a new internal endpoint must always check which service is calling if it matters.
