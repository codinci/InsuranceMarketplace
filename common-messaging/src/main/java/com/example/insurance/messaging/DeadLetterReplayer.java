package com.example.insurance.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Sends dead letters back to the topic they came from, so the consumer that gave up on them reads
 * them again (ADR-019). Fix the cause first: a replay of a message that still fails just lands in
 * the dead-letter topic again.
 *
 * <p>It reads {@code <topic>.DLT} with its own consumer group and commits only after every record
 * of a batch was sent. So a message is replayed once, a crash in the middle repeats at most one
 * batch, and the inbox (ADR-013) makes the repeat harmless. The original key is kept, so the
 * message stays in order with the rest of its aggregate.
 */
public class DeadLetterReplayer {

  private static final Logger LOG = LoggerFactory.getLogger(DeadLetterReplayer.class);
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final ConsumerSource consumers;
  private final KafkaOperations<String, String> kafka;
  private final String groupId;

  /** Opens the consumer that reads a dead-letter topic; a lambda over the service's factory. */
  @FunctionalInterface
  public interface ConsumerSource {

    /**
     * @param groupId the consumer group to read under
     * @param overrides consumer properties that replace the service's own
     */
    Consumer<String, String> open(String groupId, Properties overrides);
  }

  /**
   * @param consumers where the reading consumer comes from
   * @param kafka where replayed messages are sent
   * @param groupId the replay consumer group; it must differ from the service's listener group, or
   *     the replay would take part in that group's partition assignment
   */
  public DeadLetterReplayer(
      ConsumerSource consumers, KafkaOperations<String, String> kafka, String groupId) {
    this.consumers = consumers;
    this.kafka = kafka;
    this.groupId = groupId;
  }

  /**
   * Replays up to {@code max} messages from a dead-letter topic.
   *
   * @param dltTopic the dead-letter topic, for example {@code claim.events.DLT}
   * @param max the most messages to replay in this call; the rest stay for the next call
   * @return how many messages were sent back
   */
  public int replay(String dltTopic, int max) {
    Properties overrides = new Properties();
    overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    overrides.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.min(max, 100));
    int replayed = 0;
    try (Consumer<String, String> consumer = consumers.open(groupId, overrides)) {
      consumer.subscribe(List.of(dltTopic));
      while (replayed < max) {
        var records = consumer.poll(POLL_TIMEOUT);
        if (records.isEmpty()) {
          break;
        }
        for (ConsumerRecord<String, String> dead : records) {
          send(dead, dltTopic);
          replayed++;
        }
        consumer.commitSync();
      }
    }
    LOG.info("Replayed {} message(s) from {}.", replayed, dltTopic);
    return replayed;
  }

  private void send(ConsumerRecord<String, String> dead, String dltTopic) {
    String target = originalTopic(dead, dltTopic);
    ProducerRecord<String, String> record =
        new ProducerRecord<>(target, null, dead.key(), dead.value());
    Header eventType = dead.headers().lastHeader(OutboxRelay.EVENT_TYPE_HEADER);
    if (eventType != null) {
      record.headers().add(OutboxRelay.EVENT_TYPE_HEADER, eventType.value());
    }
    try {
      kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while replaying to " + target, ex);
    } catch (ExecutionException | TimeoutException ex) {
      throw new IllegalStateException("Could not replay a message to " + target, ex);
    }
  }

  private static String originalTopic(ConsumerRecord<String, String> dead, String dltTopic) {
    Header original = dead.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);
    if (original != null) {
      return new String(original.value(), StandardCharsets.UTF_8);
    }
    return dltTopic.endsWith(MessagingAutoConfiguration.DLT_SUFFIX)
        ? dltTopic.substring(0, dltTopic.length() - MessagingAutoConfiguration.DLT_SUFFIX.length())
        : dltTopic;
  }
}
