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

/** One status change, append-only (PLAN 7). The code never updates or deletes these rows. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private ClaimStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private ClaimStatus toStatus;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "actor_role", nullable = false, length = 20)
  private String actorRole;

  @Column(length = 200)
  private String reason;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected ClaimStatusHistory() {
    // For JPA.
  }

  public ClaimStatusHistory(
      UUID claimId,
      ClaimStatus fromStatus,
      ClaimStatus toStatus,
      UUID actorId,
      String actorRole,
      String reason,
      Instant changedAt) {
    this.claimId = claimId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.actorId = actorId;
    this.actorRole = actorRole;
    this.reason = reason;
    this.changedAt = changedAt;
  }

  public Long getId() {
    return id;
  }

  public ClaimStatus getFromStatus() {
    return fromStatus;
  }

  public ClaimStatus getToStatus() {
    return toStatus;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getReason() {
    return reason;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
