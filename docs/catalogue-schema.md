# Catalogue schema

A plan is a JSON document (PLAN 5). policy-service checks it in two layers:

1. **JSON Schema** (`policy-service/src/main/resources/catalogue/plan-schema.v1.json`, draft 2020-12). Structure, types, enums and required fields. Unknown fields are rejected everywhere (`additionalProperties: false`).
2. **Semantic rules** (`PlanSemanticValidator` and each rating strategy's `validate`). Rules that a schema cannot express. They run only when the schema passes, and every violation is reported, not only the first.

Each violation has a stable `code`, a `path` (a JSON Pointer into the document, for example `/plan/modules/0/options/0/rating/ageBands/2`) and a human message. Clients match on `code`, never on the message.

All figures in this document and in the test fixtures are invented.

## Document shape

```
{
  "schemaVersion": 1,
  "insurer": { "code", "name" },
  "product": { "code", "line": "HEALTH", "name" },
  "plan": {
    "code", "name", "version", "currency": "KES",
    "sale": { "from", "to" | null },
    "ageBasis": "AT_START" | "NEXT_BIRTHDAY",
    "eligibility": { "coverTypes", "principalAge", "spouseAge"?, "childAge"?, "maxChildren" },
    "modules": [ { "code", "mandatory", "options": [ { "code", "benefits", "rating" } ] } ],
    "waitingRules": [ { "code", "days", "precedence"?, "when" } ],
    "exclusions": [ "text" ],
    "claimRules": { "filingDeadlineDays" },
    "cancellation": { "noticeDays", "refund": { "model": "PRO_RATA" | "NONE", "noRefundAfterMonths"?, "noRefundIfClaims"? } },
    "adjustments": [ { "code", "type": "PERCENT" | "FIXED", "value" } ],
    "levies": [ { "code", "type": "PERCENT" | "FIXED", "value" } ]
  }
}
```

| Field | Rules |
|---|---|
| Codes | Upper case letters, digits, `_` and `-`, at most 40 characters, starting with a letter or digit |
| Money | A JSON number, read as `BigDecimal` (never `double`). Limits are above 0 |
| Percent values | Fractions: `0.0025` is 0.25% (D29). Co-pay and levies 0 to 1; adjustments -1 to 1. A negative adjustment is a discount |
| Ages | Whole years, 0 to 120, **in the plan's age basis**. Under `NEXT_BIRTHDAY`, a principal age range of 18 to 70 means a rated age of 18 to 70 |
| `spouseAge` | Optional. When absent, the spouse uses `principalAge` |
| `childAge.maxIfStudent` | Optional higher age for students. Rating tables must cover it |

### Benefit nodes

```
{ "code", "kind"?, "limit"?, "copay"?, "requiredDocuments"?, "preAuthRequired"?, "children"? }
```

- `kind` defaults to `REIMBURSEMENT`. `FIXED_SUM` passes the schema but is rejected with `UNSUPPORTED_FEATURE` (D18).
- A node without a `limit` is covered and limited only by its ancestors.
- `preAuthRequired` is stored only (D2).
- `limit`: `{ "amount", "period": "PER_YEAR" | "PER_CLAIM", "scope": "SHARED" | "PER_MEMBER", "maxPerVisit"?, "maxCount"? }`
- `copay`: `{ "type": "PERCENT" | "FIXED_PER_VISIT", "value" }`

## Limit semantics (D32)

- **`PER_YEAR`** keeps a running total for the policy year, which starts on the policy's start date (D30).
- **`PER_CLAIM`** caps each claim on its own. There is no running total, so `maxCount` cannot be combined with it (`PER_CLAIM_WITH_MAX_COUNT`).
- **Scope.** `SHARED` is one pot for the whole policy (utilisation `memberId` null). `PER_MEMBER` is one pot per member.
- **Nesting.** A child limit is compared with its **nearest limited ancestor**:
  - its `amount` may not exceed the ancestor's (`CHILD_LIMIT_EXCEEDS_PARENT`);
  - a `PER_MEMBER` child under a `SHARED` parent is allowed (for example a per-member imaging count inside a shared family pot);
  - a `SHARED` child under a `PER_MEMBER` parent is rejected (`INVALID_SCOPE_NESTING`), because a family pot cannot sit inside one person's pot.
- `maxPerVisit` may not exceed the limit's `amount` (`MAX_PER_VISIT_EXCEEDS_LIMIT`).

Claim-service applies limits at adjudication (PLAN 9.2, M6). It must follow these semantics exactly.

## Waiting rule semantics (D31)

A waiting rule is `{ "code", "days", "precedence" (default 0), "when" }`, where `when` may hold `cause`, `surgical`, `conditionTags` and `benefits`.

- **Every field present in `when` must match** (AND). An empty `when` matches every claim.
- **Inside a list, any one entry is enough** (any-of): `"conditionTags": ["PRE_EXISTING", "CHRONIC"]` matches a claim tagged with either.
- **A `benefits` entry also matches that benefit's descendants**: `"benefits": ["MATERNITY"]` matches a claim on `MATERNITY` or on any benefit under it in the tree.
- **Choosing a rule:** of the rules that match, the highest `precedence` wins; among equal precedence, the longest `days` wins. No matching rule means no waiting period.
- Every `benefits` entry must name a benefit that exists in one of the plan's trees (`UNKNOWN_WAITING_BENEFIT`).

Worked example (rules from the Baobab test fixture):

| Rule | days | precedence | when |
|---|---|---|---|
| GENERAL_ILLNESS | 30 | 0 | cause ILLNESS |
| OTHER_SURGERY | 300 | 0 | cause ILLNESS, surgical |
| ACUTE_SURGERY | 30 | 1 | cause ILLNESS, surgical, tags [ACUTE_ABDOMEN] |

| Claim | Matching rules | Waiting period |
|---|---|---|
| Illness, not surgical | GENERAL_ILLNESS | 30 days |
| Illness, surgical | GENERAL_ILLNESS, OTHER_SURGERY | 300 days (same precedence, longest) |
| Illness, surgical, tag ACUTE_ABDOMEN | all three | 30 days (ACUTE_SURGERY has the highest precedence) |
| Accident | none | no waiting period |

Claim-service evaluates waiting rules at verification (PLAN 9.1, M5) and must follow these semantics exactly.

## Rating

Each option has a `rating` object whose `model` selects a strategy. All amounts are annual.

| Model | Configuration | Price |
|---|---|---|
| `AGE_BAND_PER_MEMBER` | `ageBands: [ { "from", "to", "annual" } ]` | Sum over members of the band that holds each member's rating age |
| `FAMILY_SIZE_TIER` | `ageSubject?: "PRINCIPAL"`, `tables: [ { "age"?, "tiers": [ { "members", "annual" } ], "additionalMemberAnnual"? } ]` | The tier for the family size. With `ageSubject`, the table is chosen by the principal's rating age. Members beyond the largest tier add `additionalMemberAnnual` each |
| `FLAT_PER_ELIGIBLE_MEMBER` | `annual`, `eligible: { "relationships", "sex"? }` | `annual` times the members whose relationship is listed and, if `sex` is set, whose sex matches. No eligible member costs 0 |

**Rating age.** `AT_START` is the completed years on the start date. `NEXT_BIRTHDAY` is the age the member turns at their next birthday on or after the start date, so a birthday on the start date keeps that day's age. A member born on 29 February has their birthday on 1 March in other years.

**Premium** (PLAN 8.1, ADR-003):

1. Price each mandatory module, and each optional module that was selected, through its strategy.
2. Apply the adjustments in order. A PERCENT adjustment is a fraction of the running subtotal; a FIXED one is added.
3. Apply the levies to the adjusted subtotal. A PERCENT levy is a fraction of it; a FIXED levy is added as is.
4. **Every line is rounded HALF_UP to 2 places, and the annual premium is the sum of the rounded lines**, so the breakdown always adds up.

### Strategy checks

| Code | Strategy | Meaning |
|---|---|---|
| `RATE_BANDS_GAP` | age band | Ages between two bands are not covered |
| `RATE_BANDS_OVERLAP` | age band | Two bands cover the same age |
| `RATE_BANDS_DO_NOT_COVER_ELIGIBLE_AGES` | age band | An eligible principal, spouse or child age (including the student age) has no band |
| `TIERS_NOT_CONTIGUOUS` | family tier | Tiers do not cover family sizes 1 to n exactly once |
| `TIER_OVERFLOW_NOT_PRICED` | family tier | The plan allows more members than the largest tier and there is no `additionalMemberAnnual` |
| `TIER_TABLES_AMBIGUOUS` | family tier | Several tables without an `ageSubject`, or table ages that do not match the `ageSubject` |
| `TIER_AGE_TABLES_GAP`, `TIER_AGE_TABLES_OVERLAP` | family tier | Principal age tables leave a gap or overlap |
| `TIER_AGE_TABLES_DO_NOT_COVER_ELIGIBLE_AGES` | family tier | An eligible principal age has no table |
| `FLAT_RATING_NO_ELIGIBLE_RELATIONSHIP` | flat | No listed relationship can exist on the plan (for example SPOUSE on an individual-only plan) |
| `AGE_RANGE_INVALID` | all | A band or table age range starts after it ends |

## Violation codes

| Code | Layer | Meaning |
|---|---|---|
| `MALFORMED_JSON` | parse | The text is not JSON |
| `SCHEMA_VIOLATION` | schema | The document does not match the schema; the message says how |
| `PLAN_NOT_READABLE` | parse | The document matches the schema but a value cannot be read, for example 2026-02-30 |
| `CURRENCY_NOT_SUPPORTED` | semantic | The currency is not KES (D15) |
| `SALE_PERIOD_INVALID` | semantic | The sale ends before it starts |
| `AGE_RANGE_INVALID` | semantic | An eligibility range starts after it ends, or the student age is below the child age |
| `CHILD_AGE_MISSING` | semantic | Family cover with children has no `childAge` |
| `NO_MANDATORY_MODULE` | semantic | No module is mandatory |
| `DUPLICATE_CODE` | semantic | A code repeats among modules, options of one module, benefits of one tree, waiting rules, or adjustments and levies together |
| `UNSUPPORTED_FEATURE` | semantic | A `FIXED_SUM` benefit (D18) |
| `CHILD_LIMIT_EXCEEDS_PARENT`, `INVALID_SCOPE_NESTING`, `PER_CLAIM_WITH_MAX_COUNT`, `MAX_PER_VISIT_EXCEEDS_LIMIT` | semantic | Limit rules (D32) |
| `UNKNOWN_WAITING_BENEFIT` | semantic | A waiting rule names a benefit that does not exist |
| `UNKNOWN_RATING_MODEL`, `RATING_CONFIG_INVALID` | semantic | No strategy for the model, or its configuration cannot be read |
| Strategy check codes | semantic | See "Strategy checks" |

## Rating errors

A premium that cannot be computed raises `RatingException` with one of these codes. The quote API (M4) returns them in a problem response.

| Code | Meaning |
|---|---|
| `NO_MEMBERS` | No members to price |
| `MANDATORY_MODULE_MISSING`, `MODULE_NOT_FOUND`, `OPTION_NOT_FOUND` | The module selection does not fit the plan |
| `AGE_OUTSIDE_RATING_BANDS`, `NO_TIER_TABLE_FOR_AGE`, `TIER_OVERFLOW_NOT_PRICED`, `PRINCIPAL_REQUIRED` | The members cannot be priced by the option's rating |
| `DATE_OF_BIRTH_AFTER_START` | A member is born after the start date |
| `UNKNOWN_RATING_MODEL`, `RATING_CONFIG_INVALID` | The option's rating cannot be used |

## Adding a rating strategy

Only when a plan needs pricing that the three strategies cannot express (PLAN 5.4 step 5):

1. Write a class implementing `RatingStrategy<C>` in `catalogue.rating`, annotated `@Component`. Spring registers it; `RatingStrategyRegistry` needs no change. Its configuration is a record `C` that includes the `model` field.
2. Implement `validate` for the semantic checks and `price` for the amount, both with `BigDecimal`.
3. Add the model to the schema: the `rating.model` enum, an `if`/`then` entry and a `$defs` entry.
4. Add golden cases and invalid cases, keeping `catalogue.rating` at 90% line and 85% branch coverage.
5. Record an ADR and update this document.
