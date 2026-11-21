package com.example.insurance.claimservice.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * An expected failure with a stable code. The detail text is safe for clients: it never contains
 * personal data or request bodies (PLAN 12). {@code retryAfter}, when set, becomes the Retry-After
 * header.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient Duration retryAfter;

  public ApiException(ErrorCode code, String detail) {
    this(code, detail, null);
  }

  public ApiException(ErrorCode code, String detail, Duration retryAfter) {
    super(detail);
    this.code = code;
    this.retryAfter = retryAfter;
  }

  public ErrorCode code() {
    return code;
  }

  public Optional<Duration> retryAfter() {
    return Optional.ofNullable(retryAfter);
  }
}
