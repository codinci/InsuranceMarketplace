# ADR-003: Pricing through rating strategies

- **Status:** Accepted (2026-11-20). Implemented in M3a.
- **Context:** Insurers price differently: per member by age band, per family by size (optionally by the principal's age), or a flat amount for eligible members (PLAN 5.3, 6). A new insurer on a known pricing model must need no code; a genuinely new model must be one class, not a change spread across the engine.

## Decision

1. **A strategy interface.** `RatingStrategy<C>` has `model()`, `configType()`, `validate(config, plan, path)` and `price(config, context)`. Each option's `rating.model` selects one. The configuration record `C` is read from the option's raw `rating` JSON, so a new strategy never changes a shared type.
2. **A registry built by Spring.** `RatingStrategyRegistry` receives every `RatingStrategy` bean. Two strategies for one model fail at startup. An unknown model is a violation at validation and `UNKNOWN_RATING_MODEL` at pricing.
3. **Three strategies in v1:** `AGE_BAND_PER_MEMBER`, `FAMILY_SIZE_TIER` (optional principal-age tables, extra amount per member beyond the largest tier) and `FLAT_PER_ELIGIBLE_MEMBER`.
4. **Strategies validate their own configuration** against the plan (contiguous bands covering every eligible age, contiguous tiers, overflow priced), so the semantic validator stays generic.
5. **Premium assembly** (PLAN 8.1): option premiums, then adjustments in order on the running subtotal, then levies on the adjusted subtotal. **Each line is rounded HALF_UP to 2 places, and the annual premium is the sum of the rounded lines**, so a stored breakdown always adds up to the premium.
6. **Rating age.** `AT_START` is completed years; `NEXT_BIRTHDAY` is the age at the next birthday on or after the start date, so a birthday on the start date keeps that day's age. A 29 February birthday falls on 1 March in other years.
7. **Coverage.** `catalogue.rating` and `catalogue.rules` must keep 90% line and 85% branch coverage (a JaCoCo `PACKAGE` rule in policy-service's POM, CLAUDE.md). Expected premiums are golden test data worked out by hand (`golden-premiums.json`).

## Consequences

- A new pricing model is one `@Component` class with tests, a schema entry, golden cases and an ADR (docs/catalogue-schema.md, "Adding a rating strategy").
- Rounding each line can differ by a cent from rounding only the total. This is accepted: an explainable breakdown matters more, and the rule is fixed and tested.
- No `double` or `float` appears in the rating or rules packages; ArchUnit checks fields, parameters and return types.

## Alternatives considered

- **A `switch` on the model in one calculator.** Rejected: every new model edits shared code, and its tests grow without bound.
- **A sealed type hierarchy for rating configuration.** Rejected: a new strategy would have to edit the shared type and the JSON mapping.
- **Rounding only the final total.** Rejected: the stored breakdown would not add up to the premium a customer sees.
