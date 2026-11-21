package com.example.insurance.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payment attempt in the ledger (PLAN 7). Rows are written once and never updated, so the
 * ledger shows every attempt and its outcome. The class has no setters on purpose.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

  @Id private UUID id;

  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 80)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus outcome;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "provider_ref", length = 80)
  private String providerRef;

  @Column(name = "failure_reason", length = 60)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PaymentTransaction() {
    // Required by JPA.
  }

  private PaymentTransaction(
      UUID paymentId,
      String idempotencyKey,
      PaymentStatus outcome,
      BigDecimal amount,
      String currency,
      String providerRef,
      String failureReason,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.paymentId = paymentId;
    this.idempotencyKey = idempotencyKey;
    this.outcome = outcome;
    this.amount = amount;
    this.currency = currency;
    this.providerRef = providerRef;
    this.failureReason = failureReason;
    this.createdAt = createdAt;
  }

  public static PaymentTransaction succeeded(
      UUID paymentId,
      String idempotencyKey,
      BigDecimal amount,
      String currency,
      String providerRef,
      Instant now) {
    return new PaymentTransaction(
        paymentId,
        idempotencyKey,
        PaymentStatus.SUCCEEDED,
        amount,
        currency,
        providerRef,
        null,
        now);
  }

  public static PaymentTransaction failed(
      UUID paymentId,
      String idempotencyKey,
      BigDecimal amount,
      String currency,
      String failureReason,
      Instant now) {
    return new PaymentTransaction(
        paymentId,
        idempotencyKey,
        PaymentStatus.FAILED,
        amount,
        currency,
        null,
        failureReason,
        now);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** {@code SUCCEEDED} or {@code FAILED}: the result this attempt produced. */
  public PaymentStatus getOutcome() {
    return outcome;
  }

  public String getProviderRef() {
    return providerRef;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
