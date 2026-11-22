package com.example.insurance.claimservice.domain;

import com.example.insurance.claimservice.statemachine.ClaimStateMachine;
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
 * A claim against one benefit of a policy (PLAN 7). The status changes only through {@link
 * ClaimStateMachine#move}, and the service always records the change in the status history. The
 * version is optimistic locking, so two writers cannot both move the same claim.
 */
@Entity
@Table(name = "claims")
public class Claim {

  @Id private UUID id;

  @Column(name = "claim_number", nullable = false, unique = true, length = 40)
  private String claimNumber;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "holder_user_id", nullable = false)
  private UUID holderUserId;

  @Column(name = "member_id", nullable = false, length = 40)
  private String memberId;

  @Column(name = "module_code", nullable = false, length = 40)
  private String moduleCode;

  @Column(name = "benefit_code", nullable = false, length = 40)
  private String benefitCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Cause cause;

  @Column(nullable = false)
  private boolean surgical;

  /** The condition tags as a JSON array, for waiting rules (any-of). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "condition_tags", nullable = false, columnDefinition = "jsonb")
  private String conditionTags;

  @Column(name = "incident_date", nullable = false)
  private LocalDate incidentDate;

  @Column(name = "provider_name", nullable = false, length = 120)
  private String providerName;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(name = "amount_claimed", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountClaimed;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClaimStatus status;

  @Column(name = "rejection_reason", length = 60)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** What the evaluator says can be paid (PLAN 9.2); null until review starts. */
  @Column(name = "amount_eligible", precision = 19, scale = 2)
  private BigDecimal amountEligible;

  /** What the reviewer approved; never more than {@code amountEligible}. */
  @Column(name = "amount_approved", precision = 19, scale = 2)
  private BigDecimal amountApproved;

  @Column(name = "reviewer_id")
  private UUID reviewerId;

  @Column(name = "reviewer_role", length = 20)
  private String reviewerRole;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  /** True when a soft flag was raised at submission (PLAN 9.1). */
  @Column(nullable = false)
  private boolean flagged;

  @Version private long version;

  protected Claim() {
    // For JPA.
  }

  /** A new claim in DRAFT. The service assigns the number from the sequence. */
  public static Claim draft(
      UUID id,
      String claimNumber,
      UUID insurerId,
      UUID policyId,
      UUID holderUserId,
      Details details,
      String currency,
      Instant now) {
    Claim claim = new Claim();
    claim.id = id;
    claim.claimNumber = claimNumber;
    claim.insurerId = insurerId;
    claim.policyId = policyId;
    claim.holderUserId = holderUserId;
    claim.applyDetails(details);
    claim.currency = currency;
    claim.status = ClaimStatus.DRAFT;
    claim.createdAt = now;
    claim.updatedAt = now;
    return claim;
  }

  /** The member's details of a claim, as the customer states them. */
  public record Details(
      String memberId,
      String moduleCode,
      String benefitCode,
      Cause cause,
      boolean surgical,
      String conditionTagsJson,
      LocalDate incidentDate,
      String providerName,
      String description,
      BigDecimal amountClaimed) {}

  /** Replaces the details. Allowed only in DRAFT; the service checks that. */
  public void editDetails(Details details, Instant now) {
    applyDetails(details);
    updatedAt = now;
  }

  private void applyDetails(Details details) {
    memberId = details.memberId();
    moduleCode = details.moduleCode();
    benefitCode = details.benefitCode();
    cause = details.cause();
    surgical = details.surgical();
    conditionTags = details.conditionTagsJson();
    incidentDate = details.incidentDate();
    providerName = details.providerName();
    description = details.description();
    amountClaimed = details.amountClaimed();
  }

  /** Moves to {@code to} if the machine allows it, and returns the status it came from. */
  public ClaimStatus moveTo(ClaimStatus to, Instant now) {
    ClaimStatus from = status;
    status = ClaimStateMachine.move(from, to);
    updatedAt = now;
    return from;
  }

  /** Stamps the submission time, once. */
  public void markSubmitted(Instant now) {
    submittedAt = now;
  }

  /** Records why the hard checks rejected the claim. */
  public void rejectWith(String reason) {
    rejectionReason = reason;
  }

  /** Records that a soft flag was raised at submission. */
  public void markFlagged() {
    flagged = true;
  }

  /** The most the reviewer may approve, as the evaluator last computed it. */
  public void recordEligible(BigDecimal eligible) {
    amountEligible = eligible;
  }

  /** Records who decided, why, and when. The reason is the reviewer's own words. */
  public void recordDecision(UUID reviewer, String role, String reason, Instant now) {
    reviewerId = reviewer;
    reviewerRole = role;
    decisionReason = reason;
    reviewedAt = now;
  }

  /** Records the approved amount; the caller has already checked it against the evaluator. */
  public void approveAmount(BigDecimal approved) {
    amountApproved = approved;
  }

  public boolean isDraft() {
    return status == ClaimStatus.DRAFT;
  }

  public UUID getId() {
    return id;
  }

  public String getClaimNumber() {
    return claimNumber;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public UUID getHolderUserId() {
    return holderUserId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getModuleCode() {
    return moduleCode;
  }

  public String getBenefitCode() {
    return benefitCode;
  }

  public Cause getCause() {
    return cause;
  }

  public boolean isSurgical() {
    return surgical;
  }

  public String getConditionTags() {
    return conditionTags;
  }

  public LocalDate getIncidentDate() {
    return incidentDate;
  }

  public String getProviderName() {
    return providerName;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmountClaimed() {
    return amountClaimed;
  }

  public String getCurrency() {
    return currency;
  }

  public ClaimStatus getStatus() {
    return status;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public BigDecimal getAmountEligible() {
    return amountEligible;
  }

  public BigDecimal getAmountApproved() {
    return amountApproved;
  }

  public UUID getReviewerId() {
    return reviewerId;
  }

  public String getReviewerRole() {
    return reviewerRole;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public boolean isFlagged() {
    return flagged;
  }

  public long getVersion() {
    return version;
  }
}
