package com.example.insurance.common.problem;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * The shared error format (PLAN 3, CLAUDE.md): RFC 7807 {@code ProblemDetail} with a stable {@code
 * code} property and, for validation failures, an {@code errors} list. Every service builds its
 * error responses here, so clients see one shape everywhere.
 */
public final class Problems {

  /** The property that carries the stable error code. */
  public static final String CODE = "code";

  /** The property that carries field or document errors. */
  public static final String ERRORS = "errors";

  private Problems() {}

  public static ProblemDetail of(HttpStatus status, String code, String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(status.getReasonPhrase());
    body.setProperty(CODE, code);
    return body;
  }

  /** A response with an optional list of errors; {@code errors} is left out when null. */
  public static ResponseEntity<ProblemDetail> response(
      HttpStatus status, String code, String detail, List<?> errors) {
    ProblemDetail body = of(status, code, detail);
    if (errors != null) {
      body.setProperty(ERRORS, errors);
    }
    return ResponseEntity.status(status).body(body);
  }
}
