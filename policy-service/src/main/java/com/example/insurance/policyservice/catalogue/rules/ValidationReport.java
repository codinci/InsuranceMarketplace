package com.example.insurance.policyservice.catalogue.rules;

import com.example.insurance.policyservice.catalogue.domain.PlanDocument;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.util.List;
import java.util.Optional;

/**
 * The result of validating a plan document. Every violation is listed, not only the first, so an
 * importer's dry run can show all problems at once (M3b).
 */
public record ValidationReport(List<Violation> violations, PlanDocument plan) {

  public ValidationReport {
    violations = List.copyOf(violations);
  }

  public static ValidationReport invalid(List<Violation> violations) {
    return new ValidationReport(violations, null);
  }

  public boolean valid() {
    return violations.isEmpty();
  }

  /** The parsed plan, present only when the document is valid. */
  public Optional<PlanDocument> validPlan() {
    return valid() ? Optional.ofNullable(plan) : Optional.empty();
  }
}
