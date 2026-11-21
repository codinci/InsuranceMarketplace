package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.ClaimDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

  Page<ClaimDocument> findByClaimId(UUID claimId, Pageable pageable);

  /** Every document of a claim, for the required-documents check (not paged: at most 10). */
  List<ClaimDocument> findByClaimId(UUID claimId);

  Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);

  long countByClaimId(UUID claimId);
}
