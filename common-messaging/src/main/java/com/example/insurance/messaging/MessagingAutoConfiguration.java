package com.example.insurance.messaging;

import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The outbox, inbox, relay and dead-letter policy for every service that depends on
 * common-messaging (ADR-013).
 *
 * <p>Listed in {@code META-INF/spring/...AutoConfiguration.imports}, so Spring Boot loads it.
 * {@link AutoConfigurationPackage} adds this package to the ones Spring Boot scans for JPA entities
 * and repositories, next to the service's own package. {@code @EntityScan} would replace the
 * service's scanning instead. It must therefore run before the JPA auto-configurations read that
 * list. Each service keeps its own {@code outbox_events} and {@code processed_events} tables in its
 * own database, created by its own Flyway migration.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
    })
@AutoConfigurationPackage(basePackages = "com.example.insurance.messaging")
@EnableScheduling
public class MessagingAutoConfiguration {

  /**
   * Spelled out because the library default changed between Spring Kafka versions; the topics the
   * compose stack creates use this suffix (docs/events.md).
   */
  public static final String DLT_SUFFIX = ".DLT";

  @Bean
  @ConditionalOnMissingBean
  OutboxWriter outboxWriter(OutboxEventRepository outbox, Clock clock) {
    return new OutboxWriter(outbox, clock);
  }

  @Bean
  @ConditionalOnMissingBean
  OutboxRelay outboxRelay(
      OutboxEventRepository outbox,
      KafkaOperations<String, String> kafka,
      TransactionOperations transactions,
      Clock clock) {
    return new OutboxRelay(outbox, kafka, transactions, clock);
  }

  /** The name recorded with each inbox row; the service's name unless set. */
  @Bean
  @ConditionalOnMissingBean
  Inbox inbox(
      ProcessedEventRepository processed,
      Clock clock,
      @Value("${messaging.consumer-name:${spring.application.name}}") String consumerName) {
    return new Inbox(processed, clock, consumerName);
  }

  /**
   * A message that still fails after three retries, one second apart, goes to {@code <topic>.DLT}.
   * An unknown event type goes straight there, because retrying cannot help.
   */
  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
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
