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

- **Tie-break (M5b, ADR-016):** when precedence and `days` are both equal, the rule with the lower `code` wins, so the outcome never depends on the order rules are stored in.

## How claim-service pays (M6, ADR-017)

Claim-service reads the terms snapshot generically and follows these rules exactly. No insurer, plan or benefit code is known to it.

- **The chain** is the benefit nodes from the module root down to the claimed benefit.
- **Limits come first, root to leaf.** Each node with a limit caps the amount:
  - `PER_YEAR` caps what is left after the running total for that benefit (and, for `PER_MEMBER`, that member). `maxCount` blocks a claim once that many have been approved (`COUNT_LIMIT_REACHED`).
  - `PER_CLAIM` caps this claim only and keeps no running total.
  - `maxPerVisit` caps this claim for either period.
- **The co-pay comes last**, from the deepest node on the chain that has one: `PERCENT` takes that fraction off, `FIXED_PER_VISIT` subtracts the value (never below zero). Amounts are rounded to two places, half up.
- **What is reserved** is the amount the reviewer approves (after the co-pay), on every `PER_YEAR` node of the chain, in the same transaction as the approval. A visit is counted only on nodes that set `maxCount`.
- **The policy year** counts from the start of cover: year 1 for the first twelve months. A one-year policy is always in year 1.
- **Examples.** Insurer A: consultation claimed 3,000, `maxPerVisit` 2,000, payable 2,000. Insurer B: consultation claimed 1,500, fixed co-pay 300, payable 1,200.

## Cancellation and refund semantics (D7, M8b, ADR-021)

The `cancellation` block is copied into each policy's terms at purchase, so a later plan version never changes what an existing policy refunds.

- **`noticeDays`.** A holder who cancels an active policy keeps cover until today plus `noticeDays`; that day is the first without cover (`cancelEffectiveDate`). A notice period that would end after the cover is refused (`NOTICE_PAST_END`).
- **`refund.model`.** `NONE` never refunds. `PRO_RATA` refunds `annualPremium × unusedDays / coverDays`, two decimals, HALF_UP, where unused days run from the effective date to the end date inclusive (none if it is past the end).
- **`noRefundAfterMonths`** (optional). No refund when the holder asks on or after `startDate + N months`.
- **`noRefundIfClaims`** (optional). No refund once any claim was submitted against the policy (policy-service counts `ClaimSubmitted` events).
- A policy that was never paid refunds nothing, whatever the rule.

The two seed insurers show both shapes: ACACIA plans are `PRO_RATA` with six months and claims blocking, BAOBAB plans are `NONE`. The rule is evaluated in `policy.rules.RefundRule` (90% line, 85% branch gate).

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

## Eligibility

Checked when a quote is created (M4a, `EligibilityChecker`). Ages are rating ages in the plan's age basis on the cover start date. Every problem is reported, each with the member's index in the request, or none when it concerns the whole group.

| Code | Meaning |
|---|---|
| `COVER_TYPE_NOT_OFFERED` | The plan does not sell this cover type |
| `PRINCIPAL_REQUIRED` | There is not exactly one principal |
| `INDIVIDUAL_COVER_HAS_DEPENDANTS` | Individual cover with a spouse or child |
| `TOO_MANY_SPOUSES` | More than one spouse |
| `TOO_MANY_CHILDREN` | More children than `maxChildren` |
| `STUDENT_ONLY_FOR_CHILDREN` | The student flag on a principal or spouse |
| `AGE_NOT_ELIGIBLE` | The member's rating age is outside their range. A child above `childAge.max` passes only as a student up to `maxIfStudent`; a plan without `childAge` covers no children |

The quote API returns these in `errors` with code `NOT_ELIGIBLE` (HTTP 422).

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

## Importing and publishing

Plans enter the catalogue through the admin API of policy-service (ADR-010), behind the gateway at `/api/v1`.

| Step | Request | Who |
|---|---|---|
| Create the insurer | `POST /admin/insurers` with `{ "code", "name" }` | platform admin |
| Check a document | `POST /admin/insurers/{insurerId}/plans/import?dryRun=true` with the document as the body | admin of that insurer, or platform admin |
| Store it as a draft | the same request without `dryRun` | same |
| Put it on sale | `POST /admin/plans/{planId}/publish` | same |
| Take it off sale | `POST /admin/plans/{planId}/retire` | same |
| List versions | `GET /admin/plans?status=&insurerId=&page=&size=` | same; `insurerId` filter for the platform admin |

- The document's `insurer.code` must be the code of the insurer in the URL (`INSURER_MISMATCH`).
- The product is created from the document's `product` block the first time it is seen.
- Importing a version that exists replaces it while it is `DRAFT` (D34). A published or retired version cannot change; import a new version number.
- Publishing retires the older published version of the same plan (D33). A plan is on sale when it is `PUBLISHED` and today is inside its `sale` period.
- Documents are limited to 256 KB.
- An admin of one insurer gets 404 for another insurer's insurer id or plan ids.

### API error codes

| Code | HTTP | Meaning |
|---|---|---|
| `PLAN_INVALID` | 422 | The document failed validation. `errors` lists every violation as `{ code, path, message }`, with the codes above |
| `INSURER_MISMATCH` | 422 | The document names a different insurer |
| `PLAN_TOO_LARGE` | 413 | The document is over 256 KB |
| `PLAN_VERSION_IMMUTABLE` | 409 | That version is published or retired |
| `PLAN_NOT_DRAFT` | 409 | Only a draft can be published |
| `NEWER_VERSION_PUBLISHED` | 409 | A newer version of this plan is already published |
| `PLAN_NOT_PUBLISHED` | 409 | Only a published plan can be retired |
| `INSURER_CODE_TAKEN` | 409 | An insurer with that code exists |
| `CONFLICT` | 409 | Two requests changed the same plan at once; retry |
| `INSURER_NOT_FOUND`, `PLAN_NOT_FOUND` | 404 | No such insurer or plan, or it belongs to another insurer |
| `PLATFORM_ADMIN_REQUIRED` | 403 | Only a platform admin can create insurers |
| `FORBIDDEN`, `UNAUTHENTICATED` | 403, 401 | Not an admin, or no valid token |
| `VALIDATION_FAILED` | 400 | Bad paging, filter, id or request body |

### Seed files

`policy-service/src/main/resources/catalogue-seed/<insurer>/<plan>.v<version>.json`. With `catalogue.seed.enabled=true` (compose), policy-service imports and publishes every seed file at startup, creating the insurer from the document's `insurer` block, and skips versions that already exist. The build validates every seed file and prices each one against a hand-computed expected premium in `policy-service/src/test/resources/catalogue/seed-premiums.json` (`SeedFilesTest`).

## Adding a rating strategy

Only when a plan needs pricing that the three strategies cannot express (PLAN 5.4 step 5):

1. Write a class implementing `RatingStrategy<C>` in `catalogue.rating`, annotated `@Component`. Spring registers it; `RatingStrategyRegistry` needs no change. Its configuration is a record `C` that includes the `model` field.
2. Implement `validate` for the semantic checks and `price` for the amount, both with `BigDecimal`.
3. Add the model to the schema: the `rating.model` enum, an `if`/`then` entry and a `$defs` entry.
4. Add golden cases and invalid cases, keeping `catalogue.rating` at 90% line and 85% branch coverage.
5. Record an ADR and update this document.
