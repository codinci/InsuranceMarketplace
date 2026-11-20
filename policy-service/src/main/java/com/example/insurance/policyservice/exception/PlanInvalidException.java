package com.example.insurance.policyservice.exception;

import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.util.List;

/** A plan document failed validation. Every violation is returned to the caller (M3a). */
public class PlanInvalidException extends ApiException {

  private static final long serialVersionUID = 1L;

  private final transient List<Violation> violations;

  public PlanInvalidException(List<Violation> violations) {
    super(ErrorCode.PLAN_INVALID, "The plan document is not valid. See errors for every problem.");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
