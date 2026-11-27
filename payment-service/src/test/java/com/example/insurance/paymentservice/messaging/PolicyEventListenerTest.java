package com.example.insurance.paymentservice.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.paymentservice.service.PaymentExpiryService;
import org.junit.jupiter.api.Test;

/** Which {@code policy.events} types payment-service skips, and which go to the dead letters. */
class PolicyEventListenerTest {

  private final PaymentExpiryService expiry = mock(PaymentExpiryService.class);
  private final PolicyEventListener listener = new PolicyEventListener(expiry);

  private static String message(String type) {
    return "{\"eventId\":\"5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f\",\"type\":\""
        + type
        + "\",\"version\":1,\"occurredAt\":\"2026-11-27T10:00:00Z\","
        + "\"aggregateId\":\"0f8fad5b-d9cb-469f-a165-8c45e88e4a01\","
        + "\"insurerId\":\"00000000-0000-4000-8000-00000000000a\",\"correlationId\":\"c\","
        + "\"payload\":{}}";
  }

  @Test
  void activationAndExpiryAreSkippedWithoutTouchingPayments() {
    listener.onPolicyEvent(message("PolicyActivated"));
    listener.onPolicyEvent(message("PolicyExpired"));

    verifyNoInteractions(expiry);
  }

  @Test
  void anUnknownTypeStillGoesToTheDeadLetterTopic() {
    assertThatThrownBy(() -> listener.onPolicyEvent(message("SomethingNew")))
        .isInstanceOf(UnknownEventTypeException.class);
  }
}
