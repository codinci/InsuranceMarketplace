package com.example.insurance.common.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemsTest {

  @Test
  void aProblemCarriesTheStatusTitleDetailAndCode() {
    ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "THING_TAKEN", "Already there.");
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Conflict");
    assertThat(problem.getDetail()).isEqualTo("Already there.");
    assertThat(problem.getProperties()).containsEntry("code", "THING_TAKEN");
  }

  @Test
  void errorsAreAddedOnlyWhenGiven() {
    ResponseEntity<ProblemDetail> withErrors =
        Problems.response(
            HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Bad.", List.of(Map.of("field", "x")));
    ResponseEntity<ProblemDetail> without =
        Problems.response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Missing.", null);

    assertThat(withErrors.getStatusCode().value()).isEqualTo(400);
    assertThat(withErrors.getBody().getProperties()).containsKey("errors");
    assertThat(without.getBody().getProperties()).doesNotContainKey("errors");
  }
}
