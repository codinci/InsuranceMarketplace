package com.example.insurance.policyservice.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.policyservice.AbstractPolicyIT;
import com.example.insurance.policyservice.catalogue.api.CatalogueApi;
import com.example.insurance.policyservice.catalogue.api.CatalogueApiException;
import com.example.insurance.policyservice.catalogue.api.CoverType;
import com.example.insurance.policyservice.catalogue.api.MemberInput;
import com.example.insurance.policyservice.catalogue.api.QuotedPrice;
import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import com.example.insurance.policyservice.support.TestTokens;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The catalogue boundary used by the policy package (ADR-001, ADR-011). */
class CatalogueApiIT extends AbstractPolicyIT {

  private static final String ACACIA = "acacia-standard.v1.json";
  private static final LocalDate START = LocalDate.of(2027, 1, 1);
  private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"));
  private static final List<MemberInput> FAMILY =
      List.of(
          new MemberInput(Relationship.PRINCIPAL, Sex.MALE, LocalDate.of(1990, 6, 15), false),
          new MemberInput(Relationship.SPOUSE, Sex.FEMALE, LocalDate.of(1992, 1, 1), false),
          new MemberInput(Relationship.CHILD, Sex.FEMALE, LocalDate.of(2015, 3, 10), false));
  private static final Map<String, String> IP_AND_OP =
      Map.of("INPATIENT", "IP-200K", "OUTPATIENT", "OP-40K");

  @Autowired CatalogueApi catalogue;

  private String code;
  private UUID insurerId;
  private UUID planId;

  @BeforeEach
  void publishedAcacia() throws Exception {
    code = uniqueCode("API");
    insurerId = createInsurer(code);
    planId = publishedPlan(insurerId, planJson(ACACIA, code));
  }

  @Test
  void aPublishedPlanIsOnSaleAndADraftIsNot() throws Exception {
    assertThat(catalogue.planOnSale(planId, TODAY)).isPresent();
    assertThat(catalogue.planOnSale(planId, TODAY).orElseThrow().insurerCode()).isEqualTo(code);
    UUID draft = importDraft(insurerId, planJson(ACACIA, code, version(2)));
    assertThat(catalogue.planOnSale(draft, TODAY)).isEmpty();
    assertThat(catalogue.planOnSale(UUID.randomUUID(), TODAY)).isEmpty();
  }

  @Test
  void thePriceMatchesTheGoldenPremium() {
    QuotedPrice price = catalogue.price(planId, IP_AND_OP, FAMILY, START);
    assertThat(price.annualPremium()).isEqualByComparingTo("50175.00");
    assertThat(price.modules()).hasSize(2);
    assertThat(price.currency()).isEqualTo("KES");
  }

  @Test
  void aMissingMandatoryModuleIsAStableCode() {
    assertThatThrownBy(() -> catalogue.price(planId, Map.of("OUTPATIENT", "OP-40K"), FAMILY, START))
        .isInstanceOfSatisfying(
            CatalogueApiException.class,
            e -> assertThat(e.code()).isEqualTo("MANDATORY_MODULE_MISSING"));
  }

  @Test
  void eligibilityProblemsComeBackWithMemberIndexes() {
    List<MemberInput> tooOld =
        List.of(new MemberInput(Relationship.PRINCIPAL, Sex.MALE, LocalDate.of(1940, 1, 1), false));
    assertThat(catalogue.checkEligibility(planId, CoverType.INDIVIDUAL, tooOld, START))
        .singleElement()
        .satisfies(p -> {
          assertThat(p.code()).isEqualTo("AGE_NOT_ELIGIBLE");
          assertThat(p.memberIndex()).isZero();
        });
    assertThat(catalogue.checkEligibility(planId, CoverType.FAMILY, FAMILY, START)).isEmpty();
  }

  @Test
  void theSnapshotHoldsOnlyTheSelectedOptions() {
    TermsSnapshot snapshot = catalogue.termsSnapshot(planId, IP_AND_OP);
    assertThat(snapshot.snapshotVersion()).isEqualTo(TermsSnapshot.CURRENT_VERSION);
    assertThat(snapshot.insurerCode()).isEqualTo(code);
    assertThat(snapshot.planId()).isEqualTo(planId);
    assertThat(snapshot.modules())
        .extracting(m -> m.module() + ":" + m.option())
        .containsExactly("INPATIENT:IP-200K", "OUTPATIENT:OP-40K");
    BenefitTerms consultation = snapshot.modules().get(1).benefits().children().get(0);
    assertThat(consultation.code()).isEqualTo("CONSULTATION");
    assertThat(consultation.limit().maxPerVisit()).isEqualByComparingTo("2500");
    assertThat(snapshot.modules().get(1).benefits().copay().value()).isEqualByComparingTo("0.1");
    assertThat(snapshot.waitingRules()).extracting(w -> w.code()).contains("MATERNITY");
    assertThat(snapshot.exclusions()).contains("Cosmetic treatment");
    assertThat(snapshot.filingDeadlineDays()).isEqualTo(90);
    assertThat(snapshot.cancellation().refundModel()).isEqualTo("PRO_RATA");
  }

  @Test
  void aRetiredVersionCanStillBeSnapshotted() throws Exception {
    planAction(TestTokens.platformAdmin(), planId, "retire");
    assertThat(catalogue.planOnSale(planId, TODAY)).isEmpty();
    assertThat(catalogue.termsSnapshot(planId, IP_AND_OP).modules()).hasSize(2);
  }

  @Test
  void aDraftCannotBeSnapshottedOrPriced() throws Exception {
    UUID draft = importDraft(insurerId, planJson(ACACIA, code, version(2)));
    assertThatThrownBy(() -> catalogue.termsSnapshot(draft, IP_AND_OP))
        .isInstanceOfSatisfying(
            CatalogueApiException.class, e -> assertThat(e.code()).isEqualTo("PLAN_NOT_FOUND"));
    assertThatThrownBy(() -> catalogue.price(draft, IP_AND_OP, FAMILY, START))
        .isInstanceOf(CatalogueApiException.class);
  }

  @Test
  void anUnknownOptionCannotBeSnapshotted() {
    assertThatThrownBy(() -> catalogue.termsSnapshot(planId, Map.of("INPATIENT", "IP-1M")))
        .isInstanceOfSatisfying(
            CatalogueApiException.class, e -> assertThat(e.code()).isEqualTo("OPTION_NOT_FOUND"));
  }
}
