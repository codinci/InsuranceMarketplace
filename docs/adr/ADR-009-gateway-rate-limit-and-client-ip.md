# ADR-009: Gateway rate limit, client address and outage behaviour

- **Status:** Accepted (2026-11-20). Implemented in M2.
- **Context:** PLAN 12 and 17 (M2) require rate limiting at the gateway. The M1 login limiter (ADR-006, auth-service) is in memory and never pruned (parking lot, M1). Behind the gateway, every request reaches auth-service from the gateway's address, so a per-IP limit in auth-service would see one client for everyone. The gateway must limit each client, keep the limit across its instances, and keep working when its shared store fails.

## Decision

1. **Token bucket per client IP, enforced at the gateway** with Spring Cloud Gateway's `RedisRateLimiter`. Settings are data in `application.properties`: 2 requests per second sustained, bursts of 100 (`gateway.rate-limit.*`). A limited request gets HTTP 429 with the stable code `RATE_LIMITED` in RFC 7807 format and the `X-RateLimit-*` headers.
2. **Redis is the shared store.** Its buckets survive a gateway restart and are shared by every gateway instance. It runs in compose with a password, `maxmemory 64mb` and `volatile-lru`, and is never published to the host (PLAN 12). Its image tag matches the one tested in `infra-tests` (`RedisSmokeIT`).
3. **Fail open.** When Redis cannot be reached, the limiter allows the request. Logins keep working during a Redis outage, and auth-service's account lockout (ADR-006) still limits password guessing. `RedisOutageIT` checks that requests keep flowing when Redis is stopped.
4. **Client address comes from the real connection.** `ClientIpFilter` takes the address of the TCP peer. `ClientIpHeaderFilter` sets `X-Forwarded-For` on the outgoing request, replacing anything the client sent. The gateway's `x-forwarded.enabled` is off, so the client cannot pick its own forwarded address. auth-service uses `server.forward-headers-strategy=native`, so behind the gateway its per-IP login limit sees the real client.
5. **The gateway does not replace service-level limits.** auth-service keeps its own login limit and lockout. The gateway limit protects every route from floods. It does not decide who may log in.

## Consequences

- A Redis outage removes the rate limit for its duration. This is accepted: a flood during an outage is limited only by the services' own limits and the lockout. A monitoring alert on Redis health is needed before public launch (M9).
- Redis adds a container and a password to `.env` (`REDIS_PASSWORD`). The password is never in code.
- The bucket map lives in Redis with `volatile-lru`, so memory is bounded. The in-memory map from M1 is no longer used by the gateway. auth-service's login limiter is still in memory, so its parking-lot entry (eviction or a shared store) stays open.
- Limits are per IP. Clients behind one NAT share a bucket. This is accepted for the MVP and is revisited with the security checklist (M9).

## Alternatives considered

- **Limit in each service only.** Rejected: it would not stop floods to routes that have no limit of their own, and each service would need the client address from the gateway.
- **Limit in memory at the gateway.** Rejected: each gateway instance would have its own counts, and a restart would reset them.
- **Fail closed on Redis outage.** Rejected: a store outage would stop every request, including logins that customers need.
- **Trust `X-Forwarded-For` from the client.** Rejected: any client could bypass the limit by changing the header.
