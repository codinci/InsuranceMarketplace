package com.example.insurance.claimservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The running total against one limit of one policy (PLAN 7, 9.2). {@code memberId} is null for a
 * SHARED limit and set for a PER_MEMBER limit. {@code version} makes concurrent approvals safe: two
 * writers that read the same total cannot both save it, so the second one retries with the new
 * total (PLAN 11).
 */
@Entity
@Table(name = "benefit_utilisation")
public class BenefitUtilisation {

  @Id private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "member_id", length = 40)
  private String memberId;

  @Column(name = "benefit_code", nullable = false, length = 40)
  private String benefitCode;

  @Column(name = "policy_year", nullable = false)
  private int policyYear;

  @Column(name = "amount_used", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountUsed;

  @Column(name = "count_used", nullable = false)
  private int countUsed;

  @Version private long version;

  protected BenefitUtilisation() {
    // For JPA.
  }

  /** A fresh total of zero for a limit that has not been used yet. */
  public static BenefitUtilisation start(
      UUID policyId, String memberId, String benefitCode, int policyYear) {
    BenefitUtilisation row = new BenefitUtilisation();
    row.id = UUID.randomUUID();
    row.policyId = policyId;
    row.memberId = memberId;
    row.benefitCode = benefitCode;
    row.policyYear = policyYear;
    row.amountUsed = BigDecimal.ZERO.setScale(2);
    row.countUsed = 0;
    return row;
  }

  /** Adds an approved amount and, when the limit counts visits, one visit. */
  public void reserve(BigDecimal amount, boolean counted) {
    amountUsed = amountUsed.add(amount);
    if (counted) {
      countUsed++;
    }
  }

  /** Takes back a reservation. A total never goes below zero. */
  public void release(BigDecimal amount, boolean counted) {
    amountUsed = amountUsed.subtract(amount).max(BigDecimal.ZERO);
    if (counted && countUsed > 0) {
      countUsed--;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getBenefitCode() {
    return benefitCode;
  }

  public int getPolicyYear() {
    return policyYear;
  }

  public BigDecimal getAmountUsed() {
    return amountUsed;
  }

  public int getCountUsed() {
    return countUsed;
  }

  public long getVersion() {
    return version;
  }
}
