# M6: Adjudication, limits and staff access

## What M6 delivers

- **A staff side to claim-service.** `GET /api/v1/claims/queue` and `POST /claims/{id}/start-review`, `/request-info`, `/approve` and `/reject`. Staff also read a claim, its history and its documents. Everything is scoped by the insurer in the token. A platform admin sees every insurer.
- **The limit evaluator** (`adjudication` package, pure functions): the benefit chain from the module root to the claimed benefit, each limit capping the amount, then the co-pay of the deepest node that has one. Both worked examples from the PLAN are tests.
- **Running totals.** `benefit_utilisation` holds what has been used against each limit. Approval reads the totals, evaluates, and reserves the approved amount in one transaction. A version column stops two approvals overwriting each other.
- **Approval rules.** The reviewer may approve less (with a reason) but never more than the payable amount. Above the configured threshold only an admin may approve. Nobody reviews their own claim.
- **Soft flags.** Possible duplicate, high amount, early in cover and frequent claims are raised at submission, shown to reviewers, hidden from customers, and put a claim first in the queue.
- **The customer's answer** to an information request: `POST /claims/{id}/respond`.
- **Three events** through the outbox: `ClaimApproved`, `ClaimRejected`, `ClaimInfoRequested`.
- **An audit log** of every staff action, successful or refused, with identifiers and codes only.
- **A reservation release function**, built and tested now and wired in M7.

## New concepts

**Optimistic locking, and why a passing test can prove nothing.** A running total is read, changed and written back. If two requests read the same total, the second write silently erases the first: a *lost update*. `@Version` adds `WHERE version = ?` to the update, so the second writer finds the version has moved and fails; the service then starts again from the new total. The lesson of this milestone is about the test, not the code. The first concurrency test passed even with `@Version` removed. On a policy's first approval both requests try to create the row, and the database's unique index makes one wait for the other, so the race never happened. We found out by removing `@Version` on purpose and re-running (a *mutation check*: break the code and see whether a test notices). Two fixes followed: a test that interleaves two transactions by hand (deterministic, always fails without the version), and a race test that creates the row first. With `@Version` removed, the race paid 4,000 against a 3,000 limit. A test you have never seen fail is a guess.

**Programmatic transactions and retry.** `@Transactional` wraps a whole method. Approval needs a *retry* around the transaction: on a version conflict, throw the work away and run the whole transaction again. A retry cannot live inside the transaction it retries, so the service uses `TransactionOperations.execute(...)` in a loop with three attempts. Each attempt is a new transaction with fresh reads. Laravel's `DB::transaction($callback, $attempts)` does the same thing and retries on deadlocks.

**Self-invocation skips the proxy.** Spring applies `@Transactional` by wrapping the bean. A method that calls another `@Transactional` method *on the same object* goes around the wrapper, so no transaction starts. I wrote exactly that in `startReview` and caught it on review: the annotation moved to the method the caller actually calls.

**A pure evaluator.** `LimitEvaluator` takes the chain, the claimed amount and a map of totals, and returns a result. It never touches a database or a clock. The tests are rows of "this chain, this total, this claim, this payable". The service around it only gathers the totals and saves the outcome. This is the same shape as `HardChecks` in M5b, and it is why the `adjudication` package has 100% line and branch coverage.

**Role-aware reads and the path rules.** One URL, `GET /api/v1/claims/{id}`, serves a customer (their own claim) and a reviewer (their insurer's). `SecurityConfig` lists the staff-only paths first, then the customer-only policy reads, then the shared reads, and the catch-all last. Spring takes the first matching rule, so order is part of the meaning. The *service* then decides which claim each caller can see, through one class (`ClaimLookup`), so there is no second copy of the rule to forget.

**Response shaping.** Soft flags, the payable estimate and the reviewer's id are for staff. `ClaimResponse` has a `forCustomer` and a `forStaff` factory, and the customer's version leaves out the `FLAG` checks and sets the reviewer fields to null. Putting the rule in one place is what stops a later field from leaking by accident.

**Structured audit logging.** The `audit` logger writes one line per staff action using SLF4J's fluent API (`atInfo().addKeyValue("action", ...)`). With JSON console logging, each key becomes a field a log tool can filter on. The test attaches a Logback `ListAppender` to capture the lines, and checks that a refusal's message text, which could contain personal data, never appears. Logs hold identifiers and codes; they never hold names or what someone typed.

**`@Validated` changes which exception you get.** The controllers are annotated `@Validated`, so a bad `?size=0` raises `ConstraintViolationException` (from method validation by proxy), not Spring's `HandlerMethodValidationException`. Our handler only knew the second, so the request came back as a server error. It had been that way since M5a, and the new queue test found it. The other services do not use class-level `@Validated`, so they were not affected.

**Test builders and a shared base class.** The review tests share `AbstractReviewIT`, which builds a policy with the limits under test and walks a claim to any stage (submitted, under review). Each test then reads as the story it checks. A wrong fixture failed nearly every integration test at once: a policy that "started 400 days ago" had also *ended* 36 days ago, so every claim was rightly rejected. The fix was to the fixture, not the code.

## Why this approachYe

- **Reserve the approved amount after the co-pay.** The limit caps what the insurer pays, so the customer's share does not use it up (ADR-017).
- **Refuse, do not auto-reject, when nothing is payable.** A person chooses the reason for a rejection.
- **Re-evaluate at approval.** The figure shown when review starts can be out of date by the time someone approves; the authoritative evaluation happens inside the transaction.
- **A version column, not a lock.** A lock held across the evaluation could deadlock when a chain touches several rows; a short retry cannot.
- **Hide the flags from customers.** They are heuristics for reviewers; showing them would tell a customer exactly what to avoid.

## Self-check (answer before M7 starts)

1. Two reviewers approve two claims on the same shared limit at the same instant, and both read a used total of 500. What stops the limit being exceeded, and what does the second reviewer's request do?
2. Why did the first concurrency test pass with the version column removed, and what did we change so that it fails?
3. A claim is claimed at 1,500 with a fixed co-pay of 300 and a per-visit cap of 2,000. What is payable, and which amount is reserved against an annual limit if the reviewer approves it in full?
4. An adjuster token arrives with no `insurerId`. Why is it refused, when a platform admin token with no `insurerId` is allowed?
5. A refused approval writes an audit line. Which fields does it hold, and which things must it never hold?

## Corrected answers

Answered so far: questions 1 to 4. Question 5 is still open.

1. Each running-total row has a `version` column (optimistic locking). Both approvals read the total as 500 at version N, and both try to save with `WHERE version = N`. The first save succeeds and moves the row to N+1. The second finds no row at version N, so its transaction rolls back and nothing is reserved. The second reviewer's request returns 409 with code `CONFLICT`, the claim stays under review, and the reviewer retries. The retry reads the new total and either approves within the limit or is refused because the limit is now used up. An atomic conditional update (`UPDATE ... SET used = used + :amount WHERE used + :amount <= :limit`) would also work, but this service reads, evaluates the whole benefit chain and then saves, so a version check is the fit. A lock held across the evaluation could deadlock when a chain touches several rows.

2. The requests did run at the same time, on a real PostgreSQL. The test passed without the version column because on a policy's first approval no total row exists yet. Both requests try to insert it, and the unique index makes one wait for the other, so the loser retries on the new total and no update is lost. The race never happened. We found this by removing `@Version` on purpose (a mutation check). Two tests fix it. `UtilisationLockingIT` interleaves two transactions by hand, so both read the same total and the second save is refused: it always fails without the version. `ConcurrentApprovalIT` approves a first claim beforehand so the row exists, then races two approvals; with `@Version` removed it paid 4,000 against a 3,000 limit. A single race run may not collide, so the hand-interleaved test is the proof.

3. Payable is 1,200. The limits cap first: 1,500 is under the 2,000 per-visit cap, so 1,500 is eligible. The fixed co-pay of 300 then comes off, leaving 1,200. A full approval reserves 1,200, the amount after the co-pay, because a limit caps what the insurer pays and the customer's share must not use it up (ADR-017). The amount is reserved on every `PER_YEAR` node of the chain and recorded per claim in `claim_reservations`, so it can be released exactly.

4. Every staff query is filtered by the `insurerId` in the token. An adjuster token with no `insurerId` has no insurer to filter by, and treating "no insurer" as "no filter" would show that adjuster every insurer's claims. So the request is refused with `STAFF_SCOPE_MISSING`, and a missing claim can never widen an adjuster's reach. A platform admin (an ADMIN with no `insurerId`) is deliberately allowed to see every insurer, so there the absence is meaningful, not missing. A claim of another insurer answers 404, the same as a claim that does not exist.
