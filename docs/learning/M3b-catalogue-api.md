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

## Official documentation

- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- Spring Security resource server (JWT): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Testcontainers for Java: https://java.testcontainers.org/
- PostgreSQL triggers: https://www.postgresql.org/docs/current/plpgsql-trigger.html
