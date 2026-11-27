package com.example.insurance.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.events.user.UserRegistered;
import com.example.insurance.notificationservice.domain.NotificationLog;
import com.example.insurance.notificationservice.messaging.Topics;
import com.example.insurance.notificationservice.repository.NotificationLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Events in, notifications out, against a real Kafka broker and Postgres (M8a). */
class NotificationFlowIT extends AbstractNotificationIT {

  @Autowired private NotificationLogRepository notifications;
  @Autowired private JdbcTemplate jdbc;

  private static PremiumPaid premiumPaid(UUID holder) {
    return new PremiumPaid(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "POL-IT-1",
        holder,
        new BigDecimal("12500.00"),
        "KES",
        "mock-1");
  }

  @Test
  void aRegistrationBecomesAWelcomeForThatUser() throws Exception {
    UUID user = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    publish(
        Topics.USER_EVENTS,
        EventTypes.USER_REGISTERED,
        eventId,
        user,
        new UserRegistered(user, "amina@example.com", "Amina", "Otieno"));

    await("the welcome notification", () -> notifications.countByEventId(eventId) == 1);
    NotificationLog log =
        notifications.findAll().stream()
            .filter(n -> n.getEventId().equals(eventId))
            .findFirst()
            .orElseThrow();
    assertThat(log.getRecipientUserId()).isEqualTo(user);
    assertThat(log.getType()).isEqualTo("UserRegistered");
    assertThat(log.getChannel()).isEqualTo("LOG");
    assertThat(log.getBody()).doesNotContain("Amina").doesNotContain("@");
  }

  @Test
  void theSameEventDeliveredTwiceNotifiesOnce() throws Exception {
    UUID holder = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID policy = UUID.randomUUID();

    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, eventId, policy, premiumPaid(holder));
    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, eventId, policy, premiumPaid(holder));
    // A later, different event from the same topic and key is read after both copies, so once it
    // has a row the duplicate has already been handled.
    UUID marker = UUID.randomUUID();
    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, marker, policy, premiumPaid(holder));

    await("the marker notification", () -> notifications.countByEventId(marker) == 1);
    assertThat(notifications.countByEventId(eventId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId))
        .isEqualTo(1);
  }

  @Test
  void aNewEventIdForTheSameFactNotifiesAgain() throws Exception {
    // The inbox de-duplicates by event id only. Two events with different ids are two facts to the
    // consumer, so a producer that re-announces under a new id sends a second message. This is the
    // documented behaviour (ADR-013): producers, not this service, own "once per business fact".
    UUID holder = UUID.randomUUID();
    UUID policy = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, first, policy, premiumPaid(holder));
    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, second, policy, premiumPaid(holder));

    await(
        "both notifications",
        () ->
            notifications.countByEventId(first) == 1 && notifications.countByEventId(second) == 1);
  }

  @Test
  void aClaimEventAddressesTheHolderNamedInTheEvent() throws Exception {
    UUID holder = UUID.randomUUID();
    UUID claim = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    publish(
        Topics.CLAIM_EVENTS,
        EventTypes.CLAIM_APPROVED,
        eventId,
        claim,
        new ClaimApproved(
            claim,
            "CLM-IT-1",
            UUID.randomUUID(),
            holder,
            "MEM-1",
            "OP-CONSULT",
            new BigDecimal("2000.00"),
            "KES",
            Instant.now()));

    await("the claim notification", () -> notifications.countByEventId(eventId) == 1);
    List<NotificationLog> forHolder =
        notifications.findAll().stream()
            .filter(n -> n.getRecipientUserId().equals(holder))
            .toList();
    assertThat(forHolder).hasSize(1);
    assertThat(forHolder.get(0).getBody()).contains("CLM-IT-1").contains("KES 2000.00");
  }

  @Test
  void anEventWithNoHolderIsSkippedAndTheNextOneStillArrives() throws Exception {
    UUID policy = UUID.randomUUID();
    UUID old = UUID.randomUUID();
    UUID next = UUID.randomUUID();

    publish(Topics.PAYMENT_EVENTS, EventTypes.PREMIUM_PAID, old, policy, premiumPaid(null));
    publish(
        Topics.PAYMENT_EVENTS,
        EventTypes.PREMIUM_PAID,
        next,
        policy,
        premiumPaid(UUID.randomUUID()));

    await("the event after the skipped one", () -> notifications.countByEventId(next) == 1);
    assertThat(notifications.countByEventId(old)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, old))
        .as("the skipped event is still recorded, so it is not read again")
        .isEqualTo(1);
  }

  @Test
  void anUnknownTypeGoesToTheDeadLetterTopicAndNothingIsLogged() throws Exception {
    UUID aggregate = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    try (KafkaConsumer<String, String> dlt = consumerOf(Topics.CLAIM_EVENTS + ".DLT")) {
      publish(Topics.CLAIM_EVENTS, "ClaimTeleported", eventId, aggregate, "anything");

      ConsumerRecord<String, String> dead = awaitRecord(dlt, aggregate.toString());
      assertThat(dead.value()).contains("ClaimTeleported").contains(eventId.toString());
    }
    assertThat(notifications.countByEventId(eventId)).isZero();
  }
}
