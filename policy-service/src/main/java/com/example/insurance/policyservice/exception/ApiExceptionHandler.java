package com.example.insurance.policyservice.exception;

import com.example.insurance.common.problem.Problems;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every controller failure becomes RFC 7807 with a stable {@code code} (common-starter format). */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(PlanInvalidException.class)
  ResponseEntity<ProblemDetail> handlePlanInvalid(PlanInvalidException ex) {
    List<Map<String, String>> errors =
        ex.violations().stream()
            .map(v -> Map.of("code", v.code(), "path", v.path(), "message", v.message()))
            .toList();
    return problem(ex.code(), ex.getMessage(), errors);
  }

  @ExceptionHandler(ApiErrorsException.class)
  ResponseEntity<ProblemDetail> handleApiErrors(ApiErrorsException ex) {
    return Problems.response(ex.code().status(), ex.code().name(), ex.getMessage(), ex.errors());
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
    return problem(ex.code(), ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleBody(MethodArgumentNotValidException ex) {
    List<Map<String, String>> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error ->
                Map.of("field", error.getField(), "message", messageOf(error.getDefaultMessage())))
            .toList();
    return problem(ErrorCode.VALIDATION_FAILED, "The request is not valid.", fields);
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
    return problem(ErrorCode.VALIDATION_FAILED, "The request is not valid.", null);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    return problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Send the request as application/json.", null);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
    return problem(ErrorCode.NOT_FOUND, "No such resource.", null);
  }

  /** Two requests raced on the same row or unique key; the caller can retry. */
  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> handleConflict(Exception ex) {
    return problem(
        ErrorCode.CONFLICT, "The resource was changed at the same time. Try again.", null);
  }

  private static String messageOf(String message) {
    return message == null ? "is not valid" : message;
  }

  private static ResponseEntity<ProblemDetail> problem(
      ErrorCode code, String detail, List<Map<String, String>> errors) {
    return Problems.response(code.status(), code.name(), detail, errors);
  }
}
