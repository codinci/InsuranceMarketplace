package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.PolicyMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyMemberRepository extends JpaRepository<PolicyMember, UUID> {

  List<PolicyMember> findByPolicyIdOrderByPosition(UUID policyId);
}
