# ADR-010: Plan lifecycle, immutability and staff scoping

- **Status:** Accepted (2026-11-21). Implemented in M3b.
- **Context:** ADR-002 makes a plan a versioned document that is immutable once published. M3b stores plans, lets admins import and publish them, and shows them to customers. Policies (M4) will snapshot the terms of the version they buy, so a published version must never change underneath them. Admins of one insurer must never see or change another insurer's plans (CLAUDE.md).

## Decision

1. **Statuses.** Each plan version is `DRAFT`, then `PUBLISHED`, then `RETIRED`. There is no way back: a published version never returns to draft, and a retired one is never republished.
2. **Import creates or replaces a draft (D34).** Importing a version that does not exist stores it as `DRAFT`. Importing a version that exists replaces it only while it is `DRAFT`; a `PUBLISHED` or `RETIRED` version answers 409 `PLAN_VERSION_IMMUTABLE`, and the author imports a new version number.
3. **Publishing retires the older version (D33).** Publishing a draft retires the published version of the same plan (same product and plan code) in the same transaction, so at most one version of a plan is on sale. Publishing an older version while a newer one is published answers 409 `NEWER_VERSION_PUBLISHED`.
4. **Two layers of immutability.** The service refuses changes first. In the database, a trigger refuses any change to the terms (`definition`, codes, version, sale period, currency) of a row that is not `DRAFT`, refuses deleting such a row, and refuses the backward status moves. A partial unique index allows one `PUBLISHED` row per plan. A bug or a manual `UPDATE` cannot break the rule.
5. **The public sees only what is on sale.** The browse endpoints list `PUBLISHED` plans whose sale period includes today, and show the terms of `PUBLISHED` plans only. Drafts and retired plans answer 404. Rating tables are not shown; prices come from quotes (M4).
6. **Staff scoping in the service layer.** `StaffScope` is built from the token: no `insurerId` means platform admin (every insurer), otherwise one insurer. Another insurer's insurer or plan answers **404**, not 403, so its existence is not revealed. Creating insurers needs a platform admin (403 `PLATFORM_ADMIN_REQUIRED`).
7. **Seeds use the same path.** The startup seeder (compose only) imports and publishes through the same services, so a seed file meets exactly the rules an admin's import does, and an invalid seed stops the service from starting.

## Consequences

- A policy can store just the plan id and version plus its own snapshot, knowing the version's terms never change.
- Fixing a typo in a published plan means a new version; the old one is retired automatically when the new one is published. Quotes already priced on the old version are handled in M4 (they keep their plan version).
- Staff of one insurer get 404 for another insurer's plans, which tests check endpoint by endpoint (`CatalogueIsolationIT`).
- auth-service cannot check that a staff `insurerId` exists in policy-service (parking lot).

## Alternatives considered

- **Keep several versions published and let browse pick the newest.** Rejected (D33): two versions of one plan could be quoted at once.
- **Always require a new version number, even for drafts.** Rejected (D34): every typo in a draft would burn a version number.
- **Service checks only, no trigger.** Rejected: immutability of sold terms is the expensive-to-break rule; the database is the last line of defence.
- **403 for another insurer's resources.** Rejected: it confirms that the resource exists.
