# ADR-022: policy expiry and ShedLock for scheduled jobs

- **Status:** Accepted (2026-11-26). Implemented in M8b.
- **Context:** `PolicyStatus.EXPIRED` existed but was never set, and `PolicyExpired` was planned since PLAN 10. policy-service's unpaid-policy job (M4b) ran on every instance with nothing to stop two instances running it at once; PLAN 11 named ShedLock for that and M4b deferred it.

## Decision

1. **An expiry job in policy-service.** `PolicyExpiryJob` expires ACTIVE policies whose `endDate` is before today in the business calendar, so a policy is still ACTIVE on its last day and EXPIRED the day after. It has the unpaid job's shape: batches of 100, one transaction per policy, an optimistic-lock conflict skipped (a cancellation at the same moment wins). Each expiry bumps `policyVersion` and writes `PolicyExpired` through the outbox. It runs at start-up and then every `policy.expiry.check-delay-ms` (one hour by default).
2. **claim-service keeps expired policies.** The read model gains `EXPIRED`, applied by `policyVersion` like the other events. The in-force check treats an expired policy like an active one for incidents between cover start and end, so a late claim for something that happened during cover is still possible within the filing deadline.
3. **ShedLock for policy-service's two jobs.** `shedlock-spring` and `shedlock-provider-jdbc-template` 7.11.0, the newest release built against Spring Framework 7.0.10, which Spring Boot 4.1.2 manages. Each job's `run()` carries `@SchedulerLock` (at most 10 minutes, at least 30 seconds). Locks live in the service's own `shedlock` table and use the database clock (`usingDbTime`), so instances with skewed clocks agree.
4. **The `shedlock` columns are `TIMESTAMP` without a zone.** In database-time mode ShedLock writes and compares UTC timestamps without a zone. With a `TIMESTAMP WITH TIME ZONE` column, PostgreSQL converted those values using the session time zone, so ShedLock's "now" and a lock time written by anything else disagreed by the session's UTC offset (three hours in a session on UTC+3, the business zone): a lock that had run out still looked held. `ShedLockIT` caught it; ShedLock's documented PostgreSQL table uses plain `TIMESTAMP`.
5. **The outbox relay keeps SKIP LOCKED.** It already claims rows safely on several instances (ADR-013) and runs every second; a single global lock would only slow it.
6. **Tests call the job's work method, not its schedule.** `expireDue(LocalDate)` and `cancelOverdue(Instant)` take the day or instant as a parameter. The locked `run()` is tested separately by writing another instance's lock row.

## Consequences

- A second policy-service instance is now safe for the scheduled jobs. Other services have no scheduled jobs besides the relay.
- A crashed instance holds a lock for at most ten minutes; the next run after that proceeds. No manual cleanup is needed.
- The expiry and cancellation paths can race on one policy; the `@Version` column makes one of them fail and be retried or skipped, never both applied.

## Alternatives rejected

- **Run the jobs on one instance only (configuration).** Simple, but it needs every deployment to remember which instance, and a lost instance stops the jobs.
- **ShedLock 7.12.0.** Built against Spring Framework 7.1, ahead of the 7.0 line Boot 4.1.2 manages. Rejected until Boot moves.
- **Expire policies lazily when read.** No event would be sent at the end of cover, so claim-service and notification-service would never hear about it.
