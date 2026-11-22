package com.example.insurance.claimservice.exception;

import com.example.insurance.claimservice.statemachine.ClaimStateMachine;
import com.example.insurance.common.problem.Problems;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every controller failure becomes RFC 7807 with a stable {@code code} (common-starter format). */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
    ResponseEntity<ProblemDetail> response = problem(ex.code(), ex.getMessage(), null);
    if (ex.retryAfter().isEmpty()) {
      return response;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfter().get().toSeconds()));
    return ResponseEntity.status(response.getStatusCode())
        .headers(headers)
        .body(response.getBody());
  }

  @ExceptionHandler(ClaimStateMachine.InvalidTransitionException.class)
  ResponseEntity<ProblemDetail> handleTransition(ClaimStateMachine.InvalidTransitionException ex) {
    return problem(ErrorCode.INVALID_TRANSITION, ex.getMessage(), null);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handleUploadSize(MaxUploadSizeExceededException ex) {
    return problem(ErrorCode.DOCUMENT_TOO_LARGE, "Documents must be 10 MB or less.", null);
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
    ConstraintViolationException.class,
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
    return problem(ErrorCode.CONFLICT, "The record was changed at the same time. Try again.", null);
  }

  private static String messageOf(String message) {
    return message == null ? "is not valid" : message;
  }

  private static ResponseEntity<ProblemDetail> problem(
      ErrorCode code, String detail, List<Map<String, String>> errors) {
    return Problems.response(code.status(), code.name(), detail, errors);
  }
}
