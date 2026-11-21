package com.example.insurance.paymentservice.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.paymentservice.service.PaymentExpiryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Reads {@code policy.events} and reacts to cancellations: a policy cancelled for not being paid
 * expires its payment (PLAN 8.2). Other known types are ignored; unknown ones go to the dead-letter
 * topic (ADR-005).
 */
@Component
public class PolicyEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(PolicyEventListener.class);

  private final PaymentExpiryService expiry;

  public PolicyEventListener(PaymentExpiryService expiry) {
    this.expiry = expiry;
  }

  @KafkaListener(topics = Topics.POLICY_EVENTS)
  public void onPolicyEvent(String message) {
    String type = EventJson.MAPPER.readValue(message, EventHeader.class).type();
    if (EventTypes.POLICY_CANCELLED.equals(type)) {
      EventEnvelope<PolicyCancelled> event =
          EventJson.MAPPER.readValue(
              message, new TypeReference<EventEnvelope<PolicyCancelled>>() {});
      expiry.onPolicyCancelled(event.eventId(), event.payload());
    } else if (EventTypes.POLICY_ACTIVATED.equals(type)) {
      LOG.debug("PolicyActivated is not used by payment-service; skipped.");
    } else {
      throw new UnknownEventTypeException(type);
    }
  }

  /** Only the type, so the right payload class can be chosen before reading the rest. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record EventHeader(String type) {}
}
