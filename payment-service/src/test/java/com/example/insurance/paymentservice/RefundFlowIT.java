package com.example.insurance.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.provider.MockPaymentProvider;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import com.example.insurance.paymentservice.support.TestSecurityConfig;
import com.example.insurance.paymentservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A paid policy cancelled by its holder is refunded once, from {@code PolicyCancelled} (M8b,
 * ADR-021), and an insurer's admin can retry a failed refund.
 */
@SpringBootTest(properties = "messaging.outbox.relay-delay-ms=200")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class RefundFlowIT extends AbstractPaymentIT {

  private static final String PREMIUM = "36500.00";
  private static final String REFUND = "15500.00";
  private static final MockPaymentProvider.Charge DECLINED =
      new MockPaymentProvider.Charge(false, null, MockPaymentProvider.DECLINED);

  @MockitoSpyBean MockPaymentProvider provider;
  @Autowired PaymentRepository payments;

  /** A policy whose premium was paid by its holder; returns the policy id. */
  private UUID paidPolicy() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID premium = idOf(createIntent(TestTokens.service(), policy, PREMIUM, newKey()));
    pay(TestTokens.customer(holderOf(policy)), premium, newKey(), null);
    awaitStatus(TestTokens.customer(holderOf(policy)), premium, "SUCCEEDED");
    return policy;
  }

  private void cancelled(UUID policy, UUID eventId, String refund) throws Exception {
    publish(
        Topics.POLICY_EVENTS,
        policy.toString(),
        EventTypes.POLICY_CANCELLED,
        eventId,
        new PolicyCancelled(
            policy,
            "POL-ACACIA-2026-" + policy.toString().substring(0, 6),
            holderOf(policy),
            3,
            PolicyCancelled.REASON_CUSTOMER,
            LocalDate.of(2027, 7, 30),
            refund == null ? null : new BigDecimal(refund)));
  }

  private MvcResult findRefund(String token, UUID policy) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/payments/refunds")
                .param("policyId", policy.toString())
                .header("Authorization", "Bearer " + token))
        .andReturn();
  }

  private MvcResult retry(String token, UUID paymentId, String key) throws Exception {
    var request = post("/api/v1/payments/{id}/retry-refund", paymentId);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mockMvc.perform(request).andReturn();
  }

  /** Polls the staff lookup until the refund exists and has {@code status}. */
  private MvcResult awaitRefund(UUID policy, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult found = findRefund(TestTokens.platformAdmin(), policy);
      if (found.getResponse().getStatus() == 200 && status.equals(jsonField(found, "$.status"))) {
        return found;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Refund of policy " + policy + " never reached " + status);
  }

  /**
   * The test topics are created on first use with one partition, so a message read after the
   * earlier ones proves they were consumed: a marker policy's refund is that message.
   */
  private void awaitEarlierCancellationsConsumed() throws Exception {
    UUID marker = paidPolicy();
    cancelled(marker, UUID.randomUUID(), "1.00");
    awaitRefund(marker, "SUCCEEDED");
  }

  @Test
  void aPaidPolicyIsRefundedOnceAndTheRefundIsAnnouncedKeyedByThePolicy() throws Exception {
    UUID policy = paidPolicy();

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      cancelled(policy, UUID.randomUUID(), REFUND);
      MvcResult refund = awaitRefund(policy, "SUCCEEDED");

      assertThat(jsonField(refund, "$.referenceType")).isEqualTo("REFUND");
      assertThat(new BigDecimal(JsonPath.read(body(refund), "$.amount").toString()))
          .isEqualByComparingTo(REFUND);
      ConsumerRecord<String, String> event =
          awaitRecord(consumer, policy.toString(), EventTypes.REFUND_SUCCEEDED);
      assertThat(JsonPath.<String>read(event.value(), "$.payload.holderUserId"))
          .isEqualTo(holderOf(policy).toString());
      assertThat(JsonPath.<String>read(event.value(), "$.payload.providerRef"))
          .startsWith("mock-refund-");
      assertThat(JsonPath.<String>read(event.value(), "$.insurerId")).isEqualTo(INSURER.toString());
    }
    // The holder sees both: the premium they paid and the refund they got.
    MvcResult mine = listPayments(TestTokens.customer(holderOf(policy)), policy);
    assertThat(JsonPath.<java.util.List<String>>read(body(mine), "$[*].referenceType"))
        .containsExactlyInAnyOrder("POLICY", "REFUND");
  }

  @Test
  void theSameCancellationDeliveredTwiceOrWithANewIdMakesOneRefund() throws Exception {
    UUID policy = paidPolicy();
    UUID eventId = UUID.randomUUID();

    cancelled(policy, eventId, REFUND);
    UUID refundId = idOf(awaitRefund(policy, "SUCCEEDED"));
    cancelled(policy, eventId, REFUND);
    cancelled(policy, UUID.randomUUID(), REFUND);
    awaitEarlierCancellationsConsumed();

    assertThat(payments.findByReferenceTypeAndReferenceId("REFUND", policy)).isPresent();
    assertThat(transactions.countByPaymentId(refundId)).isEqualTo(1);
  }

  @Test
  void aDeclinedRefundFailsAndAnAdminRetriesItOncePerKey() throws Exception {
    UUID policy = paidPolicy();
    doReturn(DECLINED).when(provider).refund(eq(holderOf(policy)), any(), any());

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      cancelled(policy, UUID.randomUUID(), REFUND);
      UUID refundId = idOf(awaitRefund(policy, "FAILED"));
      awaitMessage(consumer, policy.toString(), EventTypes.REFUND_FAILED);

      org.mockito.Mockito.reset(provider);
      String key = newKey();
      MvcResult paid = retry(TestTokens.insurerAdmin(INSURER), refundId, key);
      assertThat(paid.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(paid, "$.status")).isEqualTo("SUCCEEDED");
      awaitMessage(consumer, policy.toString(), EventTypes.REFUND_SUCCEEDED);

      MvcResult replay = retry(TestTokens.platformAdmin(), refundId, key);
      assertThat(replay.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(replay, "$.status")).isEqualTo("SUCCEEDED");
      assertThat(transactions.countByPaymentId(refundId)).isEqualTo(2);

      MvcResult again = retry(TestTokens.platformAdmin(), refundId, newKey());
      assertThat(again.getResponse().getStatus()).isEqualTo(409);
      assertThat(jsonField(again, "$.code")).isEqualTo("REFUND_NOT_RETRYABLE");
    }
  }

  @Test
  void onlyAnAdminOfTheRightInsurerCanFindOrRetryARefund() throws Exception {
    UUID policy = paidPolicy();
    UUID holder = holderOf(policy);
    doReturn(DECLINED).when(provider).refund(eq(holder), any(), any());
    cancelled(policy, UUID.randomUUID(), REFUND);
    UUID refundId = idOf(awaitRefund(policy, "FAILED"));
    UUID otherInsurer = UUID.randomUUID();

    assertThat(retry(null, refundId, newKey()).getResponse().getStatus()).isEqualTo(401);
    assertThat(retry(TestTokens.customer(holder), refundId, newKey()).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(retry(TestTokens.adjuster(INSURER), refundId, newKey()).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(findRefund(TestTokens.adjuster(INSURER), policy).getResponse().getStatus())
        .isEqualTo(403);

    // Cross-insurer isolation: another insurer's admin sees nothing, as if it did not exist.
    MvcResult foreign = retry(TestTokens.insurerAdmin(otherInsurer), refundId, newKey());
    assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
    assertThat(jsonField(foreign, "$.code")).isEqualTo("PAYMENT_NOT_FOUND");
    assertThat(findRefund(TestTokens.insurerAdmin(otherInsurer), policy).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(findRefund(TestTokens.insurerAdmin(INSURER), policy).getResponse().getStatus())
        .isEqualTo(200);

    // A refund is not a payout, and the holder cannot pay it.
    assertThat(
            mockMvc
                .perform(
                    post("/api/v1/payments/{id}/retry-payout", refundId)
                        .header("Authorization", "Bearer " + TestTokens.platformAdmin())
                        .header("Idempotency-Key", newKey()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    MvcResult paid = pay(TestTokens.customer(holder), refundId, newKey(), null);
    assertThat(paid.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(paid, "$.code")).isEqualTo("PAYMENT_NOT_PAYABLE");
    assertThat(transactions.countByPaymentId(refundId)).isEqualTo(1);
  }

  @Test
  void noRefundIsMadeForAZeroRefundAnUnpaidPremiumOrMoreThanWasPaid() throws Exception {
    UUID zero = paidPolicy();
    UUID tooMuch = paidPolicy();
    UUID unpaid = UUID.randomUUID();
    UUID unpaidPremium = idOf(createIntent(TestTokens.service(), unpaid, PREMIUM, newKey()));

    cancelled(zero, UUID.randomUUID(), "0.00");
    cancelled(tooMuch, UUID.randomUUID(), "36500.01");
    cancelled(unpaid, UUID.randomUUID(), "0.00");
    awaitEarlierCancellationsConsumed();
    awaitStatus(TestTokens.customer(holderOf(unpaid)), unpaidPremium, "EXPIRED");

    assertThat(payments.findByReferenceTypeAndReferenceId("REFUND", zero)).isEmpty();
    assertThat(payments.findByReferenceTypeAndReferenceId("REFUND", tooMuch)).isEmpty();
    assertThat(payments.findByReferenceTypeAndReferenceId("REFUND", unpaid)).isEmpty();
  }
}
