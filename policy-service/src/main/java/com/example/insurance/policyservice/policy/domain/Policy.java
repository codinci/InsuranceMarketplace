package com.example.insurance.policyservice.policy.domain;

import com.example.insurance.policyservice.catalogue.api.CoverType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bought policy (PLAN 7, 8.2). The terms snapshot is frozen at purchase and never changes
 * (CLAUDE.md, ADR-011). Members are {@link PolicyMember} rows.
 */
@Entity
@Table(name = "policies")
public class Policy {

  @Id private UUID id;

  @Column(name = "policy_number", nullable = false, unique = true, length = 60)
  private String policyNumber;

  @Column(name = "holder_user_id", nullable = false)
  private UUID holderUserId;

  @Column(name = "quote_id", nullable = false, unique = true)
  private UUID quoteId;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Column(name = "insurer_code", nullable = false, length = 40)
  private String insurerCode;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "plan_code", nullable = false, length = 40)
  private String planCode;

  @Column(name = "plan_version", nullable = false)
  private int planVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "cover_type", nullable = false, length = 20)
  private CoverType coverType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "selected_modules", nullable = false, columnDefinition = "jsonb")
  private String selectedModules;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "terms_snapshot", nullable = false, columnDefinition = "jsonb")
  private String termsSnapshot;

  @Column(name = "annual_premium", nullable = false, precision = 19, scale = 2)
  private BigDecimal annualPremium;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "premium_breakdown", nullable = false, columnDefinition = "jsonb")
  private String premiumBreakdown;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PolicyStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String declaration;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** The premium payment in payment-service, set at purchase (M4b). */
  @Column(name = "payment_id", unique = true)
  private UUID paymentId;

  /** Business version, sent in events (PLAN 10). Not the JPA lock version below. */
  @Column(name = "policy_version", nullable = false)
  private long policyVersion;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected Policy() {
    // Required by JPA.
  }

  /** A new policy awaiting payment, bought from {@code quote}. Cover runs for one year. */
  public static Policy pendingPayment(
      Quote quote,
      String policyNumber,
      String termsSnapshotJson,
      String declarationJson,
      Instant now) {
    Policy policy = new Policy();
    policy.id = UUID.randomUUID();
    policy.policyNumber = policyNumber;
    policy.holderUserId = quote.getHolderUserId();
    policy.quoteId = quote.getId();
    policy.insurerId = quote.getInsurerId();
    policy.insurerCode = quote.getInsurerCode();
    policy.planId = quote.getPlanId();
    policy.planCode = quote.getPlanCode();
    policy.planVersion = quote.getPlanVersion();
    policy.coverType = quote.getCoverType();
    policy.selectedModules = quote.getSelectedModules();
    policy.termsSnapshot = termsSnapshotJson;
    policy.annualPremium = quote.getAnnualPremium();
    policy.premiumBreakdown = quote.getBreakdown();
    policy.currency = quote.getCurrency();
    policy.startDate = quote.getStartDate();
    policy.endDate = quote.getStartDate().plusYears(1).minusDays(1);
    policy.status = PolicyStatus.PENDING_PAYMENT;
    policy.declaration = declarationJson;
    policy.createdAt = now;
    policy.policyVersion = 1;
    return policy;
  }

  /** Links the premium payment that payment-service created for this policy. */
  public void attachPayment(UUID id) {
    if (paymentId != null && !paymentId.equals(id)) {
      throw new IllegalStateException("Policy " + this.id + " already has a payment.");
    }
    this.paymentId = id;
  }

  /** The premium was paid: cover is live (PLAN 7). Only a policy awaiting payment can activate. */
  public void activate() {
    requirePendingPayment("activate");
    status = PolicyStatus.ACTIVE;
    policyVersion++;
  }

  /** Not paid in time (PLAN 8.2, M4b): the policy never starts. */
  public void cancelUnpaid() {
    requirePendingPayment("cancel for non-payment");
    status = PolicyStatus.CANCELLED;
    policyVersion++;
  }

  public boolean isPendingPayment() {
    return status == PolicyStatus.PENDING_PAYMENT;
  }

  private void requirePendingPayment(String action) {
    if (!isPendingPayment()) {
      throw new IllegalStateException("Cannot " + action + " a policy that is " + status + ".");
    }
  }

  public UUID getId() {
    return id;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public UUID getHolderUserId() {
    return holderUserId;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public String getInsurerCode() {
    return insurerCode;
  }

  public UUID getPlanId() {
    return planId;
  }

  public String getPlanCode() {
    return planCode;
  }

  public int getPlanVersion() {
    return planVersion;
  }

  public CoverType getCoverType() {
    return coverType;
  }

  public String getTermsSnapshot() {
    return termsSnapshot;
  }

  public BigDecimal getAnnualPremium() {
    return annualPremium;
  }

  public String getPremiumBreakdown() {
    return premiumBreakdown;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public PolicyStatus getStatus() {
    return status;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public long getPolicyVersion() {
    return policyVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
