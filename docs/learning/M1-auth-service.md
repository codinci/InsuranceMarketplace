# M1: auth-service (identity, tokens and staff accounts)

## What was new

- **Spring Security filter chain.** Every request passes through a chain of filters before it reaches a controller. `SecurityConfig` lists which paths are public, which need a role (`hasRole("ADMIN")`), and which need any signed-in user. It is the Laravel middleware stack with route rules. A filter can refuse a request before any controller code runs, which is why the 401 and 403 responses are written by `SecurityProblemHandlers` and not by `@ControllerAdvice`.
- **Resource server and JWT.** Access tokens are JWTs: a header, a claims body, and a signature. The service signs with an RSA private key (RS256). Other services check the signature with the public key from `/.well-known/jwks.json`, so they never hold the private key. Laravel Passport and Sanctum do the same job with a private key file, but the public key is published as a JWKS document here.
- **JWKS and `kid`.** The JWKS is a JSON list of public keys. Each key has an id (`kid`), and the token header names the key that signed it. That is how a key can be rotated: publish the new key, sign with it, keep the old one in the list until its tokens expire (ADR-007).
- **Records for configuration.** `AuthProperties` is a Java `record`, so its values are final and bound from `application.properties` through the constructor. A record also gives you equality and a readable `toString`, without the boilerplate of a class.
- **Flyway and `ddl-auto=validate`.** Flyway runs `V1__auth_schema.sql` on start and records what it ran. Hibernate then only checks that the entity mappings match the tables. It never changes the schema. This is the Laravel migrations model with the mapping checked at startup.
- **JPA entities and `@Version`.** `UserAccount` is an entity: a class that Hibernate maps to a table. `@Version` adds an optimistic lock, so two requests that change the same row at once cannot silently overwrite each other. The `roles` set is stored in its own table with `@ElementCollection`.
- **`@Transactional(noRollbackFor = ApiException.class)`.** By default, Spring rolls back a transaction when a `RuntimeException` leaves the method. A failed login must still save its failure count, and a reused refresh token must still revoke its family, even though the request then fails. The annotation tells Spring to commit in that case.
- **BCrypt.** A slow, salted password hash. `passwordEncoder.encode` makes the hash, and `matches` checks a password against it. Laravel's `Hash::make` and `Hash::check` are the same idea. The cost factor (12) is what makes guessing slow.
- **Bucket4j.** A token bucket: each client has a bucket of tokens that refills over time, and each attempt takes one. When the bucket is empty, the attempt is rate limited. It is the library version of a Laravel `RateLimiter::attempt`.
- **springdoc-openapi.** Annotations (`@Operation`, `@ApiResponse`, `@Schema`) describe the API, and the library builds the OpenAPI document from them. The integration test compares that document with the committed file, so an API change cannot slip in unnoticed.
- **Testcontainers as a shared singleton.** Spring caches application contexts between test classes. If a Testcontainers extension stops the database after each class, a cached context keeps pointing at a dead database. Starting one container for the whole run avoids that.

## Why it was done this way

- **Insurers do not appear in the token until M3b.** The `insurerId` claim is a UUID, checked only for format in M1. The decision is in ADR-006.
- **Refresh tokens are random values stored as hashes.** A stolen database does not give anyone a working login. Reusing a rotated token revokes the whole family, so a copied token ends the login for everyone who holds it (ADR-007).
- **Staff rules that depend on the insurer live in the service, not the filter chain.** A path rule cannot see who the insurer is. `StaffService` reads the caller's `insurerId` claim and checks it against the target, and the unit tests cover each combination.
- **The timing check on unknown emails.** Without it, a login for an unknown email returns faster than one for a known email, and that difference reveals which emails have accounts.
- **Error codes are stable and separate from messages.** Clients match on `code`, and the text can change. Every failure, including the ones from Spring Security, uses the same RFC 7807 shape.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `SecurityFilterChain` and `authorizeHttpRequests` | Middleware and route groups with `can`/`role` |
| `JwtDecoder` and the JWKS | Passport or Sanctum token verification, with the public key published |
| `Hash` with BCrypt | `Hash::make` and `Hash::check` |
| Flyway migrations | `php artisan migrate` |
| JPA entity with `@Version` | An Eloquent model with an optimistic lock column |
| `@Transactional` | `DB::transaction` |
| Bucket4j | `RateLimiter::attempt` |
| `ApiException` with a code | A custom exception rendered with a JSON `code` field |
| `PlatformAdminSeeder` (`ApplicationRunner`) | A seeder that runs once in `DatabaseSeeder` |

## Self-check questions (answer before starting M2)

1. A refresh token is rotated on every use. A thief copies the token from a stolen device and uses it before the owner does. Describe what the owner sees on their next refresh, and why revoking the whole family is safer than only rejecting the reused token.
2. The `roles` claim is read from the token, not from the database, so a role change takes up to 15 minutes to apply. Describe a case where that delay matters, and one change to the design that would shorten it without storing sessions.
3. `StaffService` checks `insurerId` in code, not in the filter chain. Give one request that a path-based rule like `hasRole("ADMIN")` would allow but the service must refuse.

## Corrected answers

1. **Answered by the owner:** the owner sees a session timed out, because revoking the whole family is safer than rejecting only the reused token, since the stolen token cannot be traced.
   **Corrected version:** On the owner's next refresh, the token they hold has already been rotated, so it is reused and the server returns `REFRESH_TOKEN_REUSED` (401). The owner's session has ended, and they must sign in again. Revoking the family is safer because the server cannot tell which holder is the real owner. The thief's refresh created a newer token in the same family. Rejecting only the reused token would leave that newer token valid, so the thief would stay signed in. Revoking the family ends every token in that login chain, including the thief's. The revocation is committed even though the request returns an error (`noRollbackFor`, ADR-007 rule 8). Stolen tokens are not traced: the family is cut off, and the owner signs in again to get a fresh one.

   The owner's answer was right on the main point, which is the session ending. Its reason was wrong: the stolen token is not traced, the family is revoked so the thief's newer token also dies.

2. **Answered by the owner:** You can change the refresh token timer to a smaller value, but that would increase the number of requests.
   **Corrected version:** The 15-minute delay comes from the access token lifetime (`auth.token.access-ttl=PT15M`), not the refresh token. The `roles` claim is fixed inside the access token until it expires. The delay matters when access must end at once, for example an adjuster who is removed from an insurer or deactivated keeps approving claims for up to 15 minutes. The design change is to shorten the access token lifetime, for example to 5 minutes. Each refresh then re-reads the user and roles from the database, so a role change takes effect within 5 minutes. The cost is more refresh requests, which is the trade-off the owner named. Shortening the refresh token (7 days) does not change the role delay.

   The owner's answer was right that shorter lifetimes cost more requests. It named the wrong token, and it did not give the case where the delay matters.

3. **Answered by the owner:** Roles direct a user to the page they see after login, and the resource rule stops a user of one insurer from doing admin work for another.
   **Corrected version:** The request is an insurer admin, a user with the `ADMIN` role and `insurerId` A, who calls `POST /api/v1/admin/users` with `insurerId` B and role `ADJUSTER`. `hasRole("ADMIN")` lets it through, because the role matches and the filter chain never looks at the body. `StaffService.create` compares the target insurer with the token's `insurerId`, sees that they differ, and throws `INSURER_SCOPE_VIOLATION`. The filter decides who may enter the endpoint, and the service decides which data they may touch.

   The owner's answer named the right rule but no request. The example above shows why the check must be in the service.

## Summary of the self-check

All three questions have corrected answers. Question 1 was right on the main point. Question 2 named the refresh token, when the delay comes from the access token. Question 3 gave the rule without an example.

## Official documentation

- Spring Security OAuth2 resource server (JWT): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Spring Boot externalised configuration and records: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Flyway with Spring Boot: https://docs.spring.io/spring-boot/reference/data/sql.html
- Bucket4j: https://bucket4j.com/
- springdoc-openapi: https://springdoc.org/
- BCrypt in Spring Security: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
