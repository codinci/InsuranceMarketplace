# M3a: Catalogue model and rating engine

## What M3a delivers

- `policy-service`, with its first package `catalogue`. It has no database, endpoints or Dockerfile yet; those arrive with M3b.
- A JSON Schema and a semantic validator for plan documents, with stable violation codes (docs/catalogue-schema.md).
- A rating engine with three strategies, and premium assembly with adjustments, levies and rounding.
- Golden premium tests worked out by hand, and a table of invalid plans with the exact code and path each must produce.
- ADR-001 (catalogue inside policy-service), ADR-002 (document-first plans, D31 and D32) and ADR-003 (rating strategies).

## New concepts

**Records.** `public record Limit(BigDecimal amount, LimitPeriod period, ...) {}` declares an immutable data class. Java writes the constructor, the accessors (`limit.amount()`), `equals`, `hashCode` and `toString`. A *compact constructor* (`public BenefitNode { children = children == null ? List.of() : List.copyOf(children); }`) runs before the fields are set, so it can fill defaults and take defensive copies. `List.copyOf` returns a list nobody can change, which keeps the record truly immutable.

**Enums.** `enum LimitScope { SHARED, PER_MEMBER }` is a closed set of values. Jackson reads `"SHARED"` from JSON into `LimitScope.SHARED`, and an unknown value fails. The schema rejects it first.

**Jackson 3 and `JsonNode`.** Spring Boot 4 uses Jackson 3, whose package is `tools.jackson`. `mapper.readTree(text)` gives a `JsonNode` tree you can walk or check against a schema. `mapper.treeToValue(node, PlanDocument.class)` turns it into records. `USE_BIG_DECIMAL_FOR_FLOATS` makes `0.0025` a `BigDecimal`. Otherwise it would become a `double` and lose exactness.

**`BigDecimal` and `RoundingMode`.** `new BigDecimal("0.1")` is exact, but `0.1` as a `double` is not. Arithmetic is by method: `a.add(b)`, `a.multiply(b)`. `setScale(2, RoundingMode.HALF_UP)` rounds to cents, with half a cent going up (12.345 becomes 12.35). Compare amounts with `compareTo`, not `equals`, because `equals` also compares the scale: `5464` and `5464.00` are not `equals`.

**The strategy pattern with Spring.** `RatingStrategy<C>` is an interface. Each pricing model is a class marked `@Component`. `RatingStrategyRegistry`'s constructor takes `List<RatingStrategy<?>>`, and Spring fills it with every bean that implements the interface. A new strategy is picked up without editing the registry. `<C>` is a *generic*: each strategy says which configuration record it reads.

**JSON Schema.** A JSON document that describes valid JSON: types, required fields, enums, patterns, and `if`/`then` for "when the model is X, these fields apply". `additionalProperties: false` rejects unknown or misspelt fields. The schema cannot express rules that span fields, such as "age bands are contiguous", so those live in Java.

**Parameterized tests.** `@ParameterizedTest` with `@CsvSource` or `@MethodSource` runs one test method per row of data. The golden premiums and the invalid plans are JSON files, and each row appears as its own named test in the report.

**JaCoCo package rules.** JaCoCo measures which lines and branches the tests ran. The parent POM sets a 70% line floor per module. policy-service adds a `PACKAGE` rule: `rating` and `rules` need 90% line and 85% branch coverage, or the build fails.

## Why this approach

- **A plan is data.** A new insurer is one JSON file that passes both validation layers. No migration and no code (ADR-002).
- **Strategies keep pricing open to extension.** A new pricing model is one class, not a new branch in a `switch` that every other model depends on (ADR-003).
- **Every breakdown line is rounded, and the total is the sum of the lines.** The breakdown a customer sees then always adds up to the premium.
- **Claim-time rules wait for claim-service.** The waiting-rule resolver and the limit evaluator belong to M5 and M6, because services share only event contracts. Their meaning is fixed now in docs/catalogue-schema.md.

## How it maps to Laravel

| Here | Laravel equivalent |
|---|---|
| `record` | A `readonly` DTO class with constructor property promotion |
| JSON Schema plus `PlanSemanticValidator` | `FormRequest` rules plus an `after()` hook for cross-field rules |
| `Violation(code, path, message)` | The validator's error bag, keyed by field path |
| `RatingStrategyRegistry` | A Manager class with drivers (`Cache::driver('redis')`), or tagged services resolved with `$app->tagged()` |
| `BigDecimal` | `brick/money` or `bcmath` (`bcadd`, `bcmul`) instead of floats |
| `@ParameterizedTest` | PHPUnit `#[DataProvider]` |
| JaCoCo rule | `phpunit --coverage` with a minimum enforced in CI |

## Self-check questions (answer before starting M3b)

1. A Baobab family of five buys inpatient only. Tiers stop at four members (13,200) and each extra member adds 2,100. The plan has a 5% online discount and a 0.25% levy. Work out the premium, line by line, using the rounding rule.
2. A plan author adds a `SHARED` dental limit of 10,000 under a `PER_MEMBER` outpatient limit of 40,000. Which violation do they get, and why does the rule exist? Would a `PER_MEMBER` dental limit under a `SHARED` outpatient limit pass?
3. A claim is for a surgical illness tagged `ACUTE_ABDOMEN` on the Baobab plan. Using the waiting-rule table in docs/catalogue-schema.md, which rule applies, and what would change if `ACUTE_SURGERY` had no `precedence`?

## Corrected answers

1. **Answered by the owner:** 18,360.
   **Corrected version:** Each line is rounded HALF_UP to 2 places, and the premium is the sum of the rounded lines (ADR-003).

   | Step | Calculation | Amount |
   |---|---|---|
   | Inpatient, family of 5 | largest tier (4 members) 13,200 + 1 extra member x 2,100 | 15,300.00 |
   | Subtotal | | 15,300.00 |
   | Online discount (-5%) | 15,300 x -0.05 | -765.00 |
   | Adjusted subtotal | the levy is charged on this, not on the subtotal | 14,535.00 |
   | Levy (0.25%) | 14,535 x 0.0025 = 36.3375, rounded HALF_UP | 36.34 |
   | **Annual premium** | 14,535.00 + 36.34 | **14,571.34** |

   The real Baobab fixture also has a fixed stamp duty of 50, which makes 14,621.34. That is the golden case "Baobab family of five, inpatient only" in `golden-premiums.json`. The question named only the discount and the 0.25% levy, so 14,571.34 is the answer to the question as asked.

   The owner's answer was not correct. 18,360 is 15,300 plus 20%, which suggests the discount was added instead of subtracted, or a percentage was misread (0.25% is 0.0025, not 0.25). Three points to keep: a family beyond the largest tier pays the largest tier plus the extra amount per additional member; a discount is a negative adjustment; levies apply to the adjusted subtotal.

2. **Answered by the owner:** It returns a scope violation error. The rule exists to stop a child's scope being broader than its parent's. A PER_MEMBER limit would pass under a SHARED outpatient limit.
   **Corrected version:** The validator reports `INVALID_SCOPE_NESTING` at the dental node's `limit/scope` path, for example `/plan/modules/1/options/0/benefits/children/0/limit/scope` (D32). The amount itself is fine (10,000 is not above 40,000), so this is the only violation. The rule exists because a `SHARED` pot belongs to the whole family, while a `PER_MEMBER` pot belongs to one person. A family-wide dental pot inside each person's outpatient pot has no clear meaning. At adjudication (M6), claim-service reserves utilisation on every limited node in the chain, keyed by member for `PER_MEMBER` and by policy for `SHARED`, and it could not tell which person's 40,000 the shared 10,000 sits inside. The reverse, a `PER_MEMBER` dental limit of 10,000 under a `SHARED` outpatient limit of 40,000, passes: each member has their own 10,000 for dental, and every dental claim also counts against the family's shared 40,000. It still has to obey the amount rule, so a per-member dental limit of 50,000 would fail with `CHILD_LIMIT_EXCEEDS_PARENT`.

   The owner's answer was correct on the rule and on the reverse case. The code is `INVALID_SCOPE_NESTING`, not a scope violation; clients match on the exact code.

3. **Answered by the owner:** The precedence rule applies and the surgery is covered after 30 days. Without precedence, ACUTE_SURGERY would have the same priority as GENERAL_ILLNESS.
   **Corrected version:** All three Baobab rules match the claim: `GENERAL_ILLNESS` (cause ILLNESS, 30 days, precedence 0), `OTHER_SURGERY` (ILLNESS and surgical, 300 days, precedence 0) and `ACUTE_SURGERY` (ILLNESS, surgical and tag `ACUTE_ABDOMEN`, 30 days, precedence 1). The highest precedence wins, so `ACUTE_SURGERY` applies and the waiting period is 30 days. The incident date must be at least 30 days after the policy start date for the claim to pass verification (PLAN 9.1, M5). Without its precedence, all three rules have precedence 0, so the tie-break picks the longest: `OTHER_SURGERY` with 300 days. The acute-abdomen exception would disappear, and an emergency appendix operation in month two would be refused. That is why an exception that is *shorter* than the general rule needs a higher precedence: "longest wins" alone always favours the stricter rule.

   The owner's answer was correct on the first part. The second part named the effect (equal priority) but not the outcome: the longest rule wins, so the wait becomes 300 days, not 30.

## Summary of the self-check

All three questions have corrected answers. Question 1 was not correct: the extra member, the discount sign and the levy base needed working through. Question 2 was correct apart from the code name (`INVALID_SCOPE_NESTING`). Question 3 was correct on the rule chosen, and incomplete on what happens without precedence (300 days).

## Official documentation

- Java records: https://docs.oracle.com/en/java/javase/21/language/records.html
- `BigDecimal`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html
- JSON Schema: https://json-schema.org/understanding-json-schema/
- JUnit 5 parameterized tests: https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests
