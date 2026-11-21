package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.Claim;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Claim queries. Every customer query is filtered by the holder (CLAUDE.md ownership rule). */
public interface ClaimRepository extends JpaRepository<Claim, UUID> {

  Optional<Claim> findByIdAndHolderUserId(UUID id, UUID holderUserId);

  Page<Claim> findByHolderUserId(UUID holderUserId, Pageable pageable);

  /** The next value of the claim number sequence (V2 migration). */
  @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
  long nextClaimNumber();
}
