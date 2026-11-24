package com.example.insurance.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.provider.MockPaymentProvider;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import com.example.insurance.paymentservice.support.TestSecurityConfig;
import com.example.insurance.paymentservice.support.TestTokens;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * The payout saga on the payment side (PLAN 10, M7): an approved claim is paid once, the outcome is
 * published, a failed payout is retried by staff of the right insurer, and redelivery changes
 * nothing.
 */
@SpringBootTest(properties = "messaging.outbox.relay-delay-ms=200")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class PayoutFlowIT extends AbstractPaymentIT {

  private static final String AMOUNT = "1200.00";
  private static final MockPaymentProvider.Charge DECLINED =
      new MockPaymentProvider.Charge(false, null, MockPaymentProvider.DECLINED);

  @MockitoSpyBean MockPaymentProvider provider;
  @Autowired PaymentRepository payments;

  private static UUID newHolder() {
    return UUID.randomUUID();
  }

  private static ClaimApproved approved(UUID claimId, UUID holder) {
    return new ClaimApproved(
        claimId,
        "CLM-ACACIA-2026-" + claimId.toString().substring(0, 6),
        UUID.randomUUID(),
        holder,
        "M1",
        "OUTPATIENT",
        new BigDecimal(AMOUNT),
        "KES",
        Instant.now());
  }

  private void approve(UUID claimId, UUID holder, UUID eventId) throws Exception {
    publish(
        Topics.CLAIM_EVENTS,
        claimId.toString(),
        EventTypes.CLAIM_APPROVED,
        eventId,
        approved(claimId, holder));
  }

  private MvcResult findPayout(String token, UUID claimId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/payments/payouts")
                .param("claimId", claimId.toString())
                .header("Authorization", "Bearer " + token))
        .andReturn();
  }

  private MvcResult retry(String token, UUID paymentId, String key) throws Exception {
    var request = post("/api/v1/payments/{id}/retry-payout", paymentId);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mockMvc.perform(request).andReturn();
  }

  /** Polls the staff lookup until the payout exists and has {@code status}. */
  private MvcResult awaitPayout(UUID claimId, String status) throws Exception {
    String admin = TestTokens.platformAdmin();
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    MvcResult last = null;
    while (System.nanoTime() < deadline) {
      last = findPayout(admin, claimId);
      if (last.getResponse().getStatus() == 200 && status.equals(jsonField(last, "$.status"))) {
        return last;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Payout of claim " + claimId + " never reached " + status);
  }

  @Test
  void anApprovedClaimIsPaidOnceAndPublishedAsPayoutSucceededKeyedByTheClaim() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      approve(claimId, holder, UUID.randomUUID());

      MvcResult payout = awaitPayout(claimId, "SUCCEEDED");
      assertThat(jsonField(payout, "$.referenceType")).isEqualTo("CLAIM");
      assertThat(body(payout)).contains("\"amount\":1200.00");
      assertThat(jsonField(payout, "$.providerRef")).startsWith("mock-payout-");

      ConsumerRecord<String, String> record =
          awaitRecord(consumer, claimId.toString(), EventTypes.PAYOUT_SUCCEEDED);
      assertThat(record.value())
          .contains("\"claimId\":\"" + claimId + "\"")
          .contains("\"holderUserId\":\"" + holder + "\"");
      assertThat(transactions.countByPaymentId(idOf(payout))).isEqualTo(1);
    }
  }

  @Test
  void theSameClaimApprovedEventDeliveredTwiceOrWithANewIdMakesOnePayout() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();
    UUID eventId = UUID.randomUUID();

    approve(claimId, holder, eventId);
    MvcResult payout = awaitPayout(claimId, "SUCCEEDED");
    approve(claimId, holder, eventId);
    approve(claimId, holder, UUID.randomUUID());
    // A later claim proves the two repeats were consumed: it is on the same partition key space.
    UUID other = UUID.randomUUID();
    approve(other, newHolder(), UUID.randomUUID());
    awaitPayout(other, "SUCCEEDED");
    Thread.sleep(1500);

    assertThat(payments.findByReferenceTypeAndReferenceId("CLAIM", claimId)).isPresent();
    assertThat(transactions.countByPaymentId(idOf(payout))).isEqualTo(1);
  }

  @Test
  void aDeclinedPayoutFailsPublishesPayoutFailedAndCanBeRetriedToSuccess() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();
    doReturn(DECLINED).when(provider).payout(eq(holder), any(), any());

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      approve(claimId, holder, UUID.randomUUID());
      MvcResult failed = awaitPayout(claimId, "FAILED");
      awaitMessage(consumer, claimId.toString(), EventTypes.PAYOUT_FAILED);
      UUID paymentId = idOf(failed);

      // Still declined: a second failed attempt, a second PayoutFailed, still FAILED.
      MvcResult again = retry(TestTokens.platformAdmin(), paymentId, newKey());
      assertThat(again.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(again, "$.status")).isEqualTo("FAILED");
      assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(2);

      // The provider recovers: the retry succeeds and PayoutSucceeded follows.
      org.mockito.Mockito.reset(provider);
      MvcResult paid = retry(TestTokens.insurerAdmin(INSURER), paymentId, newKey());
      assertThat(paid.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(paid, "$.status")).isEqualTo("SUCCEEDED");
      awaitMessage(consumer, claimId.toString(), EventTypes.PAYOUT_SUCCEEDED);
      assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(3);
    }
  }

  @Test
  void aRetryWithTheSameKeyReplaysAndParallelRetriesLeaveOneLedgerRow() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();
    doReturn(DECLINED).when(provider).payout(eq(holder), any(), any());
    approve(claimId, holder, UUID.randomUUID());
    UUID paymentId = idOf(awaitPayout(claimId, "FAILED"));
    String admin = TestTokens.platformAdmin();
    String key = newKey();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        calls.add(() -> retry(admin, paymentId, key).getResponse().getStatus());
      }
      for (var future : pool.invokeAll(calls)) {
        assertThat(future.get()).isIn(200, 409);
      }
    } finally {
      pool.shutdownNow();
    }
    MvcResult replay = retry(admin, paymentId, key);

    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(2);
  }

  @Test
  void onlyAnAdminOfTheRightInsurerCanFindOrRetryAPayout() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();
    doReturn(DECLINED).when(provider).payout(eq(holder), any(), any());
    approve(claimId, holder, UUID.randomUUID());
    UUID paymentId = idOf(awaitPayout(claimId, "FAILED"));
    UUID otherInsurer = UUID.randomUUID();

    assertThat(retry(null, paymentId, newKey()).getResponse().getStatus()).isEqualTo(401);
    assertThat(retry(TestTokens.customer(holder), paymentId, newKey()).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(retry(TestTokens.adjuster(INSURER), paymentId, newKey()).getResponse().getStatus())
        .isEqualTo(403);
    assertThat(findPayout(TestTokens.adjuster(INSURER), claimId).getResponse().getStatus())
        .isEqualTo(403);

    // Cross-insurer isolation: another insurer's admin sees nothing, as if it did not exist.
    MvcResult foreignRetry = retry(TestTokens.insurerAdmin(otherInsurer), paymentId, newKey());
    assertThat(foreignRetry.getResponse().getStatus()).isEqualTo(404);
    assertThat(jsonField(foreignRetry, "$.code")).isEqualTo("PAYMENT_NOT_FOUND");
    assertThat(findPayout(TestTokens.insurerAdmin(otherInsurer), claimId).getResponse().getStatus())
        .isEqualTo(404);
    assertThat(findPayout(TestTokens.insurerAdmin(INSURER), claimId).getResponse().getStatus())
        .isEqualTo(200);

    MvcResult noKey = retry(TestTokens.platformAdmin(), paymentId, null);
    assertThat(noKey.getResponse().getStatus()).isEqualTo(400);
    assertThat(jsonField(noKey, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(1);
  }

  @Test
  void aPayoutThatAlreadySucceededCannotBeRetriedAndACustomerCannotPayOne() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID holder = newHolder();
    approve(claimId, holder, UUID.randomUUID());
    UUID paymentId = idOf(awaitPayout(claimId, "SUCCEEDED"));

    MvcResult retried = retry(TestTokens.platformAdmin(), paymentId, newKey());
    assertThat(retried.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(retried, "$.code")).isEqualTo("PAYOUT_NOT_RETRYABLE");

    MvcResult paid = pay(TestTokens.customer(holder), paymentId, newKey(), null);
    assertThat(paid.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(paid, "$.code")).isEqualTo("PAYMENT_NOT_PAYABLE");
    // The holder can still read their own payout.
    assertThat(jsonField(getPayment(TestTokens.customer(holder), paymentId), "$.status"))
        .isEqualTo("SUCCEEDED");
    assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(1);
  }

  @Test
  void aPremiumCannotBeRetriedAsAPayout() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID premiumId = idOf(createIntent(TestTokens.service(), policy, "12500.00", newKey()));

    MvcResult retried = retry(TestTokens.platformAdmin(), premiumId, newKey());

    assertThat(retried.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void otherClaimEventsAreSkippedAndUnknownOnesGoToTheDeadLetterTopic() throws Exception {
    UUID rejectedClaim = UUID.randomUUID();
    String unknownKey = UUID.randomUUID().toString();

    try (KafkaConsumer<String, String> deadLetters = consumerOf(Topics.CLAIM_EVENTS + ".DLT")) {
      publish(
          Topics.CLAIM_EVENTS,
          rejectedClaim.toString(),
          EventTypes.CLAIM_REJECTED,
          UUID.randomUUID(),
          "{}");
      publish(Topics.CLAIM_EVENTS, unknownKey, "SomethingNew", UUID.randomUUID(), "{}");

      assertThat(awaitMessage(deadLetters, unknownKey, "SomethingNew")).contains("SomethingNew");
      // The rejected event was read before the unknown one on the same topic and never
      // dead-lettered.
      for (ConsumerRecord<String, String> record : deadLetters.poll(Duration.ofSeconds(1))) {
        assertThat(record.key()).isNotEqualTo(rejectedClaim.toString());
      }
      assertThat(findPayout(TestTokens.platformAdmin(), rejectedClaim).getResponse().getStatus())
          .isEqualTo(404);
    }
  }
}
