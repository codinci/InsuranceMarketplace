package com.example.insurance.claimservice.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.adjudication.ApprovalRules.Violation;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The approval rules (PLAN 9.2, D13): the payable cap, the partial-approval reason, the threshold.
 */
class ApprovalRulesTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("50000.00");

  @Test
  void anAdjusterMayApproveThePayableAmountUpToTheThreshold() {
    assertThat(check("2000.00", "2000.00", "ADJUSTER", null)).isEmpty();
  }

  @Test
  void approvingMoreThanPayableIsRefused() {
    assertThat(check("2000.01", "2000.00", "ADMIN", "x"))
        .map(Violation::code)
        .contains(ApprovalRules.APPROVAL_EXCEEDS_PAYABLE);
  }

  @Test
  void approvingLessNeedsAReason() {
    assertThat(check("1500.00", "2000.00", "ADJUSTER", null))
        .map(Violation::code)
        .contains(ApprovalRules.PARTIAL_APPROVAL_REASON_REQUIRED);
    assertThat(check("1500.00", "2000.00", "ADJUSTER", "   "))
        .map(Violation::code)
        .contains(ApprovalRules.PARTIAL_APPROVAL_REASON_REQUIRED);
  }

  @Test
  void approvingLessWithAReasonIsAllowed() {
    assertThat(check("1500.00", "2000.00", "ADJUSTER", "Part of the invoice is not covered"))
        .isEmpty();
  }

  @Test
  void aboveTheThresholdOnlyAnAdminMayApprove() {
    assertThat(check("60000.00", "60000.00", "ADJUSTER", null))
        .map(Violation::code)
        .contains(ApprovalRules.ADMIN_APPROVAL_REQUIRED);
    assertThat(check("60000.00", "60000.00", "ADMIN", null)).isEmpty();
  }

  @Test
  void anAmountExactlyAtTheThresholdIsStillTheAdjustersToApprove() {
    assertThat(check("50000.00", "50000.00", "ADJUSTER", null)).isEmpty();
    assertThat(check("50000.01", "50000.01", "ADJUSTER", null))
        .map(Violation::code)
        .contains(ApprovalRules.ADMIN_APPROVAL_REQUIRED);
  }

  @Test
  void theMoreBasicProblemIsReportedFirst() {
    // Above payable and above the threshold for an adjuster: payable is reported first.
    assertThat(check("90000.00", "60000.00", "ADJUSTER", null))
        .map(Violation::code)
        .contains(ApprovalRules.APPROVAL_EXCEEDS_PAYABLE);
    // Partial without a reason, and above the threshold for an adjuster: the reason is reported
    // first.
    assertThat(check("55000.00", "60000.00", "ADJUSTER", null))
        .map(Violation::code)
        .contains(ApprovalRules.PARTIAL_APPROVAL_REASON_REQUIRED);
  }

  @Test
  void everyViolationCarriesAMessageForTheReviewer() {
    assertThat(check("3000.00", "2000.00", "ADMIN", null).orElseThrow().message())
        .contains("2000.00");
    assertThat(check("60000.00", "60000.00", "ADJUSTER", null).orElseThrow().message())
        .contains("50000.00");
  }

  private static Optional<Violation> check(
      String approved, String payable, String role, String reason) {
    return ApprovalRules.check(
        new BigDecimal(approved), new BigDecimal(payable), THRESHOLD, role, reason);
  }
}
