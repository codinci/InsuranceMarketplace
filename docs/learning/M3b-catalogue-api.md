# M3b: Catalogue API, persistence and seeds

## What M3b delivers

- policy-service stores the catalogue in `policy_db`. Insurers, products and plan versions are tables, and each plan version keeps its whole document as `jsonb`.
- An admin API to create insurers, import plan documents (with a dry run), publish and retire them, all scoped to the admin's insurer.
- A public API to browse insurers, plans on sale and a plan's terms.
- Two fictional insurers with two plans each, seeded at startup in compose. A test onboards a third insurer from a single JSON file.
- policy-service's Dockerfile, its compose entry, the gateway route for its Swagger spec, and smoke steps.
- The shared error format moved into common-starter. ADR-010 records the plan lifecycle.

## New concepts

**JPA entities and repositories.** `@Entity` marks a class that maps to a table, and `@Id` its primary key. A repository is an interface such as `PlanVersionRepository extends JpaRepository<PlanVersion, UUID>`. Spring writes the class for you, and a method name like `findByProductIdAndCodeAndPlanVersion` becomes a query. `@Query` holds JPQL (queries over entities, not tables) when a name would be unreadable. `@Version` adds optimistic locking: two saves of the same row from stale copies make the second one fail instead of silently overwriting.

**`@Transactional`.** Everything in the method commits together or not at all. Publishing retires the old version and publishes the new one in one transaction, so nobody ever sees two published versions or none. `saveAndFlush` sends the SQL immediately, inside the transaction. Here that matters because the database checks "one published version" row by row.

**Flyway and `ddl-auto=validate`.** `V1__catalogue_schema.sql` is the only way the schema changes. At startup, Hibernate compares the entities with the tables and refuses to start if they differ. That is how a `CHAR(3)` column against a `String` field was caught during M3b.

**`jsonb` with Hibernate.** `@JdbcTypeCode(SqlTypes.JSON)` on a `String` field stores it in a `jsonb` column. Postgres validates and normalises the JSON. `@DynamicUpdate` writes only the columns that changed, so publishing never rewrites the document.

**Database triggers.** A function in PL/pgSQL runs before every `UPDATE` or `DELETE` on `plans` and raises an error if a published plan's terms would change. The service checks first; the trigger is the last line of defence.

**Spring Security resource server with JWKS.** policy-service never sees passwords. It reads the `Authorization: Bearer` token, fetches auth-service's public keys from `/.well-known/jwks.json` on the first request, and checks the signature, issuer and expiry. `@AuthenticationPrincipal Jwt jwt` gives the controller the verified token. Roles come from the `roles` claim, and the insurer from `insurerId`.

**Testcontainers and MockMvc.** Integration tests start a real Postgres 17 in Docker (`@ServiceConnection` wires Spring to it). MockMvc calls the controllers through the whole Spring stack, including security and error handling, without opening a port. Test tokens are signed with a key generated per run, and a `@Primary` test decoder trusts it.

**Pagination.** `PageRequest.of(page, size, sort)` asks the repository for one page. `Page` carries the items and the totals, and `PageResponse` is the stable shape clients see.

**`ApplicationRunner`.** A bean whose `run` method executes once after startup. `@ConditionalOnProperty` creates the seeder runner only when `catalogue.seed.enabled=true`.

## Why this approach

- **Documents stay whole** (ADR-002). Storing the validated document avoids a table per building block, and a new insurer is still just data.
- **404 for another insurer's data** (ADR-010). A 403 would confirm the plan exists.
- **The seeder uses the import and publish services.** A seed file gets exactly the checks an admin's import gets, and a bad seed stops the service from starting.
- **Rating tables are not public.** Prices come from quotes in M4, computed on the server (CLAUDE.md).

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `@Entity` and `JpaRepository` | An Eloquent model, with query scopes as method names |
| `@Transactional` | `DB::transaction(fn () => ...)` |
| Flyway `V1__...sql` | A migration in `database/migrations` |
| `ddl-auto=validate` | No direct equivalent; like a test that the models match the schema |
| `jsonb` column with `@JdbcTypeCode` | A `json` column with an `array` or `AsArrayObject` cast |
| Resource server with JWKS | `auth:api` with a JWT guard that verifies with a public key |
| `StaffScope` checks in services | Policies (`Gate::allows`) and a global scope on `insurer_id` |
| `PageResponse` | `->paginate()` and its JSON shape |
| `CatalogueSeeder` with `ApplicationRunner` | A seeder called from `DatabaseSeeder`, run on deploy |
| Testcontainers and MockMvc | `RefreshDatabase` against a real database, and `$this->getJson()` |

## Self-check questions (answer before starting M4)

1. An insurer admin of ACACIA calls `POST /admin/plans/{id}/publish` with the id of a BAOBAB plan. Trace the request through the gateway, the security filter chain and the service, and say which response they get and why it is not 403.
2. Version 1 of a plan is published. The author imports version 1 again with a corrected limit, then imports it as version 2 and publishes. What happens at each step, and what state are both versions in at the end?
3. Someone runs `UPDATE plans SET definition = '{}' WHERE status = 'PUBLISHED'` directly in psql. What stops it, and why is the service check alone not enough?

## Corrected answers

1. **Answered by the owner:** The gateway validates the JWT and routes. The security filter chain authenticates the JWT and discovers the user's role. The service scopes the plan to the tenant, and a Baobab plan is not in Acacia, so it returns 404.
   **Corrected version:**
   - **Gateway:** checks the token's signature, issuer and expiry against auth-service's JWKS, applies the per-IP rate limit, and routes `/api/v1/admin/plans/**` to policy-service. It does not look at roles or insurers.
   - **policy-service filter chain:** validates the token again, independently (design decision 7: every service checks tokens itself and never trusts the gateway alone). It maps the `roles` claim to `ROLE_ADMIN`, and the rule `/api/v1/admin/** hasRole ADMIN` lets the request through.
   - **Controller and service:** the controller builds a `StaffScope` from the token's `insurerId` (ACACIA's id). `PlanLifecycleService` loads the plan and keeps it only if `scope.canSee(plan.insurerId)`. The plan belongs to BAOBAB, so the result is empty and the service answers **404 `PLAN_NOT_FOUND`**, exactly as for an id that does not exist.
   - **Why not 403:** a 403 would tell the caller "this plan exists, but it is not yours", which leaks that another insurer has a plan with that id. With 404, a plan of another insurer and a plan that does not exist look the same (ADR-010).

   The owner's answer was right on the path and the result. It did not say why 404 is chosen over 403, and it missed that policy-service checks the token again rather than trusting the gateway.

2. **Answered by the owner:** Version 1 cannot be published again; it has to be created as a new version. After version 2 is created, version 1 is suppressed.
   **Corrected version:**

   | Step | Request | Result | v1 | v2 |
   |---|---|---|---|---|
   | Start | | | PUBLISHED, on sale | none |
   | Re-import v1 with the corrected limit | `POST .../plans/import` | **409 `PLAN_VERSION_IMMUTABLE`**: v1 is published, so its terms cannot change (D34). Nothing is stored | PUBLISHED, unchanged | none |
   | Import the same document as v2 | `POST .../plans/import` | **201 `CREATED`**: a new DRAFT | PUBLISHED, still on sale | DRAFT, not visible to the public |
   | Publish v2 | `POST /admin/plans/{v2}/publish` | **200**: in one transaction v1 is retired, then v2 is published (D33) | RETIRED | PUBLISHED, on sale |

   At the end v1 is `RETIRED` and v2 is `PUBLISHED`. v1's terms never changed, so any policy sold on v1 keeps exactly what it bought. Customers only ever see one version of the plan on sale.

   The owner's answer was right that a published version cannot change and the fix is a new version. Two corrections: the re-import is refused with 409 rather than "published again", and v1 is retired when v2 is **published**, not when v2 is created. Between import and publish, v1 stays on sale and v2 is only a draft.

3. **Answered by the owner:** The database trigger stops it, because the service can be bypassed.
   **Corrected version:** The `plans_immutable` trigger runs `plans_guard_immutable()` before every `UPDATE` or `DELETE` on `plans`. For a row whose status is not `DRAFT`, a change to `definition` (or the code, version, sale period or currency) raises `plan ... its terms are immutable`. The statement fails, and nothing changes in any row. The same trigger refuses deleting a published or retired plan and moving a status backwards. The service check is not enough because anything that talks to the database directly skips it: psql, a migration, a batch script, or a future code path with a bug. Sold terms are the rule that is most expensive to break, so the database enforces it too (ADR-010). One limit remains: a database superuser can disable the trigger. That is why each service should log in with its own user that has rights only on its own database (parking lot, from M1).

   The owner's answer was correct. The corrected version adds what the trigger checks, what happens to the statement, and the remaining limit.

## Summary of the self-check

All three questions have corrected answers. Question 1 had the path and result right but not why 404 beats 403, nor that policy-service checks the token itself. Question 2 had the rule right but the timing wrong: v1 is retired when v2 is published, and re-importing v1 is refused with 409. Question 3 was correct.

## Official documentation

- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- Spring Security resource server (JWT): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Testcontainers for Java: https://java.testcontainers.org/
- PostgreSQL triggers: https://www.postgresql.org/docs/current/plpgsql-trigger.html
