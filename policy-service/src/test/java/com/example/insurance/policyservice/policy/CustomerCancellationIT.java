package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.node.ObjectNode;

/**
 * A holder cancels their own policy (M8b, D7, ADR-021). The refund follows the plan's data: the
 * Acacia rule (pro-rata, none after six months, none once a claim was made) and the Baobab rule
 * (none), with the same code.
 */
class CustomerCancellationIT extends AbstractPolicyFlowIT {

  @Autowired JdbcTemplate jdbc;

  private UUID policyId;
  private UUID paymentId;
  private String policyNumber;

  @BeforeEach
  void profile() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
  }

  @Test
  void acaciaRefundsTheUnusedDaysAndAnnouncesTheRefund() throws Exception {
    activePolicy();
    BigDecimal premium = premium();
    LocalDate end = START.plusYears(1).minusDays(1);
    LocalDate effective = TODAY.plusDays(30);
    BigDecimal expected =
        premium
            .multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(effective, end) + 1))
            .divide(
                BigDecimal.valueOf(ChronoUnit.DAYS.between(START, end) + 1),
                2,
                RoundingMode.HALF_UP);

    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      MvcResult result = cancel(customer);

      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat((String) read(result, "$.status")).isEqualTo("CANCELLED");
      assertThat((String) read(result, "$.cancelReason")).isEqualTo("CUSTOMER");
      assertThat((String) read(result, "$.cancelEffectiveDate")).isEqualTo(effective.toString());
      assertThat(refundOf(result)).isEqualByComparingTo(expected);
      assertThat(expected).isPositive().isLessThan(premium);

      String event = awaitMessage(policyEvents, policyId.toString(), EventTypes.POLICY_CANCELLED);
      assertThat(JsonPath.<String>read(event, "$.payload.reason")).isEqualTo("CUSTOMER");
      assertThat(JsonPath.<Integer>read(event, "$.payload.policyVersion")).isEqualTo(3);
      assertThat(JsonPath.<String>read(event, "$.payload.effectiveDate"))
          .isEqualTo(effective.toString());
      assertThat(new BigDecimal(JsonPath.read(event, "$.payload.refundAmount").toString()))
          .isEqualByComparingTo(expected);
      assertThat(JsonPath.<String>read(event, "$.payload.holderUserId"))
          .isEqualTo(customerId.toString());
    }
  }

  @Test
  void acaciaRefundsNothingOnceAClaimWasSubmitted() throws Exception {
    activePolicy();
    publish(
        Topics.CLAIM_EVENTS,
        UUID.randomUUID().toString(),
        EventTypes.CLAIM_SUBMITTED,
        UUID.randomUUID(),
        insurerId,
        claimSubmitted(UUID.randomUUID()));
    awaitClaims(1);

    MvcResult result = cancel(customer);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(refundOf(result)).isEqualByComparingTo("0.00");
  }

  @Test
  void acaciaRefundsNothingSixMonthsIntoCover() throws Exception {
    activePolicy();
    LocalDate start = TODAY.minusMonths(6);
    jdbc.update(
        "update policies set start_date = ?, end_date = ? where id = ?",
        start,
        start.plusYears(1).minusDays(1),
        policyId);

    MvcResult result = cancel(customer);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(refundOf(result)).isEqualByComparingTo("0.00");
  }

  @Test
  void aBaobabStyleRuleRefundsNothingWithItsOwnNotice() throws Exception {
    planId =
        publishedPlan(
            insurerId,
            planJson(
                "acacia-standard.v1.json",
                insurerCode,
                version(2)
                    .andThen(plan ->
                        ((ObjectNode) plan.get("plan"))
                            .set(
                                "cancellation",
                                tools.jackson.databind.json.JsonMapper.builder()
                                    .build()
                                    .readTree(
                                        "{\"noticeDays\":14,\"refund\":{\"model\":\"NONE\"}}")))));
    activePolicy();

    MvcResult result = cancel(customer);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(refundOf(result)).isEqualByComparingTo("0.00");
    assertThat((String) read(result, "$.cancelEffectiveDate"))
        .isEqualTo(TODAY.plusDays(14).toString());
  }

  @Test
  void aPolicyAwaitingPaymentIsCancelledAtOnceWithoutARefund() throws Exception {
    pendingPolicy();

    MvcResult result = cancel(customer);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(result, "$.status")).isEqualTo("CANCELLED");
    assertThat((String) read(result, "$.cancelEffectiveDate")).isEqualTo(TODAY.toString());
    assertThat(refundOf(result)).isEqualByComparingTo("0.00");
  }

  @Test
  void aCancelledPolicyCannotBeCancelledAgain() throws Exception {
    activePolicy();
    cancel(customer);

    assertProblem(cancel(customer), 409, "POLICY_NOT_CANCELLABLE");
  }

  @Test
  void aNoticePeriodPastTheEndOfCoverIsRefused() throws Exception {
    activePolicy();
    jdbc.update(
        "update policies set start_date = ?, end_date = ? where id = ?",
        TODAY.minusYears(1).plusDays(5),
        TODAY.plusDays(5),
        policyId);

    assertProblem(cancel(customer), 422, "NOTICE_PAST_END");
    assertThat(status()).isEqualTo("ACTIVE");
  }

  @Test
  void anotherCustomerGetsNotFoundAndChangesNothing() throws Exception {
    activePolicy();

    assertProblem(cancel(TestTokens.customer(UUID.randomUUID())), 404, "POLICY_NOT_FOUND");
    assertThat(status()).isEqualTo("ACTIVE");
  }

  private MvcResult cancel(String token) throws Exception {
    return mockMvc
        .perform(withToken(post("/api/v1/policies/" + policyId + "/cancel"), token))
        .andReturn();
  }

  private void pendingPolicy() throws Exception {
    MvcResult result = purchase(customer, purchaseBody(openQuote(customer), PRINCIPAL_DOB, true));
    policyId = UUID.fromString(read(result, "$.id"));
    paymentId = UUID.fromString(read(result, "$.paymentId"));
    policyNumber = read(result, "$.policyNumber");
  }

  private void activePolicy() throws Exception {
    pendingPolicy();
    publish(
        Topics.PAYMENT_EVENTS,
        policyId.toString(),
        EventTypes.PREMIUM_PAID,
        UUID.randomUUID(),
        insurerId,
        new PremiumPaid(paymentId, policyId, policyNumber, customerId, premium(), "KES", "MOCK-1"));
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!"ACTIVE".equals(status()) && System.nanoTime() < deadline) {
      Thread.sleep(200);
    }
    assertThat(status()).isEqualTo("ACTIVE");
  }

  private ClaimSubmitted claimSubmitted(UUID claimId) {
    return new ClaimSubmitted(
        claimId,
        "CLM-TEST-1",
        policyId,
        customerId,
        "m1",
        "OUTPATIENT",
        "OP-CONSULT",
        "ILLNESS",
        TODAY,
        new BigDecimal("1500.00"),
        "KES",
        Instant.now());
  }

  private void awaitClaims(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (claims() < expected && System.nanoTime() < deadline) {
      Thread.sleep(200);
    }
    assertThat(claims()).isEqualTo(expected);
  }

  private int claims() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from policy_claims where policy_id = ?", Integer.class, policyId);
    return count == null ? 0 : count;
  }

  private BigDecimal premium() {
    return jdbc.queryForObject(
        "select annual_premium from policies where id = ?", BigDecimal.class, policyId);
  }

  private static BigDecimal refundOf(MvcResult result) throws Exception {
    Object refund = read(result, "$.refundAmount");
    return new BigDecimal(refund.toString());
  }

  private String status() throws Exception {
    return read(
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), customer)).andReturn(),
        "$.status");
  }
}
