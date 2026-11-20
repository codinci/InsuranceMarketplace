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

## Official documentation

- Java records: https://docs.oracle.com/en/java/javase/21/language/records.html
- `BigDecimal`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html
- JSON Schema: https://json-schema.org/understanding-json-schema/
- JUnit 5 parameterized tests: https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests
