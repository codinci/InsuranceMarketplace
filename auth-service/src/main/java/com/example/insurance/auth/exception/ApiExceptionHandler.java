package com.example.insurance.auth.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every failure in the controllers into RFC 7807 {@code ProblemDetail} with a stable {@code
 * code}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
    return problem(ex.code(), ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
            .toList();
    return problem(ErrorCode.VALIDATION_FAILED, "The request is not valid.", fields);
  }

  static ResponseEntity<ProblemDetail> problem(
      ErrorCode code, String detail, List<Map<String, String>> errors) {
    HttpStatus status = code.status();
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(status.getReasonPhrase());
    body.setProperty("code", code.name());
    if (errors != null) {
      body.setProperty("errors", errors);
    }
    return ResponseEntity.status(status).body(body);
  }
}
