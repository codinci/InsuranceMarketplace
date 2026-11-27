package com.example.insurance.notificationservice.repository;

import com.example.insurance.notificationservice.domain.NotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Access to the notification log. */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  long countByEventId(UUID eventId);
}
