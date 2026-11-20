package com.example.insurance.policyservice.exception;

/**
 * An expected failure with a stable code. The detail text is safe for clients: no personal data and
 * no plan content beyond codes (PLAN 12).
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ApiException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
