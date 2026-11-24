package com.example.insurance.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.events.claim.ClaimInfoRequested;
import com.example.insurance.events.claim.ClaimRejected;
import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.events.payment.PayoutFailed;
import com.example.insurance.events.payment.PayoutSucceeded;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.user.UserRegistered;
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
                HOLDER_ID,
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
                PAYMENT_ID, POLICY_ID, "POL-ACACIA-2026-000001", HOLDER_ID, "PROVIDER_DECLINED"));

    roundTrip(event, new TypeReference<EventEnvelope<PremiumFailed>>() {});

    assertThat(mapper.writeValueAsString(event)).contains("\"reason\":\"PROVIDER_DECLINED\"");
  }

  @Test
  void policyCancelledRoundTrips() {
    EventEnvelope<PolicyCancelled> event =
        envelope(
            "PolicyCancelled",
            new PolicyCancelled(
                POLICY_ID,
                "POL-ACACIA-2026-000001",
                HOLDER_ID,
                3,
                "UNPAID",
                LocalDate.of(2026, 12, 31)));

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

  @Test
  void claimSubmittedRoundTripsAndKeepsMoneyExact() {
    EventEnvelope<ClaimSubmitted> event =
        envelope(
            "ClaimSubmitted",
            new ClaimSubmitted(
                UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555"),
                "CLM-ACACIA-2026-000001",
                POLICY_ID,
                HOLDER_ID,
                "MEM-ACACIA-2026-000001-0",
                "OUTPATIENT",
                "OP-CONSULT",
                "ILLNESS",
                LocalDate.of(2027, 1, 15),
                new BigDecimal("3000.00"),
                "KES",
                Instant.parse("2027-01-16T08:00:00Z")));

    EventEnvelope<ClaimSubmitted> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<ClaimSubmitted>>() {});

    assertThat(readBack.payload().amountClaimed()).isEqualByComparingTo("3000.00");
    assertThat(mapper.writeValueAsString(event))
        .contains("\"claimNumber\":\"CLM-ACACIA-2026-000001\"")
        .contains("\"incidentDate\":\"2027-01-15\"")
        .contains("\"amountClaimed\":3000.00");
  }

  private static final UUID CLAIM_ID = UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555");

  @Test
  void claimApprovedRoundTripsAndKeepsMoneyExact() {
    EventEnvelope<ClaimApproved> event =
        envelope(
            "ClaimApproved",
            new ClaimApproved(
                CLAIM_ID,
                "CLM-ACACIA-2026-000001",
                POLICY_ID,
                HOLDER_ID,
                "MEM-ACACIA-2026-000001-0",
                "OP-CONSULT",
                new BigDecimal("2000.00"),
                "KES",
                Instant.parse("2027-01-17T09:30:00Z")));

    EventEnvelope<ClaimApproved> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<ClaimApproved>>() {});

    assertThat(readBack.payload().amountApproved()).isEqualByComparingTo("2000.00");
    assertThat(mapper.writeValueAsString(event))
        .contains("\"amountApproved\":2000.00")
        .contains("\"holderUserId\":\"" + HOLDER_ID + "\"")
        .contains("\"benefitCode\":\"OP-CONSULT\"");
  }

  @Test
  void claimRejectedRoundTripsWithItsReason() {
    EventEnvelope<ClaimRejected> event =
        envelope(
            "ClaimRejected",
            new ClaimRejected(
                CLAIM_ID,
                "CLM-ACACIA-2026-000001",
                POLICY_ID,
                HOLDER_ID,
                "The invoice does not match the provider named.",
                Instant.parse("2027-01-17T09:30:00Z")));

    EventEnvelope<ClaimRejected> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<ClaimRejected>>() {});

    assertThat(readBack.payload().reason()).startsWith("The invoice");
    assertThat(mapper.writeValueAsString(event)).contains("\"rejectedAt\":");
  }

  @Test
  void claimInfoRequestedRoundTripsWithItsMessage() {
    EventEnvelope<ClaimInfoRequested> event =
        envelope(
            "ClaimInfoRequested",
            new ClaimInfoRequested(
                CLAIM_ID,
                "CLM-ACACIA-2026-000001",
                POLICY_ID,
                HOLDER_ID,
                "Please attach the discharge summary.",
                Instant.parse("2027-01-17T09:30:00Z")));

    EventEnvelope<ClaimInfoRequested> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<ClaimInfoRequested>>() {});

    assertThat(readBack.payload().message()).contains("discharge summary");
    assertThat(mapper.writeValueAsString(event)).contains("\"requestedAt\":");
  }

  @Test
  void theClaimEventTypesAreTheNamesOnTheWire() {
    assertThat(EventTypes.CLAIM_SUBMITTED).isEqualTo("ClaimSubmitted");
    assertThat(EventTypes.CLAIM_APPROVED).isEqualTo("ClaimApproved");
    assertThat(EventTypes.CLAIM_REJECTED).isEqualTo("ClaimRejected");
    assertThat(EventTypes.CLAIM_INFO_REQUESTED).isEqualTo("ClaimInfoRequested");
  }

  @Test
  void payoutSucceededRoundTripsAndKeepsMoneyExact() {
    UUID claimId = UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555");
    EventEnvelope<PayoutSucceeded> event =
        envelope(
            "PayoutSucceeded",
            new PayoutSucceeded(
                PAYMENT_ID,
                claimId,
                "CLM-ACACIA-2026-000001",
                HOLDER_ID,
                new BigDecimal("1200.00"),
                "KES",
                "mock-payout-1",
                Instant.parse("2026-11-22T10:00:00Z")));

    EventEnvelope<PayoutSucceeded> readBack =
        roundTrip(event, new TypeReference<EventEnvelope<PayoutSucceeded>>() {});

    assertThat(readBack.payload().amount()).isEqualByComparingTo("1200.00");
    assertThat(mapper.writeValueAsString(event))
        .contains("\"claimId\":\"" + claimId + "\"")
        .contains("\"holderUserId\":\"" + HOLDER_ID + "\"")
        .contains("\"amount\":1200.00")
        .contains("\"providerRef\":\"mock-payout-1\"")
        .contains("\"paidAt\":");
  }

  @Test
  void payoutFailedRoundTripsWithAStableReason() {
    UUID claimId = UUID.fromString("c1a1a1a1-2222-4333-8444-555555555555");
    EventEnvelope<PayoutFailed> event =
        envelope(
            "PayoutFailed",
            new PayoutFailed(
                PAYMENT_ID,
                claimId,
                "CLM-ACACIA-2026-000001",
                HOLDER_ID,
                new BigDecimal("1200.00"),
                "KES",
                "PROVIDER_DECLINED",
                Instant.parse("2026-11-22T10:00:00Z")));

    roundTrip(event, new TypeReference<EventEnvelope<PayoutFailed>>() {});

    assertThat(mapper.writeValueAsString(event))
        .contains("\"reason\":\"PROVIDER_DECLINED\"")
        .contains("\"failedAt\":");
  }

  @Test
  void thePayoutEventTypesAreTheNamesOnTheWire() {
    assertThat(EventTypes.PAYOUT_SUCCEEDED).isEqualTo("PayoutSucceeded");
    assertThat(EventTypes.PAYOUT_FAILED).isEqualTo("PayoutFailed");
  }

  @Test
  void userRegisteredRoundTripsAndCarriesNoSecrets() {
    EventEnvelope<UserRegistered> event =
        envelope(
            EventTypes.USER_REGISTERED,
            new UserRegistered(HOLDER_ID, "amina@example.com", "Amina", "Test"));

    roundTrip(event, new TypeReference<EventEnvelope<UserRegistered>>() {});

    assertThat(mapper.writeValueAsString(event))
        .contains("\"email\":\"amina@example.com\"")
        .doesNotContain("password")
        .doesNotContain("nationalId")
        .doesNotContain("roles");
  }

  @Test
  void holderUserIdIsOnTheFourEventsAConsumerNeedsItFor() {
    assertThat(
            mapper.writeValueAsString(
                new PremiumFailed(PAYMENT_ID, POLICY_ID, "P", HOLDER_ID, "R")))
        .contains("\"holderUserId\":\"" + HOLDER_ID + "\"");
    assertThat(
            mapper
                .readValue(
                    "{\"paymentId\":null,\"policyId\":null,\"policyNumber\":\"P\",\"reason\":\"R\"}",
                    PremiumFailed.class)
                .holderUserId())
        .as("an event from before M8a has no holder and still reads")
        .isNull();
  }
}
