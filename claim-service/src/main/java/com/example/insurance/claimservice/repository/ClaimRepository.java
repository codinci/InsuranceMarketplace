package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Claim queries. Every customer query is filtered by the holder (CLAUDE.md ownership rule). */
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

  Optional<Claim> findByIdAndHolderUserId(UUID id, UUID holderUserId);

  Page<Claim> findByHolderUserId(UUID holderUserId, Pageable pageable);

  /** One insurer's claims in the given statuses: the staff queue, scoped by the token's insurer. */
  Page<Claim> findByInsurerIdAndStatusIn(
      UUID insurerId, Collection<ClaimStatus> statuses, Pageable pageable);

  /** Every insurer's claims in the given statuses: the queue of a platform admin. */
  Page<Claim> findByStatusIn(Collection<ClaimStatus> statuses, Pageable pageable);

  /** A claim of one insurer, for staff. Another insurer's claim is simply not found. */
  Optional<Claim> findByIdAndInsurerId(UUID id, UUID insurerId);

  /** Other claims for the same member, benefit, provider, incident day and amount (a soft flag). */
  @Query(
      "select count(c) from Claim c where c.policyId = :policy and c.memberId = :member"
          + " and c.benefitCode = :benefit and c.providerName = :provider"
          + " and c.incidentDate = :incident and c.amountClaimed = :amount and c.id <> :self"
          + " and c.status not in :ignored")
  long countPossibleDuplicates(
      @Param("policy") UUID policyId,
      @Param("member") String memberId,
      @Param("benefit") String benefitCode,
      @Param("provider") String providerName,
      @Param("incident") LocalDate incidentDate,
      @Param("amount") BigDecimal amount,
      @Param("self") UUID self,
      @Param("ignored") Collection<ClaimStatus> ignored);

  /** The member's other claims submitted since {@code since} (a soft flag). */
  @Query(
      "select count(c) from Claim c where c.policyId = :policy and c.memberId = :member"
          + " and c.id <> :self and c.submittedAt >= :since and c.status not in :ignored")
  long countRecentClaims(
      @Param("policy") UUID policyId,
      @Param("member") String memberId,
      @Param("self") UUID self,
      @Param("since") Instant since,
      @Param("ignored") Collection<ClaimStatus> ignored);

  /** The next value of the claim number sequence (V2 migration). */
  @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
  long nextClaimNumber();
}
