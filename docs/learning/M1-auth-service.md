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

## Official documentation

- Spring Security OAuth2 resource server (JWT): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Spring Boot externalised configuration and records: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Flyway with Spring Boot: https://docs.spring.io/spring-boot/reference/data/sql.html
- Bucket4j: https://bucket4j.com/
- springdoc-openapi: https://springdoc.org/
- BCrypt in Spring Security: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
