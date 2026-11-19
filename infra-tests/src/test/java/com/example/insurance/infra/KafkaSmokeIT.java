package com.example.insurance.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.EventEnvelope;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Produces and consumes a real {@link EventEnvelope} through Kafka with the same image version as
 * docker-compose. The message key is the aggregate id (PLAN 10).
 */
@Testcontainers
class KafkaSmokeIT {

  /** Stand-in payload. Real payloads arrive with their producers in later milestones. */
  record SamplePayload(String policyNumber, BigDecimal premium) {}

  private static final TypeReference<EventEnvelope<SamplePayload>> ENVELOPE_TYPE =
      new TypeReference<>() {};

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void producedEnvelopeIsConsumedWithItsKeyAndFields() {
    String topic = "policy.events";
    EventEnvelope<SamplePayload> event =
        new EventEnvelope<>(
            UUID.randomUUID(),
            "PolicyActivated",
            1,
            Instant.parse("2026-11-19T04:00:00Z"),
            "policy-42",
            "insurer-a",
            "corr-kafka-1",
            new SamplePayload("ACA-0001-01", new BigDecimal("12500.00")));

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
      producer.send(
          new ProducerRecord<>(topic, event.aggregateId(), mapper.writeValueAsString(event)));
      producer.flush();

      consumer.subscribe(Collections.singletonList(topic));
      ConsumerRecord<String, String> received = pollOne(consumer);

      assertThat(received.key()).isEqualTo("policy-42");
      EventEnvelope<SamplePayload> readBack = mapper.readValue(received.value(), ENVELOPE_TYPE);
      assertThat(readBack).isEqualTo(event);
    }
  }

  private static ConsumerRecord<String, String> pollOne(KafkaConsumer<String, String> consumer) {
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
    while (System.currentTimeMillis() < deadline) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        return record;
      }
    }
    throw new AssertionError("No record received within 30 seconds");
  }

  private static Properties producerProps() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return props;
  }

  private static Properties consumerProps() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "infra-tests-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return props;
  }
}
