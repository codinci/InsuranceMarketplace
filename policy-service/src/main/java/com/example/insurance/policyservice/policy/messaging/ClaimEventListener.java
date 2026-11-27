package com.example.insurance.policyservice.policy.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.policyservice.policy.service.ClaimCounterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads {@code claim.events} only to count claims per policy (M8b, ADR-021). Every other known
 * claim type is skipped by name, so a new type still goes to the dead-letter topic.
 */
@Component
public class ClaimEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(ClaimEventListener.class);

  private final ClaimCounterService counter;

  public ClaimEventListener(ClaimCounterService counter) {
    this.counter = counter;
  }

  @KafkaListener(topics = Topics.CLAIM_EVENTS)
  public void onClaimEvent(String message) {
    String type = EventJson.typeOf(message);
    if (EventTypes.CLAIM_SUBMITTED.equals(type)) {
      EventEnvelope<ClaimSubmitted> event = EventJson.envelope(message, ClaimSubmitted.class);
      counter.onClaimSubmitted(event.eventId(), event.payload());
    } else if (EventTypes.CLAIM_APPROVED.equals(type)
        || EventTypes.CLAIM_REJECTED.equals(type)
        || EventTypes.CLAIM_INFO_REQUESTED.equals(type)) {
      LOG.debug("{} is not used by policy-service; skipped.", type);
    } else {
      throw new UnknownEventTypeException(type);
    }
  }
}
