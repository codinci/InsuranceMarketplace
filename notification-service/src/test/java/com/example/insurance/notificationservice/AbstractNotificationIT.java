package com.example.insurance.notificationservice;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.messaging.EventJson;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests: one Postgres 17 and one Kafka broker (the same images as compose),
 * shared by every test class, and helpers that publish events the way the other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractNotificationIT {

  // One container each for the whole test run: Spring caches application contexts across classes
  // and they keep pointing at these containers. Ryuk removes them.
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @ServiceConnection
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));

  static {
    POSTGRES.start();
    KAFKA.start();
  }

  @Autowired protected KafkaTemplate<String, String> kafka;

  /** Publishes an envelope with the given event id; the aggregate id is the Kafka key. */
  protected void publish(String topic, String type, UUID eventId, UUID aggregateId, Object payload)
      throws Exception {
    EventEnvelope<Object> envelope =
        new EventEnvelope<>(
            eventId, type, 1, Instant.now(), aggregateId.toString(), null, "it-corr", payload);
    kafka.send(topic, aggregateId.toString(), EventJson.write(envelope)).get();
  }

  /** Publishes raw text, for messages no service would send. */
  protected void publishRaw(String topic, String key, String value) throws Exception {
    kafka.send(topic, key, value).get();
  }

  /** Polls until the condition holds; fails after 30 seconds. */
  protected static void await(String what, BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Gave up waiting for " + what);
  }

  /** A consumer that reads a topic from the beginning, under its own group. */
  protected static KafkaConsumer<String, String> consumerOf(String topic) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(List.of(topic));
    return consumer;
  }

  /** The first record on the topic with this key; fails after 30 seconds. */
  protected static ConsumerRecord<String, String> awaitRecord(
      KafkaConsumer<String, String> consumer, String key) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
        if (key.equals(record.key())) {
          return record;
        }
      }
    }
    throw new AssertionError("No record with key " + key);
  }
}
