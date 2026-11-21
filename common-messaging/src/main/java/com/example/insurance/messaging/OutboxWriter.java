package com.example.insurance.messaging;

import com.example.insurance.common.web.CorrelationIds;
import com.example.insurance.events.EventEnvelope;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Adds an event to the outbox. Call it inside the same transaction as the business change, so the
 * event exists if and only if the change does (ADR-013).
 */
public class OutboxWriter {

  private final OutboxEventRepository outbox;
  private final Clock clock;

  public OutboxWriter(OutboxEventRepository outbox, Clock clock) {
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Wraps the payload in the shared envelope and stores it. The aggregate id is also the Kafka key,
   * so every event about one aggregate stays in order. Returns the new event's id.
   */
  public <T> UUID record(
      String topic,
      String eventType,
      int version,
      String aggregateId,
      String insurerId,
      T payload) {
    EventEnvelope<T> envelope =
        new EventEnvelope<>(
            UUID.randomUUID(),
            eventType,
            version,
            clock.instant(),
            aggregateId,
            insurerId,
            Objects.requireNonNullElse(MDC.get(CorrelationIds.MDC_KEY), "none"),
            payload);
    outbox.save(
        new OutboxEvent(
            envelope.eventId(),
            topic,
            aggregateId,
            eventType,
            EventJson.write(envelope),
            envelope.occurredAt()));
    return envelope.eventId();
  }
}
