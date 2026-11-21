package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.ClaimAssessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimAssessmentRepository extends JpaRepository<ClaimAssessment, Long> {

  /** The checks of one claim, in the order they ran (at most a few per submission). */
  List<ClaimAssessment> findByClaimIdOrderByIdAsc(UUID claimId);
}
