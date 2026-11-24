package com.example.insurance.paymentservice.service;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.events.payment.PayoutFailed;
import com.example.insurance.events.payment.PayoutSucceeded;
import com.example.insurance.messaging.Inbox;
import com.example.insurance.messaging.OutboxWriter;
import com.example.insurance.paymentservice.domain.Payment;
import com.example.insurance.paymentservice.domain.PaymentStatus;
import com.example.insurance.paymentservice.domain.PaymentTransaction;
import com.example.insurance.paymentservice.dto.PaymentResponse;
import com.example.insurance.paymentservice.exception.ApiException;
import com.example.insurance.paymentservice.exception.ErrorCode;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.provider.MockPaymentProvider;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import com.example.insurance.paymentservice.repository.PaymentTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays approved claims (PLAN 10, M7). An approved claim becomes one payout payment, attempted at
 * once; the result goes out as {@code PayoutSucceeded} or {@code PayoutFailed} through the outbox
 * in the same transaction (ADR-013). Redelivery is harmless twice over: the inbox remembers the
 * event id, and a claim can only ever have one payout row. A failed payout is retried by staff,
 * which adds a new ledger row to the same payment.
 */
@Service
public class PayoutService {

  private static final Logger LOG = LoggerFactory.getLogger(PayoutService.class);

  private final PaymentRepository payments;
  private final PaymentTransactionRepository transactions;
  private final MockPaymentProvider provider;
  private final OutboxWriter outbox;
  private final Inbox inbox;
  private final Clock clock;

  public PayoutService(
      PaymentRepository payments,
      PaymentTransactionRepository transactions,
      MockPaymentProvider provider,
      OutboxWriter outbox,
      Inbox inbox,
      Clock clock) {
    this.payments = payments;
    this.transactions = transactions;
    this.provider = provider;
    this.outbox = outbox;
    this.inbox = inbox;
    this.clock = clock;
  }

  /**
   * Creates and attempts the payout for an approved claim. The event id is the payout's intent key
   * and the idempotency key of its first attempt.
   */
  @Transactional
  public void onClaimApproved(UUID eventId, String insurerId, ClaimApproved event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    if (payments
        .findByReferenceTypeAndReferenceId(Payment.REFERENCE_CLAIM, event.claimId())
        .isPresent()) {
      LOG.warn("Claim {} already has a payout; event {} ignored.", event.claimId(), eventId);
    } else {
      // save() may return a different managed copy (the entity has a primitive version), so keep
      // it.
      Payment payout =
          payments.save(
              Payment.createPayout(
                  eventId.toString(),
                  event.claimId(),
                  event.claimNumber(),
                  UUID.fromString(insurerId),
                  event.holderUserId(),
                  event.amountApproved(),
                  event.currency(),
                  clock.instant()));
      attempt(payout, eventId.toString());
    }
    inbox.markApplied(eventId);
  }

  /**
   * One more attempt at a failed payout, by an ADMIN. {@code insurerId} is the token's insurer, or
   * null for a platform admin. Another insurer's payout looks like a missing one. A repeat with the
   * same key returns the first attempt's result.
   */
  @Transactional
  public PaymentResponse retry(UUID insurerId, UUID paymentId, String idempotencyKey) {
    Payment payment = visiblePayout(insurerId, paymentId);

    Optional<PaymentTransaction> earlier = transactions.findByIdempotencyKey(idempotencyKey);
    if (earlier.isPresent()) {
      PaymentTransaction attempt = earlier.get();
      if (!attempt.getPaymentId().equals(paymentId)) {
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED, "This key was used for a different payment request.");
      }
      return PaymentResponse.of(payment, attempt.getOutcome());
    }
    if (payment.getStatus() != PaymentStatus.FAILED) {
      throw new ApiException(
          ErrorCode.PAYOUT_NOT_RETRYABLE, "Only a failed payout can be retried.");
    }
    attempt(payment, idempotencyKey);
    return PaymentResponse.of(payment, payment.getStatus());
  }

  /** The payout for a claim, for staff of that claim's insurer (or a platform admin). */
  @Transactional(readOnly = true)
  public PaymentResponse findByClaim(UUID insurerId, UUID claimId) {
    Payment payment =
        payments
            .findByReferenceTypeAndReferenceId(Payment.REFERENCE_CLAIM, claimId)
            .filter(found -> insurerId == null || insurerId.equals(found.getInsurerId()))
            .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No such payout."));
    return PaymentResponse.of(payment, payment.getStatus());
  }

  private Payment visiblePayout(UUID insurerId, UUID paymentId) {
    return payments
        .findById(paymentId)
        .filter(Payment::isPayout)
        .filter(found -> insurerId == null || insurerId.equals(found.getInsurerId()))
        .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No such payout."));
  }

  /** One attempt: ask the provider, write the ledger row, update the status, queue the event. */
  private void attempt(Payment payment, String idempotencyKey) {
    Instant now = clock.instant();
    MockPaymentProvider.Charge result =
        provider.payout(payment.getHolderUserId(), payment.getAmount(), payment.getCurrency());
    if (result.succeeded()) {
      payment.recordSuccess(result.providerRef(), now);
      transactions.save(
          PaymentTransaction.succeeded(
              payment.getId(),
              idempotencyKey,
              payment.getAmount(),
              payment.getCurrency(),
              result.providerRef(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.PAYOUT_SUCCEEDED,
          PayoutSucceeded.VERSION,
          payment.getReferenceId().toString(),
          payment.getInsurerId().toString(),
          new PayoutSucceeded(
              payment.getId(),
              payment.getReferenceId(),
              payment.getReferenceNumber(),
              payment.getHolderUserId(),
              payment.getAmount(),
              payment.getCurrency(),
              result.providerRef(),
              now));
    } else {
      payment.recordFailure(now);
      transactions.save(
          PaymentTransaction.failed(
              payment.getId(),
              idempotencyKey,
              payment.getAmount(),
              payment.getCurrency(),
              result.failureReason(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.PAYOUT_FAILED,
          PayoutFailed.VERSION,
          payment.getReferenceId().toString(),
          payment.getInsurerId().toString(),
          new PayoutFailed(
              payment.getId(),
              payment.getReferenceId(),
              payment.getReferenceNumber(),
              payment.getHolderUserId(),
              payment.getAmount(),
              payment.getCurrency(),
              result.failureReason(),
              now));
    }
  }
}
