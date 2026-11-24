package com.example.insurance.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One notification that was "sent". The channel is a stub in v1: the row is the delivery. The body
 * never holds a name, an email address or any other personal data, only the recipient's id.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

  /** The only channel in v1: the log row itself. */
  public static final String CHANNEL_LOG = "LOG";

  @Id private UUID id;

  @Column(name = "event_id", nullable = false, unique = true, updatable = false)
  private UUID eventId;

  @Column(nullable = false, updatable = false)
  private String type;

  @Column(name = "recipient_user_id", nullable = false, updatable = false)
  private UUID recipientUserId;

  @Column(nullable = false, updatable = false)
  private String channel;

  @Column(nullable = false, updatable = false)
  private String subject;

  @Column(nullable = false, updatable = false)
  private String body;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  protected NotificationLog() {
    // For JPA.
  }

  public static NotificationLog of(
      UUID eventId,
      String type,
      UUID recipientUserId,
      String subject,
      String body,
      Instant sentAt) {
    NotificationLog log = new NotificationLog();
    log.id = UUID.randomUUID();
    log.eventId = eventId;
    log.type = type;
    log.recipientUserId = recipientUserId;
    log.channel = CHANNEL_LOG;
    log.subject = subject;
    log.body = body;
    log.sentAt = sentAt;
    return log;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getType() {
    return type;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public String getChannel() {
    return channel;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
