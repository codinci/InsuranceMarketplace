package com.example.insurance.notificationservice.service;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.messaging.Inbox;
import com.example.insurance.notificationservice.domain.NotificationLog;
import com.example.insurance.notificationservice.repository.NotificationLogRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one event into at most one notification. The inbox record and the log row commit together,
 * so a redelivered event does nothing (ADR-013).
 */
@Service
public class NotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationLogRepository notifications;
  private final Inbox inbox;
  private final Clock clock;

  public NotificationService(NotificationLogRepository notifications, Inbox inbox, Clock clock) {
    this.notifications = notifications;
    this.inbox = inbox;
    this.clock = clock;
  }

  @Transactional
  public void onEvent(EventEnvelope<?> event) {
    if (inbox.alreadyApplied(event.eventId())) {
      return;
    }
    Optional<NotificationTemplates.Rendered> rendered = NotificationTemplates.render(event);
    if (rendered.isPresent()) {
      NotificationTemplates.Rendered message = rendered.get();
      NotificationLog saved =
          notifications.save(
              NotificationLog.of(
                  event.eventId(),
                  event.type(),
                  message.recipientUserId(),
                  message.subject(),
                  message.body(),
                  clock.instant()));
      LOG.info(
          "Notification {} for {} sent to user {}.",
          saved.getId(),
          event.type(),
          message.recipientUserId());
    } else {
      LOG.warn("Event {} ({}) names no recipient; no notification.", event.eventId(), event.type());
    }
    inbox.markApplied(event.eventId());
  }
}
