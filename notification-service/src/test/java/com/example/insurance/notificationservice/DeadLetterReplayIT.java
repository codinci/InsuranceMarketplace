package com.example.insurance.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.messaging.DeadLetterReplayer;
import com.example.insurance.notificationservice.messaging.Topics;
import com.example.insurance.notificationservice.repository.NotificationLogRepository;
import com.example.insurance.notificationservice.service.NotificationService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The operator's procedure end to end (ADR-019): a message fails until its cause is fixed, lands in
 * the dead-letter topic, is replayed once the cause is fixed, and notifies exactly once.
 */
class DeadLetterReplayIT extends AbstractNotificationIT {

  private static final String DLT = Topics.PAYMENT_EVENTS + ".DLT";

  @MockitoSpyBean private NotificationService service;
  @Autowired private NotificationLogRepository notifications;
  @Autowired private DeadLetterReplayer replayer;

  @Test
  void aDeadLetterIsReplayedOnceTheCauseIsFixedAndNotifiesOnce() throws Exception {
    AtomicBoolean causeFixed = new AtomicBoolean(false);
    doAnswer(
            call -> {
              if (!causeFixed.get()) {
                throw new IllegalStateException("the database is unavailable");
              }
              return call.callRealMethod();
            })
        .when(service)
        .onEvent(any(EventEnvelope.class));
    UUID policy = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    try (KafkaConsumer<String, String> dlt = consumerOf(DLT)) {
      publish(
          Topics.PAYMENT_EVENTS,
          EventTypes.PREMIUM_PAID,
          eventId,
          policy,
          new PremiumPaid(
              UUID.randomUUID(),
              policy,
              "POL-REPLAY-1",
              UUID.randomUUID(),
              new BigDecimal("12500.00"),
              "KES",
              "mock-1"));

      assertThat(awaitRecord(dlt, policy.toString()).value()).contains(eventId.toString());
    }
    assertThat(notifications.countByEventId(eventId)).as("nothing sent while broken").isZero();

    causeFixed.set(true);
    int replayed = replayer.replay(DLT, 10);

    assertThat(replayed).isEqualTo(1);
    await("the replayed notification", () -> notifications.countByEventId(eventId) == 1);

    assertThat(replayer.replay(DLT, 10)).as("a second replay finds nothing new").isZero();
    assertThat(notifications.countByEventId(eventId)).isEqualTo(1);
  }
}
