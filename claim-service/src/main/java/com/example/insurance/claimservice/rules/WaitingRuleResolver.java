package com.example.insurance.claimservice.rules;

import com.example.insurance.claimservice.domain.Cause;
import com.example.insurance.events.policy.TermsSnapshot.WaitingRuleTerms;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses the waiting rule that applies to a claim (docs/catalogue-schema.md, D31). Every field
 * present in a rule must match (AND). Inside a list one entry is enough (any-of). A benefit also
 * matches its descendants, through {@link BenefitPath}. Of the matches, the highest precedence
 * wins, then the longest days, then the lower code, so the outcome never depends on the order rules
 * are stored in.
 */
public final class WaitingRuleResolver {

  private WaitingRuleResolver() {
    // Static rules only; not instantiable.
  }

  /**
   * The rule that applies, or empty when none matches. {@code benefitPath} holds the codes from the
   * claimed benefit's root down to it, inclusive.
   */
  public static Optional<WaitingRuleTerms> resolve(
      List<WaitingRuleTerms> rules,
      Cause cause,
      boolean surgical,
      Set<String> conditionTags,
      Set<String> benefitPath) {
    return rules.stream()
        .filter(rule -> matches(rule, cause, surgical, conditionTags, benefitPath))
        .min(
            Comparator.comparingInt(WaitingRuleTerms::precedence)
                .reversed()
                .thenComparing(Comparator.comparingInt(WaitingRuleTerms::days).reversed())
                .thenComparing(WaitingRuleTerms::code));
  }

  static boolean matches(
      WaitingRuleTerms rule,
      Cause cause,
      boolean surgical,
      Set<String> conditionTags,
      Set<String> benefitPath) {
    boolean causeOk = rule.cause() == null || rule.cause().equals(cause.name());
    boolean surgicalOk = rule.surgical() == null || rule.surgical() == surgical;
    boolean tagsOk =
        rule.conditionTags().isEmpty()
            || rule.conditionTags().stream().anyMatch(conditionTags::contains);
    boolean benefitOk =
        rule.benefits().isEmpty() || rule.benefits().stream().anyMatch(benefitPath::contains);
    return causeOk && surgicalOk && tagsOk && benefitOk;
  }
}
