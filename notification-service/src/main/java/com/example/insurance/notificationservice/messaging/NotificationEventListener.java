package com.example.insurance.notificationservice.messaging;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.messaging.EventJson;
import com.example.insurance.notificationservice.service.NotificationService;
import com.example.insurance.notificationservice.service.NotificationTemplates;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the four event topics. Every known event type becomes a notification; an unknown type
 * throws and goes to the dead-letter topic (ADR-005, ADR-013).
 */
@Component
public class NotificationEventListener {

  private final NotificationService notifications;

  public NotificationEventListener(NotificationService notifications) {
    this.notifications = notifications;
  }

  @KafkaListener(
      topics = {
        Topics.USER_EVENTS,
        Topics.POLICY_EVENTS,
        Topics.PAYMENT_EVENTS,
        Topics.CLAIM_EVENTS
      })
  public void onEvent(String message) {
    Class<?> payload = NotificationTemplates.payloadClassOf(EventJson.typeOf(message));
    EventEnvelope<?> event = EventJson.envelope(message, payload);
    notifications.onEvent(event);
  }
}
