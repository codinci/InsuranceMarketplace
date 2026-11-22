package com.example.insurance.claimservice.adjudication;

import com.example.insurance.common.Roles;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * What a reviewer may approve (PLAN 9.2, D13). Pure: the amount, what the evaluator says is
 * payable, the configured threshold and the reviewer's role. The first rule that is broken is
 * returned, in the order below, so a reviewer always sees the most basic problem first.
 */
public final class ApprovalRules {

  public static final String APPROVAL_EXCEEDS_PAYABLE = "APPROVAL_EXCEEDS_PAYABLE";
  public static final String PARTIAL_APPROVAL_REASON_REQUIRED = "PARTIAL_APPROVAL_REASON_REQUIRED";
  public static final String ADMIN_APPROVAL_REQUIRED = "ADMIN_APPROVAL_REQUIRED";

  private ApprovalRules() {
    // Static rules only; not instantiable.
  }

  /** A rule the approval breaks, with a stable code and a message that is safe to show. */
  public record Violation(String code, String message) {}

  /**
   * Checks an approval. Never more than payable. Less than payable needs a reason. Above the
   * threshold only an ADMIN may approve; the threshold itself is still the adjuster's to approve.
   */
  public static Optional<Violation> check(
      BigDecimal approved, BigDecimal payable, BigDecimal threshold, String role, String reason) {
    if (approved.compareTo(payable) > 0) {
      return Optional.of(
          new Violation(
              APPROVAL_EXCEEDS_PAYABLE,
              "The amount is more than the payable amount of " + payable + "."));
    }
    if (approved.compareTo(payable) < 0 && (reason == null || reason.isBlank())) {
      return Optional.of(
          new Violation(
              PARTIAL_APPROVAL_REASON_REQUIRED,
              "Approving less than the payable amount needs a reason."));
    }
    if (approved.compareTo(threshold) > 0 && !Roles.ADMIN.equals(role)) {
      return Optional.of(
          new Violation(
              ADMIN_APPROVAL_REQUIRED,
              "Only an admin can approve an amount above " + threshold + "."));
    }
    return Optional.empty();
  }
}
