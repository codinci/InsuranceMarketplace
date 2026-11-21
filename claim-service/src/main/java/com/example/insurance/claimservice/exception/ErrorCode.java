package com.example.insurance.claimservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes (PLAN 14, CLAUDE.md). Clients match on the {@code code} property of the RFC
 * 7807 response, not on the text. Add a code here, never reuse one for a different meaning.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  POLICY_NOT_FOUND(HttpStatus.NOT_FOUND),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  CONFLICT(HttpStatus.CONFLICT),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  POLICY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND),
  DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  CLAIM_NOT_EDITABLE(HttpStatus.CONFLICT),
  INVALID_TRANSITION(HttpStatus.CONFLICT),
  POLICY_UNUSABLE(HttpStatus.CONFLICT),
  DOCUMENT_LIMIT_REACHED(HttpStatus.CONFLICT),
  DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
  DOCUMENT_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  DOCUMENT_CONTENT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
