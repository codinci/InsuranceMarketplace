package com.example.insurance.policyservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.support.PaymentServiceStub;
import com.example.insurance.policyservice.support.TestJwtConfig;
import com.example.insurance.policyservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base for integration tests: one Postgres 17 and one Kafka broker for the whole run (the compose
 * images), a payment-service stub over HTTP, MockMvc, and helpers that call the API as a client
 * would. Each test creates its own insurers with unique codes, so tests never see each other's
 * data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
public abstract class AbstractPolicyIT {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @ServiceConnection
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));

  protected static final PaymentServiceStub PAYMENTS = PaymentServiceStub.start();

  static {
    POSTGRES.start();
    KAFKA.start();
  }

  @DynamicPropertySource
  static void paymentService(DynamicPropertyRegistry registry) {
    registry.add("policy.payment.base-url", PAYMENTS::baseUrl);
    registry.add(
        "policy.service-auth.token-url", () -> PAYMENTS.baseUrl() + "/internal/service-token");
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected KafkaTemplate<String, String> kafka;

  protected static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /** Creates an insurer through the API as a platform admin and returns its id. */
  protected UUID createInsurer(String code) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withToken(post("/api/v1/admin/insurers"), TestTokens.platformAdmin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code + "\",\"name\":\"Test insurer\"}"))
            .andReturn();
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return UUID.fromString(read(result, "$.id"));
  }

  /** A valid fixture plan, re-labelled for {@code insurerCode}, with optional edits. */
  protected static String planJson(String fixture, String insurerCode, Consumer<JsonNode> edits) {
    JsonNode plan = CatalogueFixtures.validPlanJson(fixture);
    ((ObjectNode) plan.get("insurer")).put("code", insurerCode);
    edits.accept(plan);
    return plan.toString();
  }

  protected static String planJson(String fixture, String insurerCode) {
    return planJson(fixture, insurerCode, plan -> {});
  }

  protected static Consumer<JsonNode> version(int version) {
    return plan -> ((ObjectNode) plan.get("plan")).put("version", version);
  }

  protected MvcResult importPlan(String token, UUID insurerId, String json, boolean dryRun)
      throws Exception {
    return mockMvc
        .perform(
            withToken(
                    post("/api/v1/admin/insurers/" + insurerId + "/plans/import")
                        .param("dryRun", String.valueOf(dryRun)),
                    token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  /** Imports as a platform admin and returns the new plan id. */
  protected UUID importDraft(UUID insurerId, String json) throws Exception {
    MvcResult result = importPlan(TestTokens.platformAdmin(), insurerId, json, false);
    if (result.getResponse().getStatus() != 201) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return UUID.fromString(read(result, "$.plan.id"));
  }

  protected MvcResult planAction(String token, UUID planId, String action) throws Exception {
    return mockMvc
        .perform(withToken(post("/api/v1/admin/plans/" + planId + "/" + action), token))
        .andReturn();
  }

  protected UUID publishedPlan(UUID insurerId, String json) throws Exception {
    UUID planId = importDraft(insurerId, json);
    MvcResult result = planAction(TestTokens.platformAdmin(), planId, "publish");
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException(result.getResponse().getContentAsString());
    }
    return planId;
  }

  protected static MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return token == null ? request : request.header("Authorization", "Bearer " + token);
  }

  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  /** Publishes an envelope the way the producing service does: the aggregate id is the key. */
  protected void publish(
      String topic, String key, String type, UUID eventId, UUID insurerId, Object payload)
      throws Exception {
    String value =
        tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .writeValueAsString(
                new EventEnvelope<>(
                    eventId,
                    type,
                    1,
                    Instant.now(),
                    key,
                    insurerId.toString(),
                    "it-test",
                    payload));
    kafka.send(topic, key, value).get(10, TimeUnit.SECONDS);
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

  /** Every message with this key and type seen within {@code wait}. */
  protected static List<String> messages(
      KafkaConsumer<String, String> consumer, String key, String type, Duration wait) {
    List<String> found = new ArrayList<>();
    long deadline = System.nanoTime() + wait.toNanos();
    while (System.nanoTime() < deadline) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
        if (key.equals(record.key()) && type.equals(JsonPath.read(record.value(), "$.type"))) {
          found.add(record.value());
        }
      }
    }
    return found;
  }

  /** The first message with this key and type; fails after 30 seconds. */
  protected static String awaitMessage(
      KafkaConsumer<String, String> consumer, String key, String type) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
        if (key.equals(record.key()) && type.equals(JsonPath.read(record.value(), "$.type"))) {
          return record.value();
        }
      }
    }
    throw new AssertionError("No " + type + " message with key " + key);
  }
}
