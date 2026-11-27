package com.example.insurance.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Replay against a real broker: keys, values and headers survive, and each message goes once. */
class DeadLetterReplayerIT {

  private static KafkaContainer kafka;
  private static KafkaTemplate<String, String> template;

  @BeforeAll
  static void start() {
    kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));
    kafka.start();
    template =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
  }

  @AfterAll
  static void stop() {
    template.destroy();
    kafka.stop();
  }

  private static Properties consumerProps(String group) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return props;
  }

  private static DeadLetterReplayer replayer(String group) {
    return new DeadLetterReplayer(
        (groupId, overrides) -> {
          Properties props = consumerProps(groupId);
          props.putAll(overrides);
          return new KafkaConsumer<>(props);
        },
        template,
        group);
  }

  private static void deadLetter(
      String dlt, String key, String value, String originalTopic, String eventType)
      throws Exception {
    ProducerRecord<String, String> record = new ProducerRecord<>(dlt, null, key, value);
    if (originalTopic != null) {
      record
          .headers()
          .add(KafkaHeaders.DLT_ORIGINAL_TOPIC, originalTopic.getBytes(StandardCharsets.UTF_8));
    }
    if (eventType != null) {
      record
          .headers()
          .add(OutboxRelay.EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8));
    }
    template.send(record).get();
  }

  private static List<ConsumerRecord<String, String>> readAll(String topic, int expected) {
    List<ConsumerRecord<String, String>> found = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(consumerProps("read-" + UUID.randomUUID()))) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (found.size() < expected && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(300)).forEach(found::add);
      }
    }
    return found;
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    var header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  @Test
  void aReplayedMessageKeepsItsKeyValueAndTypeHeaderAndGoesOnce() throws Exception {
    String topic = "replay-a." + UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    deadLetter(topic + ".DLT", key, "{\"type\":\"X\"}", topic, "X");
    DeadLetterReplayer replayer = replayer("replayer-a");

    assertThat(replayer.replay(topic + ".DLT", 10)).isEqualTo(1);
    assertThat(replayer.replay(topic + ".DLT", 10)).as("already replayed").isZero();

    List<ConsumerRecord<String, String>> back = readAll(topic, 1);
    assertThat(back).hasSize(1);
    assertThat(back.get(0).key()).isEqualTo(key);
    assertThat(back.get(0).value()).isEqualTo("{\"type\":\"X\"}");
    assertThat(header(back.get(0), OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("X");
  }

  @Test
  void theOriginalTopicIsTakenFromTheNameWhenTheHeaderIsMissing() throws Exception {
    String topic = "replay-b." + UUID.randomUUID();
    deadLetter(topic + ".DLT", "k", "v", null, null);

    assertThat(replayer("replayer-b").replay(topic + ".DLT", 10)).isEqualTo(1);

    assertThat(readAll(topic, 1)).hasSize(1);
  }

  @Test
  void aLimitLeavesTheRestForTheNextReplay() throws Exception {
    String topic = "replay-c." + UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      deadLetter(topic + ".DLT", "k" + i, "v" + i, topic, null);
    }
    DeadLetterReplayer replayer = replayer("replayer-c");

    assertThat(replayer.replay(topic + ".DLT", 2)).isEqualTo(2);
    assertThat(replayer.replay(topic + ".DLT", 2)).isEqualTo(1);
    assertThat(replayer.replay(topic + ".DLT", 2)).isZero();

    assertThat(readAll(topic, 3)).hasSize(3);
  }

  @Test
  void anEmptyDeadLetterTopicReplaysNothing() {
    assertThat(replayer("replayer-d").replay("replay-d." + UUID.randomUUID() + ".DLT", 10))
        .isZero();
  }
}
