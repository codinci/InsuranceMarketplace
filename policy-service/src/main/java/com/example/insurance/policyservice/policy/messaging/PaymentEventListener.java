package com.example.insurance.policyservice.policy.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.policyservice.policy.service.PolicyLifecycleService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Reads {@code payment.events}: a paid premium activates the policy, a failed one is recorded and
 * the policy keeps waiting (PLAN 8.2). Unknown types go to the dead-letter topic (ADR-005).
 */
@Component
public class PaymentEventListener {

  private final PolicyLifecycleService lifecycle;

  public PaymentEventListener(PolicyLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @KafkaListener(topics = Topics.PAYMENT_EVENTS)
  public void onPaymentEvent(String message) {
    String type = EventJson.MAPPER.readValue(message, EventHeader.class).type();
    if (EventTypes.PREMIUM_PAID.equals(type)) {
      EventEnvelope<PremiumPaid> event =
          EventJson.MAPPER.readValue(message, new TypeReference<EventEnvelope<PremiumPaid>>() {});
      lifecycle.onPremiumPaid(event.eventId(), event.payload());
    } else if (EventTypes.PREMIUM_FAILED.equals(type)) {
      EventEnvelope<PremiumFailed> event =
          EventJson.MAPPER.readValue(message, new TypeReference<EventEnvelope<PremiumFailed>>() {});
      lifecycle.onPremiumFailed(event.eventId(), event.payload());
    } else {
      throw new UnknownEventTypeException(type);
    }
  }

  /** Only the type, so the right payload class can be chosen before reading the rest. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record EventHeader(String type) {}
}
