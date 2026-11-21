package com.example.insurance.claimservice.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.domain.Cause;
import com.example.insurance.events.policy.TermsSnapshot.WaitingRuleTerms;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The D31 semantics table (docs/catalogue-schema.md), with the worked example from that document
 * and the cases the schema states in words: any-of lists, descendant benefits, empty conditions,
 * and the precedence, length and code tie-breaks.
 */
class WaitingRuleResolverTest {

  private static final WaitingRuleTerms GENERAL_ILLNESS =
      rule("GENERAL_ILLNESS", 30, 0, "ILLNESS", null, List.of(), List.of());
  private static final WaitingRuleTerms OTHER_SURGERY =
      rule("OTHER_SURGERY", 300, 0, "ILLNESS", true, List.of(), List.of());
  private static final WaitingRuleTerms ACUTE_SURGERY =
      rule("ACUTE_SURGERY", 30, 1, "ILLNESS", true, List.of("ACUTE_ABDOMEN"), List.of());
  private static final List<WaitingRuleTerms> WORKED_EXAMPLE =
      List.of(GENERAL_ILLNESS, OTHER_SURGERY, ACUTE_SURGERY);

  @Test
  void illnessThatIsNotSurgicalGetsTheGeneralRule() {
    assertThat(code(WORKED_EXAMPLE, Cause.ILLNESS, false, Set.of(), Set.of("OP")))
        .contains("GENERAL_ILLNESS");
  }

  @Test
  void illnessThatIsSurgicalGetsTheLongerOfTheEqualPrecedenceRules() {
    assertThat(code(WORKED_EXAMPLE, Cause.ILLNESS, true, Set.of(), Set.of("OP")))
        .contains("OTHER_SURGERY");
  }

  @Test
  void aHigherPrecedenceRuleBeatsALongerOne() {
    assertThat(code(WORKED_EXAMPLE, Cause.ILLNESS, true, Set.of("ACUTE_ABDOMEN"), Set.of("OP")))
        .contains("ACUTE_SURGERY");
  }

  @Test
  void anAccidentMatchesNoRuleSoThereIsNoWaiting() {
    assertThat(
            WaitingRuleResolver.resolve(
                WORKED_EXAMPLE, Cause.ACCIDENT, false, Set.of(), Set.of("OP")))
        .isEmpty();
  }

  @Test
  void noRulesMeansNoWaiting() {
    assertThat(WaitingRuleResolver.resolve(List.of(), Cause.ILLNESS, false, Set.of(), Set.of("OP")))
        .isEmpty();
  }

  @Test
  void aTagListIsAnyOf() {
    WaitingRuleTerms anyOf =
        rule("ANY_TAG", 10, 0, null, null, List.of("PRE_EXISTING", "CHRONIC"), List.of());

    assertThat(code(List.of(anyOf), Cause.ILLNESS, false, Set.of("CHRONIC"), Set.of("OP")))
        .contains("ANY_TAG");
    assertThat(code(List.of(anyOf), Cause.ILLNESS, false, Set.of("OTHER"), Set.of("OP"))).isEmpty();
  }

  @Test
  void aBenefitMatchesItsDescendantsThroughThePath() {
    WaitingRuleTerms maternity =
        rule("MATERNITY_WAIT", 270, 0, null, null, List.of(), List.of("MATERNITY"));

    // The claimed benefit sits under MATERNITY, so the path from the root includes it.
    assertThat(
            code(
                List.of(maternity),
                Cause.ILLNESS,
                false,
                Set.of(),
                Set.of("INPATIENT", "MATERNITY", "CAESAREAN")))
        .contains("MATERNITY_WAIT");
    assertThat(
            code(
                List.of(maternity), Cause.ILLNESS, false, Set.of(), Set.of("INPATIENT", "SURGERY")))
        .isEmpty();
  }

  @Test
  void everyFieldPresentMustMatch() {
    WaitingRuleTerms both = rule("BOTH", 5, 0, "ILLNESS", true, List.of(), List.of());

    assertThat(code(List.of(both), Cause.ILLNESS, false, Set.of(), Set.of("OP"))).isEmpty();
    assertThat(code(List.of(both), Cause.ACCIDENT, true, Set.of(), Set.of("OP"))).isEmpty();
    assertThat(code(List.of(both), Cause.ILLNESS, true, Set.of(), Set.of("OP"))).contains("BOTH");
  }

  @Test
  void anEqualPrecedenceAndLengthIsBrokenByTheLowerCode() {
    WaitingRuleTerms zeta = rule("ZETA", 30, 0, "ILLNESS", null, List.of(), List.of());
    WaitingRuleTerms alpha = rule("ALPHA", 30, 0, "ILLNESS", null, List.of(), List.of());

    assertThat(code(List.of(zeta, alpha), Cause.ILLNESS, false, Set.of(), Set.of("OP")))
        .contains("ALPHA");
    assertThat(code(List.of(alpha, zeta), Cause.ILLNESS, false, Set.of(), Set.of("OP")))
        .contains("ALPHA");
  }

  @Test
  void theOutcomeDoesNotDependOnTheOrderRulesAreStored() {
    List<WaitingRuleTerms> forward = WORKED_EXAMPLE;
    List<WaitingRuleTerms> backward =
        Collections.unmodifiableList(List.of(ACUTE_SURGERY, OTHER_SURGERY, GENERAL_ILLNESS));

    assertThat(code(forward, Cause.ILLNESS, true, Set.of(), Set.of("OP")))
        .isEqualTo(code(backward, Cause.ILLNESS, true, Set.of(), Set.of("OP")));
  }

  private static Optional<String> code(
      List<WaitingRuleTerms> rules,
      Cause cause,
      boolean surgical,
      Set<String> tags,
      Set<String> path) {
    return WaitingRuleResolver.resolve(rules, cause, surgical, tags, path)
        .map(WaitingRuleTerms::code);
  }

  private static WaitingRuleTerms rule(
      String code,
      int days,
      int precedence,
      String cause,
      Boolean surgical,
      List<String> tags,
      List<String> benefits) {
    return new WaitingRuleTerms(code, days, precedence, cause, surgical, tags, benefits);
  }
}
