# ADR-004: Quality gates are part of the build

- **Status:** Accepted (2026-11-18). Demonstrated in M0a.
- **Context:** The owner is learning Java and Spring and wants guardrails that do not depend on remembering rules. PLAN 16 requires lint, static analysis and tests at every step, enforced by the build, git hooks and CI from the first milestone.

## Decision

Gates run at four levels (PLAN 16.2):

| Level | Trigger | What runs | Where it is implemented |
|---|---|---|---|
| G0 | Claude Code edits a Java file | `spotless:apply` on that file | `.claude/settings.json`, `scripts/hooks/format-java.sh` |
| G1 | `git commit` | `spotless:check`, compile, unit tests of changed modules | `.githooks/pre-commit` calls `scripts/hooks/pre-commit.sh` |
| G2 | Before a milestone is called done | `./mvnw clean verify` (all checks), then Hadolint; the OpenAPI diff and smoke script are added when they apply | `scripts/verify.sh` |
| G3 | Every push and pull request | `scripts/verify.sh` in GitHub Actions, plus dependency review on pull requests | `.github/workflows/ci.yml` |

The Maven build enforces: Enforcer (Java 21, Maven 3.9+, dependency convergence, no duplicate versions, no SNAPSHOT dependencies), Spotless (google-java-format, unused imports), Checkstyle (small ruleset: naming, unused and star imports, no `System.out` or `System.err`), SpotBugs (Medium threshold, effort Max), ArchUnit (architecture tests), JaCoCo (70% line floor per bundle), Surefire and Failsafe.

## Demonstrations (PLAN 16.3)

Each violation was introduced in a scratch copy and the build was run with `clean verify`. Each one failed the build:

| Violation | Result |
|---|---|
| Badly formatted Java file | Fails at `spotless-check`: "format violations" |
| Failing assertion in `HelloControllerTest` | Fails: `JSON path "$.service" expected:<not-the-service> but was:<hello-service>` |
| Field injection (`@Autowired` on a field) in main code | Fails at `HelloArchitectureTest` (ArchUnit) |
| No test executes the controller | Fails at JaCoCo: "lines covered ratio is 0.00, but expected minimum is 0.70" |

A first coverage run without `clean` reported success because a stale `jacoco.exec` from an earlier run was still present. The gate itself is correct, and `verify.sh` always uses `clean`. The demo was rerun with `clean` and failed as expected.

## Deviations from the plan, recorded here

1. **The 70% line floor is active from M0a, not M1.** PLAN 16.3 says the module floor starts at M1. It is enforced now because the gate demonstration needs a coverage failure. This is stricter, not looser.
2. **`*Application` classes are excluded from coverage.** The one-line `main` method of a Spring Boot entry point is not worth a test that boots a real server. The context test (`HelloServiceContextTest`) covers the entry point's wiring. The exclusion is in the parent POM.
3. **The OWASP dependency check is not in CI yet.** PLAN G3 lists it. It needs an NVD API key to run in reasonable time. M0a uses GitHub's dependency review on pull requests as a substitute. Adding OWASP is open; it is due before M9 (dependency scan green), or sooner if an NVD key is available.
4. **`verify.sh` reports the OpenAPI diff and smoke script as "not applicable yet".** They start at M1 and M4. The script says so explicitly rather than skipping silently.
5. **The Claude Code hook payload format is assumed, not yet confirmed.** `format-java.sh` reads `tool_input.file_path` from stdin. This should be checked against the current Claude Code hooks documentation.

## Consequences

- Commits that break a gate are rejected locally before CI sees them.
- Thresholds change only through an ADR, as PLAN 16.3 says. Suppressions need a comment and an ADR.
- The build takes a minute or two for `verify`. That is the cost of the gates. `-pl <module> -am` keeps daily work faster.
