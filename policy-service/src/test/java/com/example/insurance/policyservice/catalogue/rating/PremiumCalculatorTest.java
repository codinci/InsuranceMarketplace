package com.example.insurance.policyservice.catalogue.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.catalogue.domain.AmountType;
import com.example.insurance.policyservice.catalogue.domain.Charge;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PremiumCalculatorTest {

  private static final LocalDate START = LocalDate.of(2027, 3, 1);
  private static final List<RatedMember> ONE_ADULT =
      List.of(new RatedMember(Relationship.PRINCIPAL, Sex.MALE, LocalDate.of(1990, 7, 7)));

  private final PremiumCalculator calculator = new PremiumCalculator(CatalogueFixtures.registry());
  private final PlanDefinition baobab = CatalogueFixtures.validPlan("baobab-family.v1.json");

  /** Baobab with its adjustments and levies replaced, to isolate the arithmetic. */
  private PlanDefinition baobabWith(List<Charge> adjustments, List<Charge> levies) {
    return new PlanDefinition(
        baobab.code(),
        baobab.name(),
        baobab.version(),
        baobab.currency(),
        baobab.sale(),
        baobab.ageBasis(),
        baobab.eligibility(),
        baobab.modules(),
        baobab.waitingRules(),
        baobab.exclusions(),
        baobab.claimRules(),
        baobab.cancellation(),
        adjustments,
        levies);
  }

  private static Charge charge(String code, AmountType type, String value) {
    return new Charge(code, type, new BigDecimal(value));
  }

  @Test
  void aHalfCentIsRoundedUp() {
    // 5200 inpatient - 262 = 4938; 4938 x 0.0025 = 12.345, which HALF_UP makes 12.35.
    PlanDefinition plan =
        baobabWith(
            List.of(charge("FIXED_OFF", AmountType.FIXED, "-262")),
            List.of(charge("LEVY_A", AmountType.PERCENT, "0.0025")));
    PremiumBreakdown breakdown =
        calculator.price(plan, Map.of("INPATIENT", "IP-150K"), ONE_ADULT, START);
    assertThat(breakdown.levies().get(0).amount()).isEqualByComparingTo("12.35");
    assertThat(breakdown.annualPremium()).isEqualByComparingTo("4950.35");
  }

  @Test
  void adjustmentsApplyInOrderToTheRunningSubtotal() {
    // 5200 + 10% = 5720; then -5% of 5720 = -286; then FIXED +30 = 5464.
    PlanDefinition plan =
        baobabWith(
            List.of(
                charge("LOADING", AmountType.PERCENT, "0.10"),
                charge("DISCOUNT", AmountType.PERCENT, "-0.05"),
                charge("FEE", AmountType.FIXED, "30")),
            List.of());
    PremiumBreakdown breakdown =
        calculator.price(plan, Map.of("INPATIENT", "IP-150K"), ONE_ADULT, START);
    assertThat(breakdown.adjustments())
        .extracting(PremiumBreakdown.ChargeAmount::amount)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("520"), new BigDecimal("-286"), new BigDecimal("30"));
    assertThat(breakdown.adjustedSubtotal()).isEqualByComparingTo("5464");
    assertThat(breakdown.annualPremium()).isEqualByComparingTo("5464");
    assertThat(breakdown.currency()).isEqualTo("KES");
  }

  @Test
  void anOptionalModuleThatIsNotSelectedIsNotPriced() {
    PremiumBreakdown breakdown =
        calculator.price(baobab, Map.of("INPATIENT", "IP-150K"), ONE_ADULT, START);
    assertThat(breakdown.modules())
        .extracting(PremiumBreakdown.ModulePremium::module)
        .containsExactly("INPATIENT");
  }

  @Test
  void theMandatoryModuleMustBeSelected() {
    assertCode(
        () -> calculator.price(baobab, Map.of("OUTPATIENT", "OP-35K"), ONE_ADULT, START),
        RatingException.MANDATORY_MODULE_MISSING);
  }

  @Test
  void anUnknownModuleIsRefused() {
    assertCode(
        () ->
            calculator.price(
                baobab, Map.of("INPATIENT", "IP-150K", "DENTAL", "D-1"), ONE_ADULT, START),
        RatingException.MODULE_NOT_FOUND);
  }

  @Test
  void anUnknownOptionIsRefused() {
    assertCode(
        () -> calculator.price(baobab, Map.of("INPATIENT", "IP-999K"), ONE_ADULT, START),
        RatingException.OPTION_NOT_FOUND);
  }

  @Test
  void aQuoteNeedsMembers() {
    assertCode(
        () -> calculator.price(baobab, Map.of("INPATIENT", "IP-150K"), List.of(), START),
        RatingException.NO_MEMBERS);
  }

  private static void assertCode(Executable call, String code) {
    assertThatThrownBy(call::execute)
        .isInstanceOfSatisfying(RatingException.class, e -> assertThat(e.code()).isEqualTo(code));
  }
}
