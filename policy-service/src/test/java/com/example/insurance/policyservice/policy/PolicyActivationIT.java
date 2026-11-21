package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.support.PaymentServiceStub;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Purchase creates the payment in payment-service, and {@code PremiumPaid} on Kafka activates the
 * policy and publishes {@code PolicyActivated} through the outbox (PLAN 17 M4b). payment-service is
 * the HTTP stub; Kafka is a real broker.
 */
class PolicyActivationIT extends AbstractPolicyFlowIT {

  private static final BigDecimal PREMIUM = new BigDecimal("50175.00");

  @Autowired CircuitBreaker paymentCircuitBreaker;

  private UUID quoteId;

  @BeforeEach
  void profileAndQuote() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    quoteId = openQuote(customer);
  }

  @AfterEach
  void closeTheBreaker() {
    PAYMENTS.up();
    paymentCircuitBreaker.reset();
  }

  @Test
  void purchaseAsksPaymentServiceForThePremiumWithAServiceToken() throws Exception {
    MvcResult result = purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));

    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    String policyId = read(result, "$.id");
    assertThat((String) read(result, "$.paymentId"))
        .isEqualTo(PAYMENTS.paymentFor(policyId).toString());
    PaymentServiceStub.Received call =
        PAYMENTS.received().stream()
            .filter(r -> policyId.equals(r.idempotencyKey()))
            .findFirst()
            .orElseThrow();
    assertThat(call.authorization()).isEqualTo("Bearer " + PaymentServiceStub.TOKEN);
    assertThat(JsonPath.<String>read(call.body(), "$.policyId")).isEqualTo(policyId);
    assertThat(JsonPath.<String>read(call.body(), "$.holderUserId"))
        .isEqualTo(customerId.toString());
    assertThat(JsonPath.<String>read(call.body(), "$.insurerId")).isEqualTo(insurerId.toString());
    assertThat(JsonPath.read(call.body(), "$.amount").toString()).isEqualTo("50175.0");
    assertThat(JsonPath.<String>read(call.body(), "$.currency")).isEqualTo("KES");
  }

  @Test
  void aPaidPremiumActivatesThePolicyAndPublishesItsTerms() throws Exception {
    Bought bought = buy();

    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      publishPaid(bought, UUID.randomUUID());

      awaitStatus(bought.policyId(), "ACTIVE");
      String activated =
          awaitMessage(policyEvents, bought.policyId().toString(), EventTypes.POLICY_ACTIVATED);
      assertThat(JsonPath.<String>read(activated, "$.aggregateId"))
          .isEqualTo(bought.policyId().toString());
      assertThat(JsonPath.<String>read(activated, "$.insurerId")).isEqualTo(insurerId.toString());
      assertThat(JsonPath.<Integer>read(activated, "$.payload.policyVersion")).isEqualTo(2);
      assertThat(JsonPath.<String>read(activated, "$.payload.holderUserId"))
          .isEqualTo(customerId.toString());
      assertThat(JsonPath.<String>read(activated, "$.payload.coverStart"))
          .isEqualTo(START.toString());
      List<String> names = JsonPath.read(activated, "$.payload.members[*].firstName");
      assertThat(names).containsExactly("Juma", "Amina", "Neema");
      assertThat(JsonPath.<Integer>read(activated, "$.payload.terms.snapshotVersion")).isEqualTo(1);
    }
  }

  @Test
  void theSamePremiumPaidTwiceActivatesOnce() throws Exception {
    Bought bought = buy();
    UUID eventId = UUID.randomUUID();

    try (KafkaConsumer<String, String> policyEvents = consumerOf(Topics.POLICY_EVENTS)) {
      publishPaid(bought, eventId);
      publishPaid(bought, eventId);

      awaitStatus(bought.policyId(), "ACTIVE");
      List<String> activations =
          messages(
              policyEvents,
              bought.policyId().toString(),
              EventTypes.POLICY_ACTIVATED,
              Duration.ofSeconds(5));
      assertThat(activations).hasSize(1);
    }
  }

  @Test
  void aFailedPaymentLeavesThePolicyAwaitingPayment() throws Exception {
    Bought bought = buy();

    publish(
        Topics.PAYMENT_EVENTS,
        bought.policyId().toString(),
        EventTypes.PREMIUM_FAILED,
        UUID.randomUUID(),
        insurerId,
        new PremiumFailed(
            bought.paymentId(), bought.policyId(), bought.policyNumber(), "PROVIDER_DECLINED"));
    // A later success must still activate it: the failure changed nothing.
    publishPaid(bought, UUID.randomUUID());

    awaitStatus(bought.policyId(), "ACTIVE");
  }

  @Test
  void aPremiumPaidForAnotherPaymentIsIgnored() throws Exception {
    Bought bought = buy();

    publish(
        Topics.PAYMENT_EVENTS,
        bought.policyId().toString(),
        EventTypes.PREMIUM_PAID,
        UUID.randomUUID(),
        insurerId,
        new PremiumPaid(
            UUID.randomUUID(), bought.policyId(), bought.policyNumber(), PREMIUM, "KES", "MOCK-1"));
    Thread.sleep(3_000);

    assertThat(status(bought.policyId())).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void aShortPaymentOutageIsRetried() throws Exception {
    PAYMENTS.failNext(2);

    MvcResult result = purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));

    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
  }

  @Test
  void paymentServiceDownFailsThePurchaseAndKeepsTheQuoteOpen() throws Exception {
    PAYMENTS.down();

    assertProblem(
        purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true)), 503, "PAYMENT_UNAVAILABLE");

    MvcResult quote =
        mockMvc.perform(withToken(get("/api/v1/quotes/" + quoteId), customer)).andReturn();
    assertThat((String) read(quote, "$.status")).isEqualTo("OPEN");
    MvcResult mine = mockMvc.perform(withToken(get("/api/v1/policies"), customer)).andReturn();
    assertThat((List<?>) read(mine, "$.content")).isEmpty();

    PAYMENTS.up();
    paymentCircuitBreaker.reset();
    assertThat(
            purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
  }

  @Test
  void anUnknownEventTypeGoesStraightToTheDeadLetterTopic() throws Exception {
    String key = UUID.randomUUID().toString();

    try (KafkaConsumer<String, String> deadLetters = consumerOf(Topics.PAYMENT_EVENTS + ".DLT")) {
      publish(Topics.PAYMENT_EVENTS, key, "SomethingNew", UUID.randomUUID(), insurerId, "{}");

      assertThat(awaitMessage(deadLetters, key, "SomethingNew")).contains("SomethingNew");
    }
  }

  private record Bought(UUID policyId, UUID paymentId, String policyNumber) {}

  private Bought buy() throws Exception {
    MvcResult result = purchase(customer, purchaseBody(quoteId, PRINCIPAL_DOB, true));
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return new Bought(
        UUID.fromString(read(result, "$.id")),
        UUID.fromString(read(result, "$.paymentId")),
        read(result, "$.policyNumber"));
  }

  private void publishPaid(Bought bought, UUID eventId) throws Exception {
    publish(
        Topics.PAYMENT_EVENTS,
        bought.policyId().toString(),
        EventTypes.PREMIUM_PAID,
        eventId,
        insurerId,
        new PremiumPaid(
            bought.paymentId(),
            bought.policyId(),
            bought.policyNumber(),
            PREMIUM,
            "KES",
            "MOCK-1"));
  }

  private String status(UUID policyId) throws Exception {
    return read(
        mockMvc.perform(withToken(get("/api/v1/policies/" + policyId), customer)).andReturn(),
        "$.status");
  }

  private void awaitStatus(UUID policyId, String expected) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    String current = status(policyId);
    while (!expected.equals(current) && System.nanoTime() < deadline) {
      Thread.sleep(200);
      current = status(policyId);
    }
    assertThat(current).isEqualTo(expected);
  }
}
