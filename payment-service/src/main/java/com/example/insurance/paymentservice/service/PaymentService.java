package com.example.insurance.paymentservice.service;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.messaging.OutboxWriter;
import com.example.insurance.paymentservice.domain.Payment;
import com.example.insurance.paymentservice.domain.PaymentStatus;
import com.example.insurance.paymentservice.domain.PaymentTransaction;
import com.example.insurance.paymentservice.dto.CreateIntentRequest;
import com.example.insurance.paymentservice.dto.PaymentResponse;
import com.example.insurance.paymentservice.exception.ApiException;
import com.example.insurance.paymentservice.exception.ErrorCode;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.provider.MockPaymentProvider;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import com.example.insurance.paymentservice.repository.PaymentTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Premium payments (PLAN 8.2, 10). Creating a payment is idempotent on the intent key, and each pay
 * attempt is a ledger row whose idempotency key makes a repeat return the same result. Outcomes go
 * out through the outbox in the same transaction (ADR-013).
 */
@Service
public class PaymentService {

  private final PaymentRepository payments;
  private final PaymentTransactionRepository transactions;
  private final MockPaymentProvider provider;
  private final OutboxWriter outbox;
  private final Clock clock;

  public PaymentService(
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

  /** Creates the premium for a policy, or returns the one an earlier request with this key made. */
  @Transactional
  public PaymentResponse createIntent(String intentKey, CreateIntentRequest request) {
    Optional<Payment> existing = payments.findByIntentKey(intentKey);
    if (existing.isPresent()) {
      Payment payment = existing.get();
      if (!payment.sameRequestAs(request.policyId(), request.amount(), request.currency())) {
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED, "This key was used for a different payment request.");
      }
      return PaymentResponse.of(payment, payment.getStatus());
    }
    Payment payment =
        Payment.create(
            intentKey,
            request.policyId(),
            request.policyNumber(),
            request.insurerId(),
            request.holderUserId(),
            request.amount(),
            request.currency(),
            clock.instant());
    payments.save(payment);
    return PaymentResponse.of(payment, payment.getStatus());
  }

  /**
   * One pay attempt by the holder. The mock provider decides the outcome. Both the ledger row and
   * the event are written in this transaction.
   */
  @Transactional
  public PaymentResponse pay(
      UUID holderUserId, UUID paymentId, String idempotencyKey, boolean decline) {
    Payment payment = ownedPayment(holderUserId, paymentId);
    if (payment.paysOut()) {
      throw new ApiException(
          ErrorCode.PAYMENT_NOT_PAYABLE,
          "A payout or refund is paid out to you; there is nothing to pay.");
    }

    Optional<PaymentTransaction> earlier = transactions.findByIdempotencyKey(idempotencyKey);
    if (earlier.isPresent()) {
      PaymentTransaction attempt = earlier.get();
      if (!attempt.getPaymentId().equals(paymentId)) {
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED, "This key was used for a different payment request.");
      }
      return PaymentResponse.of(payment, attempt.getOutcome());
    }
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      throw new ApiException(ErrorCode.PAYMENT_ALREADY_PAID, "This premium is already paid.");
    }
    if (payment.getStatus() == PaymentStatus.EXPIRED) {
      throw new ApiException(
          ErrorCode.PAYMENT_EXPIRED, "The policy was cancelled for non-payment. Buy again.");
    }

    Instant now = clock.instant();
    MockPaymentProvider.Charge charge =
        provider.charge(payment.getAmount(), payment.getCurrency(), decline);
    if (charge.succeeded()) {
      payment.recordSuccess(charge.providerRef(), now);
      transactions.save(
          PaymentTransaction.succeeded(
              payment.getId(),
              idempotencyKey,
              payment.getAmount(),
              payment.getCurrency(),
              charge.providerRef(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.PREMIUM_PAID,
          PremiumPaid.VERSION,
          payment.getReferenceId().toString(),
          payment.getInsurerId().toString(),
          new PremiumPaid(
              payment.getId(),
              payment.getReferenceId(),
              payment.getReferenceNumber(),
              payment.getHolderUserId(),
              payment.getAmount(),
              payment.getCurrency(),
              charge.providerRef()));
    } else {
      payment.recordFailure(now);
      transactions.save(
          PaymentTransaction.failed(
              payment.getId(),
              idempotencyKey,
              payment.getAmount(),
              payment.getCurrency(),
              charge.failureReason(),
              now));
      outbox.record(
          Topics.PAYMENT_EVENTS,
          EventTypes.PREMIUM_FAILED,
          PremiumFailed.VERSION,
          payment.getReferenceId().toString(),
          payment.getInsurerId().toString(),
          new PremiumFailed(
              payment.getId(),
              payment.getReferenceId(),
              payment.getReferenceNumber(),
              payment.getHolderUserId(),
              charge.failureReason()));
    }
    return PaymentResponse.of(payment, payment.getStatus());
  }

  @Transactional(readOnly = true)
  public PaymentResponse get(UUID holderUserId, UUID paymentId) {
    Payment payment = ownedPayment(holderUserId, paymentId);
    return PaymentResponse.of(payment, payment.getStatus());
  }

  /**
   * The caller's payments for one policy: its premium and, after a paid cancellation, its refund
   * (one of each, by the unique reference).
   */
  @Transactional(readOnly = true)
  public List<PaymentResponse> listForReference(UUID holderUserId, UUID referenceId) {
    return payments.findByHolderUserIdAndReferenceId(holderUserId, referenceId).stream()
        .map(payment -> PaymentResponse.of(payment, payment.getStatus()))
        .toList();
  }

  /** Another customer's payment looks the same as a missing one (IDOR rule, 404). */
  private Payment ownedPayment(UUID holderUserId, UUID paymentId) {
    return payments
        .findByIdAndHolderUserId(paymentId, holderUserId)
        .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No such payment."));
  }
}
