package com.example.insurance.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A row in the transactional outbox (ADR-013). It is written in the same transaction as the
 * business change, and the relay sends it to Kafka and marks it published.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false, length = 60)
  private String topic;

  @Column(name = "message_key", nullable = false, length = 80)
  private String messageKey;

  @Column(name = "event_type", nullable = false, length = 60)
  private String eventType;

  /** The whole envelope as JSON; this is the Kafka message value. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(nullable = false)
  private int attempts;

  protected OutboxEvent() {
    // Required by JPA.
  }

  public OutboxEvent(
      UUID eventId,
      String topic,
      String messageKey,
      String eventType,
      String payload,
      Instant createdAt) {
    this.eventId = eventId;
    this.topic = topic;
    this.messageKey = messageKey;
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  public void markPublished(Instant now) {
    this.publishedAt = now;
  }

  public void recordAttempt() {
    this.attempts++;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getTopic() {
    return topic;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public int getAttempts() {
    return attempts;
  }
}
