package com.example.insurance.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurance.events.EventEnvelope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The inbox and the shared JSON helper (ADR-013). */
class InboxAndJsonTest {

  private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
  private final Inbox inbox =
      new Inbox(
          processed,
          Clock.fixed(Instant.parse("2026-11-21T08:00:00Z"), ZoneOffset.UTC),
          "claim-service");

  record Amount(BigDecimal value) {}

  @Test
  void anUnseenEventIsNotYetApplied() {
    UUID id = UUID.randomUUID();
    when(processed.existsById(id)).thenReturn(false);

    assertThat(inbox.alreadyApplied(id)).isFalse();
  }

  @Test
  void aRecordedEventIsAlreadyApplied() {
    UUID id = UUID.randomUUID();
    when(processed.existsById(id)).thenReturn(true);

    assertThat(inbox.alreadyApplied(id)).isTrue();
  }

  @Test
  void markingStoresTheIdUnderThisConsumer() {
    UUID id = UUID.randomUUID();

    inbox.markApplied(id);

    ArgumentCaptor<ProcessedEvent> saved = ArgumentCaptor.forClass(ProcessedEvent.class);
    verify(processed).save(saved.capture());
    assertThat(saved.getValue().getEventId()).isEqualTo(id);
    assertThat(saved.getValue().getConsumer()).isEqualTo("claim-service");
    assertThat(inbox.consumerName()).isEqualTo("claim-service");
  }

  @Test
  void theTypeIsReadWithoutKnowingThePayload() {
    assertThat(EventJson.typeOf("{\"type\":\"PolicyActivated\",\"payload\":{\"x\":1},\"extra\":2}"))
        .isEqualTo("PolicyActivated");
  }

  @Test
  void amountsAreReadAsExactDecimals() {
    String message =
        "{\"eventId\":\"5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f\",\"type\":\"T\",\"version\":1,"
            + "\"occurredAt\":\"2026-11-21T08:00:00Z\",\"aggregateId\":\"a\",\"insurerId\":null,"
            + "\"correlationId\":\"c\",\"payload\":{\"value\":0.10},\"unknownField\":true}";

    EventEnvelope<Amount> envelope = EventJson.envelope(message, Amount.class);

    assertThat(envelope.payload().value()).isEqualTo(new BigDecimal("0.10"));
    assertThat(envelope.type()).isEqualTo("T");
  }
}
