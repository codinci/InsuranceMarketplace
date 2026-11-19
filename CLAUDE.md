# CLAUDE.md: Mini Insurance Platform

Spring Boot microservices for multi-insurer medical insurance: browse plans, quote, buy, claim, get paid. See PLAN.md for the journey, the dynamic catalogue, domain model, rules, quality gates and milestones. PLAN.md wins if anything here conflicts.

## Stack
- Java 21 (LTS), Maven multi-module, Spring Boot (latest stable; pin in the parent POM; check springdoc and Spring Cloud compatibility before pinning)
- Spring Cloud Gateway (api-gateway), Spring Web, Spring Security (OAuth2 resource server, JWT), Spring Data JPA, Flyway, Bean Validation
- Spring Kafka (JSON, shared event envelope), Resilience4j for sync calls
- AWS SDK v2 S3 client against SeaweedFS (path-style, custom endpoint)
- springdoc-openapi, a JSON Schema validator for plan definitions
- PostgreSQL (jsonb for plan definitions and policy snapshots), Kafka (KRaft), SeaweedFS, Docker Compose
- Quality: Spotless (google-java-format), Checkstyle, SpotBugs, Maven Enforcer, ArchUnit, JaCoCo, Hadolint
- Tests: JUnit 5, Mockito, Testcontainers
- CI: GitHub Actions

## Layout
```
/pom.xml            parent (holds all quality plugin config)
/common-events      event contracts only
/common-starter     cross-cutting only (error format, correlation ID, logging, role constants). No domain code
/infra-tests        test-only module: Testcontainers smoke tests for Postgres, Kafka and SeaweedFS (M0b). No production code
/api-gateway  /auth-service  /policy-service  /claim-service  /payment-service  /notification-service
/docker-compose.yml  /scripts (verify.sh, smoke.sh)  /docs (adr, learning, parking-lot.md, events.md, catalogue-schema.md, runbook.md, openapi)
/reference/brochures   PRIVATE and git-ignored. Never commit, copy or quote from these PDFs
```
Inside each service: `controller`, `service`, `domain`, `repository`, `dto`, `config`, `messaging`, `exception`.
policy-service has two top-level packages, `catalogue` and `policy`. They talk only through interfaces and never touch each other's entities or tables (ADR-001).

## Commands
- Infra only: `docker compose --profile infra up -d`
- Everything: `docker compose --profile full up --build`
- **Full gate: `./scripts/verify.sh`** (format, style, static analysis, ArchUnit, unit and integration tests, coverage, OpenAPI diff, Hadolint, and from M4 the smoke script)
- Build and test all: `./mvnw clean verify`
- One service: `./mvnw -pl policy-service -am verify`
- Auto-format: `./mvnw spotless:apply`
- Swagger (gateway aggregate): http://localhost:8080/swagger-ui.html

## Quality rules (non-negotiable)
- **Never say a task or milestone is done without running `./scripts/verify.sh` and showing its result.** "Tests pass" with no output doesn't count.
- A failing gate is fixed, never bypassed. No `--no-verify`, no `@Disabled` tests, no lowering coverage thresholds, no new SpotBugs or Checkstyle suppressions without a comment and an ADR.
- Write the tests with the code, not after. Each milestone's required tests are listed in PLAN.md section 17.
- Coverage minimums: 90% line and 85% branch on `rating`, `rules`, `adjudication` and `statemachine` packages; 70% line per module.
- Hooks: format changed Java files after each edit; run the fast check before commit. Set them up in `.claude/settings.json` and git hooks following the current Claude Code docs (M0a).
- CI (`scripts/verify.sh` plus the dependency scan) must be green before merge. Branch protection enforces it.

## Domain rules
- One database per service. Never read another service's tables. Share only event contracts.
- **The catalogue is data.** A plan is a versioned JSON document validated against the JSON Schema and the semantic rules. Published versions are immutable; changes create a new version.
- To support a new insurer or plan, change data (a seed file or the import API), not code. Add code only for a genuinely new rating strategy or rule type, with tests, registration, a schema update and an ADR.
- Claim-service interprets the policy's terms snapshot generically. Never hardcode an insurer, plan, benefit or limit in claim-service.
- Unsupported plan features (`FIXED_SUM` benefits) are rejected by the importer with `UNSUPPORTED_FEATURE`. `preAuthRequired` is stored only.
- Limits have a `scope`: `SHARED` (utilisation `memberId` null) or `PER_MEMBER`. Child limits never exceed their parent.
- Waiting rules: highest `precedence` wins, then the longest; no matching rule means none.
- Money is `BigDecimal`, `NUMERIC(19,2)`, KES, HALF_UP. Never `double` or `float`.
- The server computes premiums and payable amounts. Never trust amounts from the client.
- Policy terms are snapshotted at purchase and never change afterwards.
- Roles: `CUSTOMER`, `ADJUSTER`, `ADMIN` (insurer admin has an `insurerId`; platform admin has none), `SERVICE`. Registration creates only `CUSTOMER`. Never accept a role or `insurerId` from a request body. Use role constants from `common-starter`.
- **Every staff query is scoped by the token's `insurerId`.** Add a cross-insurer isolation test for each staff endpoint.
- Check ownership on every customer resource access (non-owner gets 404) and add a test for each.
- `/internal/**` is never routed by the gateway and requires a `SERVICE` token.
- The claim approval threshold is configuration in claim-service. Above it, only an `ADMIN` can approve.
- Events are published through the **transactional outbox**. Consumers are idempotent through the **inbox** table. Messages are keyed by aggregate id.
- Payment endpoints and payout creation use idempotency keys.
- Claim status changes only through the state-machine class and always write `ClaimStatusHistory`.
- Schema changes only through Flyway. `ddl-auto=validate`.
- Controllers use DTOs, never entities. Validate input. Errors are RFC 7807 `ProblemDetail` with a stable `code`.
- Every endpoint has `@Operation`, `@ApiResponse`s and a security requirement. Update `docs/openapi`, `docs/events.md` and `docs/catalogue-schema.md` with each change.
- No secrets in code or Git. Never log PII or request bodies. Encrypt the national ID.
- Uploads: allowlist types, check magic bytes, 10 MB max, random object keys, presigned URLs.
- Seed data uses fictional insurers only. Never put real insurer names, logos or brochure text in the repo.
- Constructor injection only.
- Verify any new dependency exists and is compatible before adding it.

## Workflow
1. One milestone at a time from PLAN.md section 17. Use plan mode for anything non-trivial and show the plan before implementing.
2. Small commits. G0 and G1 hooks run automatically. Run `./scripts/verify.sh` before saying anything is done.
3. After each milestone: update PLAN.md status, the OpenAPI docs, the events doc, the catalogue schema doc, the README and the smoke script.
4. Every service milestone also delivers that service's Dockerfile, compose entry and smoke-script steps.
5. Write the milestone's learning note in `docs/learning/` (PLAN.md 20.2). The owner is new to Java and Spring: explain each new Java, Spring, Maven, Kafka or Docker concept the first time it appears, say why this approach was chosen, and relate it to equivalents in frameworks the owner already knows (for example Laravel) where that helps. Put explanations in chat and the learning note, not in code comments. Ask the owner the note's self-check questions before starting the next milestone.
6. If reality conflicts with PLAN.md, stop, explain and propose a plan edit. Don't silently deviate. Plan edits are separate commits prefixed `plan:`.
7. Only the next two milestones are detailed. Before starting a milestone, re-read it in plan mode with the owner and refine it.
8. Ideas outside the current milestone go in `docs/parking-lot.md`, not into the code.
9. The frontend, hosting and deployment are out of scope. Keep the API frontend-ready: CORS configurable at the gateway, stable error codes, pagination, OpenAPI.
10. Give extra review to the expensive-to-change decisions in PLAN.md 20.6: the event envelope, the JWT claims, the policy terms snapshot shape, topic design and the claim status machine.