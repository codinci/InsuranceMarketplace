package com.example.insurance.claimservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What one approved claim reserved on one limit (PLAN 9.2). Keeping the amounts lets a reservation
 * be released exactly, for example when a payout fails for good (M7). A released row is kept, with
 * the time it was released.
 */
@Entity
@Table(name = "claim_reservations")
public class ClaimReservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "utilisation_id", nullable = false)
  private UUID utilisationId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  /** 1 when this reservation also used one visit of a count limit, otherwise 0. */
  @Column(nullable = false)
  private int counted;

  @Column(name = "reserved_at", nullable = false)
  private Instant reservedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  protected ClaimReservation() {
    // For JPA.
  }

  public ClaimReservation(
      UUID claimId, UUID utilisationId, BigDecimal amount, boolean counted, Instant reservedAt) {
    this.claimId = claimId;
    this.utilisationId = utilisationId;
    this.amount = amount;
    this.counted = counted ? 1 : 0;
    this.reservedAt = reservedAt;
  }

  /** Marks the reservation released; a second call changes nothing. */
  public void markReleased(Instant now) {
    if (releasedAt == null) {
      releasedAt = now;
    }
  }

  public boolean isReleased() {
    return releasedAt != null;
  }

  public UUID getUtilisationId() {
    return utilisationId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public boolean isCounted() {
    return counted == 1;
  }
}
