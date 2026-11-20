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

## Corrected answers

1. **Answered by the owner:** 20% of the requests are allowed and the rest fail. The request after 2 seconds succeeds.
   **Corrected version:** The bucket starts full with 100 tokens. The first 100 requests take them all, and the refill adds about 2 more during that second, so about 100 to 102 succeed (about 20%). The other 398 to 400 get HTTP 429 with code `RATE_LIMITED`. The refill is worked out from the time since the last request, so two seconds later the bucket holds about 4 tokens and the next request succeeds. The client can then send about 2 requests a second without being refused, and the bucket only fills back to 100 after about 50 seconds without requests.

   The owner's answer was correct. The corrected version adds the exact counts and how the bucket refills.

2. **Answered by the owner:** Password encryption protects against attackers because it is computationally expensive. This is a non-production application, which is why it is permissible.
   **Corrected version:** When Redis is down, auth-service still limits password guessing in two ways. Account lockout counts failed logins in Postgres and locks the account for a while (ADR-006). auth-service's own login rate limit (Bucket4j, per IP and per email) still runs, because it lives in memory and does not use Redis. BCrypt makes each guess slow, but it is hashing, not encryption, and it does not limit how many guesses are made. What the gateway stops limiting is flood protection for every route: any client can send as many requests as it likes until Redis is back. This is acceptable in production too, not only because the app is not live. An outage is rare and short, logins must keep working, and lockout still stops guessing. Failing closed would turn a Redis outage into a full outage. I would change my mind if an attacker could take Redis down on purpose, if outages were long or frequent, or if a route behind the gateway had no limit of its own and was expensive to run, for example payments. The fix then is a small in-memory fallback limit at the gateway, or failing closed only on those routes.

   The owner's answer named a real protection but not the main one, called hashing encryption, and did not say what the gateway stops limiting or what would change the decision. The reason given (non-production) is not the reason in ADR-009.

3. **Answered by the owner:** With appending, if auth-service blindly accepts the request it may pick the first URL. The attacker could then evade IP-based blocking, or poison the security logs so requests seem to come from the wrong address.
   **Corrected version:** With appending, a client that sends `X-Forwarded-For: 203.0.113.9` would reach auth-service with `203.0.113.9, <real address>`. Any reader that takes the first entry (an address, not a URL) gets the value the attacker chose. The attacker can then send a new fake address on each request, so every guess gets a fresh per-IP login bucket and the limit never triggers. They can also put a victim's address in the header to get that victim rate-limited, and they can write false addresses into the security logs. Replacing the header means it holds one value, set by the gateway from the real connection, so there is nothing for a reader to choose between. There is one more reason specific to this setup. Tomcat (`forward-headers-strategy=native`) reads the list from the right and skips addresses on private networks, because it trusts them as proxies. Inside compose the real client address is private, so with appending Tomcat would skip it and land on the attacker's entry. `GatewayIT` (`aClientCannotSetTheAddressTheServicesSee`) and `ClientIpIT` check this.

   The owner's answer was correct on both gains: evading the IP limit and poisoning the logs. The corrected version adds framing a victim and the Tomcat detail.

## Summary of the self-check

All three questions have corrected answers. Question 1 was correct. Question 2 named a real protection but not the main one (lockout), and gave the wrong reason. Question 3 was correct.

## Official documentation

- Spring Cloud Gateway: https://docs.spring.io/spring-cloud-gateway/reference/
- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Cloud Gateway rate limiting (token bucket): https://docs.spring.io/spring-cloud-gateway/reference/
- Spring Data Redis: https://docs.spring.io/spring-data/redis/reference/
- Resilience4j and retries are out of scope for M2; they arrive with the synchronous calls in M4.
