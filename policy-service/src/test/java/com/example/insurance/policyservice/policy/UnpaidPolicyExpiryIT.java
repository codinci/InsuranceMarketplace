package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.policy.service.UnpaidPolicyJob;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Unpaid policies are cancelled after 24 hours and {@code PolicyCancelled(UNPAID)} is published
 * (PLAN 8.2, 17 M4b). The job is run with a chosen "now" rather than by moving the clock.
 */
class UnpaidPolicyExpiryIT extends AbstractPolicyFlowIT {

  @Autowired UnpaidPolicyJob job;
  @Autowired JdbcTemplate jdbc;

  private UUID policyId;
  private UUID paymentId;
  private String policyNumber;
  private Instant createdAt;

  @BeforeEach
  void anUnpaidPolicy() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    MvcResult result = purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true));
    policyId = UUID.fromString(read(result, "$.id"));
    paymentId = UUID.fromString(read(result, "$.paymentId"));
    policyNumber = read(result, "$.policyNumber");
    createdAt =
        java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                    "select created_at from policies where id = ?",
                    java.sql.Timestamp.class,
                    policyId))
            .toInstant();
  }

  @Test
  void aPolicyUnpaidFor23HoursIsKept() throws Exception {
    job.cancelOverdue(createdAt.plus(Duration.ofHours(23)));

    assertThat(status()).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void aPolicyUnpaidFor24HoursAndAMinuteIsCancelled() throws Exception {
    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      job.cancelOverdue(createdAt.plus(Duration.ofHours(24).plusMinutes(1)));

      assertThat(status()).isEqualTo("CANCELLED");
      String cancelled =
          awaitMessage(policyEvents, policyId.toString(), EventTypes.POLICY_CANCELLED);
      assertThat(JsonPath.<String>read(cancelled, "$.payload.reason")).isEqualTo("UNPAID");
      assertThat(JsonPath.<Integer>read(cancelled, "$.payload.policyVersion")).isEqualTo(2);
      assertThat(JsonPath.<String>read(cancelled, "$.payload.policyNumber"))
          .isEqualTo(policyNumber);
    }
  }

  @Test
  void aCancelledPolicyIsNotActivatedByALatePayment() throws Exception {
    job.cancelOverdue(createdAt.plus(Duration.ofHours(25)));

    publish(
        Topics.PAYMENT_EVENTS,
        policyId.toString(),
        EventTypes.PREMIUM_PAID,
        UUID.randomUUID(),
        insurerId,
        new PremiumPaid(
            paymentId,
            policyId,
            policyNumber,
            UUID.randomUUID(),
            new BigDecimal("50175.00"),
            "KES",
            "MOCK-1"));
    Thread.sleep(3_000);

    assertThat(status()).isEqualTo("CANCELLED");
  }

  @Test
  void aPaidPolicyIsNeverCancelled() throws Exception {
    publish(
        Topics.PAYMENT_EVENTS,
        policyId.toString(),
        EventTypes.PREMIUM_PAID,
        UUID.randomUUID(),
        insurerId,
        new PremiumPaid(
            paymentId,
            policyId,
            policyNumber,
            UUID.randomUUID(),
            new BigDecimal("50175.00"),
            "KES",
            "MOCK-1"));
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!"ACTIVE".equals(status()) && System.nanoTime() < deadline) {
      Thread.sleep(200);
    }

    job.cancelOverdue(createdAt.plus(Duration.ofHours(48)));

    assertThat(status()).isEqualTo("ACTIVE");
  }

  private String status() throws Exception {
    return read(
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), customer)).andReturn(),
        "$.status");
  }
}
