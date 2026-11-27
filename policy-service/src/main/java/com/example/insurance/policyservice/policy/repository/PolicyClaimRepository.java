package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.PolicyClaim;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyClaimRepository extends JpaRepository<PolicyClaim, UUID> {

  long countByPolicyId(UUID policyId);
}
