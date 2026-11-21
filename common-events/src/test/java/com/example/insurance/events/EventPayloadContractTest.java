package com.example.insurance.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.TermsSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Locks the wire shape of the M4b payloads (ADR-005: additive changes only). */
class EventPayloadContractTest {

  private static final UUID POLICY_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-8c45e88e4a01");
  private static final UUID PAYMENT_ID = UUID.fromString("7d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
  private static final UUID HOLDER_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

  private final ObjectMapper mapper = new ObjectMapper();

  private <T> EventEnvelope<T> envelope(String type, T payload) {
    return new EventEnvelope<>(
        UUID.fromString("5b1c2f0e-7a8d-4c3b-9e2f-1a2b3c4d5e6f"),
        type,
        1,
        Instant.parse("2026-11-21T09:00:00Z"),
        POLICY_ID.toString(),
        "00000000-0000-4000-8000-00000000000a",
        "corr-m4b",
        payload);
  }

  private <T> EventEnvelope<T> roundTrip(
      EventEnvelope<T> event, TypeReference<EventEnvelope<T>> type) {
    String json = mapper.writeValueAsString(event);
    EventEnvelope<T> readBack = mapper.readValue(json, type);
    assertThat(readBack).isEqualTo(event);
    return readBack;
  }

  @Test
  void premiumPaidRoundTripsAndKeepsMoneyExact() {
    EventEnvelope<PremiumPaid> event =
        envelope(
            "PremiumPaid",
            new PremiumPaid(
                PAYMENT_ID,
                POLICY_ID,
                "POL-ACACIA-2026-000001",
                new BigDecimal("12500.00"),
                "KES",
                "mock-ref-1"));

    EventEnvelope<PremiumPaid> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<PremiumPaid>>() {});

    assertThat(readBack.payload().amount()).isEqualByComparingTo("12500.00");
    assertThat(mapper.writeValueAsString(event))
        .contains("\"paymentId\":\"" + PAYMENT_ID + "\"")
        .contains("\"policyNumber\":\"POL-ACACIA-2026-000001\"")
        .contains("\"amount\":12500.00")
        .contains("\"currency\":\"KES\"");
  }

  @Test
  void premiumFailedRoundTrips() {
    EventEnvelope<PremiumFailed> event =
        envelope(
            "PremiumFailed",
            new PremiumFailed(
                PAYMENT_ID, POLICY_ID, "POL-ACACIA-2026-000001", "PROVIDER_DECLINED"));

    roundTrip(event, new TypeReference<EventEnvelope<PremiumFailed>>() {});

    assertThat(mapper.writeValueAsString(event)).contains("\"reason\":\"PROVIDER_DECLINED\"");
  }

  @Test
  void policyCancelledRoundTrips() {
    EventEnvelope<PolicyCancelled> event =
        envelope(
            "PolicyCancelled",
            new PolicyCancelled(
                POLICY_ID, "POL-ACACIA-2026-000001", 3, "UNPAID", LocalDate.of(2026, 12, 31)));

    roundTrip(event, new TypeReference<EventEnvelope<PolicyCancelled>>() {});

    assertThat(mapper.writeValueAsString(event))
        .contains("\"policyVersion\":3")
        .contains("\"effectiveDate\":\"2026-12-31\"");
  }

  @Test
  void policyActivatedRoundTripsWithTermsAndMembers() {
    TermsSnapshot terms =
        new TermsSnapshot(
            1,
            "ACACIA",
            UUID.fromString("22222222-3333-4444-8555-666666666666"),
            "ACA-STD",
            1,
            "KES",
            "NEXT_BIRTHDAY",
            List.of(),
            List.of(),
            List.of("cosmetic surgery"),
            30,
            null);
    PolicyActivated payload =
        new PolicyActivated(
            POLICY_ID,
            "POL-ACACIA-2026-000001",
            2,
            HOLDER_ID,
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 11, 30),
            List.of(
                new PolicyActivated.MemberTerms(
                    "MEM-ACACIA-2026-000001-0",
                    "PRINCIPAL",
                    "FEMALE",
                    "Smoke",
                    "Buyer",
                    LocalDate.of(1990, 6, 15),
                    false)),
            terms);
    EventEnvelope<PolicyActivated> event = envelope("PolicyActivated", payload);

    EventEnvelope<PolicyActivated> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<PolicyActivated>>() {});

    assertThat(readBack.payload().terms().snapshotVersion()).isEqualTo(1);
    assertThat(readBack.payload().members()).hasSize(1);
    assertThat(mapper.writeValueAsString(event))
        .contains("\"policyVersion\":2")
        .contains("\"coverStart\":\"2026-12-01\"")
        .contains("\"coverEnd\":\"2027-11-30\"")
        .contains("\"memberNumber\":\"MEM-ACACIA-2026-000001-0\"");
  }

  @Test
  void policyActivatedWithoutMembersDefaultsToEmptyList() {
    PolicyActivated payload =
        new PolicyActivated(
            POLICY_ID, "POL-X", 1, HOLDER_ID, LocalDate.now(), LocalDate.now(), null, null);

    assertThat(payload.members()).isEmpty();
  }
}
