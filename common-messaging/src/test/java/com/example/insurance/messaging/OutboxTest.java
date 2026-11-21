package com.example.insurance.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurance.common.web.CorrelationIds;
import com.example.insurance.events.EventEnvelope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

/** The outbox writer and relay without a database or broker (ADR-013). */
class OutboxTest {

  private static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);

  record Payload(String name, BigDecimal amount) {}

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void theWriterStoresTheWholeEnvelopeKeyedByTheAggregate() {
    MDC.put(CorrelationIds.MDC_KEY, "corr-1");
    UUID eventId =
        new OutboxWriter(repository, CLOCK)
            .record(
                "policy.events",
                "Thing",
                2,
                "agg-1",
                "ins-1",
                new Payload("x", new BigDecimal("1.50")));

    ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(saved.capture());
    OutboxEvent row = saved.getValue();
    assertThat(row.getEventId()).isEqualTo(eventId);
    assertThat(row.getTopic()).isEqualTo("policy.events");
    assertThat(row.getMessageKey()).isEqualTo("agg-1");
    assertThat(row.getEventType()).isEqualTo("Thing");
    EventEnvelope<Payload> envelope = EventJson.envelope(row.getPayload(), Payload.class);
    assertThat(envelope.eventId()).isEqualTo(eventId);
    assertThat(envelope.version()).isEqualTo(2);
    assertThat(envelope.occurredAt()).isEqualTo(NOW);
    assertThat(envelope.insurerId()).isEqualTo("ins-1");
    assertThat(envelope.correlationId()).isEqualTo("corr-1");
    assertThat(envelope.payload().amount()).isEqualByComparingTo("1.50");
  }

  @Test
  void withoutARequestTheCorrelationIdIsNone() {
    new OutboxWriter(repository, CLOCK).record("t", "Thing", 1, "agg", null, "p");

    ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(saved.capture());
    assertThat(EventJson.envelope(saved.getValue().getPayload(), String.class).correlationId())
        .isEqualTo("none");
  }

  @Test
  void theRelayMarksARowOnlyAfterTheBrokerAcknowledgesAndSendsTheTypeHeader() {
    OutboxEvent first = row("A");
    OutboxEvent second = row("B");
    when(repository.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
    KafkaOperations<String, String> kafka = kafka();
    when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged());

    relay(kafka).publishPending();

    assertThat(first.getPublishedAt()).isEqualTo(NOW);
    assertThat(second.getPublishedAt()).isEqualTo(NOW);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> sent =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka, times(2)).send(sent.capture());
    ProducerRecord<String, String> record = sent.getAllValues().get(0);
    assertThat(record.key()).isEqualTo("key-A");
    assertThat(record.topic()).isEqualTo("topic");
    assertThat(
            new String(
                record.headers().lastHeader(OutboxRelay.EVENT_TYPE_HEADER).value(),
                StandardCharsets.UTF_8))
        .isEqualTo("Type-A");
  }

  @Test
  void theRelayStopsAtTheFirstFailureSoLaterEventsKeepTheirOrder() {
    OutboxEvent first = row("A");
    OutboxEvent second = row("B");
    when(repository.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
    KafkaOperations<String, String> kafka = kafka();
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

    relay(kafka).publishPending();

    assertThat(first.getPublishedAt()).isNull();
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(second.getAttempts()).isZero();
    verify(kafka, times(1)).send(any(ProducerRecord.class));
  }

  @Test
  void anInterruptedRelayStopsWithoutMarking() {
    OutboxEvent first = row("A");
    when(repository.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first));
    KafkaOperations<String, String> kafka = kafka();
    CompletableFuture<SendResult<String, String>> never = new CompletableFuture<>();
    when(kafka.send(any(ProducerRecord.class))).thenReturn(never);

    Thread.currentThread().interrupt();
    try {
      relay(kafka).publishPending();
    } finally {
      assertThat(Thread.interrupted()).isTrue();
    }

    assertThat(first.getPublishedAt()).isNull();
    verify(repository, never()).save(any());
  }

  private OutboxRelay relay(KafkaOperations<String, String> kafka) {
    TransactionOperations inline =
        new TransactionOperations() {
          @Override
          public <T> T execute(
              org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(null);
          }
        };
    return new OutboxRelay(repository, kafka, inline, CLOCK);
  }

  @SuppressWarnings("unchecked")
  private static KafkaOperations<String, String> kafka() {
    return mock(KafkaOperations.class);
  }

  private static CompletableFuture<SendResult<String, String>> acknowledged() {
    return CompletableFuture.completedFuture(null);
  }

  private static OutboxEvent row(String name) {
    return new OutboxEvent(
        UUID.randomUUID(), "topic", "key-" + name, "Type-" + name, "{}", NOW.minusSeconds(5));
  }
}
