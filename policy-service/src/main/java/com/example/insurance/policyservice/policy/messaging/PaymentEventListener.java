package com.example.insurance.policyservice.policy.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.policyservice.policy.service.PolicyLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads {@code payment.events}: a paid premium activates the policy, a failed one is recorded and
 * the policy keeps waiting (PLAN 8.2). Unknown types go to the dead-letter topic (ADR-005).
 */
@Component
public class PaymentEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentEventListener.class);

  private final PolicyLifecycleService lifecycle;

  public PaymentEventListener(PolicyLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @KafkaListener(topics = Topics.PAYMENT_EVENTS)
  public void onPaymentEvent(String message) {
    String type = EventJson.typeOf(message);
    if (EventTypes.PREMIUM_PAID.equals(type)) {
      EventEnvelope<PremiumPaid> event = EventJson.envelope(message, PremiumPaid.class);
      lifecycle.onPremiumPaid(event.eventId(), event.payload());
    } else if (EventTypes.PREMIUM_FAILED.equals(type)) {
      EventEnvelope<PremiumFailed> event = EventJson.envelope(message, PremiumFailed.class);
      lifecycle.onPremiumFailed(event.eventId(), event.payload());
    } else if (EventTypes.PAYOUT_SUCCEEDED.equals(type) || EventTypes.PAYOUT_FAILED.equals(type)) {
      // Claim payouts are claim-service's business, not the policy's (M7).
      LOG.debug("{} is not used by policy-service; skipped.", type);
    } else {
      throw new UnknownEventTypeException(type);
    }
  }
}
