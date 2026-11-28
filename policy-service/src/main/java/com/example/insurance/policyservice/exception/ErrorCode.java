package com.example.insurance.policyservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes of policy-service (CLAUDE.md, docs/catalogue-schema.md). Clients match on the
 * {@code code} property of the RFC 7807 response. Add a code here; never reuse one for a different
 * meaning.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  PLATFORM_ADMIN_REQUIRED(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  INSURER_NOT_FOUND(HttpStatus.NOT_FOUND),
  PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),
  QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND),
  POLICY_NOT_FOUND(HttpStatus.NOT_FOUND),
  POLICY_NOT_CANCELLABLE(HttpStatus.CONFLICT),
  NOTICE_PAST_END(HttpStatus.UNPROCESSABLE_CONTENT),
  PROFILE_REQUIRED(HttpStatus.CONFLICT),
  QUOTE_NOT_OPEN(HttpStatus.CONFLICT),
  QUOTE_EXPIRED(HttpStatus.CONFLICT),
  START_DATE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT),
  NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT),
  MODULE_SELECTION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
  MEMBERS_DIFFER_FROM_QUOTE(HttpStatus.UNPROCESSABLE_CONTENT),
  PRINCIPAL_NOT_HOLDER(HttpStatus.UNPROCESSABLE_CONTENT),
  DECLARATION_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
  PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),
  INSURER_CODE_TAKEN(HttpStatus.CONFLICT),
  PLAN_VERSION_IMMUTABLE(HttpStatus.CONFLICT),
  PLAN_NOT_DRAFT(HttpStatus.CONFLICT),
  PLAN_NOT_PUBLISHED(HttpStatus.CONFLICT),
  NEWER_VERSION_PUBLISHED(HttpStatus.CONFLICT),
  CONFLICT(HttpStatus.CONFLICT),
  PLAN_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  PLAN_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
  INSURER_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
  PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
