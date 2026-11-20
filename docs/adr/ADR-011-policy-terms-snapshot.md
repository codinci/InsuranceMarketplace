# ADR-011: The policy terms snapshot

- **Status:** Accepted (2026-11-21). Implemented in M4a; published in `PolicyActivated` from M4b.
- **Context:** A policy must keep the terms it was sold with (CLAUDE.md, PLAN 8.2). claim-service interprets those terms generically to verify and adjudicate claims (M5, M6), and must never hardcode an insurer or plan. PLAN 20.6 names the snapshot shape as expensive to change: policies already sold keep the old shape forever.

## Decision

1. **What is frozen.** At purchase, policy-service stores a `TermsSnapshot` with the plan reference (insurer code, plan id, code and version), currency, age basis, **only the selected module options** with their full benefit trees (limits, co-pay, required documents, `preAuthRequired`), all waiting rules, exclusions, the filing deadline and the cancellation rule. Rating tables are not included: the premium is already fixed on the policy.
2. **Where it is defined.** The records live in `common-events` (`com.example.insurance.events.policy`), because the snapshot is an event contract: `PolicyActivated` (M4b) carries it to claim-service. It is not shared domain logic; both services keep their own code.
3. **Neutral types.** Enum values are strings and money is `BigDecimal`, so a consumer needs no catalogue types. Semantics are defined in docs/catalogue-schema.md (D31 waiting rules, D32 limits).
4. **Versioned.** `snapshotVersion` is 1. Changes are additive only (ADR-005): new fields must be nullable wrapper types, so older stored snapshots still read. A non-additive change needs a new `snapshotVersion`, and consumers must handle every version still held by live policies.
5. **Built by the catalogue, through its API.** `CatalogueApi.termsSnapshot` builds it from the immutable plan version (ADR-010), so the snapshot of a quote bought after its plan was retired is still exact.
6. **Stored as `jsonb`** on the policy and returned by `GET /policies/{id}`. A contract test in `common-events` locks the JSON field names.

## Consequences

- claim-service can verify and adjudicate any insurer's policy without knowing the plan.
- A plan can be changed or retired at any time; sold policies are unaffected.
- Snapshots are larger than a reference. That is the price of independence and is small next to the claim data they serve.
- Adding a building block to the catalogue (a new limit semantic or rule) usually needs a snapshot field too, and the consumer's handling of it.

## Alternatives considered

- **Store only the plan id and version, and look up terms when needed.** Rejected: claim-service would need catalogue access at claim time, coupling the services and the databases.
- **Copy the whole plan document.** Rejected: it carries unselected options and rating tables, and ties consumers to the catalogue's document shape, which evolves independently.
- **Define the snapshot inside policy-service only.** Rejected: claim-service would have to copy the classes, and the two copies could drift.
