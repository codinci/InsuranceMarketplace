package com.example.insurance.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes (PLAN 14, CLAUDE.md). Clients match on the {@code code} property of the RFC
 * 7807 response, not on the text. Add a code here, never reuse one for a different meaning.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  ACCOUNT_LOCKED(HttpStatus.LOCKED),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  EMAIL_TAKEN(HttpStatus.CONFLICT),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
  REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  INSURER_SCOPE_VIOLATION(HttpStatus.FORBIDDEN),
  INSURER_REQUIRED(HttpStatus.BAD_REQUEST);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
