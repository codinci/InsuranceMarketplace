package com.example.insurance.auth.exception;

/**
 * An expected failure with a stable code. The detail text is safe for clients: it never contains
 * personal data, passwords or tokens (PLAN 12).
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
