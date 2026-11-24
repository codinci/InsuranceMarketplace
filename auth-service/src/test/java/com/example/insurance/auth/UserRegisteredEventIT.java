package com.example.insurance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Registration announces the new customer through the outbox, in the same transaction (M8a). */
@SpringBootTest
@AutoConfigureMockMvc
class UserRegisteredEventIT extends AbstractAuthIT {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void registrationPublishesOneUserRegisteredKeyedByTheUserId() throws Exception {
    String email = uniqueEmail("welcome");
    MvcResult result =
        postJson(
            "/api/v1/auth/register",
            json("email", email, "password", PASSWORD, "firstName", "Amina", "lastName", "Otieno"),
            null);
    String userId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    ConsumerRecord<String, String> record = awaitUserEvent(userId);

    assertThat((String) JsonPath.read(record.value(), "$.type")).isEqualTo("UserRegistered");
    assertThat((String) JsonPath.read(record.value(), "$.payload.userId")).isEqualTo(userId);
    assertThat((String) JsonPath.read(record.value(), "$.payload.email")).isEqualTo(email);
    assertThat((String) JsonPath.read(record.value(), "$.payload.firstName")).isEqualTo("Amina");
    assertThat(record.value())
        .as("the event never carries a secret")
        .doesNotContain("password")
        .doesNotContain("nationalId");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE message_key = ?", Integer.class, userId))
        .isEqualTo(1);
  }

  @Test
  void aRefusedRegistrationAnnouncesNothing() throws Exception {
    String email = uniqueEmail("taken");
    registeredCustomerLogin(email);
    Integer before = jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);

    MvcResult duplicate =
        postJson(
            "/api/v1/auth/register",
            json("email", email, "password", PASSWORD, "firstName", "Amina", "lastName", "Otieno"),
            null);

    assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
        .isEqualTo(before);
  }

  private static ConsumerRecord<String, String> awaitUserEvent(String key) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of("user.events"));
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
          if (key.equals(record.key())) {
            return record;
          }
        }
      }
    }
    throw new AssertionError("No UserRegistered message with key " + key);
  }
}
