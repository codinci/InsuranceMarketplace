package com.example.insurance.paymentservice.service;

import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.paymentservice.domain.Payment;
import com.example.insurance.paymentservice.messaging.ProcessedEvent;
import com.example.insurance.paymentservice.messaging.ProcessedEventRepository;
import com.example.insurance.paymentservice.messaging.Topics;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires the premium of a policy that was cancelled for non-payment. The inbox check and the
 * change share one transaction, so a redelivered event has no second effect (ADR-013).
 */
@Service
public class PaymentExpiryService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentExpiryService.class);

  private final PaymentRepository payments;
  private final ProcessedEventRepository processed;
  private final Clock clock;

  public PaymentExpiryService(
      PaymentRepository payments, ProcessedEventRepository processed, Clock clock) {
    this.payments = payments;
    this.processed = processed;
    this.clock = clock;
  }

  @Transactional
  public void onPolicyCancelled(UUID eventId, PolicyCancelled event) {
    if (processed.existsById(eventId)) {
      LOG.debug("PolicyCancelled {} already applied; skipped.", eventId);
      return;
    }
    payments
        .findByReferenceTypeAndReferenceId(Payment.REFERENCE_POLICY, event.policyId())
        .ifPresent(this::expireIfPayable);
    processed.save(new ProcessedEvent(eventId, Topics.CONSUMER_NAME, clock.instant()));
  }

  private void expireIfPayable(Payment payment) {
    if (payment.isPayable()) {
      payment.expire(clock.instant());
    } else {
      // Paid just before the cancellation: the policy service has to reconcile this (PLAN 11, M8).
      LOG.warn(
          "Policy cancelled but payment {} is {}; left unchanged.",
          payment.getId(),
          payment.getStatus());
    }
  }
}
