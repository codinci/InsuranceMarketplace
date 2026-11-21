package com.example.insurance.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.events.EventTypes;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.support.TestSecurityConfig;
import com.example.insurance.paymentservice.support.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The payment journey through HTTP, the database and Kafka: creation, paying, the outbox, the
 * inbox, ownership and the dead-letter route (PLAN 17, M4b).
 */
@SpringBootTest(properties = "payment.outbox.relay-delay-ms=200")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class PaymentFlowIT extends AbstractPaymentIT {

  private static final String AMOUNT = "12500.00";

  @Test
  void onlyAServiceTokenCanCreateAPremium() throws Exception {
    UUID policy = UUID.randomUUID();
    String key = newKey();

    assertThat(createIntent(null, policy, AMOUNT, key).getResponse().getStatus()).isEqualTo(401);
    assertThat(
            createIntent(TestTokens.customer(holderOf(policy)), policy, AMOUNT, key)
                .getResponse()
                .getStatus())
        .isEqualTo(403);

    MvcResult created = createIntent(TestTokens.service(), policy, AMOUNT, key);
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(jsonField(created, "$.status")).isEqualTo("PENDING");
    assertThat(jsonField(created, "$.referenceType")).isEqualTo("POLICY");
  }

  @Test
  void aRetriedCreationReturnsTheSamePaymentAndAReusedKeyForAnotherRequestIsRefused()
      throws Exception {
    UUID policy = UUID.randomUUID();
    String key = newKey();
    MvcResult first = createIntent(TestTokens.service(), policy, AMOUNT, key);
    MvcResult retry = createIntent(TestTokens.service(), policy, AMOUNT, key);

    assertThat(retry.getResponse().getStatus()).isEqualTo(201);
    assertThat(idOf(retry)).isEqualTo(idOf(first));

    MvcResult different = createIntent(TestTokens.service(), policy, "12501.00", key);
    assertThat(different.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(different, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");

    MvcResult noKey = createIntent(TestTokens.service(), UUID.randomUUID(), AMOUNT, null);
    assertThat(noKey.getResponse().getStatus()).isEqualTo(400);
    assertThat(jsonField(noKey, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
  }

  @Test
  void aSuccessfulPaymentIsPublishedAsPremiumPaidKeyedByThePolicy() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      MvcResult paid = pay(token, paymentId, newKey(), null);

      assertThat(paid.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(paid, "$.status")).isEqualTo("SUCCEEDED");
      assertThat(jsonField(paid, "$.providerRef")).startsWith("mock-");

      String event = awaitMessage(consumer, policy.toString(), EventTypes.PREMIUM_PAID);
      assertThat(field(event, "$.aggregateId")).isEqualTo(policy.toString());
      assertThat(field(event, "$.insurerId")).isEqualTo(INSURER.toString());
      assertThat(field(event, "$.version")).isEqualTo("1");
      assertThat(field(event, "$.payload.paymentId")).isEqualTo(paymentId.toString());
      assertThat(event).contains("\"amount\":12500.00");
    }
  }

  @Test
  void aRepeatedPayWithTheSameKeyDoesNotChargeTwiceAndANewKeyAfterPaymentIsRefused()
      throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));
    String key = newKey();

    MvcResult first = pay(token, paymentId, key, null);
    MvcResult replay = pay(token, paymentId, key, null);

    assertThat(replay.getResponse().getStatus()).isEqualTo(200);
    assertThat(jsonField(replay, "$.status")).isEqualTo("SUCCEEDED");
    assertThat(jsonField(replay, "$.providerRef")).isEqualTo(jsonField(first, "$.providerRef"));
    assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(1);

    MvcResult another = pay(token, paymentId, newKey(), null);
    assertThat(another.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(another, "$.code")).isEqualTo("PAYMENT_ALREADY_PAID");
  }

  @Test
  void aDeclinedPaymentPublishesPremiumFailedAndCanBePaidWithANewKey() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));
    String declinedKey = newKey();

    try (KafkaConsumer<String, String> consumer = consumerOf(Topics.PAYMENT_EVENTS)) {
      MvcResult declined = pay(token, paymentId, declinedKey, "FAIL");
      assertThat(declined.getResponse().getStatus()).isEqualTo(200);
      assertThat(jsonField(declined, "$.status")).isEqualTo("FAILED");

      String event = awaitMessage(consumer, policy.toString(), EventTypes.PREMIUM_FAILED);
      assertThat(field(event, "$.payload.reason")).isEqualTo("PROVIDER_DECLINED");
    }

    MvcResult replayOfDecline = pay(token, paymentId, declinedKey, null);
    assertThat(jsonField(replayOfDecline, "$.status")).isEqualTo("FAILED");

    MvcResult retried = pay(token, paymentId, newKey(), null);
    assertThat(retried.getResponse().getStatus()).isEqualTo(200);
    assertThat(jsonField(retried, "$.status")).isEqualTo("SUCCEEDED");
  }

  @Test
  void parallelPayRequestsWithOneKeyLeaveExactlyOneLedgerRow() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));
    String key = newKey();

    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Callable<Integer>> attempts = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        attempts.add(() -> pay(token, paymentId, key, null).getResponse().getStatus());
      }
      List<Integer> statuses = new ArrayList<>();
      for (var future : pool.invokeAll(attempts)) {
        statuses.add(future.get());
      }

      assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(200, 409));
      assertThat(statuses).contains(200);
      assertThat(transactions.countByPaymentId(paymentId)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void anotherCustomersPaymentIsNotFoundAndStaffCannotUseCustomerEndpoints() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String stranger = TestTokens.customer(UUID.randomUUID());

    assertThat(getPayment(stranger, paymentId).getResponse().getStatus()).isEqualTo(404);
    assertThat(jsonField(getPayment(stranger, paymentId), "$.code")).isEqualTo("PAYMENT_NOT_FOUND");
    MvcResult strangerPays = pay(stranger, paymentId, newKey(), null);
    assertThat(strangerPays.getResponse().getStatus()).isEqualTo(404);

    MvcResult strangerLists = listPayments(stranger, policy);
    assertThat(strangerLists.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(strangerLists)).isEqualTo("[]");

    String staff = TestTokens.insurerAdmin(INSURER);
    assertThat(getPayment(staff, paymentId).getResponse().getStatus()).isEqualTo(403);
    assertThat(pay(staff, paymentId, newKey(), null).getResponse().getStatus()).isEqualTo(403);

    assertThat(
            mockMvc
                .perform(get("/api/v1/payments/{id}", paymentId))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void aCancelledUnpaidPolicyExpiresItsPaymentAndADuplicateCancellationChangesNothing()
      throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));
    UUID eventId = UUID.randomUUID();

    publish(
        Topics.POLICY_EVENTS,
        policy.toString(),
        EventTypes.POLICY_CANCELLED,
        eventId,
        policyCancelledPayload(policy));
    awaitStatus(token, paymentId, "EXPIRED");

    MvcResult late = pay(token, paymentId, newKey(), null);
    assertThat(late.getResponse().getStatus()).isEqualTo(409);
    assertThat(jsonField(late, "$.code")).isEqualTo("PAYMENT_EXPIRED");

    publish(
        Topics.POLICY_EVENTS,
        policy.toString(),
        EventTypes.POLICY_CANCELLED,
        eventId,
        policyCancelledPayload(policy));
    Thread.sleep(1_000);
    assertThat(jsonField(getPayment(token, paymentId), "$.status")).isEqualTo("EXPIRED");
  }

  @Test
  void aCancellationDoesNotUndoAPaymentThatAlreadySucceeded() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID paymentId = idOf(createIntent(TestTokens.service(), policy, AMOUNT, newKey()));
    String token = TestTokens.customer(holderOf(policy));
    pay(token, paymentId, newKey(), null);

    publish(
        Topics.POLICY_EVENTS,
        policy.toString(),
        EventTypes.POLICY_CANCELLED,
        UUID.randomUUID(),
        policyCancelledPayload(policy));
    Thread.sleep(3_000);

    assertThat(jsonField(getPayment(token, paymentId), "$.status")).isEqualTo("SUCCEEDED");
  }

  @Test
  void anUnknownEventTypeGoesStraightToTheDeadLetterTopic() throws Exception {
    String key = UUID.randomUUID().toString();

    try (KafkaConsumer<String, String> deadLetters = consumerOf(Topics.POLICY_EVENTS + ".DLT")) {
      publish(Topics.POLICY_EVENTS, key, "SomethingNew", UUID.randomUUID(), "{}");

      String dead = awaitMessage(deadLetters, key, "SomethingNew");
      assertThat(dead).contains("SomethingNew");
    }
  }

  private static String field(String json, String path) {
    return com.jayway.jsonpath.JsonPath.read(json, path).toString();
  }
}
