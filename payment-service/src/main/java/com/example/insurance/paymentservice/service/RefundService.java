package com.example.insurance.paymentservice.service;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.RefundFailed;
import com.example.insurance.events.payment.RefundSucceeded;
import com.example.insurance.events.policy.PolicyCancelled;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays back a cancelled policy's premium (M8b, ADR-021). policy-service decides the amount from the
 * plan's rule and announces it in {@code PolicyCancelled}; this service only moves the money, never
 * more than the premium it received.
 */
@Service
public class RefundService {

  private static final Logger LOG = LoggerFactory.getLogger(RefundService.class);

  private final PaymentRepository payments;
  private final PaymentTransactionRepository transactions;
  private final MockPaymentProvider provider;
  private final OutboxWriter outbox;
  private final Clock clock;

  public RefundService(
      PaymentRepository payments,
      PaymentTransactionRepository transactions,
      MockPaymentProvider provider,
      OutboxWriter outbox,
      Clock clock) {
    this.payments = payments;
    this.transactions = transactions;
    this.provider = provider;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Creates and attempts the refund for a paid premium, inside the caller's transaction (which also
   * holds the inbox row). The event id is the refund's intent key and its first attempt's key.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refund(UUID eventId, Payment premium, PolicyCancelled event) {
    if (event.refundAmount().compareTo(premium.getAmount()) > 0) {
      LOG.error(
          "PolicyCancelled {} asks to refund more than premium {} received; refused.",
          eventId,
          premium.getId());
      return;
    }
    if (payments
        .findByReferenceTypeAndReferenceId(Payment.REFERENCE_REFUND, event.policyId())
        .isPresent()) {
      LOG.warn("Policy {} already has a refund; event {} ignored.", event.policyId(), eventId);
      return;
    }
    // save() may return a different managed copy (the entity has a primitive version), so keep it.
    Payment refund =
        payments.save(
            Payment.createRefund(
                eventId.toString(),
                event.policyId(),
                premium.getReferenceNumber(),
                premium.getInsurerId(),
                premium.getHolderUserId(),
                event.refundAmount(),
                premium.getCurrency(),
                clock.instant()));
    attempt(refund, eventId.toString());
  }

  /**
   * One more attempt at a failed refund, by an ADMIN. {@code insurerId} is the token's insurer, or
   * null for a platform admin. Another insurer's refund looks like a missing one. A repeat with the
   * same key returns the first attempt's result.
   */
  @Transactional
  public PaymentResponse retry(UUID insurerId, UUID paymentId, String idempotencyKey) {
    Payment refund = visibleRefund(insurerId, paymentId);

    Optional<PaymentTransaction> earlier = transactions.findByIdempotencyKey(idempotencyKey);
    if (earlier.isPresent()) {
      PaymentTransaction attempt = earlier.get();
      if (!attempt.getPaymentId().equals(paymentId)) {
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED, "This key was used for a different payment request.");
      }
      return PaymentResponse.of(refund, attempt.getOutcome());
    }
    if (refund.getStatus() != PaymentStatus.FAILED) {
      throw new ApiException(
          ErrorCode.REFUND_NOT_RETRYABLE, "Only a failed refund can be retried.");
    }
    attempt(refund, idempotencyKey);
    return PaymentResponse.of(refund, refund.getStatus());
  }

  /** The refund of a policy, for staff of that policy's insurer (or a platform admin). */
  @Transactional(readOnly = true)
  public PaymentResponse findByPolicy(UUID insurerId, UUID policyId) {
    Payment refund =
        payments
            .findByReferenceTypeAndReferenceId(Payment.REFERENCE_REFUND, policyId)
            .filter(found -> insurerId == null || insurerId.equals(found.getInsurerId()))
            .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No such refund."));
    return PaymentResponse.of(refund, refund.getStatus());
  }

  private Payment visibleRefund(UUID insurerId, UUID paymentId) {
    return payments
        .findById(paymentId)
        .filter(Payment::isRefund)
        .filter(found -> insurerId == null || insurerId.equals(found.getInsurerId()))
        .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No such refund."));
  }

  /** One attempt: ask the provider, write the ledger row, update the status, queue the event. */
  private void attempt(Payment refund, String idempotencyKey) {
    Instant now = clock.instant();
    MockPaymentProvider.Charge result =
        provider.refund(refund.getHolderUserId(), refund.getAmount(), refund.getCurrency());
    if (result.succeeded()) {
      refund.recordSuccess(result.providerRef(), now);
      transactions.save(
          PaymentTransaction.succeeded(
              refund.getId(),
              idempotencyKey,
              refund.getAmount(),
              refund.getCurrency(),
              result.providerRef(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.REFUND_SUCCEEDED,
          RefundSucceeded.VERSION,
          refund.getReferenceId().toString(),
          refund.getInsurerId().toString(),
          new RefundSucceeded(
              refund.getId(),
              refund.getReferenceId(),
              refund.getReferenceNumber(),
              refund.getHolderUserId(),
              refund.getAmount(),
              refund.getCurrency(),
              result.providerRef(),
              now));
    } else {
      refund.recordFailure(now);
      transactions.save(
          PaymentTransaction.failed(
              refund.getId(),
              idempotencyKey,
              refund.getAmount(),
              refund.getCurrency(),
              result.failureReason(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.REFUND_FAILED,
          RefundFailed.VERSION,
          refund.getReferenceId().toString(),
          refund.getInsurerId().toString(),
          new RefundFailed(
              refund.getId(),
              refund.getReferenceId(),
              refund.getReferenceNumber(),
              refund.getHolderUserId(),
              refund.getAmount(),
              refund.getCurrency(),
              result.failureReason(),
              now));
    }
  }
}
