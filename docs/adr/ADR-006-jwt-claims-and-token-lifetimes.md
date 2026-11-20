# ADR-006: JWT claims and token lifetimes

- **Status:** Accepted (2026-11-19). Implemented in M1.
- **Context:** PLAN 21.6 names the JWT claims as an expensive-to-change decision. Every service validates tokens against the auth-service JWKS, and every staff query is filtered by the `insurerId` claim (CLAUDE.md, PLAN 4). Changing a claim name or meaning after services depend on it breaks all of them at once.

## Decision

1. **Access tokens are RS256 JWTs** signed by auth-service. The header carries `kid`, which selects the public key in `/.well-known/jwks.json`.
2. **Claims:**

   | Claim | Type | Meaning |
   |---|---|---|
   | `sub` | string (UUID) | The user id. Resource ownership checks compare this with the owner's id (PLAN 4). |
   | `roles` | array of strings | Role names from `common-starter` `Roles`: `CUSTOMER`, `ADJUSTER`, `ADMIN`, `SERVICE`. Sorted. |
   | `insurerId` | string (UUID) | Present only for staff of one insurer. **Absent** for customers and platform admins, never `null`. |
   | `iss` | string | `auth.jwt.issuer`, default `mini-insurance-auth`. Verified by every service. |
   | `iat`, `exp` | numeric dates | Issue time and expiry. |
   | `jti` | string (UUID) | Unique token id, for future revocation lists. |

3. **Roles map to Spring authorities as `ROLE_<name>`** through the `roles` claim (`SecurityConfig`), so `hasRole("ADMIN")` works in every service.
4. **Lifetimes:** access token 15 minutes; refresh token 7 days (ADR-007). A role or insurer change takes effect when the next refresh runs, at most 15 minutes later (PLAN 4).
5. **`insurerId` is a UUID, not a code** such as `ACACIA`. Renaming an insurer must not change its identity, and a human-chosen code could collide later. In M1 the value is only format-checked, because insurers become data at M3b.
6. **Validation is the same everywhere.** Services use `JwtValidators.createDefaultWithIssuer` against the published key set. Expiry and issuer are checked, and signature alone is never enough.

## Alternatives considered

- **Opaque access tokens with introspection.** Every request would call auth-service. Rejected: it couples every service to auth-service uptime and adds latency to each call.
- **One role claim per role, or nested objects.** Harder to read in logs and in the OpenAPI examples, and Spring's default converter already reads a string array. Rejected.
- **`insurerId` as an object with code and name.** More data in every token for no benefit. Rejected.
- **HS256 with a shared secret.** Every service would hold the signing secret, so any service could forge tokens. Rejected.

## Consequences

- Adding a claim is additive and safe. Renaming or changing the meaning of one is a breaking change and needs a new ADR and a `version` bump.
- Adding a role means extending `Roles` in `common-starter`, the `user_roles` check constraint (Flyway), and the PLAN 4 table.
- Customer tokens have no `insurerId`. Consumers must treat a missing claim as "not staff", not as an error.
- The 15-minute access lifetime is the upper bound on how long a revoked role or a removed staff member keeps access. Immediate revocation would need a deny list, which is in the parking lot.

Related: [ADR-007](ADR-007-signing-keys-and-refresh-tokens.md), PLAN 4, PLAN 12, PLAN 21.6.
