package com.example.insurance.notificationservice.service;

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
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What each event says to its recipient. Pure functions of the payload, so they are tested without
 * Kafka or a database. Text holds ids, numbers and amounts, never a name or an address (CLAUDE.md:
 * no PII in logs, and the log row is a log).
 */
public final class NotificationTemplates {

  /** A rendered notification. */
  public record Rendered(UUID recipientUserId, String subject, String body) {}

  private static final Map<String, Class<?>> PAYLOADS =
      Map.ofEntries(
          Map.entry(EventTypes.USER_REGISTERED, UserRegistered.class),
          Map.entry(EventTypes.POLICY_ACTIVATED, PolicyActivated.class),
          Map.entry(EventTypes.POLICY_CANCELLED, PolicyCancelled.class),
          Map.entry(EventTypes.PREMIUM_PAID, PremiumPaid.class),
          Map.entry(EventTypes.PREMIUM_FAILED, PremiumFailed.class),
          Map.entry(EventTypes.CLAIM_SUBMITTED, ClaimSubmitted.class),
          Map.entry(EventTypes.CLAIM_INFO_REQUESTED, ClaimInfoRequested.class),
          Map.entry(EventTypes.CLAIM_APPROVED, ClaimApproved.class),
          Map.entry(EventTypes.CLAIM_REJECTED, ClaimRejected.class),
          Map.entry(EventTypes.PAYOUT_SUCCEEDED, PayoutSucceeded.class),
          Map.entry(EventTypes.PAYOUT_FAILED, PayoutFailed.class));

  private NotificationTemplates() {
    // Pure functions only; not instantiable.
  }

  /**
   * The payload class of an event type this service notifies about.
   *
   * @throws UnknownEventTypeException for a type it does not know, which goes to the dead-letter
   *     topic
   */
  public static Class<?> payloadClassOf(String type) {
    Class<?> payload = PAYLOADS.get(type);
    if (payload == null) {
      throw new UnknownEventTypeException(type);
    }
    return payload;
  }

  /**
   * Renders the notification for an event, or nothing when the event names no recipient (an event
   * published before M8a, which had no holder id).
   */
  public static Optional<Rendered> render(EventEnvelope<?> event) {
    Rendered rendered =
        switch (event.payload()) {
          case UserRegistered p ->
              rendered(
                  p.userId(), "Welcome", "Your account is ready. Browse plans to get covered.");
          case PolicyActivated p ->
              rendered(
                  p.holderUserId(),
                  "Your policy is active",
                  "Policy %s covers you from %s to %s."
                      .formatted(p.policyNumber(), p.coverStart(), p.coverEnd()));
          case PolicyCancelled p ->
              rendered(
                  p.holderUserId(),
                  "Your policy was cancelled",
                  "Policy %s was cancelled (%s), effective %s."
                      .formatted(p.policyNumber(), p.reason(), p.effectiveDate()));
          case PremiumPaid p ->
              rendered(
                  p.holderUserId(),
                  "Payment received",
                  "We received %s for policy %s."
                      .formatted(money(p.currency(), p.amount()), p.policyNumber()));
          case PremiumFailed p ->
              rendered(
                  p.holderUserId(),
                  "Payment failed",
                  "Your payment for policy %s did not go through (%s). You can try again."
                      .formatted(p.policyNumber(), p.reason()));
          case ClaimSubmitted p ->
              rendered(
                  p.holderUserId(),
                  "Claim received",
                  "Claim %s was received and is waiting for review.".formatted(p.claimNumber()));
          case ClaimInfoRequested p ->
              rendered(
                  p.holderUserId(),
                  "More information needed",
                  "Claim %s: %s".formatted(p.claimNumber(), p.message()));
          case ClaimApproved p ->
              rendered(
                  p.holderUserId(),
                  "Claim approved",
                  "Claim %s was approved for %s. Payment is on its way."
                      .formatted(p.claimNumber(), money(p.currency(), p.amountApproved())));
          case ClaimRejected p ->
              rendered(
                  p.holderUserId(),
                  "Claim rejected",
                  "Claim %s was rejected: %s".formatted(p.claimNumber(), p.reason()));
          case PayoutSucceeded p ->
              rendered(
                  p.holderUserId(),
                  "Claim paid",
                  "%s was paid for claim %s."
                      .formatted(money(p.currency(), p.amount()), p.claimNumber()));
          case PayoutFailed p ->
              rendered(
                  p.holderUserId(),
                  "Payout delayed",
                  "The payout for claim %s did not go through. We will retry it."
                      .formatted(p.claimNumber()));
          default -> throw new UnknownEventTypeException(event.type());
        };
    return rendered.recipientUserId() == null ? Optional.empty() : Optional.of(rendered);
  }

  private static Rendered rendered(UUID recipient, String subject, String body) {
    return new Rendered(recipient, subject, body);
  }

  private static String money(String currency, BigDecimal amount) {
    return currency + " " + amount.toPlainString();
  }
}
