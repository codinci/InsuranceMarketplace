package com.example.insurance.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class EventEnvelopeTest {

  /** A stand-in payload. Real payloads (for example PolicyActivated) arrive in later milestones. */
  record SamplePayload(String policyNumber, BigDecimal premium) {}

  private static final TypeReference<EventEnvelope<SamplePayload>> TYPE = new TypeReference<>() {};

  private final ObjectMapper mapper = new ObjectMapper();

  private EventEnvelope<SamplePayload> sample() {
    return new EventEnvelope<>(
        UUID.fromString("5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f"),
        "PolicyActivated",
        1,
        Instant.parse("2026-11-19T04:00:00Z"),
        "policy-42",
        "insurer-a",
        "corr-abc123",
        new SamplePayload("ACA-0001-01", new BigDecimal("12500.00")));
  }

  @Test
  void roundTripKeepsEveryField() {
    EventEnvelope<SamplePayload> original = sample();

    String json = mapper.writeValueAsString(original);
    EventEnvelope<SamplePayload> readBack = mapper.readValue(json, TYPE);

    assertThat(readBack).isEqualTo(original);
  }

  @Test
  void jsonUsesTheContractFieldNames() {
    String json = mapper.writeValueAsString(sample());

    assertThat(json)
        .contains("\"eventId\"")
        .contains("\"type\":\"PolicyActivated\"")
        .contains("\"version\":1")
        .contains("\"occurredAt\":\"2026-11-19T04:00:00Z\"")
        .contains("\"aggregateId\":\"policy-42\"")
        .contains("\"insurerId\":\"insurer-a\"")
        .contains("\"correlationId\":\"corr-abc123\"")
        .contains("\"payload\"");
  }

  @Test
  void unknownFieldsFromNewerProducersAreIgnored() {
    String json =
        """
        {
          "eventId": "5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f",
          "type": "PolicyActivated",
          "version": 2,
          "occurredAt": "2026-11-19T04:00:00Z",
          "aggregateId": "policy-42",
          "insurerId": "insurer-a",
          "correlationId": "corr-abc123",
          "payload": {"policyNumber": "ACA-0001-01", "premium": 12500.00, "newField": true},
          "someFutureEnvelopeField": "ignored"
        }
        """;

    EventEnvelope<SamplePayload> event = mapper.readValue(json, TYPE);

    assertThat(event.type()).isEqualTo("PolicyActivated");
    assertThat(event.version()).isEqualTo(2);
    assertThat(event.payload().policyNumber()).isEqualTo("ACA-0001-01");
  }

  @Test
  void platformEventCanHaveNoInsurer() {
    EventEnvelope<SamplePayload> event =
        new EventEnvelope<>(
            UUID.randomUUID(),
            "UserRegistered",
            1,
            Instant.now(),
            "user-7",
            null,
            "corr-1",
            new SamplePayload("n/a", BigDecimal.ZERO));

    EventEnvelope<SamplePayload> readBack =
        mapper.readValue(mapper.writeValueAsString(event), TYPE);

    assertThat(readBack.insurerId()).isNull();
  }
}
