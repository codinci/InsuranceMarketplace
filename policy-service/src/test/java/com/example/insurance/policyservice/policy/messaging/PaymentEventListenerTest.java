package com.example.insurance.policyservice.policy.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.policyservice.policy.service.PolicyLifecycleService;
import org.junit.jupiter.api.Test;

/** Claim payout events share {@code payment.events}; the policy side must skip them (M7). */
class PaymentEventListenerTest {

  private final PolicyLifecycleService lifecycle = mock(PolicyLifecycleService.class);
  private final PaymentEventListener listener = new PaymentEventListener(lifecycle);

  private static String message(String type) {
    return "{\"eventId\":\"5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f\",\"type\":\""
        + type
        + "\",\"version\":1,\"occurredAt\":\"2026-11-22T10:00:00Z\","
        + "\"aggregateId\":\"c1a1a1a1-2222-4333-8444-555555555555\","
        + "\"insurerId\":\"00000000-0000-4000-8000-00000000000a\",\"correlationId\":\"c\","
        + "\"payload\":{}}";
  }

  @Test
  void payoutEventsAreSkippedWithoutTouchingThePolicy() {
    listener.onPaymentEvent(message("PayoutSucceeded"));
    listener.onPaymentEvent(message("PayoutFailed"));

    verifyNoInteractions(lifecycle);
  }

  @Test
  void refundEventsAreSkippedRatherThanDeadLettered() {
    listener.onPaymentEvent(message("RefundSucceeded"));
    listener.onPaymentEvent(message("RefundFailed"));

    verifyNoInteractions(lifecycle);
  }

  @Test
  void anUnknownTypeStillGoesToTheDeadLetterTopic() {
    assertThatThrownBy(() -> listener.onPaymentEvent(message("SomethingNew")))
        .isInstanceOf(UnknownEventTypeException.class);
  }
}
