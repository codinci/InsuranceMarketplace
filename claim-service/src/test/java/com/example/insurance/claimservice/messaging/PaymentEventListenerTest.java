package com.example.insurance.claimservice.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.insurance.claimservice.service.PayoutOutcomeService;
import com.example.insurance.messaging.UnknownEventTypeException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** Which {@code payment.events} types claim-service uses, skips, and sends to the dead letters. */
class PaymentEventListenerTest {

  private static final UUID EVENT = UUID.fromString("5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f");
  private static final UUID CLAIM = UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555");
  private static final UUID HOLDER = UUID.fromString("11111111-2222-4333-8444-555555555555");

  private final PayoutOutcomeService outcomes = mock(PayoutOutcomeService.class);
  private final PaymentEventListener listener = new PaymentEventListener(outcomes);

  private static String message(String type, String payload) {
    return "{\"eventId\":\""
        + EVENT
        + "\",\"type\":\""
        + type
        + "\",\"version\":1,\"occurredAt\":\"2026-11-22T10:00:00Z\",\"aggregateId\":\""
        + CLAIM
        + "\",\"insurerId\":\"00000000-0000-4000-8000-00000000000a\",\"correlationId\":\"c\","
        + "\"payload\":"
        + payload
        + "}";
  }

  private static String payoutPayload(String extra) {
    return "{\"paymentId\":\""
        + UUID.randomUUID()
        + "\",\"claimId\":\""
        + CLAIM
        + "\",\"claimNumber\":\"CLM-1\",\"holderUserId\":\""
        + HOLDER
        + "\",\"amount\":1200.00,\"currency\":\"KES\","
        + extra
        + "}";
  }

  @Test
  void aSucceededPayoutGoesToTheOutcomeService() {
    listener.onPaymentEvent(
        message(
            "PayoutSucceeded",
            payoutPayload(
                "\"providerRef\":\"mock-payout-1\",\"paidAt\":\"2026-11-22T10:00:00Z\"")));

    verify(outcomes)
        .onPayoutSucceeded(
            ArgumentMatchers.eq(EVENT),
            ArgumentMatchers.argThat(payload -> payload.claimId().equals(CLAIM)));
  }

  @Test
  void aFailedPayoutGoesToTheOutcomeService() {
    listener.onPaymentEvent(
        message(
            "PayoutFailed",
            payoutPayload(
                "\"reason\":\"PROVIDER_DECLINED\",\"failedAt\":\"2026-11-22T10:00:00Z\"")));

    verify(outcomes)
        .onPayoutFailed(
            ArgumentMatchers.eq(EVENT),
            ArgumentMatchers.argThat(payload -> "PROVIDER_DECLINED".equals(payload.reason())));
  }

  @Test
  void premiumEventsAreSkippedWithoutTouchingTheClaim() {
    listener.onPaymentEvent(message("PremiumPaid", "{}"));
    listener.onPaymentEvent(message("PremiumFailed", "{}"));

    verifyNoInteractions(outcomes);
  }

  @Test
  void refundEventsAreSkippedRatherThanDeadLettered() {
    listener.onPaymentEvent(message("RefundSucceeded", "{}"));
    listener.onPaymentEvent(message("RefundFailed", "{}"));

    verifyNoInteractions(outcomes);
  }

  @Test
  void anUnknownTypeIsRefusedSoItGoesToTheDeadLetterTopic() {
    assertThatThrownBy(() -> listener.onPaymentEvent(message("SomethingNew", "{}")))
        .isInstanceOf(UnknownEventTypeException.class);
  }
}
