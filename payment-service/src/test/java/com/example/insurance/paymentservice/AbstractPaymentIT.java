package com.example.insurance.paymentservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.paymentservice.repository.PaymentTransactionRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Base for integration tests: one Postgres 17 and one Kafka broker (the same images as compose),
 * shared by every test class. Tokens come from {@link
 * com.example.insurance.paymentservice.support.TestTokens}.
 */
abstract class AbstractPaymentIT {

  static final UUID INSURER = UUID.fromString("a3c1e7f0-1111-4222-8333-444455556666");

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @ServiceConnection
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));

  static {
    POSTGRES.start();
    KAFKA.start();
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected KafkaTemplate<String, String> kafka;
  @Autowired protected PaymentTransactionRepository transactions;

  protected static String newKey() {
    return UUID.randomUUID().toString();
  }

  /**
   * policy-service asks for a premium: the internal endpoint, with a SERVICE token and the policy
   * id as key.
   */
  protected MvcResult createIntent(String token, UUID policyId, String amount, String key)
      throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "policyId",
                policyId.toString(),
                "policyNumber",
                "POL-ACACIA-2026-" + policyId.toString().substring(0, 6),
                "insurerId",
                INSURER.toString(),
                "holderUserId",
                holderOf(policyId).toString(),
                "amount",
                new BigDecimal(amount),
                "currency",
                "KES"));
    var request = post("/internal/payments").contentType("application/json").content(body);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mockMvc.perform(request).andReturn();
  }

  /**
   * The holder of the test policy is derived from the policy id, so tests can build the customer
   * token.
   */
  protected static UUID holderOf(UUID policyId) {
    return UUID.nameUUIDFromBytes(("holder-" + policyId).getBytes());
  }

  protected MvcResult pay(String token, UUID paymentId, String key, String simulate)
      throws Exception {
    var request = post("/api/v1/payments/{id}/pay", paymentId);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    if (simulate != null) {
      request.contentType("application/json").content("{\"simulate\":\"" + simulate + "\"}");
    }
    return mockMvc.perform(request).andReturn();
  }

  protected MvcResult getPayment(String token, UUID paymentId) throws Exception {
    return mockMvc
        .perform(get("/api/v1/payments/{id}", paymentId).header("Authorization", "Bearer " + token))
        .andReturn();
  }

  protected MvcResult listPayments(String token, UUID referenceId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/payments")
                .param("referenceId", referenceId.toString())
                .header("Authorization", "Bearer " + token))
        .andReturn();
  }

  protected static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString();
  }

  protected static String jsonField(MvcResult result, String path) throws Exception {
    return JsonPath.read(body(result), path);
  }

  protected static UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(jsonField(result, "$.id"));
  }

  /** Polls the payment until it reaches {@code status}; listeners work asynchronously. */
  protected String awaitStatus(String token, UUID paymentId, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    String current = null;
    while (System.nanoTime() < deadline) {
      current = jsonField(getPayment(token, paymentId), "$.status");
      if (status.equals(current)) {
        return current;
      }
      Thread.sleep(200);
    }
    throw new AssertionError(
        "Payment " + paymentId + " stayed " + current + ", expected " + status);
  }

  /** Publishes an envelope the way policy-service does: the aggregate id is the Kafka key. */
  protected void publish(String topic, String key, String type, UUID eventId, Object payload)
      throws Exception {
    String value =
        json.writeValueAsString(
            new EventEnvelope<>(
                eventId, type, 1, Instant.now(), key, INSURER.toString(), "it-test", payload));
    kafka.send(topic, key, value).get(10, TimeUnit.SECONDS);
  }

  protected static Object policyCancelledPayload(UUID policyId) {
    return new PolicyCancelled(
        policyId, "POL-TEST", UUID.randomUUID(), 2, "UNPAID", LocalDate.of(2026, 12, 1), null);
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

  /** The first message with this key and type; fails after 30 seconds. */
  protected static String awaitMessage(
      KafkaConsumer<String, String> consumer, String key, String type) {
    return awaitRecord(consumer, key, type).value();
  }

  /** The whole Kafka record (key, value and headers) of the first match; fails after 30 seconds. */
  protected static ConsumerRecord<String, String> awaitRecord(
      KafkaConsumer<String, String> consumer, String key, String type) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
        if (key.equals(record.key()) && type.equals(JsonPath.read(record.value(), "$.type"))) {
          return record;
        }
      }
    }
    throw new AssertionError("No " + type + " message with key " + key);
  }

  /** The value of a Kafka header as text, or null when it is missing. */
  protected static String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    return header == null
        ? null
        : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
  }
}
