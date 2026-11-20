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
 * A server-priced offer (PLAN 8.2), valid for 30 days. The premium is fixed here; purchase never
 * recomputes it from the client (CLAUDE.md). JSON columns hold the members, the module selection
 * and the price breakdown.
 */
@Entity
@Table(name = "quotes")
public class Quote {

  @Id private UUID id;

  @Column(name = "holder_user_id", nullable = false)
  private UUID holderUserId;

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

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String members;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "selected_modules", nullable = false, columnDefinition = "jsonb")
  private String selectedModules;

  @Column(name = "annual_premium", nullable = false, precision = 19, scale = 2)
  private BigDecimal annualPremium;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String breakdown;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuoteStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected Quote() {
    // Required by JPA.
  }

  /** The values of a new quote, grouped so the factory stays readable. */
  public record Terms(
      UUID holderUserId,
      UUID insurerId,
      String insurerCode,
      UUID planId,
      String planCode,
      int planVersion,
      CoverType coverType,
      LocalDate startDate,
      String membersJson,
      String selectedModulesJson,
      BigDecimal annualPremium,
      String breakdownJson,
      String currency) {}

  public static Quote open(Terms terms, Instant now, Instant validUntil) {
    Quote quote = new Quote();
    quote.id = UUID.randomUUID();
    quote.holderUserId = terms.holderUserId();
    quote.insurerId = terms.insurerId();
    quote.insurerCode = terms.insurerCode();
    quote.planId = terms.planId();
    quote.planCode = terms.planCode();
    quote.planVersion = terms.planVersion();
    quote.coverType = terms.coverType();
    quote.startDate = terms.startDate();
    quote.members = terms.membersJson();
    quote.selectedModules = terms.selectedModulesJson();
    quote.annualPremium = terms.annualPremium();
    quote.breakdown = terms.breakdownJson();
    quote.currency = terms.currency();
    quote.validUntil = validUntil;
    quote.status = QuoteStatus.OPEN;
    quote.createdAt = now;
    return quote;
  }

  public boolean expiredAt(Instant now) {
    return now.isAfter(validUntil);
  }

  /** Marks the quote as bought from. Only an OPEN quote can be used. */
  public void markUsed() {
    if (status != QuoteStatus.OPEN) {
      throw new IllegalStateException("Quote " + id + " is " + status + ".");
    }
    status = QuoteStatus.USED;
  }

  public UUID getId() {
    return id;
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

  public LocalDate getStartDate() {
    return startDate;
  }

  public String getMembers() {
    return members;
  }

  public String getSelectedModules() {
    return selectedModules;
  }

  public BigDecimal getAnnualPremium() {
    return annualPremium;
  }

  public String getBreakdown() {
    return breakdown;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public QuoteStatus getStatus() {
    return status;
  }
}
