package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.domain.BenefitUtilisation;
import com.example.insurance.claimservice.repository.BenefitUtilisationRepository;
import com.example.insurance.claimservice.service.UtilisationService;
import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * What is paid (PLAN 9.2, M6) through the API: the two worked examples, the reviewer's amount, the
 * approval threshold, the annual and count limits as running totals, and giving a reservation back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class AdjudicationIT extends AbstractReviewIT {

  @Autowired private BenefitUtilisationRepository totals;
  @Autowired private UtilisationService utilisation;

  private final String staff = adjuster(UUID.randomUUID());

  @Test
  void insurerAExampleAPerVisitCapOf2000PaysAThreeThousandConsultation2000() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    String claimId =
        underReview(
            customer, policy(holder, terms(null, perVisitCap("2000"), null)), "3000.00", staff);

    MvcResult approved = approve(staff, claimId, Map.of());

    assertThat(money(approved, "$.amountEligible")).isEqualByComparingTo("2000.00");
    assertThat(money(approved, "$.amountApproved")).isEqualByComparingTo("2000.00");
  }

  @Test
  void insurerBExampleAFixedCopayOf300PaysA1500Consultation1200() throws Exception {
    UUID holder = UUID.randomUUID();
    String claimId =
        underReview(
            TestTokens.customer(holder),
            policy(holder, terms(null, null, fixedCopay("300"))),
            "1500.00",
            staff);

    assertThat(money(approve(staff, claimId, Map.of()), "$.amountApproved"))
        .isEqualByComparingTo("1200.00");
  }

  @Test
  void aReviewerMayApproveLessWithAReasonButNeverMore() throws Exception {
    UUID holder = UUID.randomUUID();
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "2000.00", staff);

    assertCode(
        approve(staff, claimId, Map.of("approvedAmount", "1500.00")),
        422,
        "PARTIAL_APPROVAL_REASON_REQUIRED");
    assertCode(
        approve(staff, claimId, Map.of("approvedAmount", "2500.00", "reason", "More")),
        422,
        "APPROVAL_EXCEEDS_PAYABLE");

    MvcResult approved =
        approve(
            staff,
            claimId,
            Map.of("approvedAmount", "1500.00", "reason", "Part of the invoice is not covered."));
    assertThat(money(approved, "$.amountApproved")).isEqualByComparingTo("1500.00");
    assertThat(this.<String>read(approved, "$.decisionReason")).contains("not covered");
  }

  @Test
  void anAmountAboveTheThresholdNeedsAnAdminAndARefusalChangesNothing() throws Exception {
    UUID holder = UUID.randomUUID();
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "60000.00", staff);

    assertCode(approve(staff, claimId, Map.of()), 403, "ADMIN_APPROVAL_REQUIRED");
    assertThat(this.<String>read(claimAs(staff, claimId), "$.status")).isEqualTo("UNDER_REVIEW");

    // An adjuster may still approve a smaller amount at or below the threshold, with a reason.
    // An admin of the insurer may approve the whole amount.
    String admin = TestTokens.insurerAdmin(UUID.randomUUID(), INSURER);
    MvcResult approved = approve(admin, claimId, Map.of());
    assertThat(approved.getResponse().getStatus()).isEqualTo(200);
    assertThat(money(approved, "$.amountApproved")).isEqualByComparingTo("60000.00");
    assertThat(this.<String>read(approved, "$.status")).isEqualTo("APPROVED");
  }

  @Test
  void anAdjusterMayApproveUpToTheThresholdWithAReasonOnALargerClaim() throws Exception {
    UUID holder = UUID.randomUUID();
    String claimId =
        underReview(TestTokens.customer(holder), policy(holder, unlimited()), "60000.00", staff);

    MvcResult approved =
        approve(
            staff,
            claimId,
            Map.of("approvedAmount", "50000.00", "reason", "Capped at the threshold."));

    assertThat(approved.getResponse().getStatus()).isEqualTo(200);
    assertThat(money(approved, "$.amountApproved")).isEqualByComparingTo("50000.00");
  }

  @Test
  void aSharedAnnualLimitIsUsedUpAcrossClaims() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, terms(annualLimit("2500", null), null, null));

    String first = underReview(customer, policyId, "2000.00", staff);
    assertThat(money(approve(staff, first, Map.of()), "$.amountApproved"))
        .isEqualByComparingTo("2000.00");

    String second = underReview(customer, policyId, "3000.00", staff);
    // Only 500 is left under the limit, so that is what can be paid.
    assertThat(money(approve(staff, second, Map.of()), "$.amountApproved"))
        .isEqualByComparingTo("500.00");

    String third = underReview(customer, policyId, "100.00", staff);
    assertCode(approve(staff, third, Map.of()), 422, "LIMIT_EXHAUSTED");

    BenefitUtilisation row = onlyRow(policyId);
    assertThat(row.getAmountUsed()).isEqualByComparingTo("2500.00");
    assertThat(row.getMemberId()).isNull();
  }

  @Test
  void aPerMemberLimitKeepsItsTotalForTheMember() throws Exception {
    UUID holder = UUID.randomUUID();
    LimitTerms perMember =
        new LimitTerms(new BigDecimal("2500"), "PER_YEAR", "PER_MEMBER", null, null);
    UUID policyId = policy(holder, terms(perMember, null, null));
    String claimId = underReview(TestTokens.customer(holder), policyId, "2000.00", staff);

    approve(staff, claimId, Map.of());

    BenefitUtilisation row = onlyRow(policyId);
    assertThat(row.getMemberId()).isEqualTo("M-1");
    assertThat(row.getAmountUsed()).isEqualByComparingTo("2000.00");
  }

  @Test
  void aLimitOnTheNumberOfClaimsBlocksTheNextOne() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, terms(annualLimit("100000", 1), null, null));

    String first = underReview(customer, policyId, "1000.00", staff);
    assertThat(approve(staff, first, Map.of()).getResponse().getStatus()).isEqualTo(200);

    String second = underReview(customer, policyId, "1000.00", staff);
    assertCode(approve(staff, second, Map.of()), 422, "COUNT_LIMIT_REACHED");
    assertThat(onlyRow(policyId).getCountUsed()).isEqualTo(1);
  }

  @Test
  void releasingAReservationGivesTheLimitBackExactlyOnce() throws Exception {
    UUID holder = UUID.randomUUID();
    String customer = TestTokens.customer(holder);
    UUID policyId = policy(holder, terms(annualLimit("2500", 5), null, null));
    String first = underReview(customer, policyId, "2000.00", staff);
    approve(staff, first, Map.of());
    assertThat(onlyRow(policyId).getAmountUsed()).isEqualByComparingTo("2000.00");
    assertThat(onlyRow(policyId).getCountUsed()).isEqualTo(1);

    assertThat(utilisation.release(UUID.fromString(first))).isEqualTo(1);

    assertThat(onlyRow(policyId).getAmountUsed()).isEqualByComparingTo("0.00");
    assertThat(onlyRow(policyId).getCountUsed()).isZero();
    // A second release finds nothing left to give back.
    assertThat(utilisation.release(UUID.fromString(first))).isZero();

    // The whole limit is available again.
    String second = underReview(customer, policyId, "3000.00", staff);
    assertThat(money(approve(staff, second, Map.of()), "$.amountApproved"))
        .isEqualByComparingTo("2500.00");
  }

  private BenefitUtilisation onlyRow(UUID policyId) {
    List<BenefitUtilisation> rows = totals.findByPolicyId(policyId);
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }
}
