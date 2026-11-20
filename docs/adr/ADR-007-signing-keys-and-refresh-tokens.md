# ADR-007: Signing keys, refresh tokens and login protection

- **Status:** Accepted (2026-11-19). Implemented in M1.
- **Context:** The auth-service private key signs every access token, and every other service trusts it through the JWKS (ADR-006). A leaked key lets anyone mint tokens for any user and any insurer. A leaked refresh token is a long-lived login. Login is the main brute-force target. PLAN 12 sets the rules: RS256, JWKS with `kid` rotation, refresh tokens with rotation and revocation, BCrypt or Argon2, login rate limiting and temporary lockout.

## Decision

### Signing keys
1. **RS256, 2048-bit RSA**, the same algorithm as the JWKS published to services.
2. **The private key comes from the environment** as `AUTH_JWT_PRIVATE_KEY_BASE64`: the PKCS#8 DER key in base64, on one line. It lives in `.env` locally and in the deployment's secret store later, and it is never in Git or in an image. The public key is derived from the private key, so the two cannot disagree.
3. **No key, no start.** Without the key the service refuses to start, unless `auth.jwt.generate-ephemeral-key=true`. That flag is for local development and tests only. Ephemeral keys break every token on restart, so the flag is never set in compose.
4. **Rotation by overlap.** Publish the new key with its own `kid`, sign with it, then keep the old public key in `auth.jwt.additional-public-keys` in the JWKS until its longest-lived token expires (15 minutes for access tokens). The runbook describes the steps.

### Refresh tokens
5. **Opaque**: 32 random bytes, base64url, not a JWT. Nothing can be read from them, and they cannot be verified without a database lookup, which is what makes revocation work.
6. **Stored as SHA-256 hashes only.** A database dump does not reveal a usable token.
7. **Rotation on every use.** A presented token is revoked and a new one is issued in the same `family_id`.
8. **Reuse of a revoked token revokes the whole family** and returns `REFRESH_TOKEN_REUSED`. A reused token means it was copied, and the attacker and the legitimate user now both need to sign in again. The revocation is committed even though the request fails (`noRollbackFor`).
9. **Lifetime 7 days.** Logout revokes the family. Logout is idempotent.

### Passwords and login protection
10. **BCrypt with cost 12** (Spring's built-in encoder, no extra dependency). Passwords are 12 to 72 bytes, because BCrypt ignores anything after 72 bytes.
11. **Account lockout:** 5 consecutive failures lock the account for 15 minutes. A success clears the counter.
12. **Rate limits:** 20 attempts a minute per client IP and 10 a minute per email, with Bucket4j token buckets in memory. Both limits are checked, so an attacker cannot spread guesses over many IPs or many emails.
13. **Timing:** an unknown email is checked against a dummy hash, so the response time does not reveal whether an account exists. The error message is the same in both cases.

## Alternatives considered

- **Key in the database.** Rotation would be easy, but every database backup would contain the signing key. Rejected.
- **Key generated at each start.** Rejected: restarts would invalidate all tokens and change the JWKS under every service.
- **Refresh tokens as JWTs.** Revocation would need a deny list anyway, and the token would carry data that a leak would expose. Rejected.
- **Argon2.** Stronger against GPU attacks, but it needs a library that is not in the Spring Boot BOM. BCrypt is accepted by PLAN 12. We can move to Argon2 later, because a stored hash says which algorithm it uses.
- **Distributed rate limits (Redis).** Correct across instances, but a new service for v1. The in-memory limit is per instance, and the gateway adds a coarse limit (M2). Recorded in the parking lot.

## Consequences

- Key rotation is an operational task. The runbook must be followed, and the overlap window must be at least the access-token lifetime.
- Losing the private key means every user signs in again after the new key is set. The key must be backed up with the other secrets.
- Rate-limit buckets reset on restart and differ per instance. A determined attacker can spend more guesses after a restart. Lockout still applies per account, because the failure count is in the database.
- Refresh-token rows accumulate. A cleanup job for expired and revoked rows belongs to M8 (lifecycle jobs). Recorded in the parking lot.

Related: [ADR-006](ADR-006-jwt-claims-and-token-lifetimes.md), PLAN 12.
