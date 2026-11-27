package com.example.insurance.policyservice.policy.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.policyservice.policy.service.ClaimCounterService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which {@code claim.events} types policy-service counts, skips, and sends to the dead letters. */
class ClaimEventListenerTest {

  private static final UUID EVENT = UUID.fromString("5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f");

  private final ClaimCounterService counter = mock(ClaimCounterService.class);
  private final ClaimEventListener listener = new ClaimEventListener(counter);

  private static String message(String type) {
    return "{\"eventId\":\""
        + EVENT
        + "\",\"type\":\""
        + type
        + "\",\"version\":1,\"occurredAt\":\"2026-11-27T10:00:00Z\","
        + "\"aggregateId\":\"c1a1a1a1-2222-4333-8444-555555555555\","
        + "\"insurerId\":\"00000000-0000-4000-8000-00000000000a\",\"correlationId\":\"c\","
        + "\"payload\":{}}";
  }

  @Test
  void aSubmittedClaimIsCounted() {
    listener.onClaimEvent(message("ClaimSubmitted"));

    verify(counter).onClaimSubmitted(eq(EVENT), any());
  }

  @Test
  void theOtherClaimEventsAreSkipped() {
    listener.onClaimEvent(message("ClaimApproved"));
    listener.onClaimEvent(message("ClaimRejected"));
    listener.onClaimEvent(message("ClaimInfoRequested"));

    verifyNoInteractions(counter);
  }

  @Test
  void anUnknownTypeGoesToTheDeadLetterTopic() {
    assertThatThrownBy(() -> listener.onClaimEvent(message("SomethingNew")))
        .isInstanceOf(UnknownEventTypeException.class);
  }
}
