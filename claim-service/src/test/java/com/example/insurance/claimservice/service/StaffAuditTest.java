package com.example.insurance.claimservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The audit trail of staff actions (PLAN 12, M6): one line for every success, refusal and failure,
 * with identifiers and a stable code, and never the free text a reviewer typed or a request body.
 */
class StaffAuditTest {

  private static final UUID CLAIM = UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555");
  private static final UUID ACTOR = UUID.fromString("a0a0a0a0-1111-4222-8333-444444444444");
  private static final UUID INSURER = UUID.fromString("b0b0b0b0-1111-4222-8333-444444444444");

  private final StaffAudit audit = new StaffAudit();
  private final Actor staff = new Actor(ACTOR, "ADJUSTER", INSURER);
  private final Logger logger = (Logger) LoggerFactory.getLogger("audit");
  private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

  @BeforeEach
  void capture() {
    lines.start();
    logger.addAppender(lines);
  }

  @AfterEach
  void release() {
    logger.detachAppender(lines);
  }

  @Test
  void aSuccessIsLoggedWithItsIdentifiersAndPassesTheResultOn() {
    String result = audit.run(staff, "APPROVE", CLAIM, () -> "done");

    assertThat(result).isEqualTo("done");
    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.INFO);
    assertThat(pairs(line))
        .containsEntry("event", "staff_action")
        .containsEntry("action", "APPROVE")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("claimId", CLAIM.toString())
        .containsEntry("actorId", ACTOR.toString())
        .containsEntry("role", "ADJUSTER")
        .containsEntry("insurerId", INSURER.toString())
        .doesNotContainKey("code");
  }

  @Test
  void aRefusalIsLoggedAsAWarningWithItsStableCodeAndRethrown() {
    ApiException refusal =
        new ApiException(ErrorCode.ADMIN_APPROVAL_REQUIRED, "Only an admin can approve.");

    assertThatThrownBy(() ->
            audit.run(
                staff,
                "APPROVE",
                CLAIM,
                () -> {
                  throw refusal;
                }))
        .isSameAs(refusal);

    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.WARN);
    assertThat(pairs(line))
        .containsEntry("outcome", "REFUSED")
        .containsEntry("code", "ADMIN_APPROVAL_REQUIRED")
        .containsEntry("action", "APPROVE");
  }

  @Test
  void anUnexpectedFailureIsLoggedAsAnErrorWithTheExceptionTypeAndRethrown() {
    IllegalStateException failure = new IllegalStateException("The database is gone.");

    assertThatThrownBy(() ->
            audit.run(
                staff,
                "REJECT",
                CLAIM,
                () -> {
                  throw failure;
                }))
        .isSameAs(failure);

    ILoggingEvent line = onlyLine();
    assertThat(line.getLevel()).isEqualTo(Level.ERROR);
    assertThat(pairs(line))
        .containsEntry("outcome", "ERROR")
        .containsEntry("code", "IllegalStateException");
  }

  @Test
  void aLineNeverCarriesTheMessageOfTheErrorOrAnyFreeText() {
    // The refusal's message and an actor's typed words must not reach the log.
    ApiException refusal =
        new ApiException(
            ErrorCode.APPROVAL_EXCEEDS_PAYABLE, "Patient Jane Doe 0722000000 owes 9999.");

    assertThatThrownBy(() ->
            audit.run(
                staff,
                "APPROVE",
                CLAIM,
                () -> {
                  throw refusal;
                }))
        .isInstanceOf(ApiException.class);

    ILoggingEvent line = onlyLine();
    assertThat(line.getFormattedMessage()).doesNotContain("Jane").doesNotContain("0722");
    assertThat(pairs(line).values())
        .noneMatch(value -> value.contains("Jane") || value.contains("0722"));
  }

  @Test
  void aPlatformAdminHasNoInsurerInTheLine() {
    Actor platform = new Actor(ACTOR, "ADMIN", null);

    audit.run(platform, "START_REVIEW", CLAIM, () -> "ok");

    assertThat(pairs(onlyLine())).containsEntry("role", "ADMIN").containsEntry("insurerId", "null");
  }

  private ILoggingEvent onlyLine() {
    assertThat(lines.list).hasSize(1);
    return lines.list.get(0);
  }

  private static Map<String, String> pairs(ILoggingEvent line) {
    return line.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
  }
}
