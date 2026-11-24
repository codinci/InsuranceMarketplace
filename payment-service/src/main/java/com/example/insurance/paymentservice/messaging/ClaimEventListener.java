package com.example.insurance.paymentservice.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.paymentservice.service.PayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads {@code claim.events} and pays approved claims (PLAN 10, M7). The other claim events are not
 * for this service and are skipped; unknown types go to the dead-letter topic (ADR-005).
 */
@Component
public class ClaimEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(ClaimEventListener.class);

  private final PayoutService payouts;

  public ClaimEventListener(PayoutService payouts) {
    this.payouts = payouts;
  }

  @KafkaListener(topics = Topics.CLAIM_EVENTS)
  public void onClaimEvent(String message) {
    String type = EventJson.typeOf(message);
    if (EventTypes.CLAIM_APPROVED.equals(type)) {
      EventEnvelope<ClaimApproved> event = EventJson.envelope(message, ClaimApproved.class);
      payouts.onClaimApproved(event.eventId(), event.insurerId(), event.payload());
    } else if (EventTypes.CLAIM_SUBMITTED.equals(type)
        || EventTypes.CLAIM_REJECTED.equals(type)
        || EventTypes.CLAIM_INFO_REQUESTED.equals(type)) {
      LOG.debug("{} is not used by payment-service; skipped.", type);
    } else {
      throw new UnknownEventTypeException(type);
    }
  }
}
