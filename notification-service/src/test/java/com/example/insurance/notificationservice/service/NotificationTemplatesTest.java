package com.example.insurance.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.EventTypes;
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
import com.example.insurance.events.user.UserRegistered;
import com.example.insurance.messaging.UnknownEventTypeException;
import com.example.insurance.notificationservice.service.NotificationTemplates.Rendered;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTemplatesTest {

  private static final UUID HOLDER = UUID.fromString("11111111-2222-4333-8444-555555555555");
  private static final UUID ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-8c45e88e4a01");
  private static final Instant NOW = Instant.parse("2026-11-24T09:00:00Z");

  private static EventEnvelope<Object> envelope(String type, Object payload) {
    return new EventEnvelope<>(ID, type, 1, NOW, ID.toString(), null, "corr", payload);
  }

  /** One event of every type the service notifies about, all addressed to {@link #HOLDER}. */
  private static List<EventEnvelope<Object>> everyKnownEvent(UUID holder) {
    return List.of(
        envelope(
            EventTypes.USER_REGISTERED,
            new UserRegistered(holder, "amina@example.com", "Amina", "Otieno")),
        envelope(
            EventTypes.POLICY_ACTIVATED,
            new PolicyActivated(
                ID,
                "POL-1",
                2,
                holder,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 11, 30),
                null,
                null)),
        envelope(
            EventTypes.POLICY_CANCELLED,
            new PolicyCancelled(ID, "POL-1", holder, 3, "UNPAID", LocalDate.of(2026, 12, 1), null)),
        envelope(
            EventTypes.PREMIUM_PAID,
            new PremiumPaid(ID, ID, "POL-1", holder, new BigDecimal("12500.00"), "KES", "mock-1")),
        envelope(EventTypes.PREMIUM_FAILED, new PremiumFailed(ID, ID, "POL-1", holder, "DECLINED")),
        envelope(
            EventTypes.CLAIM_SUBMITTED,
            new ClaimSubmitted(
                ID,
                "CLM-1",
                ID,
                holder,
                "MEM-1",
                "OUTPATIENT",
                "OP-CONSULT",
                "ILLNESS",
                LocalDate.of(2027, 1, 15),
                new BigDecimal("3000.00"),
                "KES",
                NOW)),
        envelope(
            EventTypes.CLAIM_INFO_REQUESTED,
            new ClaimInfoRequested(ID, "CLM-1", ID, holder, "Please upload the invoice.", NOW)),
        envelope(
            EventTypes.CLAIM_APPROVED,
            new ClaimApproved(
                ID,
                "CLM-1",
                ID,
                holder,
                "MEM-1",
                "OP-CONSULT",
                new BigDecimal("2000.00"),
                "KES",
                NOW)),
        envelope(
            EventTypes.CLAIM_REJECTED,
            new ClaimRejected(ID, "CLM-1", ID, holder, "Not covered", NOW)),
        envelope(
            EventTypes.PAYOUT_SUCCEEDED,
            new PayoutSucceeded(
                ID, ID, "CLM-1", holder, new BigDecimal("2000.00"), "KES", "mock-2", NOW)),
        envelope(
            EventTypes.PAYOUT_FAILED,
            new PayoutFailed(
                ID, ID, "CLM-1", holder, new BigDecimal("2000.00"), "KES", "DECLINED", NOW)));
  }

  @Test
  void everyKnownEventRendersForItsHolder() {
    for (EventEnvelope<Object> event : everyKnownEvent(HOLDER)) {
      Rendered rendered = NotificationTemplates.render(event).orElseThrow();

      assertThat(rendered.recipientUserId()).as(event.type()).isEqualTo(HOLDER);
      assertThat(rendered.subject()).as(event.type()).isNotBlank();
      assertThat(rendered.body()).as(event.type()).isNotBlank();
    }
  }

  @Test
  void everyKnownTypeHasAPayloadClassThatMatchesTheEvent() {
    for (EventEnvelope<Object> event : everyKnownEvent(HOLDER)) {
      assertThat(NotificationTemplates.payloadClassOf(event.type()))
          .as(event.type())
          .isEqualTo(event.payload().getClass());
    }
  }

  @Test
  void anEventWithNoRecipientRendersNothing() {
    for (EventEnvelope<Object> event : everyKnownEvent(null)) {
      assertThat(NotificationTemplates.render(event)).as(event.type()).isEmpty();
    }
  }

  @Test
  void bodiesCarryAmountsAndNumbersButNoNameOrAddress() {
    String all =
        everyKnownEvent(HOLDER).stream()
            .map(e -> NotificationTemplates.render(e).orElseThrow().body())
            .reduce("", String::concat);

    assertThat(all).contains("KES 12500.00").contains("KES 2000.00").contains("POL-1");
    assertThat(all).doesNotContain("Amina").doesNotContain("Otieno").doesNotContain("@");
  }

  @Test
  void anUnknownTypeIsRefusedSoItGoesToTheDeadLetterTopic() {
    assertThatThrownBy(() -> NotificationTemplates.payloadClassOf("SomethingNew"))
        .isInstanceOf(UnknownEventTypeException.class);
    assertThatThrownBy(() -> NotificationTemplates.render(envelope("SomethingNew", "not an event")))
        .isInstanceOf(UnknownEventTypeException.class);
  }
}
