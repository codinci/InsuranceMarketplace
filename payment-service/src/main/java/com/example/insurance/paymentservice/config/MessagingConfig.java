package com.example.insurance.paymentservice.config;

import com.example.insurance.paymentservice.messaging.UnknownEventTypeException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Turns on the outbox relay's schedule and the consumer's failure policy (ADR-013). A message that
 * still fails after three retries goes to {@code <topic>.DLT}. An unknown event type goes straight
 * there, because retrying it cannot help.
 */
@Configuration
@EnableScheduling
public class MessagingConfig {

  /**
   * Spelled out because the library default changed between Spring Kafka versions; the topics the
   * compose stack creates use this suffix (docs/events.md).
   */
  static final String DLT_SUFFIX = ".DLT";

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> operations) {
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(
                operations,
                (failed, exception) -> new TopicPartition(failed.topic() + DLT_SUFFIX, -1)),
            new FixedBackOff(1_000L, 3L));
    handler.addNotRetryableExceptions(UnknownEventTypeException.class);
    return handler;
  }
}
