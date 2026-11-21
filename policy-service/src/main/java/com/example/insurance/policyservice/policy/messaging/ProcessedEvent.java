package com.example.insurance.policyservice.policy.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An inbox row (ADR-013): this consumer has already applied the event with this id. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false, length = 60)
  private String consumer;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedEvent() {
    // Required by JPA.
  }

  public ProcessedEvent(UUID eventId, String consumer, Instant processedAt) {
    this.eventId = eventId;
    this.consumer = consumer;
    this.processedAt = processedAt;
  }
}
