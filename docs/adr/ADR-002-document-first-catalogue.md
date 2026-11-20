# ADR-002: A plan is a validated, versioned JSON document

- **Status:** Accepted (2026-11-20). Model and validation implemented in M3a; storage, import and publishing in M3b.
- **Context:** Insurers structure plans differently: per-member or shared limits, nested sub-limits, age-band or family-size pricing, different waiting rules (PLAN 6). Supporting a new insurer must be a data change, not a code change (CLAUDE.md). A relational model of every block would need a migration for each new shape, and a policy must keep the terms it was sold even after the plan changes.

## Decision

1. **The whole plan is one JSON document** (`schemaVersion: 1`), stored as `jsonb` in M3b. Java records in `catalogue.domain` mirror it. Rating configuration stays raw JSON, read by the strategy that owns it (ADR-003).
2. **Two validation layers.** A JSON Schema (draft 2020-12, `additionalProperties: false` everywhere) checks structure. Semantic rules check what a schema cannot, with stable violation codes and JSON Pointer paths, all reported at once (docs/catalogue-schema.md).
3. **Decimals are read as `BigDecimal`** (`USE_BIG_DECIMAL_FOR_FLOATS`), and unknown fields fail when the records are read, so the records and the schema cannot drift apart.
4. **Versions are immutable once published** (enforced in M3b). A change creates a new version. Policies snapshot the terms they use (PLAN 8.2).
5. **Limit semantics (D32).** `PER_CLAIM` caps each claim with no running total and cannot have `maxCount`. A child limit may not exceed its nearest limited ancestor. A `PER_MEMBER` child may sit under a `SHARED` parent; a `SHARED` child under a `PER_MEMBER` parent is rejected.
6. **Waiting-rule semantics (D31).** Every field in `when` must match; inside a list any entry matches; a `benefits` entry also matches its descendants. Highest precedence wins, then the longest; no match means no waiting period.
7. **Claim-time logic lives in claim-service.** The waiting-rule resolver (M5) and the limit evaluator (M6) read the policy's terms snapshot there. Services share only event contracts, so policy-service does not ship that code; docs/catalogue-schema.md is the shared definition both services follow.
8. **Supported in the schema but rejected:** `FIXED_SUM` benefits (`UNSUPPORTED_FEATURE`, D18). `preAuthRequired` is stored only (D2).

## Consequences

- A new insurer on known building blocks is one JSON file (the M3b acceptance test).
- The schema and the semantic rules are the contract for plan authors and the importer. Changing them needs care, because published plans must stay valid.
- The D31 and D32 rules are implemented twice: validated here, applied in claim-service. Both sides test against the same written definition, and the worked examples in docs/catalogue-schema.md become test cases in M5 and M6.
- Queries inside plan documents (for example "all plans with maternity") need `jsonb` operators or the browse DTOs mapped from the document.

## Alternatives considered

- **Fully relational plan tables.** Rejected: every new structure needs a migration and code, which defeats "the catalogue is data".
- **A shared library with the claim-time rules.** Rejected for v1: it breaks the "share only event contracts" rule. Revisit if the two implementations drift.
- **Schema validation only.** Rejected: rules such as contiguous age bands or child-not-above-parent limits cannot be expressed cleanly in JSON Schema.
