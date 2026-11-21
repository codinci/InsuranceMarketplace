package com.example.insurance.claimservice.messaging;

import com.example.insurance.claimservice.service.PolicySnapshotService;
import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.messaging.UnknownEventTypeException;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the policy read model from {@code policy.events} (PLAN 10, M5a). Unknown types go to the
 * dead-letter topic (ADR-005) rather than being guessed at.
 */
@Component
public class PolicyEventListener {

  private final PolicySnapshotService snapshots;

  public PolicyEventListener(PolicySnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @KafkaListener(topics = Topics.POLICY_EVENTS)
  public void onPolicyEvent(String message) {
    String type = EventJson.typeOf(message);
    if (EventTypes.POLICY_ACTIVATED.equals(type)) {
      EventEnvelope<PolicyActivated> event = EventJson.envelope(message, PolicyActivated.class);
      snapshots.onActivated(
          event.eventId(),
          UUID.fromString(event.insurerId()),
          event.payload(),
          EventJson.payloadJson(message));
    } else if (EventTypes.POLICY_CANCELLED.equals(type)) {
      EventEnvelope<PolicyCancelled> event = EventJson.envelope(message, PolicyCancelled.class);
      snapshots.onCancelled(event.eventId(), UUID.fromString(event.insurerId()), event.payload());
    } else {
      throw new UnknownEventTypeException(type);
    }
  }
}
