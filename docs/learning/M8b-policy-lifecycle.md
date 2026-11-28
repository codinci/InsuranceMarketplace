# M8b: cancellation, refunds, expiry and ShedLock

## What M8b delivers

- **Customer cancellation.** `POST /api/v1/policies/{id}/cancel`. An active policy becomes CANCELLED at once, with cover until the plan's notice period ends. A policy awaiting payment is cancelled with no refund (ADR-021).
- **The refund rule, as data.** `RefundRule` reads the plan's `cancellation` terms from the policy's snapshot: ACACIA refunds the unused days unless six months have passed or a claim was made, BAOBAB never refunds. Same code, different data.
- **A claim counter without a call.** policy-service reads `ClaimSubmitted` from `claim.events` into its own `policy_claims` table.
- **Refunds in payment-service.** `PolicyCancelled` now carries `refundAmount`; payment-service pays it through the mock provider, announces `RefundSucceeded` or `RefundFailed`, and an admin can retry a failed refund.
- **Expiry.** A job expires active policies the day after their end date and sends `PolicyExpired`. claim-service keeps the policy as EXPIRED and still accepts claims for incidents during cover (ADR-022).
- **ShedLock.** policy-service's two scheduled jobs take a database lock, so two instances never run the same job at once.
- **Smoke script:** cancel with a refund that is declined, an admin retry, an expiry, and the new notifications.

## New concepts

**A distributed lock (ShedLock).** `@Scheduled` runs a method on a timer inside one JVM. With two instances of the service there are two timers, so the job runs twice. ShedLock adds a row per job in a `shedlock` table. Before running, an instance tries to update that row to "locked by me until T". Only one update can succeed while the lock is live; the other instance skips its run. In Laravel this is `->onOneServer()` on a scheduled command, which uses the cache as the lock store; here the lock store is the service's own PostgreSQL database.

**`lockAtMostFor` and `lockAtLeastFor`.** The first is a safety net: if the instance holding the lock crashes, the lock frees itself after that time (10 minutes here). The second keeps the lock for a minimum time even if the job finished quickly (30 seconds), so a second instance whose timer fires a moment later does not run the job again straight away.

**Database time and time zones.** We asked ShedLock to use the database's clock (`usingDbTime`) so instances with slightly different clocks still agree. ShedLock then writes UTC times *without* a zone. Our first migration used `TIMESTAMP WITH TIME ZONE`, so PostgreSQL read ShedLock's zone-less UTC "now" as local time in a session on UTC+3 (the business zone). ShedLock's idea of "now" was then three hours behind the real lock times, and a lock that had run out still looked held. The ShedLock test caught it, and ShedLock's documented table uses plain `TIMESTAMP`. The lesson: when two systems exchange times, both must agree on whether a zone is attached.

**Annotations work through a proxy.** `@SchedulerLock` (like `@Transactional`) is applied by Spring wrapping the bean in a proxy. The lock is taken only when the call comes through that proxy, from outside the bean. That is why the tests call `expireDue(day)` directly to test the job's logic without a lock, and call `run()` to test the lock. A method calling another method of the same object skips the proxy; Laravel has no direct equivalent, but it is similar to middleware that only runs on routed requests, not when you call the controller method yourself.

**`Propagation.MANDATORY`.** `RefundService.refund(...)` must run inside a transaction that already exists, the one that also writes the inbox row in `PaymentExpiryService`. `MANDATORY` makes Spring throw if someone calls it without one, instead of quietly starting a new transaction that could commit the refund without the inbox row.

**Money division in `BigDecimal`.** `premium × unusedDays / coverDays` cannot be exact (36500 / 365 is fine, 10000 / 366 is not). `divide(divisor, 2, RoundingMode.HALF_UP)` says how many decimals to keep and how to round; without them, `divide` throws when the result has no exact decimal form. Multiply first, divide last, so the rounding happens once.

**A parameter object as a record.** The refund rule needs eight inputs. Instead of a method with eight parameters, it takes one `Cancellation` record. Eight values of similar types (three dates in a row) are easy to pass in the wrong order; a record gives the group a name and keeps the call readable. Laravel developers do the same with a small data object instead of a long argument list.

**Parameterised tests.** `@ParameterizedTest` with `@CsvSource` runs one test method once per row of data: request date, effective date, expected refund. It is the JUnit form of a PHPUnit data provider.

**Widening a CHECK constraint with Flyway.** A status or reference type is guarded by a database `CHECK (x IN (...))`. A new value means a new migration that drops the constraint and adds it again with the longer list. The Java enum alone is not enough: the database would reject the row.

**A SpotBugs heuristic.** SpotBugs decides whether a class is "mutable" partly by method names: a method starting with `set` looks like a setter. A private method named `settle` made `PaymentExpiryService` look mutable, and the listener that stores it was flagged. Renaming it was the fix, following the project rule to fix findings rather than suppress them.

## Decisions to remember

- **The service that owns the terms computes the refund.** policy-service has the plan's rule and the claim count; payment-service only moves money and never refunds more than it received.
- **Any submitted claim blocks a refund under `noRefundIfClaims`.** Counting only approved claims would allow a refund and a payout for the same period.
- **CANCELLED now, cover until the notice ends.** No pending state and no second job; claim-service already understood an effective date.
- **The refund travels in the event.** `refundAmount` is an additive field (ADR-020), so the cancellation works while payment-service is down.
- **One refund per policy** by the unique `(reference_type, reference_id)`, and the event id as the idempotency key, the same pattern as payouts.
- **The claim count can lag** by the time an event takes to arrive. Parked.
- **An expired policy still covers its past.** Expiry ends cover for the future; it does not erase it.

## Self-check (answer before M9 starts)

1. A holder of an ACACIA policy that started four months ago cancels today. Their only claim was submitted and then rejected. What refund do they get, why, and which part of the system knows about the claim?
2. `PolicyCancelled` with a refund is delivered to payment-service twice with the same event id, and later once more with a new event id. How many refunds are made, and which mechanism stops each extra one?
3. Why does `RefundService.refund` use `Propagation.MANDATORY`, and what could go wrong with the default `REQUIRED` if someone later called it from a method without a transaction?
4. Two policy-service instances start at the same moment and both run the expiry job at start-up. Walk through what ShedLock does, and say why the tests call `expireDue(day)` for the job's logic but `run()` for the lock.
5. A policy's cover ended yesterday and it is now EXPIRED. The holder files a claim for an incident from last month. Does the in-force check pass, and why was expiry designed that way?
