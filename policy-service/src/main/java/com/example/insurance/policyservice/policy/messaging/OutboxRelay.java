package com.example.insurance.policyservice.policy.messaging;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends unsent outbox rows to Kafka (ADR-013). A row is marked published only after the broker has
 * acknowledged it, so a crash means a resend, never a loss. Consumers handle the resend through
 * their inbox.
 */
@Component
public class OutboxRelay {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxEventRepository outbox;
  private final KafkaOperations<String, String> kafka;
  private final TransactionOperations transactions;
  private final Clock clock;

  public OutboxRelay(
      OutboxEventRepository outbox,
      KafkaOperations<String, String> kafka,
      TransactionOperations transactions,
      Clock clock) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.transactions = transactions;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${policy.outbox.relay-delay-ms:1000}")
  public void publishPending() {
    transactions.executeWithoutResult(status -> publishBatch());
  }

  /** Stops at the first failure so later events of the same policy are not sent out of order. */
  void publishBatch() {
    for (OutboxEvent event : outbox.lockUnpublished(BATCH_SIZE)) {
      try {
        kafka
            .send(event.getTopic(), event.getMessageKey(), event.getPayload())
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        event.markPublished(clock.instant());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException | TimeoutException e) {
        event.recordAttempt();
        LOG.warn(
            "Outbox event {} not sent yet; will retry: {}", event.getEventId(), e.getMessage());
        return;
      }
    }
  }
}
