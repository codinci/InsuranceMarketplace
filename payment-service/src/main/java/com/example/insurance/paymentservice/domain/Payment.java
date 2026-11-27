package com.example.insurance.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The premium owed for one policy (PLAN 7, 8.2). The amount is the one policy-service computed;
 * this service never reprices it. Status changes go through the methods here, so an illegal move
 * fails in one place.
 */
@Entity
@Table(name = "payments")
public class Payment {

  /** A policy's premium. */
  public static final String REFERENCE_POLICY = "POLICY";

  /** A payout for an approved claim (M7). */
  public static final String REFERENCE_CLAIM = "CLAIM";

  /** A refund of a cancelled policy's premium (M8b). The reference is the policy. */
  public static final String REFERENCE_REFUND = "REFUND";

  @Id private UUID id;

  @Column(name = "intent_key", nullable = false, unique = true, length = 80)
  private String intentKey;

  @Column(name = "reference_type", nullable = false, length = 20)
  private String referenceType;

  @Column(name = "reference_id", nullable = false)
  private UUID referenceId;

  @Column(name = "reference_number", nullable = false, length = 60)
  private String referenceNumber;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Column(name = "holder_user_id", nullable = false)
  private UUID holderUserId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "provider_ref", length = 80)
  private String providerRef;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected Payment() {
    // Required by JPA.
  }

  /** A new payment waiting for the customer. The intent key makes a retried creation return it. */
  public static Payment create(
      String intentKey,
      UUID policyId,
      String policyNumber,
      UUID insurerId,
      UUID holderUserId,
      BigDecimal amount,
      String currency,
      Instant now) {
    Payment payment = new Payment();
    payment.id = UUID.randomUUID();
    payment.intentKey = intentKey;
    payment.referenceType = REFERENCE_POLICY;
    payment.referenceId = policyId;
    payment.referenceNumber = policyNumber;
    payment.insurerId = insurerId;
    payment.holderUserId = holderUserId;
    payment.amount = amount;
    payment.currency = currency;
    payment.status = PaymentStatus.PENDING;
    payment.createdAt = now;
    payment.updatedAt = now;
    return payment;
  }

  /**
   * A payout for an approved claim, waiting for its first attempt. The intent key is the id of the
   * {@code ClaimApproved} event, so a redelivered event finds this payment instead of making one.
   */
  public static Payment createPayout(
      String intentKey,
      UUID claimId,
      String claimNumber,
      UUID insurerId,
      UUID holderUserId,
      BigDecimal amount,
      String currency,
      Instant now) {
    Payment payment =
        create(intentKey, claimId, claimNumber, insurerId, holderUserId, amount, currency, now);
    payment.referenceType = REFERENCE_CLAIM;
    return payment;
  }

  /**
   * A refund for a cancelled policy, waiting for its first attempt (M8b, ADR-021). The intent key
   * is the id of the {@code PolicyCancelled} event, so a redelivered event finds this payment.
   */
  public static Payment createRefund(
      String intentKey,
      UUID policyId,
      String policyNumber,
      UUID insurerId,
      UUID holderUserId,
      BigDecimal amount,
      String currency,
      Instant now) {
    Payment payment =
        create(intentKey, policyId, policyNumber, insurerId, holderUserId, amount, currency, now);
    payment.referenceType = REFERENCE_REFUND;
    return payment;
  }

  /** True for a claim payout. */
  public boolean isPayout() {
    return REFERENCE_CLAIM.equals(referenceType);
  }

  /** True for a premium refund (M8b). */
  public boolean isRefund() {
    return REFERENCE_REFUND.equals(referenceType);
  }

  /** True when the money goes to the holder (a payout or a refund), so the holder never pays it. */
  public boolean paysOut() {
    return isPayout() || isRefund();
  }

  /** True when the same creation request (same policy, amount and currency) built this payment. */
  public boolean sameRequestAs(
      UUID policyId, BigDecimal requestedAmount, String requestedCurrency) {
    return referenceId.equals(policyId)
        && amount.compareTo(requestedAmount) == 0
        && currency.equals(requestedCurrency);
  }

  /** Whether a pay attempt may start: a payment waiting for money, or one that failed before. */
  public boolean isPayable() {
    return status == PaymentStatus.PENDING || status == PaymentStatus.FAILED;
  }

  public void recordSuccess(String providerReference, Instant now) {
    requirePayable();
    this.status = PaymentStatus.SUCCEEDED;
    this.providerRef = providerReference;
    this.updatedAt = now;
  }

  public void recordFailure(Instant now) {
    requirePayable();
    this.status = PaymentStatus.FAILED;
    this.updatedAt = now;
  }

  /** The policy was cancelled for not being paid in time (M4b). */
  public void expire(Instant now) {
    requirePayable();
    this.status = PaymentStatus.EXPIRED;
    this.updatedAt = now;
  }

  private void requirePayable() {
    if (!isPayable()) {
      throw new IllegalStateException("Payment " + id + " is " + status + " and cannot change.");
    }
  }

  public UUID getId() {
    return id;
  }

  public String getIntentKey() {
    return intentKey;
  }

  public String getReferenceType() {
    return referenceType;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public UUID getHolderUserId() {
    return holderUserId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getProviderRef() {
    return providerRef;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
