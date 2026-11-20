package com.example.insurance.policyservice.exception;

import java.util.List;
import java.util.Map;

/** An expected failure that also lists details in the {@code errors} property. */
public class ApiErrorsException extends ApiException {

  private static final long serialVersionUID = 1L;

  private final transient List<Map<String, Object>> errors;

  public ApiErrorsException(ErrorCode code, String detail, List<Map<String, Object>> errors) {
    super(code, detail);
    this.errors = List.copyOf(errors);
  }

  public List<Map<String, Object>> errors() {
    return errors;
  }
}
