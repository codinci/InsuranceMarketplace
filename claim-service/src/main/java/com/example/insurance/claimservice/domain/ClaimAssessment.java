package com.example.insurance.claimservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The result of one automatic check on a submitted claim (PLAN 7). Append-only. */
@Entity
@Table(name = "claim_assessments")
public class ClaimAssessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "rule_code", nullable = false, length = 60)
  private String ruleCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private AssessmentResult result;

  @Column(nullable = false, length = 300)
  private String detail;

  @Column(name = "assessed_at", nullable = false)
  private Instant assessedAt;

  protected ClaimAssessment() {
    // For JPA.
  }

  public ClaimAssessment(
      UUID claimId, String ruleCode, AssessmentResult result, String detail, Instant assessedAt) {
    this.claimId = claimId;
    this.ruleCode = ruleCode;
    this.result = result;
    this.detail = detail;
    this.assessedAt = assessedAt;
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public AssessmentResult getResult() {
    return result;
  }

  public String getDetail() {
    return detail;
  }
}
