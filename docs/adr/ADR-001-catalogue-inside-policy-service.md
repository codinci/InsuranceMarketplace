# ADR-001: The catalogue lives inside policy-service

- **Status:** Accepted (2026-11-20). Implemented from M3a.
- **Context:** Insurers, plans and rating (the catalogue) are read on every quote and purchase. Policies snapshot catalogue terms at purchase (PLAN 8.2). A separate catalogue service would add a network hop, a second database and a failure mode to every quote, for a team of one. The two still have different reasons to change, so they must stay separable.

## Decision

1. **One service, two top-level packages.** `com.example.insurance.policyservice.catalogue` (insurers, products, plans, validation, rating) and `com.example.insurance.policyservice.policy` (holders, quotes, policies, members, lifecycle; from M4).
2. **They talk only through interfaces.** `policy` calls catalogue services such as `PremiumCalculator` and, from M3b, a plan lookup. It never uses catalogue entities, repositories or tables. `catalogue` never depends on `policy`.
3. **Separate tables** in `policy_db`, with no foreign keys across the two packages. A policy holds the plan id and version and its own terms snapshot, not a reference into catalogue tables.
4. **ArchUnit enforces the boundary** (`PolicyServiceArchitectureTest`): no `catalogue` class depends on `policy`, and no `policy` class depends on `catalogue.domain` or `catalogue.repository`.

## Consequences

- A quote prices in one process, with no remote call.
- Extracting the catalogue later means moving one package and its tables, and replacing the interface calls with HTTP calls. Nothing else changes.
- The boundary is only as strong as the ArchUnit rules. Every new package in policy-service must fit one of the two sides.

## Alternatives considered

- **A separate catalogue-service.** Rejected for now: one more service, database and network call on every quote, with no team boundary to justify it.
- **One package with shared entities.** Rejected: the catalogue could then never be extracted, and policies could read catalogue rows that change after purchase.
