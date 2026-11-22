package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.BenefitUtilisation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenefitUtilisationRepository extends JpaRepository<BenefitUtilisation, UUID> {

  /** The total for one limit. A null member means a SHARED limit. */
  @Query(
      "select u from BenefitUtilisation u where u.policyId = :policyId and u.benefitCode = :benefit"
          + " and u.policyYear = :year and ((:member is null and u.memberId is null)"
          + " or u.memberId = :member)")
  Optional<BenefitUtilisation> findKey(
      @Param("policyId") UUID policyId,
      @Param("benefit") String benefitCode,
      @Param("year") int policyYear,
      @Param("member") String memberId);

  List<BenefitUtilisation> findByPolicyId(UUID policyId);
}
