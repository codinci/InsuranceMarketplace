# M2: API gateway

## What M2 delivers

- `api-gateway`: one public entry point on port 8080. It routes each path prefix to the service that owns it, checks the JWT signature and issuer, blocks `/internal/**`, applies a rate limit, handles CORS preflight, and serves the Swagger UI for each service's spec.
- Redis, in compose, holds the rate-limit buckets. It is never published to the host.
- ADR-009 records the rate-limit decisions, including fail-open on a Redis outage.

## New concepts

**Spring Cloud Gateway is reactive.** It runs on WebFlux, which uses Netty and a small set of event-loop threads, not one thread per request. Code in filters returns `Mono` or `Flux` and must not block. The M1 code used Spring MVC (one thread per request). The gateway does not use MVC at all. This is why `common-starter` makes its servlet pieces optional and `CommonWebAutoConfiguration` only applies to servlet apps.

**Routes are data.** Each route is a `uri` plus `predicates` (which requests match) and `filters` (what changes on the way). They live in `application.properties`, so a new service gets a route with configuration, not new code (PLAN 3 and 21.5). Calling a service that is not running returns 503 `UPSTREAM_UNAVAILABLE`.

**Global filters have an order.** `GlobalFilter` beans run for every request. `getOrder()` sets the sequence. The correlation ID filter runs first, then the client-address filter, then the rate limiter, then routing. A filter that runs too late may see the wrong address or no correlation ID.

**Token bucket.** Each client has a bucket that holds up to `burst-capacity` tokens and refills at `replenish-rate-per-second`. A request takes one token. An empty bucket means HTTP 429. Redis keeps the bucket, so every gateway instance shares it. The Lua script runs atomically inside Redis.

**Two JWT checks, on purpose.** The gateway checks signature, issuer and expiry, so bad tokens never reach a service. Each service still checks roles and ownership itself (ADR-006). The gateway does not know which insurer owns a record, so it cannot make that decision.

**CORS is configured, not coded.** `gateway.cors.allowed-origins` is empty for now, so browsers from any origin are refused. The frontend (later) gets its origin added to configuration. The preflight test in `GatewayIT` checks the refusal.

## Why this approach

- The gateway limits floods on all routes at one place. Each service would otherwise need the same logic.
- Redis makes the limit hold across instances and restarts. A limit kept in memory would reset on each restart and would not be shared.
- Fail open keeps logins working during a Redis outage. Account lockout in auth-service still limits password guessing (ADR-009).

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| Gateway routes in `application.properties` | A reverse proxy such as Nginx `location` blocks, or a package like Lumen routes that forward requests |
| `GlobalFilter` with `getOrder()` | Global middleware in `$middleware`, run in order |
| `RedisRateLimiter` | `RateLimiter::attempt` with a Redis store |
| `ReactiveJwtDecoder` at the edge | `auth:api` middleware that verifies a token before the controller |
| `CorsConfigurationSource` | The `fruitcake/laravel-cors` package or Laravel's built-in `HandleCors` middleware |
| Swagger aggregate | Several API docs merged into one page |
| `Mono` and `Flux` | Promises or async streams. Blocking code inside a filter stalls every request on that thread |

## Self-check questions (answer before starting M3a)

1. A client sends 500 requests in one second. The burst capacity is 100 and the refill is 2 a second. How many requests succeed in that second, and how many get 429? What happens to the client's next request two seconds later?
2. Redis goes down during a login flood. Name what still limits an attacker who guesses passwords, and what the gateway no longer limits. Why is this acceptable here, and what would change your mind?
3. The gateway forwards `X-Forwarded-For` to auth-service. Explain why the gateway replaces any value the client sent, instead of appending to it. What would an attacker gain from appending?

## Official documentation

- Spring Cloud Gateway: https://docs.spring.io/spring-cloud-gateway/reference/
- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Cloud Gateway rate limiting (token bucket): https://docs.spring.io/spring-cloud-gateway/reference/
- Spring Data Redis: https://docs.spring.io/spring-data/redis/reference/
- Resilience4j and retries are out of scope for M2; they arrive with the synchronous calls in M4.
