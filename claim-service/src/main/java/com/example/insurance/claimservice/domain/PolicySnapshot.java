package com.example.insurance.claimservice.domain;

import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.PolicyExpired;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * claim-service's copy of one policy, built only from {@code policy.events} (PLAN 7, ADR-011). It
 * is a read model: nothing here is ever written back to policy-service.
 *
 * <p>Every change carries the policy's {@code policyVersion}, which policy-service increases with
 * each change (PLAN 11). A change applies only when its version is higher than the stored one. So a
 * redelivered event, or an older one that arrives late, changes nothing. A cancellation that
 * arrives before its activation leaves a row with no holder and no terms; the late activation is
 * then older than the cancellation and is ignored.
 */
@Entity
@Table(name = "policy_snapshots")
public class PolicySnapshot {

  @Id
  @Column(name = "policy_id")
  private UUID policyId;

  @Column(name = "policy_number", nullable = false, length = 40)
  private String policyNumber;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  /** Null until the policy is activated; a row that was never active has no holder. */
  @Column(name = "holder_user_id")
  private UUID holderUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SnapshotStatus status;

  @Column(name = "cover_start")
  private LocalDate coverStart;

  @Column(name = "cover_end")
  private LocalDate coverEnd;

  @Column(name = "cancel_effective_date")
  private LocalDate cancelEffectiveDate;

  @Column(name = "policy_version", nullable = false)
  private long policyVersion;

  @Column(name = "cancel_reason", length = 40)
  private String cancelReason;

  /** The {@code PolicyActivated} payload as received, kept whole so its terms stay as sold. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String activation;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PolicySnapshot() {
    // For JPA.
  }

  /** A row for a policy never seen before. Version 0 means any real event applies to it. */
  public static PolicySnapshot unseen(UUID policyId, String policyNumber, UUID insurerId) {
    PolicySnapshot snapshot = new PolicySnapshot();
    snapshot.policyId = policyId;
    snapshot.policyNumber = policyNumber;
    snapshot.insurerId = insurerId;
    snapshot.status = SnapshotStatus.CANCELLED;
    snapshot.policyVersion = 0;
    return snapshot;
  }

  /** Applies {@code PolicyActivated} if it is newer than what is stored. */
  public boolean applyActivated(
      PolicyActivated event, UUID eventInsurerId, String activationJson, Instant now) {
    if (event.policyVersion() <= policyVersion) {
      return false;
    }
    policyNumber = event.policyNumber();
    insurerId = eventInsurerId;
    holderUserId = event.holderUserId();
    status = SnapshotStatus.ACTIVE;
    coverStart = event.coverStart();
    coverEnd = event.coverEnd();
    cancelEffectiveDate = null;
    cancelReason = null;
    policyVersion = event.policyVersion();
    activation = activationJson;
    updatedAt = now;
    return true;
  }

  /** Applies {@code PolicyCancelled} if it is newer than what is stored. */
  public boolean applyCancelled(PolicyCancelled event, UUID eventInsurerId, Instant now) {
    if (event.policyVersion() <= policyVersion) {
      return false;
    }
    policyNumber = event.policyNumber();
    insurerId = eventInsurerId;
    status = SnapshotStatus.CANCELLED;
    cancelEffectiveDate = event.effectiveDate();
    cancelReason = event.reason();
    policyVersion = event.policyVersion();
    updatedAt = now;
    return true;
  }

  /** Applies {@code PolicyExpired} if it is newer than what is stored (M8b). */
  public boolean applyExpired(PolicyExpired event, UUID eventInsurerId, Instant now) {
    if (event.policyVersion() <= policyVersion) {
      return false;
    }
    policyNumber = event.policyNumber();
    insurerId = eventInsurerId;
    status = SnapshotStatus.EXPIRED;
    policyVersion = event.policyVersion();
    updatedAt = now;
    return true;
  }

  /** True if this policy belongs to the customer (the only owner check the read model needs). */
  public boolean isHeldBy(UUID userId) {
    return userId != null && userId.equals(holderUserId);
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public UUID getInsurerId() {
    return insurerId;
  }

  public UUID getHolderUserId() {
    return holderUserId;
  }

  public SnapshotStatus getStatus() {
    return status;
  }

  public LocalDate getCoverStart() {
    return coverStart;
  }

  public LocalDate getCoverEnd() {
    return coverEnd;
  }

  public LocalDate getCancelEffectiveDate() {
    return cancelEffectiveDate;
  }

  public long getPolicyVersion() {
    return policyVersion;
  }

  /** The stored activation payload as JSON, or null if the policy was never activated. */
  public String getActivation() {
    return activation;
  }
}
