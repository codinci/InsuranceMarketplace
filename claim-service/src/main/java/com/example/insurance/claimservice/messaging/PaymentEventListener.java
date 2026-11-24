package com.example.insurance.claimservice.messaging;

import com.example.insurance.claimservice.service.PayoutOutcomeService;
import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PayoutFailed;
import com.example.insurance.events.payment.PayoutSucceeded;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.UnknownEventTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads {@code payment.events} and moves a claim to PAID or PAYOUT_FAILED (PLAN 10, M7). Premium
 * events are for policy-service and are skipped; unknown types go to the dead-letter topic
 * (ADR-005).
 */
@Component
public class PaymentEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentEventListener.class);

  private final PayoutOutcomeService outcomes;

  public PaymentEventListener(PayoutOutcomeService outcomes) {
    this.outcomes = outcomes;
  }

  @KafkaListener(topics = Topics.PAYMENT_EVENTS)
  public void onPaymentEvent(String message) {
    String type = EventJson.typeOf(message);
    if (EventTypes.PAYOUT_SUCCEEDED.equals(type)) {
      EventEnvelope<PayoutSucceeded> event = EventJson.envelope(message, PayoutSucceeded.class);
      outcomes.onPayoutSucceeded(event.eventId(), event.payload());
    } else if (EventTypes.PAYOUT_FAILED.equals(type)) {
      EventEnvelope<PayoutFailed> event = EventJson.envelope(message, PayoutFailed.class);
      outcomes.onPayoutFailed(event.eventId(), event.payload());
    } else if (EventTypes.PREMIUM_PAID.equals(type) || EventTypes.PREMIUM_FAILED.equals(type)) {
      LOG.debug("{} is not used by claim-service; skipped.", type);
    } else {
      throw new UnknownEventTypeException(type);
    }
  }
}
